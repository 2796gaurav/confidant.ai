package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.FeedbackEntity

@Dao
interface FeedbackDao {
    
    @Insert
    suspend fun insert(feedback: FeedbackEntity): Long
    
    @Query("SELECT * FROM feedback ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 1000): List<FeedbackEntity>
    
    @Query("SELECT * FROM feedback WHERE messageId = :messageId")
    suspend fun getByMessageId(messageId: Long): FeedbackEntity?
    
    @Query("SELECT * FROM feedback WHERE feedbackType = :feedbackType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(feedbackType: String, limit: Int = 100): List<FeedbackEntity>
    
    @Query("SELECT * FROM feedback WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun getSince(sinceMillis: Long): List<FeedbackEntity>
    

    
    @Query("SELECT COUNT(*) FROM feedback WHERE feedbackType = :feedbackType")
    suspend fun countByType(feedbackType: String): Int
    
    @Query("SELECT AVG(responseTimeSeconds) FROM feedback WHERE responseTimeSeconds IS NOT NULL")
    suspend fun getAverageResponseTime(): Float?
    
    @Query("DELETE FROM feedback WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
}
