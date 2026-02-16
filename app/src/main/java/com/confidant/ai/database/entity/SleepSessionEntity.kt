package com.confidant.ai.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sleepStart: Long,           // Unix timestamp (milliseconds)
    val sleepEnd: Long,             // Unix timestamp (milliseconds)
    val durationHours: Float,       // Total sleep duration
    val quality: String,            // POOR, FAIR, GOOD, EXCELLENT
    val interruptions: Int = 0,     // Number of wake-ups during sleep
    val createdAt: Long = System.currentTimeMillis()
)
