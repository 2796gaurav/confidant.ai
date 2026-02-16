package com.confidant.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.confidant.ai.ConfidantApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BootReceiver - Restarts services after device reboot
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed - restarting services")
            
            CoroutineScope(Dispatchers.IO).launch {
                val app = ConfidantApplication.instance
                
                // Check if setup is complete
                if (app.preferencesManager.isSetupComplete()) {
                    // Start thermal monitoring
                    ThermalMonitoringService.start(context)
                    
                    // Start Telegram bot if configured
                    val token = app.preferencesManager.getTelegramBotToken()
                    if (token != null) {
                        kotlinx.coroutines.GlobalScope.launch {
                            app.telegramBotManager.startBot()
                        }
                    }
                    
                    // Schedule intelligence windows (temporarily disabled)
                    // app.intelligenceScheduler.start()
                    
                    Log.i(TAG, "Services restarted successfully")
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiver"
    }
}