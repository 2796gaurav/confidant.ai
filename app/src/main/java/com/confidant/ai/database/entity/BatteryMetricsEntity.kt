package com.confidant.ai.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * BatteryMetricsEntity - Tracks daily battery usage metrics for Google Play compliance
 * 
 * Google Play 2026 Policy:
 * - Apps with wake lock >2 hours/day will be flagged
 * - This entity helps monitor and ensure compliance
 */
@Entity(tableName = "battery_metrics")
data class BatteryMetricsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Date in epoch day format (LocalDate.toEpochDay())
    val date: Long,
    
    // Wake lock usage in milliseconds
    val wakeLockDurationMs: Long,
    
    // Foreground service runtime in milliseconds
    val foregroundServiceDurationMs: Long,
    
    // LLM inference statistics
    val llmInferenceCount: Int,
    val llmInferenceDurationMs: Long,
    
    // Notification statistics
    val notificationCount: Int,
    val proactiveMessageCount: Int,
    
    // Thermal throttling events
    val thermalThrottleCount: Int,
    
    // Timestamp when metrics were recorded
    val timestamp: Long = System.currentTimeMillis()
)
