package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UnifiedToolSystem - Generalizable tool calling architecture
 * 
 * SOLVES:
 * 1. KV Cache Reuse: Single base system prompt, task-specific instructions in user message
 * 2. False Positive Prevention: Multi-layer validation before tool execution
 * 3. Function Call Format: Robust parsing with fallbacks
 * 4. Latency Optimization: Pattern matching + unified prompts reduce LLM calls
 * 
 * ARCHITECTURE:
 * - Base system prompt (cached) + task instructions (user message)
 * - Pattern matching for obvious cases (no LLM)
 * - LLM for ambiguous cases with validation
 * - Unified function call parsing
 * - Tool execution with validation
 */
class UnifiedToolSystem(
    private val context: Context,
    private val database: AppDatabase,
    private val llmEngine: LLMEngine
) {
    
    private val functionCallingSystem = FunctionCallingSystem(context)
    
    companion object {
        private const val TAG = "UnifiedToolSystem"
        
        /**
         * UNIFIED BASE SYSTEM PROMPT - Reused across all tool operations
         * This enables KV cache hits when switching between tasks
         * Task-specific instructions go in user message
         */
        private const val UNIFIED_BASE_PROMPT = """You are a tool-calling assistant.

Available tools:
- SAVE_NOTE: Save user notes/reminders
- RETRIEVE_NOTE: Find saved notes
- WEB_SEARCH: Search web for current information
- SEARCH_NOTIFICATIONS: Search user notifications

Output format: ONE word (tool name) or NONE for conversation.

Rules:
1. Greetings (hi, hello, thanks) → NONE
2. Simple responses (ok, yes, no) → NONE  
3. Explicit tool requests → tool name
4. Ambiguous queries → tool name if clear intent, else NONE"""
    }
    
    /**
     * Detect tool intent with unified architecture
     * Returns ToolIntent or null (for conversation)
     */
    suspend fun detectIntent(userQuery: String): ToolIntent? = withContext(Dispatchers.IO) {
        // Layer 1: Pattern matching (instant, no LLM)
        val patternResult = detectIntentPattern(userQuery)
        if (patternResult != null) {
            Log.i(TAG, "✓ Pattern match: $patternResult")
            return@withContext patternResult
        }
        
        // Layer 2: LLM classification (with unified prompt for cache reuse)
        val llmResult = detectIntentLLM(userQuery)
        
        // Layer 3: Validation (prevent false positives)
        if (llmResult != null && validateIntent(userQuery, llmResult)) {
            Log.i(TAG, "✓ Validated LLM result: $llmResult")
            return@withContext llmResult
        }
        
        Log.i(TAG, "✓ No tool needed (conversation)")
        null
    }
    
    /**
     * Pattern-based detection (Layer 1)
     * Catches obvious cases instantly without LLM
     */
    private fun detectIntentPattern(query: String): ToolIntent? {
        val lower = query.lowercase().trim()
            .removeSuffix("!").removeSuffix(".").removeSuffix(",")
            .removeSuffix("?").trim()
        
        // Explicit greetings - NO TOOL
        val greetings = setOf(
            "hi", "hello", "hey", "hey there", "hi there",
            "thanks", "thank you", "thank u", "thx",
            "ok", "okay", "okey", "k", "kk",
            "yes", "yeah", "yep", "yup", "sure",
            "no", "nope", "nah",
            "bye", "goodbye", "see ya", "see you",
            "how are you", "how's it going", "what's up", "whats up", "sup",
            "good morning", "good afternoon", "good evening", "good night"
        )
        
        if (greetings.contains(lower) || (lower.split("\\s+".toRegex()).size <= 2 && 
            lower.split("\\s+".toRegex()).all { it.length <= 4 || greetings.contains(it) })) {
            return null // No tool needed
        }
        
        // High-confidence tool patterns
        val savePatterns = listOf(
            "save note", "save reminder", "remember", "remind me",
            "don't forget", "write down", "keep track", "store"
        )
        if (savePatterns.any { lower.contains(it) }) {
            return ToolIntent.SAVE_NOTE
        }
        
        val retrievePatterns = listOf(
            "find my", "show my", "get my", "what's my", "whats my",
            "where is my", "what did i save", "my password", "my wifi"
        )
        if (retrievePatterns.any { lower.contains(it) }) {
            return ToolIntent.RETRIEVE_NOTE
        }
        
        val searchPatterns = listOf(
            "search for", "look up", "find out", "what is the",
            "price of", "bitcoin", "stock price"
        )
        if (searchPatterns.any { lower.contains(it) }) {
            return ToolIntent.WEB_SEARCH
        }
        
        val notificationPatterns = listOf(
            "notification", "any updates", "my mail", "my email", "alerts",
            "recent notifications", "latest notifications", "any messages"
        )
        if (notificationPatterns.any { lower.contains(it) }) {
            return ToolIntent.SEARCH_NOTIFICATIONS
        }
        
        return null // Ambiguous - use LLM
    }
    
    /**
     * LLM-based detection (Layer 2)
     * Uses UNIFIED prompt for KV cache reuse
     */
    private suspend fun detectIntentLLM(query: String): ToolIntent? {
        return try {
            // Task-specific instruction in user message (not system prompt)
            // This allows KV cache reuse across different tasks
            val userMessage = """Task: Classify intent
Query: "$query"
Output:"""
            
            val result = llmEngine.generateWithCache(
                systemPrompt = UNIFIED_BASE_PROMPT,
                userMessage = userMessage,
                maxTokens = 10,
                temperature = 0.1f
            ).getOrNull() ?: return null
            
            val classification = result.trim().uppercase()
            Log.d(TAG, "LLM classification: '$classification'")
            
            when {
                classification.contains("SAVE") -> ToolIntent.SAVE_NOTE
                classification.contains("RETRIEVE") -> ToolIntent.RETRIEVE_NOTE
                classification.contains("SEARCH_NOTIFICATIONS") || classification.contains("NOTIFICATION") -> ToolIntent.SEARCH_NOTIFICATIONS
                classification.contains("SEARCH") || classification.contains("WEB") -> ToolIntent.WEB_SEARCH
                classification.contains("NONE") -> null
                else -> null // Default to conversation for unclear results
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM classification failed", e)
            null
        }
    }
    
    /**
     * Validation layer (Layer 3)
     * Prevents false positives by validating intent against query
     */
    private fun validateIntent(query: String, intent: ToolIntent): Boolean {
        val lower = query.lowercase().trim()
        
        // Reject if query is too short for tool execution
        if (lower.length < 3) {
            Log.w(TAG, "Query too short for tool: '$query'")
            return false
        }
        
        // Reject greetings even if LLM misclassified
        val greetings = setOf("hi", "hello", "hey", "thanks", "ok", "yes", "no", "bye")
        if (greetings.contains(lower) || lower.split("\\s+".toRegex()).all { 
            it.length <= 4 || greetings.contains(it) 
        }) {
            Log.w(TAG, "Rejected greeting misclassified as tool: '$query' → $intent")
            return false
        }
        
        // Validate intent-specific patterns
        return when (intent) {
            ToolIntent.SAVE_NOTE -> {
                // Must have some content to save
                lower.length > 5 && !greetings.contains(lower)
            }
            ToolIntent.RETRIEVE_NOTE -> {
                // Must have search terms
                lower.length > 5 && (lower.contains("my ") || lower.contains("find") || lower.contains("show"))
            }
            ToolIntent.WEB_SEARCH -> {
                // Must have search query
                lower.length > 5 && (lower.contains("what") || lower.contains("search") || lower.contains("price"))
            }
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                // Must have notification-related keywords
                lower.length > 3 && (lower.contains("notification") || lower.contains("update") || 
                    lower.contains("mail") || lower.contains("alert") || lower.contains("message"))
            }
        }
    }
    
    /**
     * Execute tool with unified parameter extraction
     * Uses same unified prompt structure for KV cache reuse
     */
    suspend fun executeTool(
        intent: ToolIntent,
        userQuery: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Extract parameters using unified prompt
            val params = extractParameters(intent, userQuery)
            
            // Validate parameters before execution
            if (!validateParameters(intent, params)) {
                return@withContext Result.failure(Exception("Invalid parameters extracted"))
            }
            
            // Execute tool
            val functionCall = FunctionCall(
                name = when (intent) {
                    ToolIntent.SAVE_NOTE -> "save_note"
                    ToolIntent.RETRIEVE_NOTE -> "retrieve_notes"
                    ToolIntent.WEB_SEARCH -> "web_search"
                    ToolIntent.SEARCH_NOTIFICATIONS -> "search_notifications"
                },
                arguments = params
            )
            
            functionCallingSystem.executeFunction(functionCall)
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Extract parameters using unified prompt
     * Task-specific instructions in user message for cache reuse
     */
    private suspend fun extractParameters(intent: ToolIntent, query: String): Map<String, String> {
        // Use unified base prompt + task instruction in user message
        val taskInstruction = when (intent) {
            ToolIntent.SAVE_NOTE -> """Extract: content (required), title (optional, generate if missing)
Format: save_note(content="...", title="...")
Query: "$query"
Output:"""
            ToolIntent.RETRIEVE_NOTE -> """Extract: query (required)
Format: retrieve_notes(query="...")
Query: "$query"
Output:"""
            ToolIntent.WEB_SEARCH -> """Extract: query (required)
Format: web_search(query="...")
Query: "$query"
Output:"""
            ToolIntent.SEARCH_NOTIFICATIONS -> """Extract: query (optional, defaults to "recent"), app_name (optional), hours (optional, default 24)
Format: search_notifications(query="...", app_name="...", hours="24")
Query: "$query"
Output:"""
        }
        
        val result = llmEngine.generateWithCache(
            systemPrompt = UNIFIED_BASE_PROMPT,
            userMessage = taskInstruction,
            maxTokens = 128,
            temperature = 0.3f
        ).getOrNull() ?: return emptyMap()
        
        // Parse function call from response
        val functionCall = functionCallingSystem.parseFunctionCall(result)
        return functionCall?.arguments ?: extractDirectParameters(intent, query)
    }
    
    /**
     * Direct parameter extraction (fallback)
     */
    private fun extractDirectParameters(intent: ToolIntent, query: String): Map<String, String> {
        return when (intent) {
            ToolIntent.SAVE_NOTE -> {
                val title = query.take(50).trim()
                mapOf(
                    "title" to (if (title.isNotBlank()) title else "Note"),
                    "content" to query,
                    "category" to "general"
                )
            }
            ToolIntent.RETRIEVE_NOTE -> {
                mapOf("query" to query, "limit" to "10")
            }
            ToolIntent.WEB_SEARCH -> {
                mapOf("query" to query, "max_results" to "5")
            }
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                val lowerQuery = query.lowercase()
                val extractedQuery = when {
                    lowerQuery.contains("mail") || lowerQuery.contains("gmail") -> "mail"
                    lowerQuery.contains("recent") || lowerQuery.contains("latest") -> "recent"
                    else -> query
                }
                mapOf("query" to extractedQuery, "hours" to "24", "limit" to "10")
            }
        }
    }
    
    /**
     * Validate extracted parameters
     */
    private fun validateParameters(intent: ToolIntent, params: Map<String, String>): Boolean {
        return when (intent) {
            ToolIntent.SAVE_NOTE -> {
                params.containsKey("content") && params["content"]?.isNotBlank() == true
            }
            ToolIntent.RETRIEVE_NOTE -> {
                params.containsKey("query") && params["query"]?.isNotBlank() == true
            }
            ToolIntent.WEB_SEARCH -> {
                params.containsKey("query") && params["query"]?.isNotBlank() == true
            }
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                // Query is optional for notification search (defaults to "recent")
                true
            }
        }
    }
}
