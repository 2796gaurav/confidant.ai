package com.confidant.ai.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.system.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * ForegroundServiceTimeoutManager - Handles Android 15+ foreground service timeout limits
 * 
 * Android 15 Restrictions:
 * - dataSync type: 6-hour limit per 24-hour period
 * - After timeout: Service stopped by system, must wait for next window
 * 
 * Strategy:
 * - Track runtime budget
 * - Schedule graceful shutdown before timeout
 * - Auto-restart after timeout window resets
 */
class ForegroundServiceTimeoutManager(private val context: Context) {
    
    private var serviceStartTime = 0L
    private var totalRuntime = 0L
    private var lastResetTime = System.currentTimeMillis()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    companion object {
        private const val TAG = "FGSTimeoutManager"
        
        // Android 15: 6-hour limit for dataSync type per 24 hours
        private const val TIMEOUT_LIMIT_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val WARNING_THRESHOLD_MS = 5 * 60 * 60 * 1000L // 5 hours
        private const val RESET_PERIOD_MS = 24 * 60 * 60 * 1000L // 24 hours
        
        // Stop service 30 minutes before timeout to avoid abrupt kill
        private const val SAFETY_MARGIN_MS = 30 * 60 * 1000L // 30 minutes
    }
    
    /**
     * Called when service starts
     */
    fun onServiceStart() {
        serviceStartTime = System.currentTimeMillis()
        
        // Check if we need to reset daily counter
        if (System.currentTimeMillis() - lastResetTime >= RESET_PERIOD_MS) {
            resetDailyCounter()
        }
        
        // Schedule warning before timeout
        scheduleTimeoutWarning()
        
        val remainingMinutes = getRemainingBudget() / 1000 / 60
        Log.d(TAG, "Service started - runtime budget: $remainingMinutes minutes")
        
        ConfidantApplication.instance.serverManager.addLog(
            "🚀 Service started (budget: ${remainingMinutes}m)",
            LogLevel.INFO,
            "Service"
        )
    }
    
    /**
     * Called when service stops
     */
    fun onServiceStop() {
        if (serviceStartTime > 0) {
            // Calculate actual usage
            val sessionDuration = System.currentTimeMillis() - serviceStartTime
            totalRuntime += sessionDuration
            serviceStartTime = 0
            
            val sessionMinutes = sessionDuration / 1000 / 60
            val totalMinutes = totalRuntime / 1000 / 60
            
            Log.d(TAG, "Service stopped - session: ${sessionMinutes}m, total: ${totalMinutes}m")
            
            ConfidantApplication.instance.serverManager.addLog(
                "⏸️ Service stopped (session: ${sessionMinutes}m, total: ${totalMinutes}m)",
                LogLevel.INFO,
                "Service"
            )
        }
    }
    
    /**
     * Get remaining runtime budget
     */
    fun getRemainingBudget(): Long {
        val currentSession = if (serviceStartTime > 0) {
            System.currentTimeMillis() - serviceStartTime
        } else 0
        
        return max(0, TIMEOUT_LIMIT_MS - totalRuntime - currentSession)
    }
    
    /**
     * Check if service should stop to avoid timeout
     */
    fun shouldStopService(): Boolean {
        return getRemainingBudget() < SAFETY_MARGIN_MS
    }
    
    /**
     * Get usage percentage
     */
    fun getUsagePercentage(): Int {
        val currentSession = if (serviceStartTime > 0) {
            System.currentTimeMillis() - serviceStartTime
        } else 0
        
        val totalUsage = totalRuntime + currentSession
        return ((totalUsage.toFloat() / TIMEOUT_LIMIT_MS) * 100).toInt()
    }
    
    /**
     * Schedule graceful shutdown before Android kills service
     */
    private fun scheduleTimeoutWarning() {
        val remainingBudget = getRemainingBudget()
        
        if (remainingBudget < WARNING_THRESHOLD_MS) {
            // Schedule warning 30 minutes before timeout
            val warningTime = System.currentTimeMillis() + remainingBudget - SAFETY_MARGIN_MS
            
            if (warningTime > System.currentTimeMillis()) {
                val intent = Intent(context, ForegroundServiceTimeoutReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1003,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            warningTime,
                            pendingIntent
                        )
                        
                        val warningMinutes = (warningTime - System.currentTimeMillis()) / 1000 / 60
                        Log.d(TAG, "Scheduled timeout warning in $warningMinutes minutes")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        warningTime,
                        pendingIntent
                    )
                }
            }
        }
    }
    
    /**
     * Reset daily counter (called at 24-hour mark)
     */
    fun resetDailyCounter() {
        totalRuntime = 0L
        lastResetTime = System.currentTimeMillis()
        Log.d(TAG, "📅 Daily runtime counter reset")
        
        ConfidantApplication.instance.serverManager.addLog(
            "📅 Service runtime budget reset (6 hours available)",
            LogLevel.INFO,
            "Service"
        )
    }
}

/**
 * Receiver for foreground service timeout warnings
 */
