package com.confidant.ai.power

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.system.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * WakeLockManager - Intelligent wake lock management for Google Play 2026 compliance
 * 
 * Google Play Policy (March 1, 2026):
 * - Apps using PARTIAL_WAKE_LOCK >2 hours/day will be flagged
 * - Consequences: Reduced visibility, public warning label
 * 
 * Strategy:
 * - Track daily wake lock usage
 * - Adaptive timeout based on remaining budget
 * - Graceful degradation when approaching threshold
 */
class WakeLockManager(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Track wake lock usage for Google Play compliance
    private var wakeLockAcquireTime = 0L
    private var dailyWakeLockDuration = 0L
    private var lastResetDate = LocalDate.now()
    
    private val _usageStats = MutableStateFlow(WakeLockStats(0L, DAILY_THRESHOLD_MS, 0, false))
    val usageStats: StateFlow<WakeLockStats> = _usageStats.asStateFlow()
    
    companion object {
        private const val TAG = "WakeLockManager"
        private const val WAKELOCK_TAG = "ConfidantAI::ServiceWakeLock"
        
        // Google Play 2026 threshold: 2 hours/day
        private const val DAILY_THRESHOLD_MS = 2 * 60 * 60 * 1000L // 2 hours
        
        // Adaptive timeout based on usage
        private const val MIN_TIMEOUT = 5 * 60 * 1000L // 5 minutes
        private const val MAX_TIMEOUT = 15 * 60 * 1000L // 15 minutes
        
        // Warning threshold (80% of limit)
        private const val WARNING_THRESHOLD_MS = (DAILY_THRESHOLD_MS * 0.8).toLong()
    }
    
    /**
     * Acquire wake lock with intelligent timeout
     * Returns false if daily threshold exceeded
     */
    fun acquireWakeLock(): Boolean {
        // Reset daily counter if new day
        val today = LocalDate.now()
        if (today != lastResetDate) {
            dailyWakeLockDuration = 0L
            lastResetDate = today
            Log.d(TAG, "📅 New day - reset wake lock counter")
        }
        
        // Check if we're approaching daily threshold
        if (dailyWakeLockDuration >= DAILY_THRESHOLD_MS) {
            Log.w(TAG, "⚠️ Daily wake lock threshold exceeded (${dailyWakeLockDuration / 1000 / 60} minutes)")
            ConfidantApplication.instance.serverManager.addLog(
                "⚠️ Wake lock threshold exceeded - entering power-saving mode",
                LogLevel.WARN,
                "Power"
            )
            updateStats()
            return false
        }
        
        // Warn if approaching threshold
        if (dailyWakeLockDuration >= WARNING_THRESHOLD_MS) {
            Log.w(TAG, "⚠️ Approaching wake lock threshold (${dailyWakeLockDuration / 1000 / 60}/${DAILY_THRESHOLD_MS / 1000 / 60} minutes)")
        }
        
        try {
            // Calculate adaptive timeout
            val remainingBudget = DAILY_THRESHOLD_MS - dailyWakeLockDuration
            val timeout = min(MAX_TIMEOUT, max(MIN_TIMEOUT, remainingBudget / 10))
            
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                acquire(timeout)
                wakeLockAcquireTime = System.currentTimeMillis()
                Log.d(TAG, "✅ Wake lock acquired (timeout: ${timeout / 1000}s, daily usage: ${dailyWakeLockDuration / 1000 / 60}m)")
            }
            
            // Track in battery analytics
            ConfidantApplication.instance.batteryAnalyticsTracker.onWakeLockAcquired()
            
            updateStats()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to acquire wake lock", e)
            return false
        }
    }
    
    /**
     * Release wake lock and log usage
     */
    fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    // Calculate actual usage
                    val actualUsage = System.currentTimeMillis() - wakeLockAcquireTime
                    dailyWakeLockDuration += actualUsage
                    
                    it.release()
                    Log.d(TAG, "✅ Wake lock released (session: ${actualUsage / 1000}s, daily total: ${dailyWakeLockDuration / 1000 / 60}m)")
                    
                    // Track in battery analytics
                    ConfidantApplication.instance.batteryAnalyticsTracker.onWakeLockReleased()
                }
            }
            wakeLock = null
            wakeLockAcquireTime = 0L
            updateStats()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to release wake lock", e)
        }
    }
    
    /**
     * Renew wake lock before timeout
     */
    fun renewWakeLock(): Boolean {
        if (wakeLock?.isHeld == true) {
            releaseWakeLock()
        }
        return acquireWakeLock()
    }
    
    /**
     * Get daily wake lock usage statistics
     */
    fun getDailyUsageStats(): WakeLockStats {
        return _usageStats.value
    }
    
    /**
     * Check if wake lock is currently held
     */
    fun isHeld(): Boolean {
        return wakeLock?.isHeld == true
    }
    
    /**
     * Get remaining budget in milliseconds
     */
    fun getRemainingBudget(): Long {
        return max(0, DAILY_THRESHOLD_MS - dailyWakeLockDuration)
    }
    
    /**
     * Update usage statistics
     */
    private fun updateStats() {
        _usageStats.value = WakeLockStats(
            durationMs = dailyWakeLockDuration,
            thresholdMs = DAILY_THRESHOLD_MS,
            percentageUsed = (dailyWakeLockDuration.toFloat() / DAILY_THRESHOLD_MS * 100).toInt(),
            isNearThreshold = dailyWakeLockDuration >= WARNING_THRESHOLD_MS
        )
    }
    
    data class WakeLockStats(
        val durationMs: Long,
        val thresholdMs: Long,
        val percentageUsed: Int,
        val isNearThreshold: Boolean
    ) {
        fun getDurationHours(): Double = durationMs / 1000.0 / 3600.0
        fun getThresholdHours(): Double = thresholdMs / 1000.0 / 3600.0
        fun getRemainingHours(): Double = (thresholdMs - durationMs) / 1000.0 / 3600.0
    }
}
