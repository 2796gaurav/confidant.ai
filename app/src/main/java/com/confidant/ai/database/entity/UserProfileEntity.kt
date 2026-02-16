package com.confidant.ai.database.entity

import androidx.room.*
import java.time.Instant

/**
 * UserProfileEntity - User profile for personalization
 * Privacy-first: Only stores interests, no names or gender data
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1, // Single row table
    
    // User interests (stored as JSON array string)
    val interests: String = "[]", // ["technology", "finance", "health", ...]
    
    // Additional profile data (optional)
    val age: Int? = null,
    val occupation: String? = null,
    val location: String? = null,
    
    // Metadata
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * IntegrationConfigEntity - Stores integration credentials and status
 */
@Entity(tableName = "integration_config")
data class IntegrationConfigEntity(
    @PrimaryKey
    val integrationName: String, // "telegram", "news_search"
    
    val isEnabled: Boolean = false,
    val isActive: Boolean = false, // Has valid credentials
    
    // Credentials (encrypted in production)
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    
    // Metadata
    val lastAuthenticated: Instant? = null,
    val tokenExpiresAt: Instant? = null,
    val updatedAt: Instant = Instant.now()
)

/**
 * ToolExecutionLogEntity - Logs all tool/function executions
 * Used for debugging and user transparency
 */
@Entity(tableName = "tool_execution_log")
data class ToolExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val toolName: String,
    val arguments: String, // JSON string
    val result: String, // JSON string or error message
    val success: Boolean,
    val executionTimeMs: Long,
    
    // Context
    val conversationId: Long? = null,
    val userQuery: String? = null,
    
    val timestamp: Instant = Instant.now()
)
