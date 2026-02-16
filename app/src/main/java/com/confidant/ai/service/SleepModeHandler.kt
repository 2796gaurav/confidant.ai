package com.confidant.ai.service

import android.content.Context
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.system.ServerManager
import com.confidant.ai.telegram.TelegramBotManager
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Sleep Mode Handler - Manages server shutdown during sleep hours
 * 
 * Features:
 * - Shuts down server during sleep hours
 * - Sends standardized "sleeping" message
 * - Ignores user messages during sleep
 * - Processes proactive messages after sleep ends
 */
class SleepModeHandler(
    private val context: Context,
    private val database: AppDatabase,
    private val serverManager: ServerManager,
    private val telegramBot: TelegramBotManager
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isSleepModeActive = false
    private var sleepCheckJob: Job? = null
    
    companion object {
        private const val TAG = "SleepModeHandler"
        private const val SLEEP_MESSAGE = "😴 I'm currently in sleep mode. I'll be back soon and will catch up on any notifications then!"
    }
    
    /**
     * Initialize sleep mode monitoring
     */
    fun initialize() {
        scope.launch {
            // Check sleep mode every minute
            sleepCheckJob = scope.launch {
                while (isActive) {
                    checkAndUpdateSleepMode()
                    delay(60 * 1000) // Check every minute
                }
            }
            
            Log.i(TAG, "✅ Sleep mode handler initialized")
        }
    }
    
    /**
     * Check if sleep mode should be active and update accordingly
     */
    private suspend fun checkAndUpdateSleepMode() {
        try {
            val sleepStartHour = database.coreMemoryDao().getByKey("sleep_mode.start_hour")?.value?.toIntOrNull()
            val sleepEndHour = database.coreMemoryDao().getByKey("sleep_mode.end_hour")?.value?.toIntOrNull()
            
            if (sleepStartHour == null || sleepEndHour == null) {
                // Sleep mode not configured
                if (isSleepModeActive) {
                    // Exit sleep mode
                    exitSleepMode()
                }
                return
            }
            
            val currentHour = LocalDateTime.now().hour
            val shouldBeSleeping = isInSleepWindow(currentHour, sleepStartHour, sleepEndHour)
            
            if (shouldBeSleeping && !isSleepModeActive) {
                // Enter sleep mode
                enterSleepMode()
            } else if (!shouldBeSleeping && isSleepModeActive) {
                // Exit sleep mode
                exitSleepMode()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check sleep mode", e)
        }
    }
    
    /**
     * Check if current hour is in sleep window
     */
    private fun isInSleepWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        return if (startHour < endHour) {
            // Normal case: e.g., 22:00 to 8:00
            currentHour >= startHour && currentHour < endHour
        } else {
            // Wraps around midnight: e.g., 22:00 to 8:00
            currentHour >= startHour || currentHour < endHour
        }
    }
    
    /**
     * Enter sleep mode
     */
    private suspend fun enterSleepMode() {
        if (isSleepModeActive) return
        
        Log.i(TAG, "🌙 Entering sleep mode")
        isSleepModeActive = true
        
        // Shutdown server
        val stopResult = serverManager.stopServer()
        if (stopResult.isSuccess) {
            Log.i(TAG, "✅ Server stopped for sleep mode")
        } else {
            Log.e(TAG, "Failed to stop server for sleep mode")
        }
        
        // Send sleep message to user
        telegramBot.sendSystemMessage(SLEEP_MESSAGE)
    }
    
    /**
     * Exit sleep mode
     */
    private suspend fun exitSleepMode() {
        if (!isSleepModeActive) return
        
        Log.i(TAG, "☀️ Exiting sleep mode")
        isSleepModeActive = false
        
        // Server will be started by user or auto-start if configured
        // Proactive system will process notifications after sleep ends
        
        // Trigger proactive processing after sleep ends
        scope.launch {
            delay(5000) // Wait 5 seconds after sleep ends
            try {
                // Process proactive notifications that accumulated during sleep
                ConfidantApplication.instance.simplifiedProactiveSystem.processHourlyProactive()
                Log.i(TAG, "✅ Proactive processing triggered after sleep mode")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger proactive processing after sleep", e)
            }
        }
    }
    
    /**
     * Check if sleep mode is currently active
     */
    fun isSleepModeActive(): Boolean = isSleepModeActive
    
    /**
     * Handle user message during sleep mode
     */
    suspend fun handleSleepModeMessage(): Boolean {
        if (!isSleepModeActive) return false
        
        // Send standardized sleep message
        telegramBot.sendSystemMessage(SLEEP_MESSAGE)
        return true
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        sleepCheckJob?.cancel()
        scope.cancel()
        Log.d(TAG, "Sleep mode handler cleaned up")
    }
}
