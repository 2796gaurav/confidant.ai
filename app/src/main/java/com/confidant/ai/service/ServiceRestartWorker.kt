package com.confidant.ai.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.system.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * ServiceRestartWorker - WorkManager backup for service restart
 * 
 * Purpose:
 * - Runs every 15 minutes to check if service should be running
 * - Restarts service if it died unexpectedly
 * - Respects user auto-start preference
 * - Provides backup to AlarmManager restart mechanism
 * 
 * Why WorkManager:
 * - Survives app kills and device reboots
 * - Handles battery optimization automatically
 * - Exponential backoff on failures
 * - Guaranteed execution (eventually)
 */
class ServiceRestartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 ServiceRestartWorker triggered - checking service health")
                
                val app = applicationContext as ConfidantApplication
                
                // Check if user wants auto-start
                val shouldRun = app.preferencesManager.getServerAutoStart()
                if (!shouldRun) {
                    Log.d(TAG, "Auto-start disabled by user - skipping restart")
                    return@withContext Result.success()
                }
                
                // Check if service is actually running
                val isRunning = app.serverManager.serverState.value == 
                    com.confidant.ai.system.ServerManager.ServerState.RUNNING
                
                if (isRunning) {
                    Log.d(TAG, "✅ Service is running - no restart needed")
                    return@withContext Result.success()
                }
                
                // Service should be running but isn't - restart it
                Log.w(TAG, "⚠️ Service is dead - restarting...")
                
                app.serverManager.addLog(
                    "⚠️ Service died unexpectedly - WorkManager restarting",
                    LogLevel.WARN,
                    "Service"
                )
                
                app.serverManager.startServer()
                
                Log.i(TAG, "✅ Service restarted successfully by WorkManager")
                
                app.serverManager.addLog(
                    "✅ Service restarted by WorkManager",
                    LogLevel.SUCCESS,
                    "Service"
                )
                
                return@withContext Result.success()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restart service", e)
                
                // Retry with exponential backoff
                return@withContext Result.retry()
            }
        }
    }
    
    companion object {
        private const val TAG = "ServiceRestartWorker"
        const val WORK_NAME = "service_restart_worker"
        
        /**
         * Schedule periodic service health check
         */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false) // Run even on low battery
                .build()
            
            val request = PeriodicWorkRequestBuilder<ServiceRestartWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            
            Log.d(TAG, "✅ Scheduled periodic service health check (15-minute intervals)")
        }
        
        /**
         * Cancel scheduled work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic service health check")
        }
    }
}
