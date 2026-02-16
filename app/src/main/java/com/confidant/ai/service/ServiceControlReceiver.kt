package com.confidant.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.confidant.ai.ConfidantApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ServiceControlReceiver - Handles notification actions for server control
 * 
 * Actions:
 * - STOP_SERVER: Stop the AI server and background service
 */
class ServiceControlReceiver : BroadcastReceiver() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_SERVER -> {
                Log.i(TAG, "🛑 Stop server action received from notification")
                
                scope.launch {
                    try {
                        val app = context.applicationContext as ConfidantApplication
                        
                        // Stop server via ServerControlManager
                        app.serverControlManager.stopServer()
                        
                        Log.i(TAG, "✅ Server stopped successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to stop server", e)
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }
    
    companion object {
        private const val TAG = "ServiceControlReceiver"
        const val ACTION_STOP_SERVER = "com.confidant.ai.ACTION_STOP_SERVER"
    }
}
