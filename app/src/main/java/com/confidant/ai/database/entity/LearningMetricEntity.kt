package com.confidant.ai.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_metrics")
data class LearningMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,               // Category this metric applies to
    val metricType: String,             // Type of metric (motivation_adjustment, optimal_hour, etc.)
    val value: Float,                   // Numeric value of the metric
    val context: String? = null,        // Additional context (JSON or string)
    val timestamp: Long = System.currentTimeMillis()
)
