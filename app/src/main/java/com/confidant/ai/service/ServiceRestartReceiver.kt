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
 * ServiceRestartReceiver - Handles service restart after timeout or task removal
 * Triggered by AlarmManager to restart the service
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "🔄 Service restart alarm triggered")
        
        scope.launch {
            try {
                val app = context.applicationContext as ConfidantApplication
                
                // Check if user wants auto-start
                val shouldAutoStart = app.preferencesManager.getServerAutoStart()
                if (!shouldAutoStart) {
                    Log.d(TAG, "Auto-start disabled by user - skipping restart")
                    return@launch
                }
                
                // Check if server is already running
                val isRunning = app.serverManager.serverState.value == 
                    com.confidant.ai.system.ServerManager.ServerState.RUNNING
                
                if (isRunning) {
                    Log.d(TAG, "Server already running - no restart needed")
                    return@launch
                }
                
                // Restart the server
                Log.i(TAG, "🚀 Restarting server...")
                app.serverManager.startServer()
                
                Log.i(TAG, "✅ Server restarted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restart server", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }
}
