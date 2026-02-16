package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import com.confidant.ai.conversation.*
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.telegram.StreamingResponseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * IntelligentToolManager - Automatic tool detection and execution with multi-turn support
 * 
 * Detects when user queries need tool execution (notes, web search, etc.)
 * and automatically triggers the appropriate tool with proper prompting.
 * 
 * Features:
 * 1. Pattern-based tool detection (80% instant)
 * 2. Multi-turn parameter collection (asks follow-up questions)
 * 3. Confirmation flow (shows preview before saving)
 * 4. Modification support (allows user to change parameters)
 * 5. LLM-based tool calling for complex queries
 * 6. Parallel tool execution support
 * 7. Status updates during execution
 * 8. Proper error handling and fallbacks
 */
class IntelligentToolManager(
    private val context: Context,
    private val database: AppDatabase,
    private val llmEngine: LLMEngine,
    private val statusCallback: suspend (String) -> Unit
) {
    
    private val functionCallingSystem = FunctionCallingSystem(context)
    private val conversationStateManager = ConversationStateManager(context)
    private val parameterCollector = ParameterCollector(context, llmEngine)
    private val confirmationManager = ConfirmationManager(context)
    
    /**
     * Detect if query needs tool execution
     * 
     * OPTIMIZED 2026: Keyword-first detection (instant, deterministic)
     * - On-device LLM (1.2B) is too slow (~29s) and unreliable for classification
     * - Keyword matching with fuzzy/prefix support handles typos
     * - Validation layer prevents false positives
     */
    suspend fun detectToolIntent(userQuery: String): ToolIntent? {
        // FAST PATH: Check for greetings first (instant rejection)
        val lower = userQuery.lowercase().trim()
            .removeSuffix("!").removeSuffix(".").removeSuffix(",")
            .removeSuffix("?").trim()
        
        val greetings = setOf(
            "hi", "hello", "hey", "hey there", "hi there",
            "thanks", "thank you", "thank u", "thx",
            "ok", "okay", "okey", "k", "kk",
            "yes", "yeah", "yep", "yup", "sure",
            "no", "nope", "nah",
            "bye", "goodbye", "see ya", "see you"
        )
        
        if (greetings.contains(lower) || lower.length < 3) {
            Log.i(TAG, "✓ Fast path: greeting/short query → no tool")
            return null
        }
        
        // PRIMARY: Keyword-based detection with fuzzy matching (instant)
        val keywordResult = detectIntentFromKeywords(userQuery)
        
        if (keywordResult != null && validateToolIntent(userQuery, keywordResult)) {
            Log.i(TAG, "✓ Keyword detection: $keywordResult (instant)")
            return keywordResult
        }
        
        // No tool needed (conversational)
        Log.i(TAG, "✓ No tool needed - conversational response")
        return null
    }
    
    
    /**
     * Validation layer - prevents false positives
     * Rejects misclassifications like "hi" → SAVE_NOTE
     */
    private fun validateToolIntent(query: String, intent: ToolIntent): Boolean {
        val lower = query.lowercase().trim()
            .removeSuffix("!").removeSuffix(".").removeSuffix(",")
            .removeSuffix("?").trim()
        
        // Reject if query is too short
        if (lower.length < 3) {
            Log.w(TAG, "⚠ Rejected: Query too short for tool execution")
            return false
        }
        
        // Reject explicit greetings even if LLM misclassified
        val greetings = setOf(
            "hi", "hello", "hey", "hey there", "hi there",
            "thanks", "thank you", "thank u", "thx",
            "ok", "okay", "okey", "k", "kk",
            "yes", "yeah", "yep", "yup", "sure",
            "no", "nope", "nah",
            "bye", "goodbye", "see ya", "see you"
        )
        
        if (greetings.contains(lower)) {
            Log.w(TAG, "⚠ Rejected: Greeting misclassified as tool: '$query' → $intent")
            return false
        }
        
        // Reject very short casual responses
        val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 2 && words.all { it.length <= 4 || greetings.contains(it) }) {
            Log.w(TAG, "⚠ Rejected: Casual response misclassified as tool: '$query' → $intent")
            return false
        }
        
        // Intent-specific validation
        return when (intent) {
            ToolIntent.SAVE_NOTE -> {
                // Must have meaningful content (not just "hi")
                lower.length > 5 && !greetings.contains(lower)
            }
            ToolIntent.RETRIEVE_NOTE -> {
                // Very lenient validation - trust LLM classification
                // Personal queries can be short ("my password") or longer ("when i have to visit gardener")
                // Just ensure it's not a greeting
                !greetings.contains(lower) && lower.length > 2
            }
            ToolIntent.WEB_SEARCH -> {
                // FIXED: Only require minimum length + not a greeting
                // Previous check was too restrictive (required "what"/"search"/"price")
                // This missed valid queries like "latest news", "weather in London", "how to make pasta"
                lower.length > 3 && !greetings.contains(lower)
            }
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                // Must have notification-related keywords
                lower.length > 3 && (lower.contains("notification") || lower.contains("update") || 
                    lower.contains("mail") || lower.contains("alert") || lower.contains("message"))
            }
        }
    }
    
    /**
     * Comprehensive keyword-based intent detection with FUZZY MATCHING
     * Primary detection method — instant, deterministic, typo-resilient
     * 
     * Uses prefix matching (e.g., "notif" matches "notification", "notificarions")
     * to handle common typos without needing the LLM
     */
    private fun detectIntentFromKeywords(userQuery: String): ToolIntent? {
        val lowerQuery = userQuery.lowercase().trim()
        val words = lowerQuery.split(Regex("\\s+"))
        
        // Helper: check if any word in query starts with prefix (fuzzy match)
        fun hasPrefix(prefix: String): Boolean = words.any { it.startsWith(prefix) }
        // Helper: exact substring match
        fun has(keyword: String): Boolean = lowerQuery.contains(keyword)
        
        return when {
            // ═══════════════════════════════════════════
            // SAVE_NOTE: User wants to save/store data
            // ═══════════════════════════════════════════
            (has("save") || hasPrefix("rememb") || has("store") || hasPrefix("remind") ||
             has("don't forget") || has("keep track") || has("write down") ||
             has("make a note") || has("take note") || has("create note")) &&
            // Must have something to save (not just "save" alone)
            (has("note") || hasPrefix("passw") || hasPrefix("appoint") ||
             hasPrefix("remind") || hasPrefix("email") || hasPrefix("credent") ||
             has("to ") || has("that ") || has("of ")) -> {
                Log.i(TAG, "✓ Keyword: SAVE_NOTE")
                ToolIntent.SAVE_NOTE
            }
            
            // ═══════════════════════════════════════════
            // RETRIEVE_NOTE: User wants to find saved data
            // ═══════════════════════════════════════════
            (has("what") || has("when") || has("find") || has("get") ||
             has("show") || has("have i") || has("did i") || has("do i have") ||
             has("where") || has("list")) && 
            (has("my ") || hasPrefix("passw") || has("note") ||
             hasPrefix("appoint") || has("saved") || has("stored") ||
             hasPrefix("remind") || hasPrefix("schedul")) -> {
                Log.i(TAG, "✓ Keyword: RETRIEVE_NOTE")
                ToolIntent.RETRIEVE_NOTE
            }
            
            // ═══════════════════════════════════════════
            // SEARCH_NOTIFICATIONS: User asks about notifications
            // Fuzzy: "notif" matches notification/notificarions/notifcation
            // ═══════════════════════════════════════════
            (hasPrefix("notif") || hasPrefix("updat") || has("mail") ||
             hasPrefix("alert") || has("message") || has("inbox")) &&
            (has("my ") || has("any ") || hasPrefix("recent") ||
             hasPrefix("latest") || has("new ") || has("check") ||
             has("came") || has("got") || has("received") || has("till")) -> {
                Log.i(TAG, "✓ Keyword: SEARCH_NOTIFICATIONS")
                ToolIntent.SEARCH_NOTIFICATIONS
            }
            // Also match if just asking about notifications without qualifiers
            (hasPrefix("notif") && lowerQuery.length > 8) -> {
                Log.i(TAG, "✓ Keyword: SEARCH_NOTIFICATIONS (notification-only)")
                ToolIntent.SEARCH_NOTIFICATIONS
            }
            
            // ═══════════════════════════════════════════
            // WEB_SEARCH: General information queries
            // ═══════════════════════════════════════════
            (has("price") || has("what is") || has("what are") ||
             has("search for") || has("look up") || has("google") ||
             has("how to") || has("how do") || has("how much") || has("how many") ||
             hasPrefix("latest") || has("news") || hasPrefix("weather") ||
             has("who is") || has("who was") || has("who are") ||
             has("where is") || has("where are") ||
             has("tell me about") || hasPrefix("explain") || hasPrefix("defin") ||
             has("meaning of") || hasPrefix("populat") || has("capital of") ||
             has("stock") || has("score") || has("result") ||
             has("current affairs") || has("happening")) &&
            !has("my ") && !has("save") -> {
                Log.i(TAG, "✓ Keyword: WEB_SEARCH")
                ToolIntent.WEB_SEARCH
            }
            
            else -> null
        }
    }
    
    /**
     * Execute tool flow with multi-turn support
     * Handles parameter collection, confirmation, and modification
     */
    suspend fun executeToolFlowWithConversation(
        userQuery: String,
        toolIntent: ToolIntent,
        userId: Long,
        streamingManager: StreamingResponseManager,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if user has an active execution
            val activeExecution = conversationStateManager.getActiveExecution(userId)
            
            if (activeExecution != null) {
                // Continue existing execution
                return@withContext continueExecution(userId, userQuery, activeExecution)
            } else {
                // Start new execution
                return@withContext startNewExecution(userId, userQuery, toolIntent, streamingManager, conversationHistory)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Tool flow failed", e)
            conversationStateManager.completeExecution(userId)
            Result.failure(e)
        }
    }
    
    /**
     * Start a new tool execution
     * OPTIMIZED: Skip LLM for simple note saves (10x faster!)
     */
    private suspend fun startNewExecution(
        userId: Long,
        userQuery: String,
        toolIntent: ToolIntent,
        @Suppress("UNUSED_PARAMETER") streamingManager: StreamingResponseManager,
        @Suppress("UNUSED_PARAMETER") conversationHistory: List<Pair<String, String>>
    ): Result<String> {
        Log.i(TAG, "=== STARTING NEW TOOL EXECUTION ===")
        Log.i(TAG, "Query: $userQuery")
        Log.i(TAG, "Intent: $toolIntent")
        
        // GENERALIZED FAST PATH: Skip LLM for simple operations
        // RETRIEVE_NOTE: Always skip LLM - extract query directly (fast, accurate)
        // SEARCH_NOTIFICATIONS: Always skip LLM - extract query directly (fast, accurate)
        // SAVE_NOTE: Skip LLM for simple saves
        val initialParams = when {
            toolIntent == ToolIntent.RETRIEVE_NOTE -> {
                // FAST PATH: No LLM for retrieval - extract query directly from user message
                Log.i(TAG, "⚡ FAST PATH: RETRIEVE_NOTE - skipping LLM, extracting query directly")
                statusCallback("🔍 Searching your notes...")
                val extractedQuery = extractSearchQuery(userQuery)
                mutableMapOf(
                    "query" to extractedQuery.ifEmpty { userQuery },
                    "limit" to "5",  // Max 5 results as requested
                    "search_type" to "keyword"  // Use fast keyword/BM25 search
                )
            }
            toolIntent == ToolIntent.SEARCH_NOTIFICATIONS -> {
                // FAST PATH: No LLM for notification search - extract query directly
                Log.i(TAG, "⚡ FAST PATH: SEARCH_NOTIFICATIONS - skipping LLM, extracting query directly")
                statusCallback("🔍 Searching notifications...")
                val extractedQuery = extractNotificationQuery(userQuery)
                mutableMapOf(
                    "query" to extractedQuery.ifEmpty { "recent" },
                    "hours" to "24",  // Default to last 24 hours
                    "limit" to "10"
                )
            }
            toolIntent == ToolIntent.SAVE_NOTE && isSimpleNoteSave(userQuery) -> {
                Log.i(TAG, "⚡ FAST PATH: Simple note save detected, skipping LLM")
                statusCallback("⚙️ Saving...")
                extractDirectParameters(userQuery, toolIntent)
            }
            else -> {
                // Complex query - use LLM for parameter extraction
                Log.d(TAG, "Using LLM for parameter extraction")
                statusCallback("🤔 Understanding your request...")
                
                // STRICT LFM2.5 FORMAT: Get system prompt with tools from FunctionCallingSystem
                val baseSystemPrompt = buildToolSystemPrompt(toolIntent)
                val systemPromptWithTools = functionCallingSystem.getSystemPromptWithTools(
                    basePrompt = baseSystemPrompt,
                    searchEnabled = true,
                    newsEnabled = false,  // STRICT: Disabled - not in allowed tools
                    weatherEnabled = false,  // STRICT: Disabled - not in allowed tools
                    notesEnabled = true,
                    notificationsEnabled = true
                )
                
                // TASK-SPECIFIC INSTRUCTIONS IN USER MESSAGE (not system prompt)
                val userMessageWithContext = buildTaskUserMessage(toolIntent, userQuery)
                
                // OPTIMIZED GENERATION PARAMETERS for LFM2.5 tool calling
                // Temperature 0.1: LFM2.5 recommended for precise format matching
                // Max tokens 128: Sufficient for function calls (allows for tool call + some text)
                val llmResult = llmEngine.generateWithCache(
                    systemPrompt = systemPromptWithTools,
                    userMessage = userMessageWithContext,
                    maxTokens = 128,
                    temperature = 0.1f  // OPTIMIZED: Use LFM2.5 recommended temperature (0.1) for best results
                )
                
                if (llmResult.isFailure) {
                    return Result.failure(llmResult.exceptionOrNull()!!)
                }
                
                val llmResponse = llmResult.getOrNull()!!
                val functionCall = functionCallingSystem.parseFunctionCall(llmResponse)
                
                if (functionCall != null) {
                    Log.i(TAG, "✓ Parsed function call: ${functionCall.name}(${functionCall.arguments})")
                    functionCall.arguments.toMutableMap()
                } else {
                    Log.w(TAG, "No function call parsed, extracting directly")
                    extractDirectParameters(userQuery, toolIntent)
                }
            }
        }
        
        // Validate parameters (ensure required ones are present)
        val validatedParams = when (toolIntent) {
            ToolIntent.RETRIEVE_NOTE -> {
                // Query should already be extracted above, but ensure it exists
                if (initialParams.containsKey("query") && initialParams["query"]?.isNotBlank() == true) {
                    initialParams.toMutableMap()
                } else {
                    // Fallback: use original query
                    mutableMapOf(
                        "query" to userQuery,
                        "limit" to "5",
                        "search_type" to "keyword"
                    )
                }
            }
            ToolIntent.WEB_SEARCH -> {
                // For web_search, ensure query exists
                if (!initialParams.containsKey("query") || initialParams["query"]?.isNotBlank() != true) {
                    initialParams.toMutableMap().apply {
                        put("query", extractSearchQuery(userQuery).ifEmpty { userQuery })
                        put("max_results", "5")
                    }
                } else {
                    initialParams.toMutableMap()
                }
            }
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                // For notification search, ensure query exists
                if (!initialParams.containsKey("query") || initialParams["query"]?.isNotBlank() != true) {
                    initialParams.toMutableMap().apply {
                        put("query", extractNotificationQuery(userQuery).ifEmpty { "recent" })
                        put("hours", "24")
                        put("limit", "10")
                    }
                } else {
                    initialParams.toMutableMap()
                }
            }
            else -> initialParams.toMutableMap()
        }
        
        // Start execution state
        val toolName = when (toolIntent) {
            ToolIntent.SAVE_NOTE -> "save_note"
            ToolIntent.RETRIEVE_NOTE -> "retrieve_notes"
            ToolIntent.WEB_SEARCH -> "web_search"
            ToolIntent.SEARCH_NOTIFICATIONS -> "search_notifications"
        }
        
        val state = conversationStateManager.startExecution(
            userId = userId,
            toolName = toolName,
            originalQuery = userQuery,
            initialParameters = validatedParams
        )
        
        // Check for missing parameters
        val missingParams = when (toolIntent) {
            ToolIntent.SAVE_NOTE -> {
                // SIMPLIFIED: For notes, if we have content, we're good to go
                // Don't ask for confirmation - just save it
                val hasContent = validatedParams.containsKey("content") && validatedParams["content"]?.isNotBlank() == true
                Log.d(TAG, "Has content: $hasContent, params: $validatedParams")
                
                if (hasContent) {
                    Log.i(TAG, "✓ All required parameters present - proceeding to save")
                    emptyList() // No missing params - proceed to save
                } else {
                    Log.w(TAG, "Missing content parameter - analyzing requirements")
                    parameterCollector.analyzeMissingNoteParameters(
                        originalQuery = userQuery,
                        collectedParams = validatedParams
                    )
                }
            }
            ToolIntent.RETRIEVE_NOTE -> {
                // Query should already be validated above
                val hasQuery = validatedParams.containsKey("query") && validatedParams["query"]?.isNotBlank() == true
                if (hasQuery) {
                    Log.i(TAG, "✓ Query parameter present - proceeding to retrieve")
                    emptyList()
                } else {
                    Log.e(TAG, "Query parameter still missing after validation")
                    emptyList() // Will fail at execution with clear error
                }
            }
            ToolIntent.WEB_SEARCH -> {
                // Query should already be validated above
                emptyList()
            }
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                // Query is optional for notification search (defaults to "recent")
                emptyList()
            }
        }
        
        if (missingParams.isNotEmpty()) {
            Log.i(TAG, "Missing ${missingParams.size} parameters: ${missingParams.map { it.name }}")
            // Need to collect more parameters
            val updatedState = state.updateMissingParameters(missingParams)
            conversationStateManager.updateExecution(userId, updatedState)
            
            val followUpQuestion = parameterCollector.generateFollowUpQuestion(
                missingParams = missingParams,
                collectedParams = initialParams
            )
            
            return Result.success(buildString {
                appendLine("I'd like to help you save this. A few questions:")
                appendLine()
                append(followUpQuestion)
            })
        } else {
            Log.i(TAG, "✓ No missing parameters - executing tool immediately")
            // All parameters collected, execute immediately (no confirmation needed)
            return executeConfirmedTool(userId, state)
        }
    }
    
    /**
     * Continue an existing execution
     */
    private suspend fun continueExecution(
        userId: Long,
        userResponse: String,
        state: ToolExecutionState
    ): Result<String> {
        Log.i(TAG, "=== CONTINUING EXECUTION ===")
        Log.i(TAG, "Stage: ${state.stage}")
        Log.i(TAG, "Response: $userResponse")
        
        return when (state.stage) {
            ExecutionStage.COLLECTING_PARAMETERS -> handleParameterCollection(userId, userResponse, state)
            ExecutionStage.AWAITING_CONFIRMATION -> handleConfirmation(userId, userResponse, state)
            ExecutionStage.AWAITING_MODIFICATION -> handleModification(userId, userResponse, state)
            else -> {
                conversationStateManager.completeExecution(userId)
                Result.success("This operation has already been completed or cancelled.")
            }
        }
    }
    
    /**
     * Handle parameter collection stage
     */
    private suspend fun handleParameterCollection(
        userId: Long,
        userResponse: String,
        state: ToolExecutionState
    ): Result<String> {
        // Extract parameter value from response
        val nextParam = state.missingParameters.firstOrNull { it.required } 
            ?: state.missingParameters.firstOrNull()
            ?: return moveToConfirmation(userId, state)
        
        val paramValue = parameterCollector.extractParameterFromResponse(
            parameterName = nextParam.name,
            userResponse = userResponse,
            context = state.collectedParameters
        )
        
        // Add to collected parameters
        val updatedState = if (paramValue != null) {
            state.addParameter(nextParam.name, paramValue)
        } else {
            state // User skipped
        }
        
        // Remove this parameter from missing list
        val remainingMissing = updatedState.missingParameters.filter { it.name != nextParam.name }
        val stateWithUpdatedMissing = updatedState.updateMissingParameters(remainingMissing)
        conversationStateManager.updateExecution(userId, stateWithUpdatedMissing)
        
        // Check if more parameters needed
        if (remainingMissing.isNotEmpty()) {
            val followUpQuestion = parameterCollector.generateFollowUpQuestion(
                missingParams = remainingMissing,
                collectedParams = stateWithUpdatedMissing.collectedParameters
            )
            return Result.success(followUpQuestion)
        } else {
            // All collected, move to confirmation
            return moveToConfirmation(userId, stateWithUpdatedMissing)
        }
    }
    
    /**
     * Move to confirmation stage
     */
    private suspend fun moveToConfirmation(
        userId: Long,
        state: ToolExecutionState
    ): Result<String> {
        val updatedState = state.moveToStage(ExecutionStage.AWAITING_CONFIRMATION)
        conversationStateManager.updateExecution(userId, updatedState)
        
        // Generate confirmation message based on tool type
        val confirmationMessage = when (state.toolName) {
            "save_note" -> generateNoteConfirmation(state.collectedParameters)
            else -> "Ready to execute. Confirm?"
        }
        
        return Result.success(confirmationMessage)
    }
    
    /**
     * Generate note confirmation message
     */
    private fun generateNoteConfirmation(params: Map<String, String>): String {
        val title = params["title"] ?: "Untitled"
        val content = params["content"] ?: ""
        val category = params["category"] ?: "general"
        val tags = params["tags"]?.split(",")?.map { it.trim() } ?: emptyList()
        val reminderText = params["reminder"]
        val priority = params["priority"] ?: "normal"
        
        // Parse reminder if present
        val reminder = reminderText?.let { 
            try {
                com.confidant.ai.notes.NotesManager(context, database).parseReminderTime(it)
            } catch (e: Exception) {
                null
            }
        }
        
        // Extract additional context
        val additionalContext = params.filterKeys { 
            it !in listOf("title", "content", "category", "tags", "reminder", "priority")
        }
        
        return confirmationManager.generateNoteConfirmation(
            title = title,
            content = content,
            category = category,
            tags = tags,
            reminder = reminder,
            priority = priority,
            additionalContext = additionalContext
        )
    }
    
    /**
     * Handle confirmation stage
     */
    private suspend fun handleConfirmation(
        userId: Long,
        userResponse: String,
        state: ToolExecutionState
    ): Result<String> {
        val response = confirmationManager.parseConfirmationResponse(userResponse)
        
        return when (response) {
            is ConfirmationResponse.CONFIRMED -> {
                // Execute the tool
                executeConfirmedTool(userId, state)
            }
            
            is ConfirmationResponse.CANCELLED -> {
                conversationStateManager.cancelExecution(userId)
                Result.success("Okay, I've cancelled that. Let me know if you need anything else!")
            }
            
            is ConfirmationResponse.MODIFY -> {
                val updatedState = state.moveToStage(ExecutionStage.AWAITING_MODIFICATION)
                conversationStateManager.updateExecution(userId, updatedState)
                
                val modificationPrompt = confirmationManager.generateModificationPrompt(response.field)
                Result.success(modificationPrompt)
            }
            
            is ConfirmationResponse.UNCLEAR -> {
                val clarification = confirmationManager.generateUnclearResponseMessage()
                Result.success(clarification)
            }
        }
    }
    
    /**
     * Handle modification stage
     */
    private suspend fun handleModification(
        userId: Long,
        userResponse: String,
        state: ToolExecutionState
    ): Result<String> {
        // Parse modification (e.g., "change title to Home WiFi Password")
        val modification = parseModification(userResponse)
        
        if (modification != null) {
            val updatedState = state.addParameter(modification.first, modification.second)
            return moveToConfirmation(userId, updatedState)
        } else {
            return Result.success("I didn't understand that modification. Please try again or say 'cancel' to abort.")
        }
    }
    
    /**
     * Parse modification from user response
     */
    private fun parseModification(response: String): Pair<String, String>? {
        // Try to extract "field to value" pattern
        val patterns = listOf(
            Regex("""(title|content|category|tags|reminder|priority)\s+to\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""change\s+(title|content|category|tags|reminder|priority)\s+to\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(title|content|category|tags|reminder|priority):\s*(.+)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null) {
                val field = match.groupValues[1].lowercase()
                val value = match.groupValues[2].trim()
                return Pair(field, value)
            }
        }
        
        return null
    }
    
    /**
     * Execute confirmed tool
     * STRICT LFM2.5 FORMAT: Tool results must be formatted as <|im_start|>tool\n[result]<|im_end|>
     */
    private suspend fun executeConfirmedTool(
        userId: Long,
        state: ToolExecutionState
    ): Result<String> {
        Log.i(TAG, "=== EXECUTING TOOL ===")
        Log.i(TAG, "Tool: ${state.toolName}")
        Log.i(TAG, "Parameters: ${state.collectedParameters}")
        
        statusCallback("⚙️ Executing...")
        
        val functionCall = FunctionCall(state.toolName, state.collectedParameters)
        val toolResult = functionCallingSystem.executeFunction(functionCall)
        
        conversationStateManager.completeExecution(userId)
        
        Log.i(TAG, "Tool execution result: ${if (toolResult.isSuccess) "SUCCESS" else "FAILURE"}")
        
        return if (toolResult.isSuccess) {
            val rawResult = toolResult.getOrNull()!!
            Log.i(TAG, "Tool response: ${rawResult.take(200)}")
            
            // STRICT LFM2.5 FORMAT: Format tool result as <|im_start|>tool\n[result]<|im_end|>
            // Note: The actual formatting happens when this result is used in a conversation
            // For now, return the raw result - it will be formatted by ChatMLFormatter when needed
            Result.success(rawResult)
        } else {
            Log.e(TAG, "Tool execution failed", toolResult.exceptionOrNull())
            Result.failure(toolResult.exceptionOrNull()!!)
        }
    }
    
    /**
     * Check if this is a simple note save that doesn't need LLM parsing
     * Simple saves are straightforward "save note X" commands without complex parameters
     * 
     * OPTIMIZATION: Detecting simple saves allows us to skip 10 seconds of LLM inference!
     * ENHANCED 2026: Catches 95%+ of simple note saves
     */
    private fun isSimpleNoteSave(query: String): Boolean {
        val lowerQuery = query.lowercase()
        
        // Simple patterns that indicate straightforward note saves
        val simplePatterns = listOf(
            // Direct commands (IMPROVED: catch "save note of")
            "save note", "save note of", "remember", "remind me", "write down",
            "keep track", "store", "record", "add note",
            "create note", "make a note", "take a note",
            
            // Natural language variations
            "note for me", "note to", "save this",
            "remember this", "don't forget", "keep this",
            
            // Action-oriented (very common)
            "to go", "to visit", "to call", "to buy",
            "need to", "have to",
            
            // Password/credential saves (IMPROVED)
            "save password", "save email", "save credential"
        )
        
        // Check if query contains any simple pattern
        val isSimple = simplePatterns.any { lowerQuery.contains(it) } ||
                // Also catch "save" + "password"/"email"/"note" combinations
                (lowerQuery.contains("save") && (
                    lowerQuery.contains("password") || 
                    lowerQuery.contains("email") || 
                    lowerQuery.contains("note") ||
                    lowerQuery.contains("credential")
                ))
        
        // Complex patterns that require LLM (has specific metadata)
        val complexPatterns = listOf(
            "with title", "titled", "category:", "tag:",
            "priority:", "remind me on", "remind me at",
            "set reminder for", "at 3pm", "tomorrow at",
            "next week", "next month"
        )
        
        val isComplex = complexPatterns.any { lowerQuery.contains(it) }
        
        // Simple if matches simple pattern and doesn't match complex pattern
        val result = isSimple && !isComplex
        
        if (result) {
            Log.i(TAG, "⚡ FAST PATH: Simple note save detected - skipping LLM (saves 10s!)")
        } else {
            Log.d(TAG, "Complex note save - will use LLM for parsing")
        }
        
        return result
    }
    
    /**
     * Extract parameters directly from query (fallback)
     */
    private fun extractDirectParameters(query: String, toolIntent: ToolIntent): MutableMap<String, String> {
        return when (toolIntent) {
            ToolIntent.SAVE_NOTE -> {
                // IMPROVED: Better content extraction for "save note of my email password"
                val lowerQuery = query.lowercase()
                val content = when {
                    // Extract content after "save note of"
                    lowerQuery.contains("save note of") -> {
                        query.substringAfter("save note of", "").trim()
                            .replace(Regex("^my\\s+"), "").trim()
                            .ifEmpty { query }
                    }
                    // Extract content after "save note"
                    lowerQuery.contains("save note") -> {
                        query.substringAfter("save note", "").trim()
                            .replace(Regex("^of\\s+my\\s+"), "")
                            .replace(Regex("^of\\s+"), "")
                            .replace(Regex("^my\\s+"), "").trim()
                            .ifEmpty { query }
                    }
                    // Extract content after "remember"
                    lowerQuery.contains("remember") -> {
                        query.substringAfter("remember", "").trim()
                            .replace(Regex("^that\\s+"), "")
                            .replace(Regex("^my\\s+"), "").trim()
                            .ifEmpty { query }
                    }
                    else -> query
                }
                
                val title = extractNoteTitle(query)
                mutableMapOf(
                    "title" to title,
                    "content" to content.ifEmpty { query },
                    "category" to "general"
                )
            }
            ToolIntent.RETRIEVE_NOTE -> {
                // Extract search query from user's message
                val searchQuery = extractSearchQuery(query)
                mutableMapOf(
                    "query" to searchQuery,
                    "limit" to "10"
                )
            }
            ToolIntent.WEB_SEARCH -> {
                // Extract search query from user's message
                val searchQuery = extractSearchQuery(query)
                mutableMapOf(
                    "query" to searchQuery,
                    "max_results" to "5"
                )
            }
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                // Extract notification search query
                val searchQuery = extractNotificationQuery(query)
                mutableMapOf(
                    "query" to searchQuery.ifEmpty { "recent" },
                    "hours" to "24",
                    "limit" to "10"
                )
            }
        }
    }
    
    /**
     * Extract notification search query from user request
     * GENERALIZABLE: Works for any notification query pattern
     */
    private fun extractNotificationQuery(query: String): String {
        val lowerQuery = query.lowercase().trim()
        
        // Remove common notification search prefixes
        val prefixes = listOf(
            "any updates", "any notifications", "any alerts", "any messages",
            "show notifications", "show updates", "show alerts", "show messages",
            "get notifications", "get updates", "get alerts", "get messages",
            "find notifications", "find updates", "find alerts",
            "search notifications", "search updates", "search alerts",
            "my notifications", "my updates", "my alerts", "my messages",
            "recent notifications", "recent updates", "recent alerts",
            "latest notifications", "latest updates", "latest alerts"
        )
        
        var cleaned = query
        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (lowerQuery.startsWith(prefix)) {
                cleaned = query.substring(prefix.length).trim()
                break
            }
        }
        
        // Extract app name if mentioned (e.g., "gmail", "whatsapp", "mail")
        val appKeywords = listOf("gmail", "mail", "email", "whatsapp", "telegram", "messages", "sms")
        val foundApp = appKeywords.firstOrNull { lowerQuery.contains(it) }
        if (foundApp != null) {
            return foundApp
        }
        
        // If cleaned is empty or just common words, return "recent"
        if (cleaned.isEmpty() || cleaned.length < 3) {
            return "recent"
        }
        
        return cleaned
    }
    
    /**
     * Build system prompt for tool calling
     * STRICT LFM2.5 FORMAT: Must use exact ChatML format with tool definitions
     * Format: <|startoftext|><|im_start|>system\nList of tools: [{...}]\n\n[instructions]<|im_end|>
     * 
     * This will be formatted by ChatMLFormatter.formatWithTools() to ensure strict compliance
     */
    private fun buildToolSystemPrompt(@Suppress("UNUSED_PARAMETER") toolIntent: ToolIntent): String {
        // Base instructions - tool definitions will be added by FunctionCallingSystem
        return """Extract parameters from user queries and output function calls in strict LFM2.5 format.

Output format: <|tool_call_start|>[function_name(arg="value")]<|tool_call_end|>

GENERALIZABLE RULES:
- save_note: For ANY personal data saving (passwords, reminders, appointments, notes, alerts, schedules, credentials)
- retrieve_notes: For ANY personal data retrieval (finding saved passwords, appointments, reminders, schedules, notes)
- Extract content naturally - remove command words like "save", "remember", "note" but keep the actual information"""
    }
    
    /**
     * Build task-specific user message for parameter extraction
     * This goes in user message (not system prompt) for KV cache reuse
     */
    private fun buildTaskUserMessage(toolIntent: ToolIntent, userQuery: String): String {
        return when (toolIntent) {
            ToolIntent.SAVE_NOTE -> """
                Extract parameters for save_note. User wants to save personal information.
                
                Rules:
                1. content: REQUIRED - Extract the actual information to save (remove "save note", "save note of", "remember", etc.)
                   - "save note to visit cobbler" → content="visit cobbler"
                   - "save note of my email password" → content="email password"
                   - "save password abc123" → content="password abc123"
                   - "remember appointment tomorrow" → content="appointment tomorrow"
                2. title: Optional - Generate short title (2-4 words) from content
                   - "visit cobbler" → title="Visit Cobbler"
                   - "email password" → title="Email Password"
                   - "password abc123" → title="Password"
                3. reminder: Optional - Extract if date/time mentioned ("tomorrow", "this weekend", "at 3pm")
                
                Examples:
                "save note to visit teacher tomorrow" → <|tool_call_start|>[save_note(content="visit teacher tomorrow", title="Visit Teacher", reminder="tomorrow")]<|tool_call_end|>
                "save note of my email password" → <|tool_call_start|>[save_note(content="email password", title="Email Password")]<|tool_call_end|>
                "save note of my email password as 4455" → <|tool_call_start|>[save_note(content="email password: 4455", title="Email Password")]<|tool_call_end|>
                "remember password is abc123" → <|tool_call_start|>[save_note(content="password is abc123", title="Password")]<|tool_call_end|>
                "save reminder to call mom" → <|tool_call_start|>[save_note(content="call mom", title="Call Mom")]<|tool_call_end|>
                
                Query: "$userQuery"
                Output:""".trimIndent()
            
            ToolIntent.RETRIEVE_NOTE -> """
                Extract parameters for retrieve_notes.
                
                Rules:
                1. query: REQUIRED - Extract key search terms from user query
                2. Remove question words: "what", "is", "my", "when", "do", "i", "have", "find", "get", "show"
                3. Keep important keywords: "gmail", "password", "doctor", "appointment", "schedule", "reminder", etc.
                4. Keep query concise but meaningful (2-5 words typically)
                
                Examples:
                "what is my gmail password" → <|tool_call_start|>[retrieve_notes(query="gmail password")]<|tool_call_end|>
                "when i have to visit doctor" → <|tool_call_start|>[retrieve_notes(query="doctor visit")]<|tool_call_end|>
                "whats my schedule for tomorrow" → <|tool_call_start|>[retrieve_notes(query="schedule tomorrow")]<|tool_call_end|>
                "find my password" → <|tool_call_start|>[retrieve_notes(query="password")]<|tool_call_end|>
                "when do i have appointment" → <|tool_call_start|>[retrieve_notes(query="appointment")]<|tool_call_end|>
                
                Query: "$userQuery"
                Output:""".trimIndent()
            
            ToolIntent.WEB_SEARCH -> """
                Extract parameters for web_search.
                
                Rules:
                1. query: REQUIRED - Extract search terms from user query
                2. max_results: Optional - Default 5
                
                Examples:
                "bitcoin price" → <|tool_call_start|>[web_search(query="bitcoin price", max_results="5")]<|tool_call_end|>
                "what is AI" → <|tool_call_start|>[web_search(query="what is AI")]<|tool_call_end|>
                
                Query: "$userQuery"
                Output:""".trimIndent()
            
            ToolIntent.SEARCH_NOTIFICATIONS -> """
                Extract parameters for search_notifications.
                
                Rules:
                1. query: Optional - Extract app name or keywords (e.g., "gmail", "mail", "recent")
                2. app_name: Optional - Specific app name if mentioned
                3. hours: Optional - Time period (default 24)
                
                Examples:
                "any updates on my mail" → <|tool_call_start|>[search_notifications(query="mail", hours="24")]<|tool_call_end|>
                "recent notifications" → <|tool_call_start|>[search_notifications(query="recent")]<|tool_call_end|>
                "gmail notifications" → <|tool_call_start|>[search_notifications(app_name="Gmail")]<|tool_call_end|>
                
                Query: "$userQuery"
                Output:""".trimIndent()
        }
    }

    /**
     * Try direct tool execution without LLM parsing
     * Fallback when LLM doesn't generate proper function call
     */
    private suspend fun tryDirectToolExecution(
        userQuery: String,
        toolIntent: ToolIntent
    ): Result<String> {
        Log.i(TAG, "Attempting direct tool execution")
        
        return when (toolIntent) {
            ToolIntent.SAVE_NOTE -> {
                // Extract title and content from query
                val title = extractNoteTitle(userQuery)
                val content = userQuery
                
                val args = mapOf(
                    "title" to title,
                    "content" to content,
                    "category" to "general"
                )
                
                functionCallingSystem.executeFunction(
                    FunctionCall("save_note", args)
                )
            }
            
            ToolIntent.RETRIEVE_NOTE -> {
                // Extract search query
                val query = extractSearchQuery(userQuery)
                
                val args = mapOf(
                    "query" to query,
                    "limit" to "10"
                )
                
                functionCallingSystem.executeFunction(
                    FunctionCall("retrieve_notes", args)
                )
            }
            
            ToolIntent.WEB_SEARCH -> {
                // Extract search query
                val query = extractSearchQuery(userQuery)
                
                val args = mapOf(
                    "query" to query,
                    "max_results" to "5"
                )
                
                functionCallingSystem.executeFunction(
                    FunctionCall("web_search", args)
                )
            }
            
            ToolIntent.SEARCH_NOTIFICATIONS -> {
                // Extract notification search query
                val query = extractNotificationQuery(userQuery)
                
                val args = mapOf(
                    "query" to query.ifEmpty { "recent" },
                    "hours" to "24",
                    "limit" to "10"
                )
                
                functionCallingSystem.executeFunction(
                    FunctionCall("search_notifications", args)
                )
            }
        }
    }
    
    /**
     * Extract note title from user query
     * IMPROVED: Better handling of "save note of my email password" type queries
     */
    private fun extractNoteTitle(query: String): String {
        var title = query
        
        // Remove common prefixes (IMPROVED: handles "save note of")
        title = title
            .replace(Regex("(?i)(save\\s+note\\s+of|save\\s+note|remember|remind me|write down|keep track of|store|record|add to notes|create note|make a note|save a note|take a note)\\s*(about|on|that|to|of)?\\s*"), "")
            .trim()
        
        // Remove "my" prefix if present (e.g., "my email password" → "email password")
        title = title.replace(Regex("(?i)^my\\s+"), "").trim()
        
        // Extract meaningful title (first few words or key phrase)
        val words = title.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 1) {
            // For "email password" type queries, use first 2-3 words as title
            title = words.take(3).joinToString(" ").capitalize()
        } else if (words.isNotEmpty()) {
            title = words[0].capitalize()
        }
        
        // Limit length
        if (title.length > 100) {
            title = title.take(97) + "..."
        }
        
        // If empty, use generic title
        if (title.isEmpty()) {
            title = "Note from ${java.time.LocalDate.now()}"
        }
        
        return title
    }
    
    /**
     * Extract search query from user request
     * GENERALIZABLE: Works for any retrieval query pattern
     * Extracts key terms that will match saved notes using BM25 + fuzzy search
     */
    private fun extractSearchQuery(query: String): String {
        val lowerQuery = query.lowercase().trim()
        
        // Remove common retrieval prefixes but preserve the actual search terms
        val prefixes = listOf(
            "find my", "find", "search for", "search my", "search",
            "get my", "get", "show my", "show", "retrieve my", "retrieve",
            "what did i save", "what did i note", "whats my", "what's my",
            "where is my", "where is", "list my", "list",
            "what is my", "what is", "tell me my", "tell me",
            "when do i", "when is my", "when is", "when"
        )
        
        var cleaned = query
        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (lowerQuery.startsWith(prefix)) {
                cleaned = query.substring(prefix.length).trim()
                break
            }
        }
        
        // Remove common connecting words
        cleaned = cleaned
            .replace(Regex("(?i)^(about|on|for|regarding|concerning|the|a|an)\\s+"), "")
            .trim()
        
        // If cleaned is empty or too short, try extracting meaningful parts
        if (cleaned.isEmpty() || cleaned.length < 2) {
            // Extract after "my" if present (e.g., "what is my gmail password" -> "gmail password")
            val myMatch = Regex("(?i)my\\s+(.+)").find(query)
            if (myMatch != null) {
                cleaned = myMatch.groupValues[1].trim()
            } else {
                // Extract key terms (remove stop words)
                val stopWords = setOf("what", "is", "my", "the", "a", "an", "for", "to", "of", "in", "on", "at")
                val words = query.split(Regex("\\s+"))
                    .filter { it.lowercase() !in stopWords && it.length > 2 }
                cleaned = words.joinToString(" ")
            }
        }
        
        // Ensure we have meaningful search terms
        val finalQuery = cleaned.ifEmpty { query }.trim()
        
        // Log for debugging
        Log.d(TAG, "Extracted search query: '$finalQuery' from: '$query'")
        
        return finalQuery
    }
    
    /**
     * Generate natural language response from tool output
     */
    private suspend fun generateToolResponse(
        @Suppress("UNUSED_PARAMETER") userQuery: String,
        @Suppress("UNUSED_PARAMETER") toolName: String,
        toolOutput: String,
        @Suppress("UNUSED_PARAMETER") streamingManager: StreamingResponseManager
    ): String {
        // For notes, the tool output is already well-formatted
        // Just return it directly
        return toolOutput
    }
    

    
    companion object {
        private const val TAG = "IntelligentToolMgr"
    }
}


/**
 * Tool intent types
 */
enum class ToolIntent {
    SAVE_NOTE,
    RETRIEVE_NOTE,
    WEB_SEARCH,
    SEARCH_NOTIFICATIONS
}

