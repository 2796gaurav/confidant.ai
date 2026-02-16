package com.confidant.ai.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.confidant.ai.R
import com.confidant.ai.service.ServiceControlReceiver

/**
 * NotificationChannelManager - Manages notification channels for battery efficiency
 * 
 * Channel Strategy:
 * - SERVICE: Low importance (silent, no vibration) - for 24/7 foreground service
 * - THERMAL: Min importance (silent, no badge) - for thermal monitoring
 * - PROACTIVE: Default importance (sound, vibration) - for AI messages
 * - SYSTEM: High importance (sound, vibration, heads-up) - for critical alerts
 */
class NotificationChannelManager(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    /**
     * Create all notification channels
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                // Service channel - LOW importance (silent)
                NotificationChannel(
                    CHANNEL_SERVICE,
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Confidant AI running in background"
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                },
                
                // Thermal channel - MIN importance (silent, no badge)
                NotificationChannel(
                    CHANNEL_THERMAL,
                    "Thermal Monitoring",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Device thermal status monitoring"
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                },
                
                // Proactive channel - DEFAULT importance (normal notifications)
                NotificationChannel(
                    CHANNEL_PROACTIVE,
                    "AI Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Intelligent proactive messages from your AI companion"
                    setShowBadge(true)
                    enableVibration(true)
                },
                
                // System channel - HIGH importance (critical alerts)
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Important system alerts and notifications"
                    setShowBadge(true)
                    enableVibration(true)
                    enableLights(true)
                }
            )
            
            notificationManager.createNotificationChannels(channels)
        }
    }
    
    /**
     * Build service notification with stop action
     */
    fun buildServiceNotification(
        title: String = "Confidant AI",
        text: String = "Running in background",
        showStopAction: Boolean = true
    ): android.app.Notification {
        // Intent to open app when notification is tapped
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
        
        // Add stop action if requested
        if (showStopAction) {
            val stopIntent = Intent(context, ServiceControlReceiver::class.java).apply {
                action = ServiceControlReceiver.ACTION_STOP_SERVER
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            builder.addAction(
                R.drawable.ic_notification,
                "Stop Server",
                stopPendingIntent
            )
        }
        
        return builder.build()
    }
    
    /**
     * Build thermal notification
     */
    fun buildThermalNotification(
        thermalState: String,
        temperature: String? = null
    ): android.app.Notification {
        val text = if (temperature != null) {
            "Thermal state: $thermalState ($temperature)"
        } else {
            "Thermal state: $thermalState"
        }
        
        return NotificationCompat.Builder(context, CHANNEL_THERMAL)
            .setContentTitle("Thermal Monitoring")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_thermal)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Build proactive message notification
     */
    fun buildProactiveNotification(
        title: String,
        message: String,
        notificationId: Int
    ): android.app.Notification {
        // Intent to open app
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_PROACTIVE)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()
    }
    
    /**
     * Build system alert notification
     */
    fun buildSystemAlertNotification(
        title: String,
        message: String
    ): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
    }
    
    companion object {
        const val CHANNEL_SERVICE = "confidant_service"
        const val CHANNEL_THERMAL = "confidant_thermal"
        const val CHANNEL_PROACTIVE = "confidant_proactive"
        const val CHANNEL_SYSTEM = "confidant_system"
        
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_THERMAL = 1002
        const val NOTIFICATION_ID_PROACTIVE_BASE = 2000
        const val NOTIFICATION_ID_SYSTEM = 3000
    }
}
