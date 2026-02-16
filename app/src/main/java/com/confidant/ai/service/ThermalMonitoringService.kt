package com.confidant.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.confidant.ai.MainActivity
import com.confidant.ai.R
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.system.LogLevel
import com.confidant.ai.thermal.ThermalManager
import kotlinx.coroutines.*

/**
 * ThermalMonitoringService - Background service for thermal monitoring
 * Runs as foreground service to keep monitoring active
 */
class ThermalMonitoringService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var thermalManager: ThermalManager
    private var monitoringJob: Job? = null
    
    // Notification throttling
    private var lastNotificationUpdate = 0L
    private var lastNotificationState: ThermalManager.ThermalState? = null
    private var lastNotificationTemp = 0f
    
    override fun onCreate() {
        super.onCreate()
        thermalManager = ConfidantApplication.instance.thermalManager
        Log.i(TAG, "ThermalMonitoringService created")
        ConfidantApplication.instance.serverManager.addLog(
            "🌡️ Thermal monitoring service created",
            LogLevel.INFO,
            "Service"
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ThermalMonitoringService started")
        ConfidantApplication.instance.serverManager.addLog(
            "✅ Thermal monitoring service started",
            LogLevel.SUCCESS,
            "Service"
        )
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start monitoring
        startMonitoring()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        monitoringJob?.cancel()
        Log.i(TAG, "ThermalMonitoringService destroyed")
    }
    
    private fun startMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Update thermal readings
                    thermalManager.updateReadings()
                    
                    // Update notification with current status
                    updateNotification()
                    
                    // Check for critical thermal state
                    val state = thermalManager.getThermalStatus()
                    if (state == ThermalManager.ThermalState.CRITICAL) {
                        Log.w(TAG, "Critical thermal state detected!")
                        // Could trigger emergency cooldown here
                    }
                    
                    delay(MONITORING_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in thermal monitoring loop", e)
                    delay(MONITORING_INTERVAL_MS)
                }
            }
        }
    }
    
    private fun createNotification(): Notification {
        val channelId = ConfidantApplication.CHANNEL_THERMAL
        
        // Create intent to open main activity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Confidant AI")
            .setContentText("Thermal monitoring active")
            .setSmallIcon(R.drawable.ic_thermal)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun updateNotification() {
        val state = thermalManager.getThermalStatus()
        val temp = thermalManager.cpuTemperature.value
        val currentTime = System.currentTimeMillis()
        
        // Only update notification if:
        // 1. State changed
        // 2. Temperature changed by more than 5°C
        // 3. More than 1 hour has passed since last update
        val stateChanged = state != lastNotificationState
        val tempChanged = kotlin.math.abs(temp - lastNotificationTemp) >= 5f
        val timeElapsed = currentTime - lastNotificationUpdate > 3600_000 // 1 hour
        
        if (!stateChanged && !tempChanged && !timeElapsed) {
            return // Skip update
        }
        
        lastNotificationUpdate = currentTime
        lastNotificationState = state
        lastNotificationTemp = temp
        
        val statusText = when (state) {
            ThermalManager.ThermalState.NOMINAL -> "Thermal: Normal | ${temp.toInt()}°C"
            ThermalManager.ThermalState.LIGHT -> "Thermal: Warm | ${temp.toInt()}°C"
            ThermalManager.ThermalState.MODERATE -> "Thermal: Hot | ${temp.toInt()}°C"
            ThermalManager.ThermalState.SEVERE -> "Thermal: Very Hot | ${temp.toInt()}°C"
            ThermalManager.ThermalState.CRITICAL -> "Thermal: Critical | ${temp.toInt()}°C"
        }
        
        val notification = NotificationCompat.Builder(this, ConfidantApplication.CHANNEL_THERMAL)
            .setContentTitle("Confidant AI")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_thermal)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Notification updated: $statusText")
    }
    
    companion object {
        private const val TAG = "ThermalMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val MONITORING_INTERVAL_MS = 5000L // 5 seconds
        
        fun start(context: Context) {
            val intent = Intent(context, ThermalMonitoringService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, ThermalMonitoringService::class.java))
        }
        
        fun isRunning(context: Context): Boolean {
            // Check if service is running
            return false // Simplified - would need actual check
        }
    }
}