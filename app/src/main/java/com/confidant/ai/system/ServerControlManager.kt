package com.confidant.ai.system

import android.content.Context
import android.util.Log
import com.confidant.ai.ConfidantApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * ServerControlManager - User control for server start/stop with preference persistence
 * 
 * Features:
 * - User-initiated server start/stop
 * - Auto-start preference (respected by BootReceiver)
 * - Server status API for UI
 * - Health check before start
 */
class ServerControlManager(private val context: Context) {
    
    private val preferencesManager by lazy {
        (context.applicationContext as ConfidantApplication).preferencesManager
    }
    
    private val serverManager by lazy {
        ConfidantApplication.instance.serverManager
    }
    
    private val _serverStatus = MutableStateFlow(ServerStatus(
        isRunning = false,
        autoStartEnabled = false,
        isHealthy = false,
        uptime = 0L,
        issues = emptyList()
    ))
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    companion object {
        private const val TAG = "ServerControlManager"
    }
    
    /**
     * Initialize and load preferences
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        updateServerStatus()
    }
    
    /**
     * User-initiated server start
     */
    suspend fun startServer(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🚀 User requested server start")
                
                // Check if already running
                if (serverManager.isServerRunning.value) {
                    val error = "Server is already running"
                    Log.w(TAG, error)
                    return@withContext Result.failure(
                        IllegalStateException(error)
                    )
                }
                
                // Perform health check
                val healthCheck = serverManager.performHealthCheck()
                if (!healthCheck.isHealthy) {
                    val error = "Health check failed: ${healthCheck.issues.joinToString()}"
                    Log.e(TAG, error)
                    
                    serverManager.addLog(
                        "❌ Cannot start server: ${healthCheck.issues.first()}",
                        LogLevel.ERROR,
                        "Server"
                    )
                    
                    return@withContext Result.failure(
                        IllegalStateException(error)
                    )
                }
                
                // Start server
                serverManager.startServer()
                
                // Save user preference (enable auto-start)
                preferencesManager.setServerAutoStart(true)
                
                // Update status
                updateServerStatus()
                
                Log.i(TAG, "✅ Server started successfully by user")
                serverManager.addLog(
                    "✅ Server started by user",
                    LogLevel.SUCCESS,
                    "Server"
                )
                
                return@withContext Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start server", e)
                serverManager.addLog(
                    "❌ Failed to start server: ${e.message}",
                    LogLevel.ERROR,
                    "Server"
                )
                return@withContext Result.failure(e)
            }
        }
    }
    
    /**
     * User-initiated server stop
     */
    suspend fun stopServer(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "⏸️ User requested server stop")
                
                // Check if running
                if (!serverManager.isServerRunning.value) {
                    val error = "Server is not running"
                    Log.w(TAG, error)
                    return@withContext Result.failure(
                        IllegalStateException(error)
                    )
                }
                
                // Stop server gracefully
                serverManager.stopServer()
                
                // Save user preference (disable auto-start)
                preferencesManager.setServerAutoStart(false)
                
                // Update status
                updateServerStatus()
                
                Log.i(TAG, "✅ Server stopped successfully by user")
                serverManager.addLog(
                    "⏸️ Server stopped by user",
                    LogLevel.INFO,
                    "Server"
                )
                
                return@withContext Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to stop server", e)
                serverManager.addLog(
                    "❌ Failed to stop server: ${e.message}",
                    LogLevel.ERROR,
                    "Server"
                )
                return@withContext Result.failure(e)
            }
        }
    }
    
    /**
     * Toggle server on/off
     */
    suspend fun toggleServer(): Result<Unit> {
        return if (serverManager.isServerRunning.value) {
            stopServer()
        } else {
            startServer()
        }
    }
    
    /**
     * Check if server should auto-start on boot
     */
    fun shouldAutoStart(): Boolean {
        return preferencesManager.getServerAutoStart()
    }
    
    /**
     * Set auto-start preference
     */
    suspend fun setAutoStart(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.setServerAutoStart(enabled)
        updateServerStatus()
        
        Log.d(TAG, "Auto-start ${if (enabled) "enabled" else "disabled"}")
        serverManager.addLog(
            "Auto-start ${if (enabled) "enabled" else "disabled"}",
            LogLevel.INFO,
            "Server"
        )
    }
    
    /**
     * Get current server status for UI
     */
    fun getServerStatus(): ServerStatus {
        return _serverStatus.value
    }
    
    /**
     * Update server status (call after state changes)
     */
    private suspend fun updateServerStatus() = withContext(Dispatchers.IO) {
        val isRunning = serverManager.isServerRunning.value
        val autoStart = shouldAutoStart()
        val healthCheck = serverManager.getLastHealthCheck()
        
        _serverStatus.value = ServerStatus(
            isRunning = isRunning,
            autoStartEnabled = autoStart,
            isHealthy = healthCheck?.isHealthy ?: false,
            uptime = if (isRunning) serverManager.getUptime() else 0L,
            issues = healthCheck?.issues ?: emptyList()
        )
    }
    
    /**
     * Server status data class for UI
     */
    data class ServerStatus(
        val isRunning: Boolean,
        val autoStartEnabled: Boolean,
        val isHealthy: Boolean,
        val uptime: Long,
        val issues: List<String>
    ) {
        fun getUptimeFormatted(): String {
            if (uptime == 0L) return "Not running"
            
            val hours = uptime / 1000 / 3600
            val minutes = (uptime / 1000 / 60) % 60
            
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "<1m"
            }
        }
        
        fun getStatusText(): String {
            return when {
                !isRunning -> "Stopped"
                !isHealthy -> "Running (Issues)"
                else -> "Running"
            }
        }
        
        fun getStatusEmoji(): String {
            return when {
                !isRunning -> "⏸️"
                !isHealthy -> "⚠️"
                else -> "✅"
            }
        }
    }
}
