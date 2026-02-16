package com.confidant.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.confidant.ai.ConfidantApplication

/**
 * ForegroundServiceTimeoutReceiver - Handles foreground service timeout warnings
 * Triggered by ForegroundServiceTimeoutManager when approaching 6-hour limit
 */
class ForegroundServiceTimeoutReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TIMEOUT_WARNING -> {
                Log.w(TAG, "⚠️ Foreground service timeout warning received")
                
                val app = context.applicationContext as ConfidantApplication
                val timeoutManager = app.foregroundServiceTimeoutManager
                
                // Log remaining time
                val remainingMs = timeoutManager.getRemainingBudget()
                val remainingMinutes = remainingMs / (60 * 1000)
                
                Log.w(TAG, "⏰ $remainingMinutes minutes remaining before timeout")
                
                // The service will handle the timeout gracefully
                // This receiver is just for logging and potential future actions
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }
    
    companion object {
        private const val TAG = "ForegroundServiceTimeoutReceiver"
        const val ACTION_TIMEOUT_WARNING = "com.confidant.ai.ACTION_TIMEOUT_WARNING"
    }
}
