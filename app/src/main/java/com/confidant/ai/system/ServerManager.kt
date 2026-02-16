package com.confidant.ai.system

import android.content.Context
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.service.NotificationCaptureService
import com.confidant.ai.thermal.ThermalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ServerManager - Manages the "server" (AI system) lifecycle
 * Handles start, stop, restart, and status monitoring
 */
class ServerManager(private val context: Context) {
    
    private val app = ConfidantApplication.instance
    
    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private val _issues = MutableStateFlow<List<SystemIssue>>(emptyList())
    val issues: StateFlow<List<SystemIssue>> = _issues.asStateFlow()
    
    // Server running state for external access
    val isServerRunning: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false).apply {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            _serverState.collect { state ->
                value = state == ServerState.RUNNING
            }
        }
    }
    
    // Track server start time for uptime calculation
    private var serverStartTime = 0L
    
    // Last health check result
    private var lastHealthCheck: HealthCheckResult? = null
    
    enum class ServerState {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }
    
    /**
     * Start the AI server
     */
    suspend fun startServer(): Result<Unit> {
        return try {
            addLog("Starting server...", LogLevel.INFO)
            _serverState.value = ServerState.STARTING
            
            // Initialize LLM FIRST if not already initialized
            if (!app.llmEngine.isInitialized.value) {
                addLog("Initializing LLM engine...", LogLevel.INFO)
                
                // CRITICAL FIX: Try to get model path from preferences first,
                // but if null, try to find it on disk (auto-recovery)
                var modelPath = app.preferencesManager.getModelPath()
                
                if (modelPath == null) {
                    addLog("Model path not in preferences, searching filesystem...", LogLevel.WARN)
                    // Try to find model on disk
                    modelPath = app.modelDownloadManager.getModelPath()
                    
                    if (modelPath != null) {
                        addLog("✓ Found model on disk, auto-recovering: $modelPath", LogLevel.INFO)
                        // Save to preferences for next time
                        try {
                            app.preferencesManager.setModelPath(modelPath)
                            addLog("✓ Model path saved to preferences", LogLevel.INFO)
                        } catch (e: Exception) {
                            addLog("Warning: Failed to save model path to preferences", LogLevel.WARN)
                        }
                    } else {
                        _serverState.value = ServerState.ERROR
                        val error = "Model not found. Please download the model first."
                        addLog(error, LogLevel.ERROR)
                        
                        // Only notify if critical and user wants notifications
                        if (shouldNotifyUser(critical = true)) {
                            app.telegramBotManager.sendSystemMessage("⚠️ Server start failed: Model not downloaded")
                        }
                        return Result.failure(Exception(error))
                    }
                }
                
                addLog("Model path: $modelPath", LogLevel.INFO)
                
                // Verify file still exists before attempting initialization
                val modelFile = java.io.File(modelPath)
                if (!modelFile.exists()) {
                    _serverState.value = ServerState.ERROR
                    val error = "Model file missing at: $modelPath. Please re-download."
                    addLog(error, LogLevel.ERROR)
                    
                    if (shouldNotifyUser(critical = true)) {
                        app.telegramBotManager.sendSystemMessage("⚠️ Server start failed: Model file missing")
                    }
                    return Result.failure(Exception(error))
                }
                
                addLog("Model file verified (${modelFile.length() / (1024 * 1024)} MB)", LogLevel.INFO)
                addLog("Attempting to load model into memory...", LogLevel.INFO)
                
                val result = app.llmEngine.initialize(modelPath)
                if (result.isFailure) {
                    _serverState.value = ServerState.ERROR
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    addLog("LLM initialization failed", LogLevel.ERROR)
                    addLog("Error: $errorMsg", LogLevel.ERROR)
                    addLog("Try re-downloading the model if issue persists", LogLevel.WARN)
                    
                    if (shouldNotifyUser(critical = true)) {
                        app.telegramBotManager.sendSystemMessage("❌ Server start failed: Model initialization error")
                    }
                    return result
                }
                addLog("LLM engine initialized successfully", LogLevel.SUCCESS)
            } else {
                addLog("LLM engine already initialized", LogLevel.INFO)
            }
            
            // NOW check for other blocking issues (after LLM init attempt)
            val blockingIssues = checkSystemIssues().filter { it.isBlocking }
            if (blockingIssues.isNotEmpty()) {
                _serverState.value = ServerState.ERROR
                addLog("Cannot start: ${blockingIssues.size} blocking issues", LogLevel.ERROR)
                blockingIssues.forEach { issue ->
                    addLog("Issue: ${issue.title} - ${issue.description}", LogLevel.ERROR)
                }
                
                if (shouldNotifyUser(critical = true)) {
                    app.telegramBotManager.sendSystemMessage("⚠️ Server start blocked: ${blockingIssues.first().title}")
                }
                return Result.failure(Exception("Blocking issues present: ${blockingIssues.joinToString { it.title }}"))
            }
            
            // Start Telegram bot
            if (!app.telegramBotManager.isRunning.value) {
                addLog("Starting Telegram bot...", LogLevel.INFO)
                app.telegramBotManager.startBot()
                addLog("Telegram bot started", LogLevel.SUCCESS)
            } else {
                addLog("Telegram bot already running", LogLevel.INFO)
            }
            
            // Start foreground service for 24/7 operation
            addLog("Starting foreground service...", LogLevel.INFO)
            com.confidant.ai.service.ConfidantBackgroundService.start(context)
            addLog("Foreground service started", LogLevel.SUCCESS)
            
            // Schedule heartbeat mechanism for service health monitoring
            addLog("Scheduling service heartbeat...", LogLevel.INFO)
            com.confidant.ai.service.ServiceHeartbeatManager(context).scheduleHeartbeat()
            addLog("Heartbeat scheduled (15-minute intervals)", LogLevel.SUCCESS)
            
            // Schedule processing windows (temporarily disabled)
            addLog("Simplified proactive messaging active (2-hour intervals)", LogLevel.INFO)
            // app.intelligenceScheduler.start()
            // addLog("Processing windows scheduled (8AM, 1PM, 6PM, 10PM)", LogLevel.SUCCESS)
            
            serverStartTime = System.currentTimeMillis()
            _serverState.value = ServerState.RUNNING
            lastHealthCheck = performHealthCheck()
            addLog("Server started successfully", LogLevel.SUCCESS)
            
            // Send "server started" message
            if (shouldNotifyUser(critical = false, serverEvent = true)) {
                app.telegramBotManager.sendSystemMessage(
                    com.confidant.ai.telegram.StandardizedMessages.getServerStartedMessage()
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            _serverState.value = ServerState.ERROR
            addLog("Failed to start server: ${e.message}", LogLevel.ERROR)
            
            if (shouldNotifyUser(critical = true)) {
                app.telegramBotManager.sendSystemMessage("❌ Server start failed: ${e.message}")
            }
            Result.failure(e)
        }
    }
    
    /**
     * Stop the AI server
     */
    suspend fun stopServer(): Result<Unit> {
        return try {
            addLog("Stopping server...", LogLevel.INFO)
            _serverState.value = ServerState.STOPPING
            
            // Cancel heartbeat
            addLog("Cancelling service heartbeat...", LogLevel.INFO)
            com.confidant.ai.service.ServiceHeartbeatManager(context).cancelHeartbeat()
            
            // Stop scheduler (temporarily disabled)
            // app.intelligenceScheduler.stop()
            addLog("Scheduler stopped", LogLevel.INFO)
            
            // Stop Telegram bot
            app.telegramBotManager.stopBot()
            addLog("Telegram bot stopped", LogLevel.INFO)
            
            // Stop foreground service
            addLog("Stopping foreground service...", LogLevel.INFO)
            com.confidant.ai.service.ConfidantBackgroundService.stop(context)
            
            serverStartTime = 0L
            _serverState.value = ServerState.STOPPED
            addLog("Server stopped", LogLevel.SUCCESS)
            
            // Send "server stopped" message
            if (shouldNotifyUser(critical = false, serverEvent = true)) {
                app.telegramBotManager.sendSystemMessage(
                    com.confidant.ai.telegram.StandardizedMessages.getServerStoppedMessage()
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            _serverState.value = ServerState.ERROR
            addLog("Failed to stop server: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }
    
    /**
     * Restart the AI server
     */
    suspend fun restartServer(): Result<Unit> {
        addLog("Restarting server...", LogLevel.INFO)
        
        // Send "restarting" message
        if (shouldNotifyUser(critical = false, serverEvent = true)) {
            val app = ConfidantApplication.instance
            app.telegramBotManager.sendSystemMessage(
                com.confidant.ai.telegram.StandardizedMessages.getServerRestartedMessage()
            )
        }
        
        stopServer()
        kotlinx.coroutines.delay(1000)
        return startServer()
    }
    
    /**
     * Check if user should be notified based on preferences
     */
    private suspend fun shouldNotifyUser(critical: Boolean = false, serverEvent: Boolean = false, thermalEvent: Boolean = false): Boolean {
        val quietMode = app.preferencesManager.isQuietModeEnabled()
        
        // Always notify critical issues regardless of quiet mode
        if (critical) {
            return true
        }
        
        // If quiet mode is enabled, don't send non-critical messages
        if (quietMode) {
            return false
        }
        
        // Check specific event preferences
        if (serverEvent) {
            return app.preferencesManager.shouldNotifyServerEvents()
        }
        
        if (thermalEvent) {
            return app.preferencesManager.shouldNotifyThermalEvents()
        }
        
        return false
    }
    
    /**
     * Check for system issues
     * 
     * CRITICAL FIX: Use ModelDownloadManager.getModelPath() which has auto-recovery
     * instead of just checking preferences
     */
    fun checkSystemIssues(): List<SystemIssue> {
        val issues = mutableListOf<SystemIssue>()
        
        val isModelDownloaded = app.modelDownloadManager.isModelDownloaded()
        // Use ModelDownloadManager which has auto-recovery logic
        val modelPath = app.modelDownloadManager.getModelPath()
        val isLLMInitialized = app.llmEngine.isInitialized.value
        
        if (!isModelDownloaded) {
            issues.add(
                SystemIssue(
                    title = "Model Not Downloaded",
                    description = "AI model needs to be downloaded before starting",
                    severity = IssueSeverity.CRITICAL,
                    isBlocking = true,
                    action = "Download Model"
                )
            )
        } else if (modelPath == null) {
            issues.add(
                SystemIssue(
                    title = "Model Path Not Set",
                    description = "Model file exists but path not configured. Try re-downloading.",
                    severity = IssueSeverity.CRITICAL,
                    isBlocking = true,
                    action = "Download Model"
                )
            )
        } else if (!isLLMInitialized) {
            val lastError = _logs.value.firstOrNull { it.level == LogLevel.ERROR }
            val errorHint = if (lastError != null && lastError.message.contains("Failed to load model")) {
                "Last error: ${lastError.message}"
            } else {
                "File may be corrupted or incompatible."
            }
            
            issues.add(
                SystemIssue(
                    title = "Model Not Loaded",
                    description = "Model file exists but failed to load. $errorHint Try re-downloading or check logs.",
                    severity = IssueSeverity.CRITICAL,
                    isBlocking = true,
                    action = "Download Model"
                )
            )
        }
        
        // Check notification access
        if (!NotificationCaptureService.isEnabled(context)) {
            issues.add(
                SystemIssue(
                    title = "Notification Access Required",
                    description = "Grant notification access to capture and learn from notifications",
                    severity = IssueSeverity.CRITICAL,
                    isBlocking = true,
                    action = "Grant Access"
                )
            )
        }
        
        // Check thermal state
        val thermalState = app.thermalManager.thermalState.value
        if (thermalState == ThermalManager.ThermalState.CRITICAL) {
            issues.add(
                SystemIssue(
                    title = "Critical Thermal State",
                    description = "Device is overheating. AI processing is disabled for safety",
                    severity = IssueSeverity.CRITICAL,
                    isBlocking = true,
                    action = "Cool Down Device"
                )
            )
        } else if (thermalState == ThermalManager.ThermalState.SEVERE) {
            issues.add(
                SystemIssue(
                    title = "High Temperature",
                    description = "Device temperature is high. Performance may be reduced",
                    severity = IssueSeverity.WARNING,
                    isBlocking = false,
                    action = "View Thermal"
                )
            )
        }
        
        // Check battery
        val batteryInfo = app.systemMonitor.batteryInfo.value
        if (batteryInfo.percent < 15 && !batteryInfo.isCharging) {
            issues.add(
                SystemIssue(
                    title = "Low Battery",
                    description = "Battery is below 15%. Consider charging to avoid interruptions",
                    severity = IssueSeverity.WARNING,
                    isBlocking = false,
                    action = null
                )
            )
        }
        
        // Check RAM
        val ramInfo = app.systemMonitor.ramUsage.value
        if (ramInfo.usagePercent > 90) {
            issues.add(
                SystemIssue(
                    title = "High RAM Usage",
                    description = "RAM usage is above 90%. Performance may be affected",
                    severity = IssueSeverity.WARNING,
                    isBlocking = false,
                    action = null
                )
            )
        }
        
        // Check storage
        val storageInfo = app.systemMonitor.storageInfo.value
        if (storageInfo.usagePercent > 90) {
            issues.add(
                SystemIssue(
                    title = "Low Storage",
                    description = "Storage is above 90%. Free up space to avoid issues",
                    severity = IssueSeverity.WARNING,
                    isBlocking = false,
                    action = null
                )
            )
        }
        
        // Check CPU usage
        val cpuUsage = app.systemMonitor.cpuUsage.value
        if (cpuUsage > 90) {
            issues.add(
                SystemIssue(
                    title = "High CPU Usage",
                    description = "CPU usage is very high. Close other apps for better performance",
                    severity = IssueSeverity.INFO,
                    isBlocking = false,
                    action = null
                )
            )
        }
        
        // Check Telegram configuration
        val telegramToken = kotlinx.coroutines.runBlocking {
            app.preferencesManager.getTelegramBotToken()
        }
        if (telegramToken == null) {
            issues.add(
                SystemIssue(
                    title = "Telegram Not Configured",
                    description = "Configure Telegram bot to enable messaging",
                    severity = IssueSeverity.WARNING,
                    isBlocking = false,
                    action = "Configure"
                )
            )
        }
        
        _issues.value = issues
        return issues
    }
    
    /**
     * Get server uptime in milliseconds
     */
    fun getUptime(): Long {
        return if (_serverState.value == ServerState.RUNNING && serverStartTime > 0) {
            System.currentTimeMillis() - serverStartTime
        } else 0L
    }
    
    /**
     * Perform health check and return result
     */
    fun performHealthCheck(): HealthCheckResult {
        val issues = checkSystemIssues()
        val blockingIssues = issues.filter { it.isBlocking }
        
        return HealthCheckResult(
            isHealthy = blockingIssues.isEmpty(),
            issues = blockingIssues.map { it.title }
        )
    }
    
    /**
     * Get last health check result
     */
    fun getLastHealthCheck(): HealthCheckResult? {
        return lastHealthCheck
    }
    
    /**
     * Health check result data class
     */
    data class HealthCheckResult(
        val isHealthy: Boolean,
        val issues: List<String>
    )
    
    /**
     * Add log entry
     */
    fun addLog(message: String, level: LogLevel = LogLevel.INFO, category: String = "System") {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            message = message
        )
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, entry) // Add to beginning
        
        // Keep only last 500 logs (increased for detailed logging)
        if (currentLogs.size > 500) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        
        _logs.value = currentLogs
        
        // Also log to Android logcat
        when (level) {
            LogLevel.ERROR -> Log.e(TAG, "[$category] $message")
            LogLevel.WARN -> Log.w(TAG, "[$category] $message")
            LogLevel.SUCCESS -> Log.i(TAG, "[$category] ✓ $message")
            LogLevel.INFO -> Log.i(TAG, "[$category] $message")
            LogLevel.DEBUG -> Log.d(TAG, "[$category] $message")
        }
    }
    
    /**
     * Clear logs
     */
    fun clearLogs() {
        _logs.value = emptyList()
        Log.i(TAG, "Logs cleared")
    }
    
    data class SystemIssue(
        val title: String,
        val description: String,
        val severity: IssueSeverity,
        val isBlocking: Boolean,
        val action: String?
    )
    
    enum class IssueSeverity {
        INFO, WARNING, CRITICAL
    }
    
    companion object {
        private const val TAG = "ServerManager"
    }
}
