package com.confidant.ai.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * HeartbeatReceiver - Periodic health check for background service
 * Detects if service is dead and restarts it
 */
class HeartbeatReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "💓 Heartbeat received")
        
        // Acquire wake lock for heartbeat processing
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ConfidantAI::HeartbeatWakeLock"
        )
        
        try {
            wakeLock.acquire(60 * 1000L) // 1 minute max
            
            // Check if service is running
            if (!isServiceRunning(context, ConfidantBackgroundService::class.java)) {
                Log.w(TAG, "⚠️ Service not running - restarting")
                restartService(context)
            } else {
                Log.d(TAG, "✅ Service is running")
            }
            
            // Reschedule next heartbeat
            ServiceHeartbeatManager(context).scheduleHeartbeat()
            
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == serviceClass.name }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service status", e)
            false
        }
    }
    
    private fun restartService(context: Context) {
        try {
            val intent = Intent(context, ConfidantBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "✅ Service restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restart service", e)
        }
    }
    
    companion object {
        private const val TAG = "HeartbeatReceiver"
    }
}
