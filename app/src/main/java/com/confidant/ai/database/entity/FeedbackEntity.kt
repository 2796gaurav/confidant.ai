package com.confidant.ai.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long,                // Reference to proactive message
    val feedbackType: String,           // POSITIVE, NEGATIVE, IGNORED, NEUTRAL
    val userResponse: String? = null,   // User's actual response text
    val responseTimeSeconds: Int? = null, // Time to respond in seconds
    val timestamp: Long = System.currentTimeMillis()
)
