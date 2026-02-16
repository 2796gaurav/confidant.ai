package com.confidant.ai.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.confidant.ai.ConfidantApplication

import com.confidant.ai.thermal.ThermalManager
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * IntelligenceScheduler - Manages periodic processing windows
 * Uses AlarmManager for reliable scheduling (survives Doze mode)
 * 
 * Processing Windows:
 * - 8:00 AM (HIGH): Pattern detection + inner thoughts
 * - 1:00 PM (MEDIUM): Inner thoughts only
 * - 6:00 PM (HIGH): Pattern detection + inner thoughts
 * - 10:00 PM (CRITICAL): Memory consolidation + inner thoughts
 */
class IntelligenceScheduler(
    private val context: Context,
    private val llmEngine: com.confidant.ai.engine.LLMEngine,
    private val memorySystem: com.confidant.ai.memory.SimplifiedMemorySystem,
    private val telegramBotManager: com.confidant.ai.telegram.TelegramBotManager,
    private val database: com.confidant.ai.database.AppDatabase
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val thermalManager = ConfidantApplication.instance.thermalManager
    
    // Lazy initialization of sleep manager
    private val sleepManager by lazy {
        com.confidant.ai.sleep.SleepManager(context, database, memorySystem)
    }
    
    // Use SimplifiedProactiveSystem for proactive messaging
    private val simplifiedProactive by lazy {
        ConfidantApplication.instance.simplifiedProactiveSystem
    }
    
    /**
     * Start the scheduler
     */
    suspend fun start() {
        // Initialize sleep manager
        sleepManager.initialize()
        
        // Schedule adaptive windows based on learned sleep patterns
        scheduleAdaptiveWindows()
        
        // Start periodic sleep state detection (every 30 minutes)
        startSleepStateMonitoring()
    }
    
    /**
     * Stop the scheduler
     */
    fun stop() {
        cancelAllWindows()
    }
    
    // Processing windows (24-hour format)
    private val processingWindows = listOf(
        ProcessingWindow(8, 0, 15, WindowPriority.HIGH),     // 8:00 AM - Morning check
        ProcessingWindow(13, 0, 10, WindowPriority.MEDIUM),  // 1:00 PM - Midday check
        ProcessingWindow(18, 0, 15, WindowPriority.HIGH),    // 6:00 PM - Evening check
        ProcessingWindow(22, 0, 20, WindowPriority.CRITICAL) // 10:00 PM - Nightly consolidation
    )
    
    /**
     * Schedule all processing windows
     */
    fun scheduleAllWindows() {
        processingWindows.forEach { window ->
            scheduleWindow(window)
        }
        Log.i(TAG, "Scheduled ${processingWindows.size} processing windows")
    }
    
    /**
     * Schedule adaptive windows based on learned sleep patterns
     */
    private suspend fun scheduleAdaptiveWindows() {
        // Get optimal consolidation time from sleep manager
        val consolidationTime = sleepManager.getOptimalConsolidationTime()
        
        // Create adaptive windows
        val adaptiveWindows = listOf(
            ProcessingWindow(8, 0, 15, WindowPriority.HIGH),     // Morning (fixed)
            ProcessingWindow(13, 0, 10, WindowPriority.MEDIUM),  // Midday (fixed)
            ProcessingWindow(18, 0, 15, WindowPriority.HIGH),    // Evening (fixed)
            ProcessingWindow(consolidationTime.hour, 0, 20, WindowPriority.CRITICAL) // Adaptive sleep consolidation
        )
        
        adaptiveWindows.forEach { window ->
            scheduleWindow(window)
        }
        
        Log.i(TAG, "Scheduled ${adaptiveWindows.size} adaptive windows (consolidation at ${consolidationTime})")
    }
    
    /**
     * Start periodic sleep state monitoring
     */
    private fun startSleepStateMonitoring() {
        val intent = Intent(context, SleepStateMonitorReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SLEEP_MONITOR_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Schedule every 30 minutes
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 30 * 60 * 1000,
            30 * 60 * 1000,
            pendingIntent
        )
        
        Log.i(TAG, "Started sleep state monitoring (every 30 minutes)")
    }
    
    /**
     * Cancel all scheduled windows
     */
    fun cancelAllWindows() {
        processingWindows.forEach { window ->
            val intent = Intent(context, ProcessingWindowReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                window.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent)
        }
        Log.i(TAG, "Cancelled all processing windows")
    }
    
    private fun scheduleWindow(window: ProcessingWindow) {
        val intent = Intent(context, ProcessingWindowReceiver::class.java).apply {
            putExtra("hour", window.hour)
            putExtra("minute", window.minute)
            putExtra("duration", window.durationMinutes)
            putExtra("priority", window.priority.name)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            window.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Schedule for next occurrence
        val triggerTime = getNextWindowTime(window.hour, window.minute)
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
        
        val nextTime = Instant.ofEpochMilli(triggerTime)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
        
        Log.i(TAG, "Scheduled window at $nextTime (${window.priority})")
    }
    
    private fun getNextWindowTime(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val windowTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // If window time has passed today, schedule for tomorrow
        if (windowTime.before(now)) {
            windowTime.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return windowTime.timeInMillis
    }
    
    /**
     * Execute a processing window
     */
    suspend fun executeProcessingWindow(window: ProcessingWindow) = withContext(Dispatchers.IO) {
        val startTime = Instant.now()
        
        Log.i(TAG, "🔄 Starting processing window: ${window.hour}:${String.format("%02d", window.minute)} (${window.priority})")
        
        try {
            // Check sleep state before starting
            sleepManager.detectCurrentSleepState()
            if (!sleepManager.canRunProcessingWindow(window.priority.name)) {
                Log.w(TAG, "⏸️ Skipping window - user sleep state: ${sleepManager.sleepState.value}")
                scheduleWindow(window)
                return@withContext
            }
            
            // Check thermal state before starting
            if (!thermalManager.canStartInference() && window.priority != WindowPriority.CRITICAL) {
                Log.w(TAG, "⚠️ Skipping window - thermal limit (${thermalManager.getThermalStatus()})")
                scheduleWindow(window)
                return@withContext
            }
            
            // Execute tasks based on priority
            when (window.priority) {
                WindowPriority.CRITICAL -> {
                    // Sleep consolidation or nightly consolidation
                    if (sleepManager.sleepState.value == com.confidant.ai.sleep.SleepManager.SleepState.SLEEPING) {
                        Log.i(TAG, "🌙 CRITICAL window: Sleep consolidation")
                        sleepManager.runSleepConsolidation()
                    } else {
                        Log.i(TAG, "🌙 CRITICAL window: Memory consolidation + proactive processing + interest optimization")
                        memorySystem.consolidate()
                        
                        // Run daily interest optimization
                        Log.i(TAG, "🎯 Running daily interest optimization...")
                        val interestOptimizer = ConfidantApplication.instance.interestOptimizer
                        interestOptimizer.runDailyOptimization()
                        
                        // Process notifications with simplified proactive messaging
                        if (thermalManager.canStartInference()) {
                            Log.i(TAG, "💭 Processing notifications with simplified proactive messaging...")
                            simplifiedProactive.processHourlyProactive()
                        }
                    }
                }
                
                WindowPriority.HIGH -> {
                    // 8 AM, 6 PM: Process notifications with simplified proactive messaging
                    Log.i(TAG, "⚡ HIGH window: Proactive notification processing")
                    
                    if (thermalManager.canStartInference()) {
                        simplifiedProactive.processHourlyProactive()
                    }
                }
                
                WindowPriority.MEDIUM -> {
                    // 1 PM: Light proactive processing
                    Log.i(TAG, "💭 MEDIUM window: Light proactive processing")
                    
                    if (thermalManager.canStartInference()) {
                        simplifiedProactive.processHourlyProactive()
                    }
                }
                
                WindowPriority.LOW -> {
                    // Reserved for future use
                    Log.i(TAG, "🔹 LOW window: Minimal processing")
                }
            }
            
            val elapsed = Duration.between(startTime, Instant.now()).seconds
            Log.i(TAG, "✅ Processing window complete (${elapsed}s)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in processing window", e)
        } finally {
            // Always reschedule for next day
            scheduleWindow(window)
        }
    }
    

    
    companion object {
        private const val TAG = "IntelligenceScheduler"
        private const val SLEEP_MONITOR_REQUEST_CODE = 9999
    }
}

/**
 * BroadcastReceiver for sleep state monitoring
 */
class SleepStateMonitorReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SleepStateMonitor", "Running periodic sleep state check")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sleepManager = com.confidant.ai.sleep.SleepManager(
                    context,
                    ConfidantApplication.instance.database,
                    ConfidantApplication.instance.memorySystem
                )
                sleepManager.detectCurrentSleepState()
            } catch (e: Exception) {
                Log.e("SleepStateMonitor", "Failed to detect sleep state", e)
            }
        }
    }
}

/**
 * Processing window configuration
 */
data class ProcessingWindow(
    val hour: Int,
    val minute: Int,
    val durationMinutes: Int,
    val priority: WindowPriority
)

enum class WindowPriority {
    LOW,      // Minimal processing
    MEDIUM,   // Inner thoughts only
    HIGH,     // Pattern detection + inner thoughts
    CRITICAL  // Memory consolidation + inner thoughts
}

/**
 * BroadcastReceiver for processing window alarms
 */
class ProcessingWindowReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("hour", 8)
        val minute = intent.getIntExtra("minute", 0)
        val duration = intent.getIntExtra("duration", 15)
        val priorityName = intent.getStringExtra("priority") ?: "MEDIUM"
        val priority = WindowPriority.valueOf(priorityName)
        
        val window = ProcessingWindow(hour, minute, duration, priority)
        
        Log.i(TAG, "Received alarm for processing window: ${window.hour}:${String.format("%02d", window.minute)}")
        
        // Execute in coroutine
        val app = ConfidantApplication.instance
        
        CoroutineScope(Dispatchers.IO).launch {
            app.intelligenceScheduler.executeProcessingWindow(window)
        }
    }
    
    companion object {
        private const val TAG = "ProcessingWindowReceiver"
    }
}