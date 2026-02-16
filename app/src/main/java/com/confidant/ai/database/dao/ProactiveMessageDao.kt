package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.ProactiveMessageEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * DAO for proactive messages
 * Stores LLM decisions about notifications and proactive messages
 */
@Dao
interface ProactiveMessageDao {
    
    @Insert
    suspend fun insert(message: ProactiveMessageEntity): Long
    
    @Query("SELECT * FROM proactive_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<ProactiveMessageEntity>
    
    @Query("SELECT * FROM proactive_messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): ProactiveMessageEntity?
    
    /**
     * Get proactive messages that should be triggered (confidence threshold met)
     */
    @Query("SELECT * FROM proactive_messages WHERE shouldTrigger = 1 AND wasSent = 0 ORDER BY confidence DESC, timestamp DESC LIMIT :limit")
    suspend fun getPendingMessages(limit: Int = 10): List<ProactiveMessageEntity>
    
    /**
     * Get proactive messages for a specific notification
     */
    @Query("SELECT * FROM proactive_messages WHERE notificationId = :notificationId LIMIT 1")
    suspend fun getByNotificationId(notificationId: Long): ProactiveMessageEntity?
    
    /**
     * Count LLM calls made in the last hour
     */
    @Query("SELECT COUNT(*) FROM proactive_messages WHERE timestamp >= :sinceHour")
    suspend fun countCallsSince(sinceHour: Instant): Int
    
    /**
     * Get notifications that have been processed (have proactive message entry)
     */
    @Query("SELECT DISTINCT notificationId FROM proactive_messages WHERE notificationId IS NOT NULL")
    suspend fun getProcessedNotificationIds(): List<Long>
    
    /**
     * Mark message as sent
     */
    @Query("UPDATE proactive_messages SET wasSent = 1 WHERE id = :id")
    suspend fun markAsSent(id: Long)
    
    /**
     * Update feedback for a message
     */
    @Query("UPDATE proactive_messages SET wasResponded = 1, responseTimeMinutes = :responseTime WHERE id = :id")
    suspend fun updateFeedback(id: Long, responseTime: Int)
    
    @Query("SELECT COUNT(*) FROM proactive_messages WHERE timestamp >= :startOfDay")
    suspend fun countSince(startOfDay: Long): Int
    
    @Query("SELECT * FROM proactive_messages WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getMessagesSince(since: Long): List<ProactiveMessageEntity>
    
    @Query("SELECT COUNT(*) FROM proactive_messages")
    suspend fun count(): Int
    
    @Query("SELECT * FROM proactive_messages ORDER BY timestamp DESC")
    suspend fun getAll(): List<ProactiveMessageEntity>
    
    @Query("SELECT * FROM proactive_messages WHERE id = :id")
    suspend fun getById(id: Long): ProactiveMessageEntity?
}
