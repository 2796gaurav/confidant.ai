package com.confidant.ai.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.R
import com.confidant.ai.thermal.ThermalManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * ConfidantBackgroundService - Foreground service for 24/7 operation
 * Survives Android Doze mode and aggressive OEM power management
 * 
 * Features:
 * - Foreground service with persistent notification
 * - Thermal state monitoring and display
 * - Auto-restart on kill (START_STICKY)
 * - Battery optimization exemption recommended
 */
class ConfidantBackgroundService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationManager: NotificationManager
    private lateinit var thermalManager: ThermalManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        thermalManager = ConfidantApplication.instance.thermalManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        Log.i(TAG, "🚀 ConfidantBackgroundService created")
        
        // Initialize timeout manager
        ConfidantApplication.instance.foregroundServiceTimeoutManager.onServiceStart()
        
        // Track service start in battery analytics
        ConfidantApplication.instance.batteryAnalyticsTracker.onServiceStarted()
        
        // Acquire wake lock using intelligent manager
        acquireWakeLock()
        
        // Create notification channel
        createNotificationChannel()
        
        // Start as foreground service (survives Doze)
        val notification = buildStatusNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Monitor thermal state and update notification
        serviceScope.launch {
            thermalManager.thermalState.collect { state ->
                updateNotification(state)
            }
        }
        
        // Check timeout periodically (every 30 minutes)
        serviceScope.launch {
            while (isActive) {
                delay(30 * 60 * 1000) // 30 minutes
                
                val timeoutManager = ConfidantApplication.instance.foregroundServiceTimeoutManager
                if (timeoutManager.shouldStopService()) {
                    Log.w(TAG, "⚠️ Approaching service timeout - stopping gracefully")
                    stopSelf()
                    break
                }
                
                // Renew wake lock
                renewWakeLock()
            }
        }
        
        Log.i(TAG, "✅ Foreground service started with wake lock")
    }
    
    private fun acquireWakeLock() {
        val success = ConfidantApplication.instance.wakeLockManager.acquireWakeLock()
        if (!success) {
            Log.w(TAG, "⚠️ Wake lock budget exhausted - entering power-saving mode")
            // Service continues but without wake lock
        }
    }
    
    private fun releaseWakeLock() {
        ConfidantApplication.instance.wakeLockManager.releaseWakeLock()
    }
    
    private fun renewWakeLock() {
        // Renew wake lock before it expires
        ConfidantApplication.instance.wakeLockManager.renewWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        
        // Check battery optimization status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (!isIgnoringBatteryOptimizations) {
                Log.w(TAG, "⚠️ Battery optimization NOT exempted - service may be killed")
            } else {
                Log.i(TAG, "✅ Battery optimization exempted")
            }
        }
        
        // Restart service if killed (START_STICKY)
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        // Notify timeout manager
        ConfidantApplication.instance.foregroundServiceTimeoutManager.onServiceStop()
        
        // Track service stop in battery analytics
        ConfidantApplication.instance.batteryAnalyticsTracker.onServiceStopped()
        
        // ALWAYS release wake lock before destroying
        releaseWakeLock()
        
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "❌ ConfidantBackgroundService destroyed")
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        Log.w(TAG, "⚠️ Task removed (Clear All) - scheduling restart")
        
        // Check if user wants auto-start
        val shouldAutoStart = ConfidantApplication.instance.preferencesManager.getServerAutoStart()
        if (!shouldAutoStart) {
            Log.d(TAG, "Auto-start disabled by user - not restarting")
            return
        }
        
        // Release current wake lock
        releaseWakeLock()
        
        // Schedule service restart via AlarmManager (survives task removal)
        scheduleServiceRestart()
        
        // Also schedule via WorkManager as backup
        ServiceRestartWorker.schedule(applicationContext)
        
        stopSelf()
    }
    
    private fun scheduleServiceRestart() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val restartIntent = Intent(applicationContext, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                1006,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Restart after 5 seconds
            val restartTime = System.currentTimeMillis() + 5000
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        restartTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    restartTime,
                    pendingIntent
                )
            }
            
            Log.i(TAG, "✅ Service restart scheduled via AlarmManager (5 seconds)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule service restart", e)
        }
    }
    
    private fun createNotificationChannel() {
        // Channels are created by NotificationChannelManager in ConfidantApplication
        // This method is kept for compatibility but does nothing
    }
    
    private fun buildStatusNotification(): Notification {
        val thermalState = thermalManager.getThermalStatus()
        val statusText = getThermalStatusText(thermalState)
        
        // Use NotificationChannelManager to build notification
        return ConfidantApplication.instance.notificationChannelManager.buildServiceNotification(
            title = "Confidant AI",
            text = statusText,
            showStopAction = true
        )
    }
    
    private fun updateNotification(thermalState: ThermalManager.ThermalState) {
        val statusText = getThermalStatusText(thermalState)
        
        // Use NotificationChannelManager to build notification
        val notification = ConfidantApplication.instance.notificationChannelManager.buildServiceNotification(
            title = "Confidant AI",
            text = statusText,
            showStopAction = true
        )
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun getThermalStatusText(state: ThermalManager.ThermalState): String {
        return when (state) {
            ThermalManager.ThermalState.NOMINAL -> "Running normally"
            ThermalManager.ThermalState.LIGHT -> "Running (device slightly warm)"
            ThermalManager.ThermalState.MODERATE -> "Throttled (device warm)"
            ThermalManager.ThermalState.SEVERE -> "Limited (device hot)"
            ThermalManager.ThermalState.CRITICAL -> "Paused (device overheating)"
        }
    }
    
    companion object {
        private const val TAG = "ConfidantService"
        private const val NOTIFICATION_ID = 1001
        
        /**
         * Start the background service
         */
        fun start(context: Context) {
            val intent = Intent(context, ConfidantBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop the background service
         */
        fun stop(context: Context) {
            val intent = Intent(context, ConfidantBackgroundService::class.java)
            context.stopService(intent)
        }
    }
}
