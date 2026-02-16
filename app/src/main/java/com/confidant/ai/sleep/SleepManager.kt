package com.confidant.ai.sleep

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.database.entity.SleepSessionEntity
import com.confidant.ai.memory.SimplifiedMemorySystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * SleepManager - Detects user sleep patterns and manages sleep-aware processing
 * 
 * Features:
 * - Passive sleep detection from notification patterns
 * - Sleep quality tracking
 * - Sleep consolidation window scheduling
 * - Adaptive sleep/wake time learning
 */
class SleepManager(
    private val context: Context,
    private val database: AppDatabase,
    private val memorySystem: SimplifiedMemorySystem
) {
    
    private val _sleepState = MutableStateFlow(SleepState.AWAKE)
    val sleepState: StateFlow<SleepState> = _sleepState.asStateFlow()
    
    private val _predictedSleepTime = MutableStateFlow<LocalTime?>(null)
    val predictedSleepTime: StateFlow<LocalTime?> = _predictedSleepTime.asStateFlow()
    
    private val _predictedWakeTime = MutableStateFlow<LocalTime?>(null)
    val predictedWakeTime: StateFlow<LocalTime?> = _predictedWakeTime.asStateFlow()
    
    private var currentSleepSession: SleepSession? = null
    
    enum class SleepState {
        AWAKE,           // User is active
        DROWSY,          // Reduced activity, likely preparing for sleep
        SLEEPING,        // No activity for extended period
        WAKING           // Activity resuming after sleep
    }
    
    data class SleepSession(
        val sleepStart: Instant,
        var sleepEnd: Instant? = null,
        var quality: SleepQuality = SleepQuality.UNKNOWN,
        var interruptions: Int = 0
    )
    
    enum class SleepQuality {
        UNKNOWN,
        POOR,      // < 5 hours or many interruptions
        FAIR,      // 5-6 hours or some interruptions
        GOOD,      // 6-8 hours, few interruptions
        EXCELLENT  // 8+ hours, no interruptions
    }
    
    /**
     * Initialize sleep manager and load historical patterns
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val prefsManager = (context.applicationContext as com.confidant.ai.ConfidantApplication).preferencesManager
            
            // Check if sleep mode is enabled
            val sleepModeEnabled = prefsManager.isSleepModeEnabled()
            val autoDetect = prefsManager.isSleepAutoDetect()
            
            if (sleepModeEnabled && !autoDetect) {
                // Use configured times
                val startHour = prefsManager.getSleepStartHour()
                val startMinute = prefsManager.getSleepStartMinute()
                val endHour = prefsManager.getSleepEndHour()
                val endMinute = prefsManager.getSleepEndMinute()
                
                _predictedSleepTime.value = LocalTime.of(startHour, startMinute)
                _predictedWakeTime.value = LocalTime.of(endHour, endMinute)
                
                Log.i(TAG, "Using configured sleep schedule: Sleep ${startHour}:${startMinute}, Wake ${endHour}:${endMinute}")
            } else if (sleepModeEnabled && autoDetect) {
                // Auto-detect disabled - sleep mode now uses core_memory settings
                // Sleep schedule is stored in core_memory (sleep_mode.start_hour, sleep_mode.end_hour)
                // This is managed by SleepModeHandler
                Log.d(TAG, "Auto-detect sleep mode - using core_memory settings")
                // Default to common sleep pattern
                _predictedSleepTime.value = LocalTime.of(23, 0)  // 11 PM
                _predictedWakeTime.value = LocalTime.of(7, 0)    // 7 AM
                Log.i(TAG, "Using default sleep pattern (managed by SleepModeHandler)")
            } else {
                // Sleep mode disabled - use default times but don't enforce
                _predictedSleepTime.value = LocalTime.of(23, 0)
                _predictedWakeTime.value = LocalTime.of(7, 0)
                Log.i(TAG, "Sleep mode disabled")
            }
            
            // Check current state
            detectCurrentSleepState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sleep manager", e)
        }
    }
    
    /**
     * Detect current sleep state from recent activity
     * Called periodically (every 30 minutes)
     */
    suspend fun detectCurrentSleepState() = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val oneHourAgo = now.minus(1, ChronoUnit.HOURS)
        val twoHoursAgo = now.minus(2, ChronoUnit.HOURS)
        
        // Check notification activity
        val recentNotifications = database.notificationDao().getNotificationsSince(oneHourAgo.toEpochMilli())
        val olderNotifications = database.notificationDao().getNotificationsBetween(
            twoHoursAgo.toEpochMilli(),
            oneHourAgo.toEpochMilli()
        )
        
        // Check user message activity
        val recentMessages = database.conversationDao().getMessagesSince(oneHourAgo.toEpochMilli())
        
        val currentHour = LocalTime.now().hour
        val predictedSleep = _predictedSleepTime.value?.hour ?: 23
        val predictedWake = _predictedWakeTime.value?.hour ?: 7
        
        val newState = when {
            // Active: Recent messages or many notifications
            recentMessages.isNotEmpty() || recentNotifications.size > 5 -> {
                if (_sleepState.value == SleepState.SLEEPING) {
                    // User waking up
                    endSleepSession(now)
                    SleepState.WAKING
                } else {
                    SleepState.AWAKE
                }
            }
            
            // Drowsy: Reduced activity near predicted sleep time
            recentNotifications.size in 1..5 && 
            abs(currentHour - predictedSleep) <= 2 -> {
                SleepState.DROWSY
            }
            
            // Sleeping: No activity for 1+ hours during sleep window
            recentNotifications.isEmpty() && 
            (currentHour >= predictedSleep || currentHour < predictedWake) -> {
                if (_sleepState.value != SleepState.SLEEPING) {
                    // User just fell asleep
                    startSleepSession(now)
                }
                SleepState.SLEEPING
            }
            
            // Default to awake
            else -> SleepState.AWAKE
        }
        
        if (newState != _sleepState.value) {
            Log.i(TAG, "Sleep state changed: ${_sleepState.value} → $newState")
            _sleepState.value = newState
        }
    }
    
    /**
     * Start a new sleep session
     */
    private suspend fun startSleepSession(startTime: Instant) {
        currentSleepSession = SleepSession(sleepStart = startTime)
        Log.i(TAG, "😴 Sleep session started at ${LocalDateTime.ofInstant(startTime, ZoneId.systemDefault())}")
        
        // Send "going to sleep" message
        val app = context.applicationContext as com.confidant.ai.ConfidantApplication
        val wakeTime = _predictedWakeTime.value?.toString() ?: "morning"
        
        app.telegramBotManager.sendSystemMessage(
            com.confidant.ai.telegram.StandardizedMessages.getGoingToSleepMessage(wakeTime)
        )
    }
    
    /**
     * End current sleep session and calculate quality
     */
    private suspend fun endSleepSession(endTime: Instant) {
        val session = currentSleepSession ?: return
        
        session.sleepEnd = endTime
        val durationHours = Duration.between(session.sleepStart, endTime).toHours()
        
        // Calculate sleep quality
        session.quality = when {
            durationHours < 5 || session.interruptions > 3 -> SleepQuality.POOR
            durationHours < 6 || session.interruptions > 1 -> SleepQuality.FAIR
            durationHours < 8 && session.interruptions <= 1 -> SleepQuality.GOOD
            durationHours >= 8 && session.interruptions == 0 -> SleepQuality.EXCELLENT
            else -> SleepQuality.FAIR
        }
        
        // Sleep sessions removed - sleep mode now managed via core_memory
        // Persist sleep mode settings to core_memory instead
        database.coreMemoryDao().insertOrUpdate(
            com.confidant.ai.database.entity.CoreMemoryEntity(
                key = "sleep_mode.last_session",
                value = "${session.sleepStart.toEpochMilli()}|${endTime.toEpochMilli()}|${durationHours}|${session.quality.name}",
                category = "sleep_mode"
            )
        )
        
        Log.i(TAG, "😊 Sleep session ended: ${durationHours}h, quality: ${session.quality}")
        
        // Send "waking up" message
        val app = context.applicationContext as com.confidant.ai.ConfidantApplication
        
        app.telegramBotManager.sendSystemMessage(
            com.confidant.ai.telegram.StandardizedMessages.getWakingUpMessage()
        )
        
        // Update core memory with sleep pattern
        updateSleepPatternInMemory(session)
        
        currentSleepSession = null
    }
    
    /**
     * Update core memory with learned sleep pattern
     */
    private suspend fun updateSleepPatternInMemory(session: SleepSession) {
        val sleepTime = LocalDateTime.ofInstant(session.sleepStart, ZoneId.systemDefault()).toLocalTime()
        val wakeTime = session.sleepEnd?.let { 
            LocalDateTime.ofInstant(it, ZoneId.systemDefault()).toLocalTime() 
        }
        
        // Update core memory with sleep routine
        memorySystem.updateRoutine(
            name = "sleep",
            updates = mapOf(
                "typical_sleep" to sleepTime.toString(),
                "typical_wake" to (wakeTime?.toString() ?: "unknown"),
                "quality" to session.quality.name.lowercase(),
                "last_updated" to Instant.now().toString()
            )
        )
    }
    
    /**
     * Check if it's appropriate to run processing window
     */
    fun canRunProcessingWindow(priority: String): Boolean {
        val prefsManager = (context.applicationContext as com.confidant.ai.ConfidantApplication).preferencesManager
        val sleepModeEnabled = kotlinx.coroutines.runBlocking { prefsManager.isSleepModeEnabled() }
        
        if (!sleepModeEnabled) {
            return true // Sleep mode disabled, allow all processing
        }
        
        return when (_sleepState.value) {
            SleepState.SLEEPING -> {
                // Only allow CRITICAL priority during sleep (consolidation)
                priority == "CRITICAL"
            }
            SleepState.DROWSY -> {
                // Avoid HIGH priority when user is drowsy
                priority != "HIGH"
            }
            SleepState.WAKING -> {
                // Allow all except CRITICAL when waking
                priority != "CRITICAL"
            }
            SleepState.AWAKE -> {
                // Allow all priorities when awake
                true
            }
        }
    }
    
    /**
     * Get optimal time for sleep consolidation
     * Returns middle of predicted sleep period
     */
    fun getOptimalConsolidationTime(): LocalTime {
        val sleepHour = _predictedSleepTime.value?.hour ?: 23
        val wakeHour = _predictedWakeTime.value?.hour ?: 7
        
        // Calculate middle of sleep period
        val midSleepHour = if (wakeHour < sleepHour) {
            // Sleep crosses midnight
            ((sleepHour + 24 + wakeHour) / 2) % 24
        } else {
            (sleepHour + wakeHour) / 2
        }
        
        return LocalTime.of(midSleepHour, 0)
    }
    
    /**
     * Run sleep consolidation - memory replay and pattern strengthening
     */
    suspend fun runSleepConsolidation() = withContext(Dispatchers.IO) {
        if (_sleepState.value != SleepState.SLEEPING) {
            Log.w(TAG, "Skipping sleep consolidation - user not sleeping")
            return@withContext
        }
        
        Log.i(TAG, "🌙 Starting sleep consolidation...")
        
        try {
            // 1. Memory consolidation (strategic forgetting)
            memorySystem.consolidate()
            
            Log.i(TAG, "✅ Sleep consolidation complete")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sleep consolidation failed", e)
        }
    }
    
    companion object {
        private const val TAG = "SleepManager"
    }
}
