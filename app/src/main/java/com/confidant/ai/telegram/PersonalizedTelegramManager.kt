package com.confidant.ai.telegram

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.integrations.EnhancedFunctionCallingSystem
import com.confidant.ai.integrations.FunctionCall
import com.confidant.ai.integrations.FunctionCallingSystem
import com.confidant.ai.integrations.IntelligentSearchManager
import com.confidant.ai.integrations.IntelligentToolManager

import com.confidant.ai.memory.SimplifiedMemorySystem
import com.confidant.ai.personalization.PersonalizationManager
import com.confidant.ai.prompts.OptimizedPrompts
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.*

/**
 * PersonalizedTelegramManager - Enhanced Telegram bot with:
 * - Personalized responses (user/bot names, pronouns, interests)
 * - Real-time tool execution notifications
 * - Parallel tool execution
 * - Tool execution logging
 * - Adaptive system prompts
 */
class PersonalizedTelegramManager(
    private val context: Context,
    private val llmEngine: LLMEngine,
    private val memorySystem: SimplifiedMemorySystem,
    private val database: AppDatabase,
    private val bot: Bot?,
    private val chatId: Long
) {
    
    private val personalizationManager = PersonalizationManager(context, database)
    private val functionCallingSystem = FunctionCallingSystem(context)
    
    // Message editor for status updates (edits same message)
    private var messageEditor: TelegramMessageEditor? = null
    
    /**
     * Set the thinking message ID to use for status updates
     * This ensures all status updates edit the same message
     */
    fun setThinkingMessageId(messageId: Long?) {
        if (messageId != null) {
            messageEditor = TelegramMessageEditor(bot, chatId, messageId)
            Log.d(TAG, "Initialized message editor with thinking message ID: $messageId")
        }
    }
    
    // Intelligent search manager for automatic, context-aware search
    private val intelligentSearchManager = IntelligentSearchManager(
        context = context,
        database = database,
        statusCallback = { message -> sendStatusUpdate(message) }
    )
    
    // Intelligent tool manager for notes, web search, etc.
    private val intelligentToolManager by lazy {
        IntelligentToolManager(
            context = context,
            database = database,
            llmEngine = llmEngine,
            statusCallback = { message -> 
                // Launch coroutine for suspend function
                CoroutineScope(Dispatchers.IO).launch {
                    sendStatusUpdate(message)
                }
            }
        )
    }
    

    

    
    /**
     * Process user message with OPTIMIZED 2-CALL FLOW + REAL STREAMING
     * 
     * Flow:
     * 1. PREDICT: Check if search needed BEFORE generation (saves time)
     * 2. If search needed → Search + Stream response
     * 3. If no search → Stream direct response
     * 
     * This ensures:
     * - REAL streaming for instant user feedback
     * - Smart search prediction (no wasted LLM calls)
     * - Parallel processing where possible
     * - <5s time to first token
     */
    suspend fun processMessageWithStreaming(
        userMessage: String,
        streamingManager: StreamingResponseManager
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== OPTIMIZED STREAMING FLOW 2026 ===")
            Log.i(TAG, "User message: $userMessage")
            
            // Get user profile
            val profile = personalizationManager.getProfile()
            Log.i(TAG, "Processing message for user")
            Log.i(TAG, "Bot: Confidant")
            
            // Get conversation history for context
            val conversationHistory = memorySystem.getRecentConversation(limit = 5)
                .map { Pair(if (it.role == "user") "user" else "assistant", it.content) }
            
            // ═══════════════════════════════════════════════════════
            // STEP 1: Check if tool execution needed (notes, web search, etc.)
            // ═══════════════════════════════════════════════════════
            val toolIntent = intelligentToolManager.detectToolIntent(userMessage)
            
            if (toolIntent != null) {
                Log.i(TAG, "🔧 PREDICTED: Tool execution needed - $toolIntent")
                
                // Use conversation-aware tool execution
                val toolResponse = intelligentToolManager.executeToolFlowWithConversation(
                    userQuery = userMessage,
                    toolIntent = toolIntent,
                    userId = chatId,  // Use chatId as userId
                    streamingManager = streamingManager,
                    conversationHistory = conversationHistory
                )
                
                if (toolResponse.isSuccess) {
                    val response = toolResponse.getOrNull()!!
                    
                    // CRITICAL FIX: Actually send the response to Telegram!
                    // The tool execution completed but never sent the final message
                    Log.d(TAG, "Sending tool response to Telegram (${response.length} chars)")
                    streamingManager.streamResponse(
                        fullText = response,
                        isActiveChat = true  // Tool responses are instant, no streaming needed
                    )
                    
                    messageEditor = null
                    memorySystem.addConversationTurn(userMessage, response)
                    return@withContext response
                } else {
                    Log.e(TAG, "Tool execution failed: ${toolResponse.exceptionOrNull()?.message}")
                    // Fall through to other paths
                }
            }
            
            // ═══════════════════════════════════════════════════════
            // STEP 2: Check if search needed BEFORE generation
            // ═══════════════════════════════════════════════════════
            val needsSearch = intelligentSearchManager.shouldSearch(
                userQuery = userMessage,
                botResponse = "",  // No response yet
                conversationContext = conversationHistory
            )
            
            if (needsSearch) {
                // ═══════════════════════════════════════════════════════
                // SEARCH PATH: Fetch data + Stream response
                // ═══════════════════════════════════════════════════════
                Log.i(TAG, "🔍 PREDICTED: Search needed - fetching data...")
                
                val searchResponse = processIntelligentSearchFlowWithStreaming(
                    userMessage = userMessage,
                    profile = profile,
                    conversationHistory = conversationHistory,
                    streamingManager = streamingManager
                )
                
                // Reset message editor for next message
                messageEditor = null
                
                memorySystem.addConversationTurn(userMessage, searchResponse)
                return@withContext searchResponse
            } else {
                // ═══════════════════════════════════════════════════════
                // DIRECT PATH: Stream conversational response
                // ═══════════════════════════════════════════════════════
                Log.i(TAG, "💬 PREDICTED: Conversational - streaming direct response...")
                
                val directResponse = streamDirectResponse(
                    userMessage = userMessage,
                    profile = profile,
                    conversationHistory = conversationHistory,
                    streamingManager = streamingManager
                )
                
                // Reset message editor for next message
                messageEditor = null
                
                memorySystem.addConversationTurn(userMessage, directResponse)
                return@withContext directResponse
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming flow", e)
            "I encountered an error processing your message. Please try again."
        }
    }
    
    /**
     * LEGACY: Non-streaming version for backward compatibility
     * Use processMessageWithStreaming() for new code
     */
    suspend fun processMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== OPTIMIZED 2-CALL FLOW (LEGACY) ===")
            Log.i(TAG, "User message: $userMessage")
            
            // Get user profile
            val profile = personalizationManager.getProfile()
            Log.i(TAG, "Processing message for user")
            Log.i(TAG, "Bot: Confidant")
            
            // Get conversation history for context
            val conversationHistory = memorySystem.getRecentConversation(limit = 5)
                .map { Pair(if (it.role == "user") "user" else "assistant", it.content) }
            
            // ═══════════════════════════════════════════════════════
            // SMART PREDICTION: Check if search needed BEFORE generation
            // ═══════════════════════════════════════════════════════
            val needsSearch = intelligentSearchManager.shouldSearch(
                userQuery = userMessage,
                botResponse = "",
                conversationContext = conversationHistory
            )
            
            if (needsSearch) {
                Log.i(TAG, "🔍 PREDICTED: Search needed")
                sendStatusUpdate("🤔 Let me search for that...")
                
                val searchResponse = processIntelligentSearchFlow(
                    userMessage = userMessage,
                    profile = profile,
                    conversationHistory = conversationHistory
                )
                
                memorySystem.addConversationTurn(userMessage, searchResponse)
                return@withContext searchResponse
            } else {
                Log.i(TAG, "💬 PREDICTED: Direct response")
                
                val firstResponse = tryDirectResponse(
                    userMessage = userMessage,
                    profile = profile,
                    conversationHistory = conversationHistory
                )
                
                memorySystem.addConversationTurn(userMessage, firstResponse)
                return@withContext firstResponse
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in 2-call flow", e)
            "I encountered an error processing your message. Please try again."
        }
    }
    
    /**
     * Stream direct conversational response (no search)
     * Uses REAL token-by-token streaming for instant feedback
     * UPDATED: Uses proper ChatML format
     */
    private suspend fun streamDirectResponse(
        userMessage: String,
        profile: com.confidant.ai.personalization.PersonalizationManager.UserProfile,
        conversationHistory: List<Pair<String, String>>,
        streamingManager: StreamingResponseManager
    ): String {
        // Build conversational system prompt with current date
        val currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        val systemPrompt = buildString {
            appendLine("You are Confidant, a friendly and helpful AI assistant.")
            appendLine("Current date: $currentDate")
            appendLine()
            appendLine("Be warm, conversational, and genuinely helpful.")
            appendLine("Keep responses clear and concise.")
            appendLine("If you're unsure about something, be honest and offer to help find the answer.")
        }
        
        // Build full prompt using ChatML format with conversation history
        val fullPrompt = com.confidant.ai.prompts.ChatMLFormatter.formatConversational(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            conversationHistory = conversationHistory
        )
        
        // Get token flow from LLM
        val tokenFlow = llmEngine.generateStreaming(
            prompt = fullPrompt,
            maxTokens = 128,  // Shorter for conversational responses
            temperature = 0.7f  // Higher for natural conversation
        )
        
        // Stream to Telegram with progressive updates
        val result = streamingManager.streamTokenFlow(
            tokenFlow = tokenFlow,
            isActiveChat = true  // Enable streaming for active chats
        )
        
        if (result.isFailure) {
            Log.e(TAG, "Streaming failed: ${result.exceptionOrNull()?.message}")
            // Fallback to blocking generation
            return tryDirectResponse(userMessage, profile, conversationHistory)
        }
        
        // Get the complete response from the streaming result
        val finalResponse = result.getOrNull() ?: ""
        
        return finalResponse
    }
    
    /**
     * Process intelligent search flow with REAL streaming
     * Fetches search results, then streams LLM response
     * OPTIMIZED 2026: Reduced prompt size, increased max tokens, lower temperature for facts
     */
    private suspend fun processIntelligentSearchFlowWithStreaming(
        userMessage: String,
        profile: com.confidant.ai.personalization.PersonalizationManager.UserProfile,
        conversationHistory: List<Pair<String, String>>,
        streamingManager: StreamingResponseManager
    ): String {
        try {
            // Notify user we're searching
            sendStatusUpdate("🔍 Searching for the latest information...")
            
            // Execute intelligent search
            val searchResult = intelligentSearchManager.executeIntelligentSearch(
                userQuery = userMessage,
                conversationContext = conversationHistory,
                maxResults = 8
            )
            
            if (searchResult.isFailure) {
                Log.e(TAG, "Search failed: ${searchResult.exceptionOrNull()?.message}")
                return "I tried to search for that information but encountered an issue. Please try again."
            }
            
            val searchResponse = searchResult.getOrNull()!!
            
            // Notify user we're analyzing
            sendStatusUpdate("📝 Got it! Analyzing the information...")
            
            // Build OPTIMIZED summary prompt (reduced token count)
            val summaryPrompt = intelligentSearchManager.buildSearchSummaryPrompt(
                userQuery = userMessage,
                searchResponse = searchResponse
            )
            
            // Build system prompt - ULTRA COMPACT for LFM2.5 with current date
            val currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            val systemPrompt = buildString {
                appendLine("You are Confidant, a friendly and helpful AI assistant.")
                appendLine("Current date: $currentDate")
                appendLine()
                appendLine("IMPORTANT GUIDELINES:")
                appendLine("1. Use ONLY information from the search results provided")
                appendLine("2. Include specific numbers, prices, and dates when available")
                appendLine("3. If the results don't contain the answer, let me know you need more information")
                appendLine("4. Always be accurate - never make up information")
                appendLine("5. Mention dates for time-sensitive information")
                appendLine()
                appendLine("Keep responses clear and helpful (2-3 sentences unless more detail is needed).")
            }
            
            // Build full prompt using ChatML format
            val fullPrompt = com.confidant.ai.prompts.ChatMLFormatter.formatWithSearchContext(
                systemPrompt = systemPrompt,
                query = userMessage,
                searchContext = summaryPrompt,
                currentDate = currentDate
            )
            
            // LOG PROMPT FOR DEBUGGING
            Log.d(TAG, "=== PROMPT SENT TO MODEL (ChatML Format) ===")
            Log.d(TAG, "Length: ${fullPrompt.length} chars")
            Log.d(TAG, "Preview: ${fullPrompt.take(400)}...")
            Log.d(TAG, "=== END PROMPT ===")
            
            // Get token flow with OPTIMIZED parameters
            // CRITICAL: Use dynamic temperature based on query type
            val queryType = llmEngine.detectQueryType(fullPrompt)
            val optimalTemp = llmEngine.getOptimalTemperature(queryType)
            
            Log.d(TAG, "Query type: $queryType, Temperature: $optimalTemp")
            
            val tokenFlow = llmEngine.generateStreaming(
                prompt = fullPrompt,
                maxTokens = 192,  // DOUBLED from 96 for complete responses
                temperature = optimalTemp  // DYNAMIC: 0.2-0.7f based on query type
            )
            
            // Stream to Telegram with progressive updates
            val result = streamingManager.streamTokenFlow(
                tokenFlow = tokenFlow,
                isActiveChat = true
            )
            
            if (result.isFailure) {
                Log.e(TAG, "Streaming failed: ${result.exceptionOrNull()?.message}")
                // Fallback to blocking generation
                return processIntelligentSearchFlow(userMessage, profile, conversationHistory)
            }
            
            // Get the complete response from the streaming result
            val finalResponse = result.getOrNull() ?: ""
            
            // LOG RESPONSE FOR DEBUGGING
            Log.d(TAG, "=== MODEL RESPONSE ===")
            Log.d(TAG, "Length: ${finalResponse.length} chars")
            Log.d(TAG, "Content: $finalResponse")
            Log.d(TAG, "=== END RESPONSE ===")
            
            // FALLBACK: If response is too short or doesn't contain numbers, extract directly
            if (finalResponse.length < 20 || !finalResponse.contains(Regex("""\d"""))) {
                Log.w(TAG, "Response seems incomplete, attempting direct extraction")
                val directExtraction = extractDirectAnswer(searchResponse.results, userMessage)
                if (directExtraction != null) {
                    Log.i(TAG, "Using direct extraction: $directExtraction")
                    return directExtraction
                }
            }
            
            return finalResponse
            
        } catch (e: Exception) {
            Log.e(TAG, "Intelligent search flow failed", e)
            return "I encountered an error while searching. Please try again."
        }
    }
    
    /**
     * Extract direct answer from search results as fallback
     * Used when LLM response is incomplete or missing key data
     */
    private fun extractDirectAnswer(searchResults: String, query: String): String? {
        val lowerQuery = query.lowercase()
        
        // For price queries, extract first price found
        if (lowerQuery.contains("price") || lowerQuery.contains("stock")) {
            val priceRegex = Regex("""[₹$€£¥]\s*[\d,]+\.?\d*""")
            val price = priceRegex.find(searchResults)
            if (price != null) {
                // Extract company/entity name from query
                val entity = query.replace(Regex("""(what|is|the|stock|price|of|'s|current|today|latest)""", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .split(" ")
                    .filter { it.length > 2 }
                    .joinToString(" ")
                
                // Try to find date in results
                val dateRegex = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""")
                val date = dateRegex.find(searchResults)
                
                return if (date != null) {
                    "$entity stock price is ${price.value} as of ${date.value}"
                } else {
                    "$entity stock price is ${price.value}"
                }
            }
        }
        
        // For news queries, extract first headline
        if (lowerQuery.contains("news") || lowerQuery.contains("latest")) {
            val lines = searchResults.lines()
            val headline = lines.firstOrNull { it.length > 20 && !it.startsWith("Search") && !it.startsWith("Key") }
            if (headline != null) {
                return headline.take(200)
            }
        }
        
        return null
    }
    
    /**
     * LEGACY: Try to respond directly without tools (blocking)
     * Returns bot's initial response (may be uncertain)
     */
    private suspend fun tryDirectResponse(
        userMessage: String,
        profile: com.confidant.ai.personalization.PersonalizationManager.UserProfile,
        conversationHistory: List<Pair<String, String>>
    ): String {
        // Build system prompt with uncertainty detection
        val systemPrompt = buildString {
            appendLine("You are Confidant, a friendly and helpful AI assistant.")
            appendLine()
            appendLine("GUIDELINES:")
            appendLine("1. If you know the answer confidently, respond directly in a friendly way")
            appendLine("2. If you're uncertain or need current data, say: \"I'm not sure about that\"")
            appendLine("3. If the query needs real-time data (news, prices, weather), say: \"I need to search for that\"")
            appendLine("4. Be honest about what you know and don't know")
            appendLine()
            appendLine("Examples:")
            appendLine("User: \"Hi, how are you?\"")
            appendLine("You: \"Hey! I'm doing great, thanks for asking! How can I help you today?\"")
            appendLine()
            appendLine("User: \"What's the Bitcoin price today?\"")
            appendLine("You: \"I need to search for that\"")
            appendLine()
            appendLine("User: \"Any news updates for today?\"")
            appendLine("You: \"I need to search for that\"")
            appendLine()
            appendLine("User: \"Tell me about mutual funds in India\"")
            appendLine("You: \"I need to search for that\"")
            appendLine()
            appendLine("Be warm, natural, and genuinely helpful!")
        }
        
        // Build user context
        val userContext = buildString {
            if (conversationHistory.isNotEmpty()) {
                appendLine("Recent conversation:")
                conversationHistory.takeLast(2).forEach { (role, content) ->
                    appendLine("- $role: ${content.take(100)}")
                }
                appendLine()
            }
            appendLine("${"User"}: $userMessage")
            appendLine()
            appendLine("Your response:")
        }
        
        // Generate with low temperature for consistent uncertainty detection
        val result = llmEngine.generateWithCache(
            systemPrompt = systemPrompt,
            userMessage = userContext,
            maxTokens = 64,  // Short response for quick decision
            temperature = 0.5f  // Lower temp for more consistent behavior
        )
        
        return result.getOrElse { 
            "I'm not sure about that"  // Fallback to search
        }
    }
    
    /**
     * Determine if search is needed after first response
     * Checks for uncertainty indicators in bot's response
     */
    private fun shouldSearchAfterResponse(
        userMessage: String,
        botResponse: String,
        conversationHistory: List<Pair<String, String>>
    ): Boolean {
        val lowerResponse = botResponse.lowercase()
        
        // Explicit uncertainty indicators
        val uncertaintyPhrases = listOf(
            "i'm not sure", "i don't know", "i need to search",
            "i don't have", "i can't", "i'm unsure",
            "i'm not certain", "i don't recall", "i'm not aware",
            "let me search", "i should search", "i'll search"
        )
        
        if (uncertaintyPhrases.any { lowerResponse.contains(it) }) {
            Log.i(TAG, "✓ Uncertainty detected in response: $botResponse")
            return true
        }
        
        // Also check if original query clearly needs search
        // (in case bot didn't properly indicate uncertainty)
        val needsSearch = intelligentSearchManager.shouldSearch(
            userQuery = userMessage,
            botResponse = botResponse,
            conversationContext = conversationHistory
        )
        
        if (needsSearch) {
            Log.i(TAG, "✓ Query analysis indicates search needed")
            return true
        }
        
        Log.i(TAG, "✗ Bot responded confidently, no search needed")
        return false
    }
    
    /**
     * Process intelligent search flow with streaming support
     * This is CALL 2 - executed when bot needs external data
     */
    private suspend fun processIntelligentSearchFlow(
        userMessage: String,
        profile: com.confidant.ai.personalization.PersonalizationManager.UserProfile,
        conversationHistory: List<Pair<String, String>>
    ): String {
        try {
            // Notify user we're searching (friendly tone)
            sendStatusUpdate("🔍 Searching for the latest information...")
            
            // Execute intelligent search with context awareness
            val searchResult = intelligentSearchManager.executeIntelligentSearch(
                userQuery = userMessage,
                conversationContext = conversationHistory,
                maxResults = 8
            )
            
            if (searchResult.isFailure) {
                Log.e(TAG, "Search failed: ${searchResult.exceptionOrNull()?.message}")
                return "I tried to search for that information but encountered an issue. Please try again."
            }
            
            val searchResponse = searchResult.getOrNull()!!
            
            // Notify user we're analyzing results
            sendStatusUpdate("📝 Got it! Analyzing the information...")
            
            // Build comprehensive summary prompt
            val summaryPrompt = intelligentSearchManager.buildSearchSummaryPrompt(
                userQuery = userMessage,
                searchResponse = searchResponse
            )
            
            // Generate final response with streaming if supported
            val finalResult = llmEngine.generateWithCache(
                systemPrompt = buildSearchResponseSystemPrompt(profile),
                userMessage = summaryPrompt,
                maxTokens = 128,  // OPTIMIZED: Reduced from 256 for faster, more concise responses
                temperature = 0.7f  // Balanced for factual yet natural responses
            )
            
            val finalResponse = finalResult.getOrElse {
                // Fallback: provide search excerpt if generation fails
                "Here's what I found:\n\n${searchResponse.results.take(800)}"
            }
            
            return finalResponse
            
        } catch (e: Exception) {
            Log.e(TAG, "Intelligent search flow failed", e)
            return "I encountered an error while searching. Please try again."
        }
    }
    
    /**
     * Build system prompt for search-based responses - ULTRA COMPACT
     */
    private fun buildSearchResponseSystemPrompt(
        profile: com.confidant.ai.personalization.PersonalizationManager.UserProfile
    ): String {
        return "You are Confidant, a helpful AI assistant. Provide clear, factual answers with specific numbers and dates. Be friendly and direct."
    }
    
    /**
     * Detect if message needs function calling
     * DEPRECATED: Now using 2-call flow with intelligent search
     */
    private fun detectFunctionCallIntent(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // Greetings should NOT trigger function calling
        val greetings = listOf(
            "hi", "hello", "hey", "good morning", "good afternoon", 
            "good evening", "greetings", "howdy", "what's up", "sup",
            "yo", "hiya", "heya"
        )
        
        // Check if message is just a greeting (with optional punctuation)
        val isGreeting = greetings.any { greeting ->
            val trimmed = lowerMessage.trim().removeSuffix("!").removeSuffix(".").removeSuffix(",")
            trimmed == greeting || trimmed.startsWith("$greeting ")
        }
        
        if (isGreeting) {
            Log.i(TAG, "Detected greeting - using conversational flow")
            return false
        }
        
        // Simple conversational phrases that don't need tools
        val conversationalPhrases = listOf(
            "how are you", "what's your name", "who are you", "tell me about yourself",
            "what can you do", "help me", "thank you", "thanks", "okay", "ok",
            "got it", "i see", "interesting", "cool", "nice", "good", "great"
        )
        
        if (conversationalPhrases.any { lowerMessage.contains(it) }) {
            Log.i(TAG, "Detected conversational phrase - no tools needed")
            return false
        }
        
        // Strong indicators for function calling (question words + info keywords)
        val questionWords = listOf("what", "when", "where", "who", "how", "why", "which")
        val infoKeywords = listOf(
            "price", "cost", "value", "worth", "market", "stock", "crypto",
            "news", "latest", "current", "today", "update", "happening",
            "weather", "forecast", "temperature"
        )
        
        val hasQuestionWord = questionWords.any { lowerMessage.startsWith(it) }
        val hasInfoKeyword = infoKeywords.any { lowerMessage.contains(it) }
        
        if (hasQuestionWord && hasInfoKeyword) {
            Log.i(TAG, "Detected information query - using function calling")
            return true
        }
        
        // Explicit search/lookup requests
        val searchKeywords = listOf(
            "search", "find", "look up", "lookup", "check", "get me",
            "show me", "tell me about", "information about", "details about",
            "what is", "what's", "whats"
        )
        
        if (searchKeywords.any { lowerMessage.contains(it) }) {
            Log.i(TAG, "Detected explicit search request - using function calling")
            return true
        }
        
        // Current events/data keywords
        val currentDataKeywords = listOf(
            "bitcoin", "ethereum", "btc", "eth", "cryptocurrency",
            "stock market", "nasdaq", "dow jones", "sensex", "nifty",
            "exchange rate", "dollar", "rupee", "euro"
        )
        
        if (currentDataKeywords.any { lowerMessage.contains(it) }) {
            Log.i(TAG, "Detected current data query - using function calling")
            return true
        }
        
        Log.i(TAG, "No function call needed - conversational response")
        return false
    }
    
    /**
     * Send status update to user using smart message editing
     * Reduces message spam by editing existing status messages
     */
    private suspend fun sendStatusUpdate(message: String) {
        try {
            // Initialize message editor if not already done
            if (messageEditor == null) {
                messageEditor = TelegramMessageEditor(bot, chatId)
            }
            
            // Use message editor to update status (edits existing message)
            // Fix: Check if content changed to avoid duplicate calls
            if (message != messageEditor?.lastContent) {
                messageEditor?.updateStatus(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send status update", e)
            // Fallback to direct send if editor fails
            try {
                bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = message
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback send also failed", e2)
            }
        }
    }
    
    companion object {
        private const val TAG = "PersonalizedTelegramMgr"
    }
}
