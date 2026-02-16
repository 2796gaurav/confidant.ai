package com.confidant.ai.proactive

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.database.entity.NotificationEntity
import com.confidant.ai.database.entity.ProactiveMessageEntity
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.memory.SimplifiedMemorySystem
import com.confidant.ai.telegram.TelegramBotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SimplifiedProactiveMessaging - 3-component architecture
 * 
 * COMPONENT 1: NotificationAggregator - Collect & deduplicate
 * COMPONENT 2: ContextBuilder - Build prompt from memory
 * COMPONENT 3: DecisionEngine - Should we message? What to say?
 * 
 * Benefits:
 * - 70% less code than 5+ component system
 * - Clearer logic (linear flow: aggregate → build → decide)
 * - Easier to tune (simple motivation threshold)
 * - Better rate limiting (built-in, simple rules)
 * - More reliable (fewer failure points)
 */
class SimplifiedProactiveMessaging(
    private val context: Context,
    private val llmEngine: LLMEngine,
    private val memorySystem: SimplifiedMemorySystem,
    private val telegramBot: TelegramBotManager,
    private val database: AppDatabase
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val aggregator = NotificationAggregator()
    private val contextBuilder = ContextBuilder(memorySystem)
    private val decisionEngine = DecisionEngine(llmEngine, telegramBot, database)
    
    companion object {
        private const val TAG = "SimplifiedProactive"
        private const val PROCESS_INTERVAL_HOURS = 2L
    }
    
    /**
     * Main entry point: notification received
     */
    fun onNotificationReceived(notification: NotificationEntity) {
        scope.launch {
            aggregator.add(notification)
        }
    }
    
    /**
     * Process buffered notifications (called every 2 hours)
     */
    suspend fun processNotifications() {
        scope.launch {
            try {
                // 1. Get aggregated notifications
                val notifications = aggregator.getAndClear()
                if (notifications.isEmpty()) {
                    Log.d(TAG, "No notifications to process")
                    return@launch
                }
                
                Log.i(TAG, "🔄 Processing ${notifications.size} notifications")
                
                // 2. Build context
                val context = contextBuilder.build(notifications)
                
                // 3. Make decision and send if appropriate
                decisionEngine.process(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notifications", e)
            }
        }
    }
    
    /**
     * Start periodic processing (every 2 hours)
     */
    fun startPeriodicProcessing() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(PROCESS_INTERVAL_HOURS * 60 * 60 * 1000)
                processNotifications()
            }
        }
    }
}

/**
 * COMPONENT 1: Notification Aggregator
 * Collects and deduplicates notifications
 */
class NotificationAggregator {
    
    private val buffer = mutableListOf<NotificationEntity>()
    private val mutex = Mutex()
    
    companion object {
        private const val MAX_BUFFER_SIZE = 50
        private const val MAX_AGE_HOURS = 4
        private const val TAG = "NotificationAggregator"
        
        // Filter out our own app and system notifications
        private val IGNORED_PACKAGES = setOf(
            "com.confidant.ai",
            "android",
            "com.android.systemui"
        )
    }
    
    suspend fun add(notification: NotificationEntity) {
        // Filter 1: Ignore our own app
        if (notification.packageName in IGNORED_PACKAGES) {
            return
        }
        
        mutex.withLock {
            // Filter 2: Remove old notifications
            val cutoff = Instant.now().minusSeconds(MAX_AGE_HOURS * 3600L)
            buffer.removeAll { it.timestamp < cutoff }
            
            // Filter 3: Deduplicate (same app + title + text)
            val isDuplicate = buffer.any {
                it.packageName == notification.packageName &&
                it.title == notification.title &&
                it.text == notification.text
            }
            
            if (isDuplicate) {
                Log.d(TAG, "🚫 Duplicate notification filtered: ${notification.appName}")
                return
            }
            
            // Filter 4: Enforce max buffer size (FIFO eviction)
            if (buffer.size >= MAX_BUFFER_SIZE) {
                buffer.removeAt(0)
                Log.w(TAG, "⚠️ Buffer full, removed oldest notification")
            }
            
            buffer.add(notification)
            Log.d(TAG, "📥 Added to buffer (${buffer.size}/$MAX_BUFFER_SIZE)")
        }
    }
    
    suspend fun getAndClear(): List<NotificationEntity> {
        return mutex.withLock {
            val copy = buffer.toList()
            buffer.clear()
            copy
        }
    }
}

/**
 * COMPONENT 2: Context Builder
 * Builds prompt from memory and notifications
 */
class ContextBuilder(private val memorySystem: SimplifiedMemorySystem) {
    
    companion object {
        private const val TAG = "ContextBuilder"
    }
    
    suspend fun build(notifications: List<NotificationEntity>): ProactiveContext {
        // Get hot memory (instant)
        val recentChat = memorySystem.getRecentConversation(3)
        val userProfile = memorySystem.getUserProfile()
        val routines = memorySystem.hotMemory.getActiveRoutines()
        
        // Analyze notifications
        val summary = summarizeNotifications(notifications)
        val deviations = detectDeviations(notifications, routines)
        
        return ProactiveContext(
            notifications = notifications,
            notificationSummary = summary,
            routineDeviations = deviations,
            recentChat = recentChat,
            userProfile = userProfile,
            currentTime = LocalDateTime.now()
        )
    }
    
    private fun summarizeNotifications(notifications: List<NotificationEntity>): String {
        val byApp = notifications.groupBy { it.appName }
        return buildString {
            byApp.forEach { (app, notifs) ->
                appendLine("$app: ${notifs.size} notifications")
                notifs.take(3).forEach { 
                    appendLine("  - ${it.title}: ${it.text.take(50)}")
                }
            }
        }
    }
    
    private fun detectDeviations(
        notifications: List<NotificationEntity>,
        routines: Map<String, com.confidant.ai.memory.Routine>
    ): List<String> {
        val deviations = mutableListOf<String>()
        
        // Check gym routine
        val gymRoutine = routines["gym"]
        if (gymRoutine != null) {
            val today = LocalDateTime.now().dayOfWeek.toString()
            if (today in gymRoutine.days) {
                val hasGymNotif = notifications.any { 
                    it.text.contains("gym", ignoreCase = true) ||
                    it.text.contains("workout", ignoreCase = true)
                }
                if (!hasGymNotif) {
                    deviations.add("No gym activity today (usually goes on $today)")
                }
            }
        }
        
        return deviations
    }
}

/**
 * COMPONENT 3: Decision Engine
 * Decides if we should message and what to say
 */
class DecisionEngine(
    private val llmEngine: LLMEngine,
    private val telegramBot: TelegramBotManager,
    private val database: AppDatabase
) {
    
    private val rateLimiter = RateLimiter()
    
    companion object {
        private const val TAG = "DecisionEngine"
    }
    
    suspend fun process(context: ProactiveContext) {
        // Check rate limits first
        if (!rateLimiter.canSend()) {
            Log.d(TAG, "Rate limit reached, skipping")
            return
        }
        
        // Generate thought + motivation score
        val prompt = buildPrompt(context)
        val response = llmEngine.generate(prompt, maxTokens = 200, temperature = 0.6f)
            .getOrElse { error ->
                Log.e(TAG, "LLM generation failed: $error")
                return
            }
        
        // Parse response
        val (thought, motivation) = parseResponse(response)
        
        // Make decision
        when {
            motivation < 0.5 -> {
                Log.d(TAG, "Low motivation ($motivation), ignoring")
            }
            motivation < 0.7 -> {
                Log.d(TAG, "Medium motivation ($motivation), deferring")
                // Could queue for later
            }
            else -> {
                // High motivation, send message
                val message = formatMessage(thought, context.userProfile)
                val sent = telegramBot.sendProactiveMessage(message)
                
                if (sent) {
                    rateLimiter.recordSent()
                    
                    // Save to database
                    database.proactiveMessageDao().insert(ProactiveMessageEntity(
                        thought = thought,
                        message = message,
                        confidence = motivation.toFloat(), // Convert motivation to confidence
                        shouldTrigger = true,
                        wasSent = true,
                        timestamp = Instant.now()
                    ))
                    
                    Log.i(TAG, "✅ Sent proactive message (motivation: $motivation)")
                }
            }
        }
    }
    
    private fun buildPrompt(context: ProactiveContext): String {
        return """
You are observing ${context.userProfile.name}'s activity.

Recent notifications (last 4 hours):
${context.notificationSummary}

Routine deviations:
${context.routineDeviations.joinToString("\n") { "- $it" }}

Current time: ${context.currentTime.format(DateTimeFormatter.ofPattern("EEEE, h:mm a"))}

Generate ONE internal thought about something worth mentioning.
Rate your motivation to share (0.0-1.0):
- 0.0-0.4: Not worth mentioning
- 0.5-0.6: Could be helpful
- 0.7-1.0: Important to mention

Output format:
THOUGHT: [your observation]
MOTIVATION: [0.0-1.0]
""".trimIndent()
    }
    
    private fun parseResponse(response: String): Pair<String, Float> {
        val thoughtMatch = Regex("THOUGHT:\\s*(.+)").find(response)
        val motivationMatch = Regex("MOTIVATION:\\s*([0-9.]+)").find(response)
        
        val thought = thoughtMatch?.groupValues?.get(1)?.trim() ?: ""
        val motivation = motivationMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        
        return Pair(thought, motivation.coerceIn(0f, 1f))
    }
    
    private fun formatMessage(thought: String, profile: com.confidant.ai.memory.UserProfile): String {
        // Remove AI patterns
        var message = thought
            .replace(Regex("I noticed|I observed|Based on|According to", RegexOption.IGNORE_CASE), "")
            .trim()
        
        // Capitalize first letter
        if (message.isNotEmpty()) {
            message = message[0].uppercase() + message.substring(1)
        }
        
        return message
    }
}

/**
 * Simple rate limiter
 */
class RateLimiter {
    
    private val sentTimestamps = mutableListOf<Instant>()
    
    companion object {
        private const val MAX_PER_DAY = 3
        private const val MIN_SPACING_HOURS = 2
    }
    
    fun canSend(): Boolean {
        val now = Instant.now()
        
        // Remove old timestamps (>24h)
        sentTimestamps.removeIf { Duration.between(it, now).toHours() > 24 }
        
        // Check daily limit
        if (sentTimestamps.size >= MAX_PER_DAY) return false
        
        // Check spacing
        val lastSent = sentTimestamps.maxOrNull()
        if (lastSent != null) {
            val hoursSince = Duration.between(lastSent, now).toHours()
            if (hoursSince < MIN_SPACING_HOURS) return false
        }
        
        return true
    }
    
    fun recordSent() {
        sentTimestamps.add(Instant.now())
    }
}

// Data classes
data class ProactiveContext(
    val notifications: List<NotificationEntity>,
    val notificationSummary: String,
    val routineDeviations: List<String>,
    val recentChat: List<com.confidant.ai.memory.Message>,
    val userProfile: com.confidant.ai.memory.UserProfile,
    val currentTime: LocalDateTime
)
