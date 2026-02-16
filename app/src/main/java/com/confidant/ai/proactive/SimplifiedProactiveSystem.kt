package com.confidant.ai.proactive

import android.content.Context
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.database.entity.NotificationEntity
import com.confidant.ai.database.entity.ProactiveMessageEntity
import com.confidant.ai.database.entity.NoteEntity
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.integrations.DuckDuckGoSearchTool
import com.confidant.ai.telegram.TelegramBotManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Random

/**
 * Simplified Proactive System - Clean and effective
 * 
 * Features:
 * 1. First-time proactive messaging with DuckDuckGo search
 * 2. Hourly proactive processing (random time, 30 calls/hour limit)
 * 3. Notification scoring and decision making
 * 4. Sleep mode integration
 */
class SimplifiedProactiveSystem(
    private val context: Context,
    private val llmEngine: LLMEngine,
    private val telegramBot: TelegramBotManager,
    private val database: AppDatabase,
    private val searchTool: DuckDuckGoSearchTool
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMutex = Mutex()
    private var isProcessing = false
    
    // Track last proactive processing time
    private var lastProcessingTime: Instant? = null
    private var nextProcessingTime: Instant? = null
    
    companion object {
        private const val TAG = "SimplifiedProactive"
        private const val MAX_LLM_CALLS_PER_HOUR = 30
        private const val RETENTION_DAYS = 10
        private const val FIRST_TIME_DELAY_MINUTES = 2L
        private const val CONFIDENCE_THRESHOLD = 0.7f
    }
    
    /**
     * Initialize the proactive system
     */
    fun initialize() {
        scope.launch {
            // Schedule first hourly processing
            scheduleNextHourlyProcessing()
            
            // Start periodic cleanup (every hour)
            startPeriodicCleanup()
            
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            Log.i(TAG, "[$timestamp] ✅ Simplified proactive system initialized")
            Log.i(TAG, "[$timestamp] 📅 Hourly proactive processing scheduled (will run at random time each hour)")
        }
    }
    
    /**
     * Cleanup resources - properly cancel all coroutines
     */
    fun cleanup() {
        runBlocking {
            processingMutex.withLock {
                isProcessing = false
            }
        }
        scope.cancel()
        Log.d(TAG, "Simplified proactive system cleaned up")
    }
    
    /**
     * Handle first-time proactive messaging when user provides preferences
     */
    suspend fun handleFirstTimeProactive(interests: List<String>) {
        if (interests.isEmpty()) {
            Log.i(TAG, "⚠️ First-time proactive skipped: No interests provided")
            return
        }
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val scheduledTime = LocalDateTime.now().plusMinutes(FIRST_TIME_DELAY_MINUTES)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        Log.i(TAG, "[$timestamp] 🎯 First-time proactive messaging scheduled")
        Log.i(TAG, "[$timestamp] 📋 Interests: ${interests.joinToString(", ")}")
        Log.i(TAG, "[$timestamp] ⏰ Planned execution: $scheduledTime (after ${FIRST_TIME_DELAY_MINUTES} minutes of inactivity)")
        Log.i(TAG, "[$timestamp] 🔍 Will search DuckDuckGo for up to 3 interests and generate personalized message")
        
        scope.launch {
            try {
                // Wait 2 minutes of inactivity
                delay(FIRST_TIME_DELAY_MINUTES * 60 * 1000)
                
                val checkTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.i(TAG, "[$checkTimestamp] 🔄 Checking if user is inactive for first-time proactive...")
                
                // Check if user is still inactive
                if (!isUserInactive()) {
                    Log.i(TAG, "[$checkTimestamp] ⏸️ User is active, skipping first-time proactive")
                    return@launch
                }
                
                Log.i(TAG, "[$checkTimestamp] ✅ User is inactive, proceeding with first-time proactive")
                
                // Search DuckDuckGo for each interest
                val searchTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.i(TAG, "[$searchTimestamp] 🔍 Starting DuckDuckGo search for interests...")
                
                val searchResults = mutableListOf<String>()
                for (interest in interests.take(3)) { // Limit to 3 interests
                    try {
                        Log.i(TAG, "[$searchTimestamp] 🔎 Searching for: $interest")
                        val result = searchTool.execute(
                            arguments = mapOf("query" to interest, "max_results" to "3")
                        )
                        if (result.isSuccess) {
                            val resultText = result.getOrNull() ?: ""
                            searchResults.add("Interest: $interest\n${resultText.take(200)}")
                            Log.i(TAG, "[$searchTimestamp] ✅ Search completed for: $interest")
                        } else {
                            Log.w(TAG, "[$searchTimestamp] ⚠️ Search failed for: $interest")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[$searchTimestamp] ❌ Search error for interest: $interest", e)
                    }
                }
                
                if (searchResults.isEmpty()) {
                    Log.w(TAG, "[$searchTimestamp] ⚠️ No search results, skipping first-time proactive")
                    return@launch
                }
                
                Log.i(TAG, "[$searchTimestamp] ✅ Found ${searchResults.size} search results, generating proactive message...")
                
                // Generate proactive message using LLM
                val prompt = buildFirstTimeProactivePrompt(interests, searchResults)
                val llmTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.i(TAG, "[$llmTimestamp] 🤖 Calling LLM to generate proactive message...")
                
                val response = llmEngine.generate(prompt, maxTokens = 300, temperature = 0.7f)
                
                response.getOrNull()?.let { message ->
                    val sendTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    Log.i(TAG, "[$sendTimestamp] 📤 Sending first-time proactive message to user...")
                    
                    // Send proactive message
                    val sent = telegramBot.sendProactiveMessage(message)
                    
                    if (sent) {
                        // Save to proactive messages table
                        database.proactiveMessageDao().insert(
                            ProactiveMessageEntity(
                                notificationId = null,
                                thought = "First-time proactive based on user interests",
                                confidence = 0.8f,
                                shouldTrigger = true,
                                message = message,
                                wasSent = true
                            )
                        )
                        
                        Log.i(TAG, "[$sendTimestamp] ✅ First-time proactive message sent successfully")
                        Log.i(TAG, "[$sendTimestamp] 💾 Message saved to proactive_messages table")
                    } else {
                        Log.w(TAG, "[$sendTimestamp] ⚠️ Failed to send first-time proactive message")
                    }
                } ?: run {
                    val errorTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    Log.e(TAG, "[$errorTimestamp] ❌ LLM failed to generate proactive message")
                }
                
            } catch (e: Exception) {
                val errorTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.e(TAG, "[$errorTimestamp] ❌ Failed to send first-time proactive message", e)
            }
        }
    }
    
    /**
     * Process notifications hourly (random time, once per hour)
     */
    suspend fun processHourlyProactive() {
        val processingStartInstant = Instant.now()
        val startTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        processingMutex.withLock {
            if (isProcessing) {
                Log.i(TAG, "[$startTimestamp] ⏸️ Already processing hourly proactive, skipping")
                return
            }
            
            // Ensure only once per hour - check if we processed recently
            if (lastProcessingTime != null) {
                val timeSinceLastProcessing = Duration.between(lastProcessingTime, Instant.now()).toMinutes()
                if (timeSinceLastProcessing < 55) { // At least 55 minutes between processing
                    Log.i(TAG, "[$startTimestamp] ⏸️ Too soon since last processing ($timeSinceLastProcessing minutes), skipping")
                    scheduleNextHourlyProcessing()
                    return
                }
            }
            
            isProcessing = true
        }
        
        Log.i(TAG, "[$startTimestamp] 🔄 Starting hourly proactive processing...")
        
        try {
            // Check if user and bot are inactive
            if (!isUserInactive()) {
                Log.i(TAG, "[$startTimestamp] ⏸️ User is active, skipping hourly processing")
                scheduleNextHourlyProcessing()
                return
            }
            
            // Check sleep mode
            if (isSleepMode()) {
                Log.i(TAG, "[$startTimestamp] ⏸️ Sleep mode active, skipping hourly processing")
                scheduleNextHourlyProcessing()
                return
            }
            
            // Check LLM calls limit (30 per hour)
            val oneHourAgo = Instant.now().minusSeconds(3600)
            val callsThisHour = database.proactiveMessageDao().countCallsSince(oneHourAgo)
            
            Log.i(TAG, "[$startTimestamp] 📊 LLM calls this hour: $callsThisHour/$MAX_LLM_CALLS_PER_HOUR")
            
            if (callsThisHour >= MAX_LLM_CALLS_PER_HOUR) {
                Log.i(TAG, "[$startTimestamp] ⏸️ LLM call limit reached ($callsThisHour/$MAX_LLM_CALLS_PER_HOUR), skipping")
                scheduleNextHourlyProcessing()
                return
            }
            
            // Get unprocessed notifications (within retention period)
            val tenDaysAgo = Instant.now().minusSeconds(RETENTION_DAYS * 24 * 3600L)
            val notifications = database.notificationDao().getNotificationsForProcessing(
                since = tenDaysAgo,
                limit = MAX_LLM_CALLS_PER_HOUR - callsThisHour
            )
            
            Log.i(TAG, "[$startTimestamp] 📬 Found ${notifications.size} notifications in retention period")
            
            if (notifications.isEmpty()) {
                Log.i(TAG, "[$startTimestamp] ℹ️ No notifications to process")
                scheduleNextHourlyProcessing()
                return
            }
            
            // Get already processed notification IDs
            val processedIds = database.proactiveMessageDao().getProcessedNotificationIds().toSet()
            
            // Filter out already processed notifications
            val unprocessedNotifications = notifications.filter { it.id !in processedIds }
            
            Log.i(TAG, "[$startTimestamp] 🔍 Filtered: ${unprocessedNotifications.size} unprocessed notifications (${processedIds.size} already processed)")
            
            if (unprocessedNotifications.isEmpty()) {
                Log.i(TAG, "[$startTimestamp] ℹ️ All notifications already processed")
                scheduleNextHourlyProcessing()
                return
            }
            
            val processCount = minOf(unprocessedNotifications.size, MAX_LLM_CALLS_PER_HOUR - callsThisHour)
            Log.i(TAG, "[$startTimestamp] 🔄 Processing $processCount notifications (one LLM call per notification)")
            
            // Process each notification (one LLM call per notification)
            var processedCount = 0
            var triggeredCount = 0
            
            for (notification in unprocessedNotifications.take(MAX_LLM_CALLS_PER_HOUR - callsThisHour)) {
                try {
                    val notificationTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    Log.i(TAG, "[$notificationTimestamp] 🔍 Processing notification ${notification.id}: ${notification.title}")
                    
                    processNotification(notification)
                    
                    // Check if this notification triggered a message
                    val afterConfidence = database.proactiveMessageDao().getByNotificationId(notification.id)?.confidence ?: 0f
                    if (afterConfidence >= CONFIDENCE_THRESHOLD) {
                        triggeredCount++
                        Log.i(TAG, "[$notificationTimestamp] ✅ Notification ${notification.id} triggered proactive message (confidence: $afterConfidence)")
                    } else {
                        Log.i(TAG, "[$notificationTimestamp] ℹ️ Notification ${notification.id} below threshold (confidence: $afterConfidence < $CONFIDENCE_THRESHOLD)")
                    }
                    
                    // Mark notification as processed
                    database.notificationDao().markAsProcessed(notification.id)
                    processedCount++
                    
                    // Small delay between calls
                    delay(1000) // 1 second
                    
                } catch (e: Exception) {
                    val errorTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    Log.e(TAG, "[$errorTimestamp] ❌ Failed to process notification ${notification.id}", e)
                }
            }
            
            Log.i(TAG, "[$startTimestamp] 📊 Processing summary: $processedCount processed, $triggeredCount triggered messages")
            
            // After processing, send messages that should be triggered
            val sendTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            Log.i(TAG, "[$sendTimestamp] 📤 Sending pending proactive messages...")
            sendPendingMessages()
            
            // Update last processing time
            lastProcessingTime = Instant.now()
            
            val completeTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val duration = Duration.between(processingStartInstant, Instant.now()).seconds
            Log.i(TAG, "[$completeTimestamp] ✅ Hourly proactive processing complete (took ${duration}s)")
            
        } catch (e: Exception) {
            val errorTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            Log.e(TAG, "[$errorTimestamp] ❌ Failed hourly proactive processing", e)
        } finally {
            processingMutex.withLock {
                isProcessing = false
            }
            // Schedule next processing for next hour (random time)
            scheduleNextHourlyProcessing()
        }
    }
    
    /**
     * Process a single notification with LLM
     */
    private suspend fun processNotification(notification: NotificationEntity) {
        val processTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        try {
            Log.i(TAG, "[$processTimestamp] 🤖 Calling LLM to analyze notification: ${notification.title}")
            
            // Build prompt for LLM
            val prompt = buildNotificationAnalysisPrompt(notification)
            
            // Call LLM
            val response = llmEngine.generate(prompt, maxTokens = 400, temperature = 0.6f)
            
            response.getOrNull()?.let { llmResponse ->
                // Parse LLM response
                val analysis = parseLLMResponse(llmResponse)
                
                Log.i(TAG, "[$processTimestamp] 📊 LLM analysis: confidence=${analysis.confidence}, trigger=${analysis.confidence >= CONFIDENCE_THRESHOLD}, saveToNotes=${analysis.shouldSaveToNotes}")
                
                // Save to proactive_messages table
                val proactiveMessage = ProactiveMessageEntity(
                    notificationId = notification.id,
                    thought = analysis.thought,
                    confidence = analysis.confidence,
                    shouldTrigger = analysis.confidence >= CONFIDENCE_THRESHOLD,
                    message = if (analysis.confidence >= CONFIDENCE_THRESHOLD) analysis.message else null,
                    shouldSaveToNotes = analysis.shouldSaveToNotes,
                    noteContent = analysis.noteContent
                )
                
                database.proactiveMessageDao().insert(proactiveMessage)
                Log.i(TAG, "[$processTimestamp] 💾 Saved analysis to proactive_messages table")
                
                // If should save to notes, save it
                if (analysis.shouldSaveToNotes && analysis.noteContent != null) {
                    Log.i(TAG, "[$processTimestamp] 📝 Saving to notes...")
                    saveToNotes(analysis.noteContent, notification)
                }
                
            } ?: run {
                Log.e(TAG, "[$processTimestamp] ❌ LLM failed to generate response for notification ${notification.id}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[$processTimestamp] ❌ Failed to process notification ${notification.id}", e)
        }
    }
    
    /**
     * Send pending proactive messages
     */
    private suspend fun sendPendingMessages() {
        val sendTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        try {
            val pendingMessages = database.proactiveMessageDao().getPendingMessages(limit = 10)
            
            if (pendingMessages.isEmpty()) {
                Log.i(TAG, "[$sendTimestamp] ℹ️ No pending messages to send")
                return
            }
            
            Log.i(TAG, "[$sendTimestamp] 📤 Sending ${pendingMessages.size} pending proactive messages")
            
            var sentCount = 0
            var failedCount = 0
            
            for (message in pendingMessages) {
                try {
                    val messageTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    Log.i(TAG, "[$messageTimestamp] 📨 Sending proactive message ${message.id} (confidence: ${message.confidence})")
                    
                    val messageText = formatProactiveMessage(message)
                    val sent = telegramBot.sendProactiveMessage(messageText)
                    
                    if (sent) {
                        database.proactiveMessageDao().markAsSent(message.id)
                        sentCount++
                        Log.i(TAG, "[$messageTimestamp] ✅ Sent proactive message ${message.id}")
                    } else {
                        failedCount++
                        Log.w(TAG, "[$messageTimestamp] ⚠️ Failed to send proactive message ${message.id}")
                    }
                } catch (e: Exception) {
                    failedCount++
                    val errorTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    Log.e(TAG, "[$errorTimestamp] ❌ Failed to send proactive message ${message.id}", e)
                }
            }
            
            val summaryTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            Log.i(TAG, "[$summaryTimestamp] 📊 Message sending summary: $sentCount sent, $failedCount failed")
            
        } catch (e: Exception) {
            Log.e(TAG, "[$sendTimestamp] ❌ Failed to send pending messages", e)
        }
    }
    
    /**
     * Format proactive message for sending - Standardized format
     */
    private fun formatProactiveMessage(message: ProactiveMessageEntity): String {
        return buildString {
            // Main message content
            append(message.message ?: message.thought)
            
            // Standardized note-saving message (no DB calls needed - info already in message entity)
            if (message.shouldSaveToNotes && message.noteContent != null) {
                append("\n\n📝 I've saved some details in your notes.")
            }
        }
    }
    
    /**
     * Save content to notes
     */
    private suspend fun saveToNotes(content: String, notification: NotificationEntity) {
        val notesTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        try {
            val note = NoteEntity(
                title = notification.title.take(100),
                content = content,
                tags = "[]",
                category = "personal",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                priority = 1
            )
            
            database.noteDao().insert(note)
            Log.i(TAG, "[$notesTimestamp] ✅ Saved notification to notes: ${note.title}")
        } catch (e: Exception) {
            Log.e(TAG, "[$notesTimestamp] ❌ Failed to save to notes", e)
        }
    }
    
    /**
     * Build prompt for first-time proactive messaging
     */
    private fun buildFirstTimeProactivePrompt(interests: List<String>, searchResults: List<String>): String {
        return """
You are Confidant, a helpful AI assistant. The user just shared their interests: ${interests.joinToString(", ")}.

Based on web search results, here's what I found:
${searchResults.joinToString("\n\n")}

Generate a friendly, interesting proactive message (2-3 sentences) that:
1. Shows you understand their interests
2. Shares something interesting/factual/news related to their interests
3. Is engaging and conversational

Keep it natural and don't mention that you searched the web. Just share the interesting information naturally.
""".trimIndent()
    }
    
    /**
     * Build prompt for notification analysis
     */
    private fun buildNotificationAnalysisPrompt(notification: NotificationEntity): String {
        return """
You are analyzing a notification to decide if it's worth proactively mentioning to the user.

Notification:
- App: ${notification.appName}
- Title: ${notification.title}
- Text: ${notification.text}
- Category: ${notification.category}
- Priority: ${notification.priority}

Analyze this notification and provide:
1. THOUGHT: Your internal thought about this notification (1-2 sentences)
2. CONFIDENCE: A score 0.0-1.0 indicating how important/interesting this is to mention (0.7+ = worth mentioning)
3. MESSAGE: If confidence >= 0.7, provide a friendly message to send (2-3 sentences). Otherwise, leave empty.
4. SAVE_TO_NOTES: true/false - Should we save personal details from this notification to notes?
5. NOTE_CONTENT: If SAVE_TO_NOTES=true, provide the content to save (personal details, important info)

Output format:
THOUGHT: [your thought]
CONFIDENCE: [0.0-1.0]
MESSAGE: [message if confidence >= 0.7, else empty]
SAVE_TO_NOTES: [true/false]
NOTE_CONTENT: [content if SAVE_TO_NOTES=true, else empty]
""".trimIndent()
    }
    
    /**
     * Parse LLM response
     */
    private fun parseLLMResponse(response: String): NotificationAnalysis {
        val thoughtMatch = Regex("THOUGHT:\\s*(.+)", RegexOption.DOT_MATCHES_ALL).find(response)
        val confidenceMatch = Regex("CONFIDENCE:\\s*([0-9.]+)").find(response)
        val messageMatch = Regex("MESSAGE:\\s*(.+?)(?=SAVE_TO_NOTES|$)", RegexOption.DOT_MATCHES_ALL).find(response)
        val saveToNotesMatch = Regex("SAVE_TO_NOTES:\\s*(true|false)", RegexOption.IGNORE_CASE).find(response)
        val noteContentMatch = Regex("NOTE_CONTENT:\\s*(.+?)(?=\\n|$)", RegexOption.DOT_MATCHES_ALL).find(response)
        
        val thought = thoughtMatch?.groupValues?.get(1)?.trim() ?: ""
        val confidence = confidenceMatch?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        val message = messageMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: ""
        val shouldSaveToNotes = saveToNotesMatch?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: false
        val noteContent = noteContentMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        
        return NotificationAnalysis(thought, confidence, message, shouldSaveToNotes, noteContent)
    }
    
    /**
     * Check if user is inactive (no conversation in last 2 minutes)
     */
    private suspend fun isUserInactive(): Boolean {
        val lastConversation = database.conversationDao().getLastConversationTimestamp()
        return if (lastConversation != null) {
            val minutesSince = Duration.between(lastConversation, Instant.now()).toMinutes()
            minutesSince >= FIRST_TIME_DELAY_MINUTES
        } else {
            true // No conversations yet, consider inactive
        }
    }
    
    /**
     * Check if sleep mode is active
     */
    private suspend fun isSleepMode(): Boolean {
        val sleepStartHour = database.coreMemoryDao().getByKey("sleep_mode.start_hour")?.value?.toIntOrNull() ?: return false
        val sleepEndHour = database.coreMemoryDao().getByKey("sleep_mode.end_hour")?.value?.toIntOrNull() ?: return false
        
        val currentHour = java.time.LocalDateTime.now().hour
        
        return if (sleepStartHour < sleepEndHour) {
            currentHour >= sleepStartHour && currentHour < sleepEndHour
        } else {
            currentHour >= sleepStartHour || currentHour < sleepEndHour
        }
    }
    
    /**
     * Schedule next hourly processing at random time (once per hour)
     * Ensures truly random time between 0-59 minutes from now
     * This ensures processing happens exactly once per hour at a random time
     */
    private fun scheduleNextHourlyProcessing() {
        scope.launch {
            try {
                val scheduleTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                val random = Random()
                
                // Schedule for next hour: wait remaining time + random minutes
                val minutesSinceLastProcessing = lastProcessingTime?.let {
                    Duration.between(it, Instant.now()).toMinutes().toInt()
                } ?: 0
                
                // Calculate delay to next hour + random minutes
                val minutesUntilNextHour = 60 - (minutesSinceLastProcessing % 60)
                val randomMinutes = random.nextInt(60) // 0-59 minutes
                val totalDelayMinutes = minutesUntilNextHour + randomMinutes
                val delayMs = (totalDelayMinutes * 60 * 1000).toLong()
                
                // Calculate when it will execute
                val plannedExecutionTime = LocalDateTime.now().plusMinutes(totalDelayMinutes.toLong())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                
                Log.i(TAG, "[$scheduleTimestamp] ⏰ Scheduling next hourly proactive processing")
                Log.i(TAG, "[$scheduleTimestamp] 📅 Planned execution time: $plannedExecutionTime")
                Log.i(TAG, "[$scheduleTimestamp] ⏱️ Delay: $totalDelayMinutes minutes (random offset: $randomMinutes minutes)")
                Log.i(TAG, "[$scheduleTimestamp] 📊 Minutes since last processing: $minutesSinceLastProcessing")
                
                delay(delayMs)
                
                // Update tracking times
                nextProcessingTime = Instant.now().plusSeconds(3600)
                
                val executeTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.i(TAG, "[$executeTimestamp] 🚀 Executing scheduled hourly proactive processing")
                
                // Process hourly proactive
                processHourlyProactive()
            } catch (e: CancellationException) {
                // Job was cancelled - this is expected
                val cancelTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.i(TAG, "[$cancelTimestamp] ⏸️ Hourly processing schedule cancelled (expected)")
            } catch (e: Exception) {
                val errorTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.e(TAG, "[$errorTimestamp] ❌ Failed to schedule hourly processing", e)
                // Retry after 1 hour
                delay(3600 * 1000)
                scheduleNextHourlyProcessing()
            }
        }
    }
    
    /**
     * Start periodic cleanup (every hour)
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(3600 * 1000) // 1 hour
                
                try {
                    // Delete old notifications (10 days retention)
                    val tenDaysAgo = Instant.now().minusSeconds(RETENTION_DAYS * 24 * 3600L)
                    database.notificationDao().deleteOldNotifications(tenDaysAgo)
                    
                    // Delete old conversations (10 days retention)
                    database.conversationDao().deleteOld(tenDaysAgo)
                    
                    Log.d(TAG, "🧹 Cleanup complete")
                } catch (e: Exception) {
                    Log.e(TAG, "Cleanup failed", e)
                }
            }
        }
    }
    
    /**
     * Data class for notification analysis
     */
    private data class NotificationAnalysis(
        val thought: String,
        val confidence: Float,
        val message: String,
        val shouldSaveToNotes: Boolean,
        val noteContent: String?
    )
}
