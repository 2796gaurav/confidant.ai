package com.confidant.ai.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.work.*
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.database.entity.NotificationEntity
import com.confidant.ai.system.LogLevel
import kotlinx.coroutines.*
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * NotificationCaptureService - 24/7 passive notification capture
 * Uses NotificationListenerService API (API 18+)
 */
class NotificationCaptureService : NotificationListenerService() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { ConfidantApplication.instance.database }
    private val parser by lazy { NotificationParser() }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NotificationCaptureService created")
        ConfidantApplication.instance.serverManager.addLog(
            "📱 Notification capture service created",
            LogLevel.INFO,
            "Service"
        )
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected - passive capture started")
        ConfidantApplication.instance.serverManager.addLog(
            "✅ Notification listener connected - passive capture active",
            LogLevel.SUCCESS,
            "Service"
        )
        
        // Schedule heartbeat worker
        scheduleHeartbeat()
    }
    
    private var lastLogTime = 0L
    private var notificationCount = 0
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        serviceScope.launch {
            try {
                val notification = extractNotificationData(sbn)
                val parsed = parser.parse(notification)
                
                // Deduplication: Check if same notification (title + description/text) exists in last 5 minutes
                val fiveMinutesAgo = java.time.Instant.now().minusSeconds(300)
                val recentNotifications = database.notificationDao().getSince(fiveMinutesAgo)
                val isDuplicate = recentNotifications.any {
                    // Match by title and text (description) - don't include packageName for content-level deduplication
                    it.title.equals(parsed.title, ignoreCase = true) && 
                    it.text.equals(parsed.text, ignoreCase = true)
                }
                
                if (isDuplicate) {
                    Log.d(TAG, "Duplicate notification filtered: ${parsed.appName} - ${parsed.title}")
                    return@launch
                }
                
                // Store to database (independent of server - always store)
                val id = database.notificationDao().insert(parsed)
                val parsedWithId = parsed.copy(id = id)
                
                // Notification is stored - proactive system will process it hourly
                // No need to trigger immediately - keeps system independent
                
                // Only log high-priority notifications or once per hour
                val currentTime = System.currentTimeMillis()
                val shouldLog = parsed.priority >= 4 || (currentTime - lastLogTime) >= 3600_000 // 1 hour
                
                if (shouldLog) {
                    if (parsed.priority >= 4) {
                        // Log high-priority notifications immediately
                        Log.d(TAG, "Captured high-priority notification from ${parsed.appName}: ${parsed.title}")
                        ConfidantApplication.instance.serverManager.addLog(
                            "📥 Captured: ${parsed.appName} - ${parsed.title}",
                            LogLevel.DEBUG,
                            "Notification"
                        )
                    } else {
                        // Log summary once per hour
                        notificationCount++
                        Log.i(TAG, "Notification capture summary: $notificationCount notifications in past hour")
                        ConfidantApplication.instance.serverManager.addLog(
                            "📊 Captured $notificationCount notifications in past hour",
                            LogLevel.INFO,
                            "Notification"
                        )
                        lastLogTime = currentTime
                        notificationCount = 0
                    }
                } else {
                    // Just count silently
                    notificationCount++
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
                // Only log errors, not every notification
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Track which notifications user dismisses (silently, no logging)
        serviceScope.launch {
            try {
                val notification = extractNotificationData(sbn)
                
                // Update existing record if found
                val recent = database.notificationDao().getRecentNotifications(10)
                val match = recent.find { n ->
                    n.packageName == notification.packageName && 
                    n.title == notification.title &&
                    n.text == notification.text
                }
                
                match?.let {
                    database.notificationDao().update(it.copy(isUserDismissed = true))
                }
                
            } catch (e: Exception) {
                // Silent failure - don't spam logs
            }
        }
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
        
        // Request reconnection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, NotificationListenerService::class.java))
        }
    }
    
    private fun extractNotificationData(sbn: StatusBarNotification): RawNotification {
        val notification = sbn.notification
        val extras = notification.extras
        
        // CRITICAL: Use getCharSequence instead of getString to handle SpannableString
        return RawNotification(
            timestamp = Instant.ofEpochMilli(sbn.postTime),
            packageName = sbn.packageName,
            appName = getAppName(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
        )
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    private fun scheduleHeartbeat() {
        val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        ).build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME_HEARTBEAT,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    companion object {
        private const val TAG = "NotificationCaptureService"
        private const val WORK_NAME_HEARTBEAT = "notification_heartbeat"
        
        fun isEnabled(context: Context): Boolean {
            // Check if notification listener is enabled
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return enabledListeners?.contains(context.packageName) == true
        }
    }
}

/**
 * Raw notification data before parsing
 */
data class RawNotification(
    val timestamp: Instant,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String? = null,
    val subText: String? = null,
    val infoText: String? = null
)

/**
 * NotificationParser - Rule-based parsing (NO LLM)
 */
class NotificationParser {
    
    fun parse(raw: RawNotification): NotificationEntity {
        val category = inferCategory(raw)
        val entities = extractEntities(raw, category)
        val priority = inferPriority(raw, category)
        val sentiment = inferSentiment(raw)
        
        return NotificationEntity(
            timestamp = raw.timestamp,
            packageName = raw.packageName,
            appName = raw.appName,
            title = raw.title,
            text = raw.text,
            bigText = raw.bigText,
            subText = raw.subText,
            category = category,
            priority = priority,
            sentiment = sentiment,
            entities = entities
        )
    }
    
    private fun inferCategory(data: RawNotification): String {
        val text = "${data.title} ${data.text}".lowercase()
        
        return when {
            // Social media
            data.packageName in SOCIAL_APPS -> "social"
            
            // Financial apps
            data.packageName in FINANCIAL_APPS ||
            text.containsAny("payment", "transaction", "credited", "debited", "₹", "upi", "bank") -> "finance"
            
            // Health & fitness
            data.packageName in HEALTH_APPS ||
            text.containsAny("steps", "workout", "calories", "sleep", "heart rate", "exercise") -> "health"
            
            // Work (email, calendar, productivity)
            data.packageName in WORK_APPS ||
            text.containsAny("meeting", "deadline", "calendar", "email", "zoom", "teams") -> "work"
            
            // Shopping
            text.containsAny("order", "delivery", "shipped", "amazon", "flipkart") -> "shopping"
            
            // Travel
            text.containsAny("flight", "booking", "cab", "uber", "ola") -> "travel"
            
            else -> "general"
        }
    }
    
    private fun extractEntities(data: RawNotification, category: String): List<String> {
        val entities = mutableListOf<String>()
        
        // Extract person names (WhatsApp format: "Name: Message")
        val namePattern = """^([A-Z][a-z]+(?:\s[A-Z][a-z]+)?):""".toRegex()
        namePattern.find(data.text)?.let {
            entities.add("person:${it.groupValues[1]}")
        }
        
        // Financial entities
        if (category == "finance") {
            // Stock symbols (2-5 uppercase letters)
            val stockPattern = """\b([A-Z]{2,5})\b""".toRegex()
            stockPattern.findAll(data.text).forEach {
                entities.add("stock:${it.value}")
            }
            
            // Amounts (₹xxx or Rs. xxx)
            val amountPattern = """[₹Rs\.\s]+(\d+(?:,\d+)*(?:\.\d{2})?)""".toRegex()
            amountPattern.findAll(data.text).forEach {
                entities.add("amount:${it.groupValues[1]}")
            }
            
            // UPI IDs
            val upiPattern = """[a-zA-Z0-9._-]+@[a-zA-Z]+""".toRegex()
            upiPattern.findAll(data.text).forEach {
                entities.add("upi:${it.value}")
            }
        }
        
        // Phone numbers
        val phonePattern = """\+?\d{10,12}""".toRegex()
        phonePattern.findAll(data.text).forEach {
            entities.add("phone:${it.value}")
        }
        
        // OTP codes
        val otpPattern = """\b\d{4,6}\b""".toRegex()
        otpPattern.findAll(data.text).forEach {
            if (data.text.contains("otp", ignoreCase = true) ||
                data.text.contains("code", ignoreCase = true)) {
                entities.add("otp:${it.value}")
            }
        }
        
        return entities
    }
    
    private fun inferPriority(data: RawNotification, category: String): Int {
        val text = "${data.title} ${data.text}".lowercase()
        
        return when {
            // Critical notifications
            text.containsAny("urgent", "critical", "emergency", "alert") -> 5
            
            // High priority
            category == "health" -> 4
            category == "finance" && text.containsAny("debit", "withdrawal", "payment failed") -> 4
            text.containsAny("otp", "verification") -> 4
            
            // Medium priority
            category == "finance" -> 3
            category == "work" -> 3
            text.containsAny("delivery", "shipped") -> 3
            
            // Low priority
            category == "social" -> 2
            
            // Background
            else -> 1
        }
    }
    
    private fun inferSentiment(data: RawNotification): String {
        val text = "${data.title} ${data.text}".lowercase()
        
        return when {
            text.containsAny("congratulations", "success", "completed", "approved", "credited", "received") -> "positive"
            text.containsAny("failed", "error", "declined", "rejected", "debit", "deducted") -> "negative"
            text.containsAny("urgent", "critical", "alert", "warning") -> "urgent"
            else -> "neutral"
        }
    }
    
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
    
    companion object {
        val SOCIAL_APPS = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.facebook.katana",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.instagram.android",
            "com.twitter.android",
            "com.x.android",
            "com.snapchat.android",
            "com.linkedin.android"
        )
        
        val FINANCIAL_APPS = setOf(
            "net.one97.paytm",
            "com.phonepe.app",
            "com.google.android.apps.nbu.paisa.user",
            "com.sbi.SBIFreedomPlus",
            "com.axis.mobile",
            "com.icicibank.pockets",
            "com.hdfcbank.payzapp"
        )
        
        val HEALTH_APPS = setOf(
            "com.google.android.apps.fitness",
            "com.samsung.android.app.health",
            "com.fitbit.FitbitMobile",
            "com.mi.health",
            "com.huawei.health"
        )
        
        val WORK_APPS = setOf(
            "com.google.android.gm",
            "com.google.android.calendar",
            "com.microsoft.office.outlook",
            "com.microsoft.teams",
            "com.slack",
            "com.zoom.us",
            "us.zoom.videomeetings",
            "com.google.android.apps.docs",
            "com.microsoft.office.word"
        )
    }
}

/**
 * HeartbeatWorker - Checks if notification service is working
 */
class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val database = ConfidantApplication.instance.database
    
    override suspend fun doWork(): Result {
        return try {
            // Check if notifications are being captured
            val oneHourAgo = Instant.now().minusSeconds(3600)
            val recentCount = database.notificationDao().countSince(oneHourAgo)
            
            if (recentCount == 0) {
                Log.w(TAG, "No notifications captured in past hour - service may not be working")
                // Could send notification to user here
            } else {
                Log.d(TAG, "Heartbeat check passed - $recentCount notifications in past hour")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat worker failed", e)
            Result.failure()
        }
    }
    
    companion object {
        private const val TAG = "HeartbeatWorker"
    }
}