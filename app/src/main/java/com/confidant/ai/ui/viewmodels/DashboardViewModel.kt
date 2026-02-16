package com.confidant.ai.ui.viewmodels

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.memory.MemoryStats
import com.confidant.ai.service.NotificationCaptureService
import com.confidant.ai.thermal.ThermalManager
import com.confidant.ai.ui.screens.ActivityItem
import com.confidant.ai.ui.screens.SystemStatus
import com.confidant.ai.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * ViewModel for Dashboard Screen
 * Manages system status, memory stats, and activity feed
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ConfidantApplication

    // System Status
    private val _systemStatus = MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    // Memory Stats
    private val _memoryStats = MutableStateFlow(MemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    // Thermal State
    private val _thermalState = MutableStateFlow(ThermalManager.ThermalState.NOMINAL)
    val thermalState: StateFlow<ThermalManager.ThermalState> = _thermalState.asStateFlow()

    // Recent Activity
    private val _recentActivity = MutableStateFlow<List<ActivityItem>>(emptyList())
    val recentActivity: StateFlow<List<ActivityItem>> = _recentActivity.asStateFlow()

    init {
        startMonitoring()
        loadInitialData()
    }

    private fun startMonitoring() {
        // Monitor system status
        viewModelScope.launch {
            while (true) {
                updateSystemStatus()
                delay(5000) // Update every 5 seconds
            }
        }

        // Monitor thermal state
        viewModelScope.launch {
            app.thermalManager.thermalState.collect { state ->
                _thermalState.value = state
            }
        }

        // Monitor memory stats
        viewModelScope.launch {
            app.memorySystem.memoryStats.collect { stats ->
                _memoryStats.value = stats
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Load recent activity
            loadRecentActivity()
        }
    }

    private suspend fun updateSystemStatus() {
        val llmInitialized = app.llmEngine.isInitialized.value
        val notificationListenerActive = NotificationCaptureService.isEnabled(getApplication())
        val telegramConnected = app.telegramBotManager.isRunning.value

        _systemStatus.value = SystemStatus(
            isOperational = llmInitialized && notificationListenerActive,
            llmInitialized = llmInitialized,
            notificationListenerActive = notificationListenerActive,
            telegramConnected = telegramConnected
        )
    }

    private suspend fun loadRecentActivity() {
        // Load recent activity from database
        val activities = mutableListOf<ActivityItem>()

        // Example activities (replace with actual database queries)
        activities.add(
            ActivityItem(
                icon = Icons.Filled.Psychology,
                title = "Memory Consolidation",
                description = "Processed 47 notifications and stored 12 new memories",
                timestamp = LocalDateTime.now().minusHours(2),
                color = InfoBlue
            )
        )

        activities.add(
            ActivityItem(
                icon = Icons.Filled.Chat,
                title = "Conversation",
                description = "Had a conversation about your workout routine",
                timestamp = LocalDateTime.now().minusHours(5),
                color = Primary
            )
        )

        activities.add(
            ActivityItem(
                icon = Icons.Filled.Notifications,
                title = "Pattern Detected",
                description = "You usually message Mom on Sundays. Haven\'t heard from her today.",
                timestamp = LocalDateTime.now().minusHours(8),
                color = WarningOrange
            )
        )

        activities.add(
            ActivityItem(
                icon = Icons.Filled.FitnessCenter,
                title = "Routine Deviation",
                description = "Missed gym today. You usually go on Mondays.",
                timestamp = LocalDateTime.now().minusHours(18),
                color = CategoryWork
            )
        )

        _recentActivity.value = activities
    }

    /**
     * Refresh all data
     */
    fun refresh() {
        viewModelScope.launch {
            updateSystemStatus()
            app.thermalManager.updateReadings()
            loadRecentActivity()
        }
    }

    /**
     * Clear recent activity
     */
    fun clearActivity() {
        _recentActivity.value = emptyList()
    }
}