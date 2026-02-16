package com.confidant.ai.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ServiceHeartbeatManager - Schedules periodic health checks for background service
 * Uses AlarmManager with setExactAndAllowWhileIdle for Doze survival
 */
class ServiceHeartbeatManager(private val context: Context) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    companion object {
        private const val TAG = "ServiceHeartbeat"
        private const val HEARTBEAT_INTERVAL = 15 * 60 * 1000L // 15 minutes
        private const val HEARTBEAT_REQUEST_CODE = 1002
    }
    
    /**
     * Schedule next heartbeat check
     */
    fun scheduleHeartbeat() {
        try {
            val intent = Intent(context, HeartbeatReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                HEARTBEAT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Use setExactAndAllowWhileIdle for Doze survival
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + HEARTBEAT_INTERVAL,
                pendingIntent
            )
            
            Log.d(TAG, "✅ Heartbeat scheduled in ${HEARTBEAT_INTERVAL / 1000 / 60} minutes")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule heartbeat", e)
        }
    }
    
    /**
     * Cancel heartbeat checks
     */
    fun cancelHeartbeat() {
        try {
            val intent = Intent(context, HeartbeatReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                HEARTBEAT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "✅ Heartbeat cancelled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel heartbeat", e)
        }
    }
}
