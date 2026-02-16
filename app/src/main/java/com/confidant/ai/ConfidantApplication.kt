package com.confidant.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import com.confidant.ai.data.PreferencesManager
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.memory.SimplifiedMemorySystem
import com.confidant.ai.model.ModelDownloadManager
import com.confidant.ai.scheduler.IntelligenceScheduler
import com.confidant.ai.service.ThermalMonitoringService
import com.confidant.ai.system.ServerManager
import com.confidant.ai.system.SystemMonitor
import com.confidant.ai.system.LogLevel
import com.confidant.ai.telegram.TelegramBotManager
import com.confidant.ai.thermal.ThermalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConfidantApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Lazy initialization of core components - use singleton for PreferencesManager
    val database by lazy { AppDatabase.getDatabase(this) }
    val preferencesManager by lazy { PreferencesManager.getInstance(this) }
    val thermalManager by lazy { ThermalManager(this) }
    val systemMonitor by lazy { SystemMonitor(this) }
    val llmEngine by lazy { LLMEngine(this, thermalManager) }
    val memorySystem by lazy { SimplifiedMemorySystem(this, llmEngine, database) }
    val telegramBotManager by lazy { TelegramBotManager(this, llmEngine, memorySystem, database) }
    val modelDownloadManager by lazy { ModelDownloadManager.getInstance(this) }
    val intelligenceScheduler by lazy { IntelligenceScheduler(this, llmEngine, memorySystem, telegramBotManager, database) }
    val serverManager by lazy { ServerManager(this) }
    val serviceHealthMonitor by lazy { com.confidant.ai.service.ServiceHealthMonitor(this) }
    
    // Interest optimizer for managing user interests
    val interestOptimizer by lazy { 
        com.confidant.ai.personalization.InterestOptimizer(this, database, llmEngine)
    }
    
    // 24/7 Operation Managers (NEW)
    val wakeLockManager by lazy { com.confidant.ai.power.WakeLockManager(this) }
    val foregroundServiceTimeoutManager by lazy { com.confidant.ai.service.ForegroundServiceTimeoutManager(this) }
    val serverControlManager by lazy { com.confidant.ai.system.ServerControlManager(this) }
    val notificationChannelManager by lazy { com.confidant.ai.notifications.NotificationChannelManager(this) }
    val batteryAnalyticsTracker by lazy { com.confidant.ai.analytics.BatteryAnalyticsTracker(this) }
    val oemOptimizationHelper by lazy { com.confidant.ai.oem.OEMOptimizationHelper(this) }
    
    // DuckDuckGo search tool for proactive messaging
    val duckDuckGoSearchTool by lazy {
        com.confidant.ai.integrations.DuckDuckGoSearchTool(this)
    }
    
    // Simplified proactive system (lazy init)
    val simplifiedProactiveSystem by lazy {
        com.confidant.ai.proactive.SimplifiedProactiveSystem(
            context = this,
            llmEngine = llmEngine,
            telegramBot = telegramBotManager,
            database = database,
            searchTool = duckDuckGoSearchTool
        ).also {
            it.initialize()
        }
    }
    
    // Sleep mode handler
    val sleepModeHandler by lazy {
        com.confidant.ai.service.SleepModeHandler(
            context = this,
            database = database,
            serverManager = serverManager,
            telegramBot = telegramBotManager
        ).also {
            it.initialize()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Log app startup
        android.util.Log.i("ConfidantApp", "🚀 Confidant AI Application starting...")
        
        // Create notification channels
        createNotificationChannels()
        
        // Initialize core systems
        applicationScope.launch {
            initializeSystems()
        }
    }
    
    private fun createNotificationChannels() {
        // Use NotificationChannelManager for optimized channel creation
        notificationChannelManager.createChannels()
    }
    
    private suspend fun initializeSystems() {
        serverManager.addLog("🚀 Initializing Confidant AI systems...", category = "System")
        
        // Initialize server control manager
        serverManager.addLog("🎮 Initializing server control manager...", category = "System")
        serverControlManager.initialize()
        serverManager.addLog("✅ Server control manager initialized", LogLevel.SUCCESS, "System")
        
        // Initialize memory system
        serverManager.addLog("💾 Initializing memory system...", category = "Memory")
        memorySystem.initialize()
        serverManager.addLog("✅ Memory system initialized", LogLevel.SUCCESS, "Memory")
        
        // Initialize proactive system
        serverManager.addLog("💭 Initializing proactive system...", category = "Proactive")
        simplifiedProactiveSystem // Trigger lazy init
        serverManager.addLog("✅ Proactive system initialized", LogLevel.SUCCESS, "Proactive")
        
        // Initialize sleep mode handler
        serverManager.addLog("🌙 Initializing sleep mode handler...", category = "Sleep")
        sleepModeHandler // Trigger lazy init
        serverManager.addLog("✅ Sleep mode handler initialized", LogLevel.SUCCESS, "Sleep")
        
        // Check if setup is complete and start services
        if (preferencesManager.isSetupComplete()) {
            serverManager.addLog("✅ Setup complete, starting core services...", LogLevel.SUCCESS, "System")
            startCoreServices()
        } else {
            serverManager.addLog("⚠️ Setup not complete, waiting for user configuration", LogLevel.WARN, "System")
        }
    }
    
    private suspend fun startCoreServices() {
        // Start thermal monitoring
        serverManager.addLog("🌡️ Starting thermal monitoring service...", category = "Thermal")
        ThermalMonitoringService.start(this)
        serverManager.addLog("✅ Thermal monitoring service started", LogLevel.SUCCESS, "Thermal")
        
        // Schedule WorkManager service health check
        serverManager.addLog("🔄 Scheduling WorkManager service health check...", category = "Service")
        com.confidant.ai.service.ServiceRestartWorker.schedule(this)
        serverManager.addLog("✅ WorkManager health check scheduled (15-minute intervals)", LogLevel.SUCCESS, "Service")
        
        // DON'T start Telegram bot automatically - only start when user starts AI server
        serverManager.addLog("ℹ️ Telegram bot will start when AI server is started", LogLevel.INFO, "Telegram")
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    
    companion object {
        @JvmStatic
        lateinit var instance: ConfidantApplication
            private set
        
        const val CHANNEL_SERVICE = "confidant_service"
        const val CHANNEL_PROACTIVE = "confidant_proactive"
        const val CHANNEL_ALERTS = "confidant_alerts"
        const val CHANNEL_THERMAL = "confidant_thermal"
    }
}