package com.confidant.ai.telegram

import android.content.Context
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.integrations.FunctionCallingSystem
import com.confidant.ai.integrations.DuckDuckGoSearchTool
import com.confidant.ai.memory.SimplifiedMemorySystem
import com.confidant.ai.system.LogLevel
import com.confidant.ai.thermal.ThermalThrottlingException
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.Update
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import com.confidant.ai.prompts.OptimizedPrompts
import java.net.HttpURLConnection
import java.net.URL

/**
 * TelegramBotManager - Manages Telegram bot for user interaction
 * Handles commands, conversations, proactive messaging, and function calling
 * 
 * 2026 ENHANCEMENTS:
 * - Integrated response generation with citations
 * - Dynamic token allocation
 * - Quality validation
 * - Truthful QA principles
 * - Source credibility tracking
 * - Smart message editing to reduce spam
 */
class TelegramBotManager(
    private val context: Context,
    private val llmEngine: LLMEngine,
    private val memorySystem: SimplifiedMemorySystem,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bot: Bot? = null
    private var userChatId: Long? = null
    private var messageEditor: TelegramMessageEditor? = null  // NEW: Message editor for status updates
    
    // NEW: Rate limiter for Telegram API compliance
    private val rateLimiter = TelegramRateLimiter()
    
    private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()
    
    // NEW: Personalized Telegram manager with 2-call flow
    private var personalizedManager: PersonalizedTelegramManager? = null
    
    // Function calling system (legacy - kept for compatibility)
    private val functionCallingSystem = FunctionCallingSystem(context)
    private val searchTool = DuckDuckGoSearchTool(context)  // Pass context for caching
    
    // CONTEXT TRACKING: Track last discussed topic for context-aware search
    private var lastDiscussedTopic: String? = null
    private var lastTopicTimestamp: Long = 0
    private val TOPIC_EXPIRY_MS = 5 * 60 * 1000L  // 5 minutes
    
    /**
     * Send status update to user using smart message editing
     * Reduces message spam by editing existing status messages
     */
    private suspend fun sendStatusUpdate(message: String) {
        val chatId = userChatId ?: return
        
        try {
            // Initialize message editor if not already done
            if (messageEditor == null) {
                messageEditor = TelegramMessageEditor(bot, chatId)
            }
            
            // Use message editor to update status (edits existing message)
            messageEditor?.updateStatus(message)
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
    
    /**
     * Validate bot token with retry logic
     * Returns Result with success/failure and error message
     */
    suspend fun validateBotToken(token: String, maxRetries: Int = 3): Result<Boolean> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val url = URL("https://api.telegram.org/bot$token/getMe")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                if (responseCode == 200) {
                    return@withContext Result.success(true)
                } else if (responseCode == 401) {
                    // Invalid token - no point retrying
                    return@withContext Result.failure(Exception("Invalid bot token. Please check and try again."))
                } else {
                    throw Exception("HTTP $responseCode")
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Bot validation attempt ${attempt + 1} failed: ${e.message}")
                
                // Wait before retry with exponential backoff
                if (attempt < maxRetries - 1) {
                    val delayMs = (1000L * (1 shl attempt)) // 1s, 2s, 4s
                    delay(delayMs)
                }
            }
        }
        
        // All retries failed
        val errorMessage = when {
            lastException?.message?.contains("timeout", ignoreCase = true) == true ->
                "Connection timeout. Please check your internet connection and try again."
            lastException?.message?.contains("connection", ignoreCase = true) == true ->
                "Connection error. Please check your internet connection and try again."
            else ->
                "Failed to validate bot token: ${lastException?.message ?: "Unknown error"}"
        }
        
        Result.failure(Exception(errorMessage))
    }
    
    /**
     * Start the Telegram bot
     */
    fun startBot() {
        scope.launch {
            val token = ConfidantApplication.instance.preferencesManager.getTelegramBotToken()
            
            if (token == null) {
                Log.w(TAG, "No Telegram bot token configured")
                return@launch
            }
            
            if (bot != null) {
                Log.i(TAG, "Bot already running")
                return@launch
            }
            
            try {
                bot = createBot(token)
                bot?.startPolling()
                _isRunning.value = true
                Log.i(TAG, "Telegram bot started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Telegram bot", e)
                _isRunning.value = false
            }
        }
    }
    
    /**
     * Stop the Telegram bot
     */
    fun stopBot() {
        bot?.stopPolling()
        bot = null
        _isRunning.value = false
        Log.i(TAG, "Telegram bot stopped")
    }
    
    /**
     * Send proactive message to user
     */
    suspend fun sendProactiveMessage(message: String): Boolean = withContext(Dispatchers.IO) {
        val chatId = userChatId ?: return@withContext false
        
        try {
            bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = message
            )
            
            // Log to database
            database.proactiveMessageDao().insert(
                com.confidant.ai.database.entity.ProactiveMessageEntity(
                    notificationId = null,
                    thought = "System proactive message",
                    confidence = 0.7f,
                    shouldTrigger = true,
                    message = message,
                    wasSent = true
                )
            )
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send proactive message", e)
            false
        }
    }
    
    /**
     * Send system message to user (for critical events only)
     * Respects quiet mode preferences
     */
    suspend fun sendSystemMessage(message: String): Boolean = withContext(Dispatchers.IO) {
        val chatId = userChatId ?: return@withContext false
        
        try {
            bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = message
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send system message", e)
            false
        }
    }
    
    /**
     * Send system status to user
     */
    suspend fun sendSystemStatus(): Boolean = withContext(Dispatchers.IO) {
        val chatId = userChatId ?: return@withContext false
        
        val thermalManager = ConfidantApplication.instance.thermalManager
        val stats = memorySystem.memoryStats.value
        
        val message = buildString {
            appendLine("*System Status* ⚙️")
            appendLine()
            appendLine("Thermal State: ${thermalManager.getThermalStatus()}")
            appendLine("Temperature: ${thermalManager.cpuTemperature.value.toInt()}°C")
            appendLine()
            appendLine("Memory Episodes: ${stats.recallMemoryCount}")
            appendLine("Core Facts: ${stats.coreMemoryEntries}")
            appendLine("Context Tokens: ${stats.workingContextTokens}/2048")
            appendLine()
            appendLine("Model: LFM2.5-1.2B-Instruct Q4_K_M")
        }
        
        try {
            bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = message,
                parseMode = ParseMode.MARKDOWN
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send status", e)
            false
        }
    }
    
    private fun createBot(token: String): Bot {
        return bot {
            this.token = token
            
            dispatch {
                // Handle /start command
                command("start") { 
                    handleStartCommand(message)
                }
                
                // Handle /status command
                command("status") { 
                    handleStatusCommand(message)
                }
                
                // Handle /memory command
                command("memory") { 
                    handleMemoryCommand(message)
                }
                
                // Handle /help command
                command("help") { 
                    handleHelpCommand(message)
                }
                
                // Handle text messages
                text { 
                    handleTextMessage(message)
                }
            }
        }
    }
    
    private fun handleStartCommand(message: com.github.kotlintelegrambot.entities.Message) {
        scope.launch {
            userChatId = message.chat.id
            
            // Save user chat ID
            ConfidantApplication.instance.preferencesManager.setTelegramChatId(message.chat.id)
            
            val welcomeText = "Hey! 👋 I'm Confidant, your AI companion.\n\nJust chat with me naturally - I'm here to help whenever you need me."
            
            bot?.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = welcomeText
            )
        }
    }
    
    private fun handleStatusCommand(message: com.github.kotlintelegrambot.entities.Message) {
        scope.launch {
            sendSystemStatus()
        }
    }
    
    private fun handleMemoryCommand(message: com.github.kotlintelegrambot.entities.Message) {
        scope.launch {
            val stats = memorySystem.memoryStats.value
            
            val memoryText = buildString {
                appendLine("*Your Memory Stats* 🧠")
                appendLine()
                appendLine("📚 Recall Memories: ${stats.recallMemoryCount}")
                appendLine("💎 Core Facts: ${stats.coreMemoryEntries}")
                appendLine("💬 Today's Messages: ${stats.todayConversations}")
                appendLine()
                appendLine("I'm constantly learning about you to be more helpful!")
            }
            
            bot?.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = memoryText,
                parseMode = ParseMode.MARKDOWN
            )
        }
    }
    
    private fun handleHelpCommand(message: com.github.kotlintelegrambot.entities.Message) {
        scope.launch {
            val helpText = buildString {
                appendLine("*Available Commands* 📋")
                appendLine()
                appendLine("/start - Start the conversation")
                appendLine("/status - Check system status")
                appendLine("/memory - View memory statistics")
                appendLine("/help - Show this help message")
                appendLine()
                appendLine("You can also just chat with me naturally!")
            }
            
            bot?.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = helpText,
                parseMode = ParseMode.MARKDOWN
            )
        }
    }
    
    // Prevent duplicate message processing
    private val processingMessages = mutableSetOf<Long>()
    
    // Track current processing job for interruption
    private var currentProcessingJob: Job? = null
    
    private fun handleTextMessage(message: com.github.kotlintelegrambot.entities.Message) {
        val text = message.text ?: return
        val chatId = message.chat.id
        val messageId = message.messageId
        val serverManager = ConfidantApplication.instance.serverManager
        val prefsManager = ConfidantApplication.instance.preferencesManager
        val modelDownloadManager = ConfidantApplication.instance.modelDownloadManager
        
        // INTERRUPT: Cancel any ongoing processing when new message arrives
        currentProcessingJob?.cancel()
        currentProcessingJob = null
        
        // CRITICAL: Check if model is downloaded and loaded
        if (!modelDownloadManager.isModelDownloaded()) {
            serverManager.addLog(
                "⚠️ Message received but model not downloaded - sending friendly message",
                LogLevel.WARN,
                "📨 Telegram"
            )
            scope.launch {
                bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = StandardizedMessages.getModelNotDownloadedMessage()
                )
            }
            return
        }
        
        // CRITICAL: Check if model is initialized in LLM engine
        if (!llmEngine.isInitialized.value) {
            serverManager.addLog(
                "⚠️ Message received but model not initialized - sending friendly message",
                LogLevel.WARN,
                "📨 Telegram"
            )
            scope.launch {
                bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = StandardizedMessages.getServerNotStartedMessage()
                )
            }
            return
        }
        
        // CRITICAL: Check sleep mode
        val sleepModeHandler = ConfidantApplication.instance.sleepModeHandler
        if (sleepModeHandler.isSleepModeActive()) {
            scope.launch {
                sleepModeHandler.handleSleepModeMessage()
            }
            return
        }
        
        // CRITICAL: Prevent duplicate processing
        synchronized(processingMessages) {
            if (processingMessages.contains(messageId)) {
                serverManager.addLog(
                    "⚠️ Duplicate message detected (ID: $messageId) - SKIPPING",
                    LogLevel.WARN,
                    "📨 Telegram"
                )
                return
            }
            processingMessages.add(messageId)
        }
        
        // LOG: Incoming message with full details
        serverManager.addLog(
            "═══════════════════════════════════════════════════",
            LogLevel.INFO,
            "📨 Telegram"
        )
        serverManager.addLog(
            "Message from user (${message.from?.firstName ?: "Unknown"}): \"${text.take(100)}${if (text.length > 100) "..." else ""}\"",
            LogLevel.INFO,
            "📨 Telegram"
        )
        serverManager.addLog(
            "Message ID: $messageId | Chat ID: $chatId | Thread: ${Thread.currentThread().name}",
            LogLevel.DEBUG,
            "📨 Telegram"
        )
        
        // Save user chat ID if not already saved
        if (userChatId == null) {
            userChatId = chatId
            scope.launch { 
                ConfidantApplication.instance.preferencesManager.setTelegramChatId(chatId)
                serverManager.addLog(
                    "Chat ID saved: $chatId",
                    LogLevel.DEBUG,
                    "📨 Telegram"
                )
            }
        }
        
        // Create new processing job (can be cancelled by next message)
        currentProcessingJob = scope.launch {
            try {
                // Show random "thinking" message and capture message ID
                val thinkingMessage = StandardizedMessages.getThinkingMessage()
                val thinkingMsg = bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = thinkingMessage
                )
                
                val thinkingMessageId = thinkingMsg?.get()?.messageId
                
                serverManager.addLog(
                    "Sent thinking message: $thinkingMessage (ID: $thinkingMessageId)",
                    LogLevel.DEBUG,
                    "📨 Telegram"
                )
                
                serverManager.addLog(
                    "Typing indicator sent",
                    LogLevel.DEBUG,
                    "📨 Telegram"
                )
                
                // Check thermal before starting
                val thermalManager = ConfidantApplication.instance.thermalManager
                val thermalState = thermalManager.getThermalStatus()
                val cpuTemp = thermalManager.cpuTemperature.value.toInt()
                
                serverManager.addLog(
                    "State: $thermalState, CPU: ${cpuTemp}°C",
                    LogLevel.DEBUG,
                    "🌡️ Thermal"
                )
                
                if (!thermalManager.canStartInference()) {
                    serverManager.addLog(
                        "Inference blocked - device too hot ($thermalState, ${cpuTemp}°C)",
                        LogLevel.WARN,
                        "🔥 Thermal"
                    )
                    
                    bot?.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = "My circuits are running a bit hot right now (${thermalState}). Give me a moment to cool down! 🔥"
                    )
                    return@launch
                }
                
                // ═══════════════════════════════════════════════════════════
                // 2026 OPTIMIZED 2-CALL FLOW WITH BUILT-IN SEARCH
                // ═══════════════════════════════════════════════════════════
                
                serverManager.addLog(
                    "Using 2026 OPTIMIZED STREAMING FLOW",
                    LogLevel.INFO,
                    "🚀 System"
                )
                
                // Initialize personalized manager if not already done
                if (personalizedManager == null) {
                    personalizedManager = PersonalizedTelegramManager(
                        context = context,
                        llmEngine = llmEngine,
                        memorySystem = memorySystem,
                        database = database,
                        bot = bot,
                        chatId = chatId
                    )
                }
                
                // Set the thinking message ID for status updates
                personalizedManager!!.setThinkingMessageId(thinkingMessageId)
                
                // Initialize streaming manager for this chat with the thinking message ID
                val streamingManager = StreamingResponseManager(
                    bot = bot,
                    chatId = chatId,
                    rateLimiter = rateLimiter,
                    initialMessageId = thinkingMessageId  // Pass the thinking message ID to edit
                )
                
                val startTime = System.currentTimeMillis()
                
                // Use PersonalizedTelegramManager with REAL STREAMING
                val response = personalizedManager!!.processMessageWithStreaming(
                    userMessage = text,
                    streamingManager = streamingManager
                )
                
                val duration = System.currentTimeMillis() - startTime
                
                serverManager.addLog(
                    "✓ ✓ Response generated in ${duration}ms",
                    LogLevel.SUCCESS,
                    "⚡ System"
                )
                
                // Response already sent via streaming, just log it
                serverManager.addLog(
                    "Full response (${response.length} chars)",
                    LogLevel.INFO,
                    "💬 Response"
                )
                
                serverManager.addLog(
                    "Response sent to user",
                    LogLevel.SUCCESS,
                    "📨 Telegram"
                )
                
                // Save to conversations table (replaces telegram_messages)
                database.conversationDao().insert(
                    com.confidant.ai.database.entity.ConversationEntity(
                        role = "user",
                        content = text,
                        timestamp = java.time.Instant.now(),
                        sessionId = chatId.toString()
                    )
                )
                database.conversationDao().insert(
                    com.confidant.ai.database.entity.ConversationEntity(
                        role = "assistant",
                        content = response,
                        timestamp = java.time.Instant.now(),
                        sessionId = chatId.toString()
                    )
                )
                
                serverManager.addLog(
                    "Messages saved to database",
                    LogLevel.DEBUG,
                    "💾 Database"
                )
                
                // Notify simplified proactive messaging about user message
                // (No action needed - handled by notification capture)
                
                // Notify simplified proactive messaging about bot message
                // (No action needed - handled by notification capture)
                
                serverManager.addLog(
                    "═══════════════════════════════════════════════════",
                    LogLevel.INFO,
                    "✅ Complete"
                )
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled by new message - this is expected behavior
                Log.i(TAG, "Message processing cancelled (interrupted by new message)")
                serverManager.addLog(
                    "Message processing interrupted by new message",
                    LogLevel.INFO,
                    "⚡ System"
                )
                // Don't send error message to user - interruption is intentional
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message", e)
                
                serverManager.addLog(
                    "Unexpected error: ${e.message}",
                    LogLevel.ERROR,
                    "💥 System"
                )
                serverManager.addLog(
                    "Stack trace: ${e.stackTraceToString().take(500)}",
                    LogLevel.ERROR,
                    "💥 System"
                )
                
                bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "Sorry, I encountered an error. Please try again later."
                )
            } finally {
                // Remove from processing set
                synchronized(processingMessages) {
                    processingMessages.remove(messageId)
                }
            }
        }
    }
    
    /**
     * Get user-friendly feedback message for function calls
     */
    private fun getFunctionFeedback(functionName: String): String {
        return when {
            functionName == "web_search" -> "information from the web"
            else -> "data"
        }
    }
    
    companion object {
        private const val TAG = "TelegramBotManager"
    }
}
