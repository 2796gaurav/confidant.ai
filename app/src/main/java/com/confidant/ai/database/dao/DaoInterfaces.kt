package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Notification Data Access Object
 */
@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NotificationEntity?
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNotifications(limit: Int = 100): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Instant): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun getNotificationsSince(sinceMillis: Long): List<NotificationEntity>
    
    /**
     * Get unprocessed notifications (not yet analyzed by proactive system)
     * Retention: 10 days
     */
    @Query("SELECT * FROM notifications WHERE isProcessed = 0 AND timestamp >= :tenDaysAgo ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getUnprocessed(limit: Int = 100, tenDaysAgo: Instant = Instant.now().minusSeconds(10 * 24 * 3600L)): List<NotificationEntity>
    
    /**
     * Get notifications for proactive processing (not yet processed, within retention period)
     */
    @Query("SELECT * FROM notifications WHERE isProcessed = 0 AND timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getNotificationsForProcessing(since: Instant, limit: Int = 30): List<NotificationEntity>
    
    /**
     * Mark notification as processed by proactive system
     */
    @Query("UPDATE notifications SET isProcessed = 1, processedAt = :processedAt WHERE id = :id")
    suspend fun markAsProcessed(id: Long, processedAt: Long = System.currentTimeMillis())
    
    /**
     * Delete notifications older than 10 days (retention policy)
     */
    @Query("DELETE FROM notifications WHERE timestamp < :tenDaysAgo")
    suspend fun deleteOldNotifications(tenDaysAgo: Instant)
    
    @Query("SELECT COUNT(*) FROM notifications WHERE timestamp > :since")
    suspend fun countSince(since: Instant): Int
    
    @Query("SELECT COUNT(*) FROM notifications WHERE date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun countToday(): Int
    
    @Query("SELECT * FROM notifications WHERE text LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE (text LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') AND timestamp >= :sinceMillis ORDER BY timestamp DESC LIMIT 10")
    suspend fun searchNotifications(query: String, hours: Int): List<NotificationEntity> {
        val sinceMillis = System.currentTimeMillis() - (hours * 3600 * 1000L)
        return searchNotificationsSince(query, sinceMillis)
    }
    
    @Query("SELECT * FROM notifications WHERE (text LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') AND timestamp >= :sinceMillis ORDER BY timestamp DESC LIMIT 10")
    suspend fun searchNotificationsSince(query: String, sinceMillis: Long): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByCategory(category: String, limit: Int = 50): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE category = 'calendar' AND timestamp BETWEEN :fromMillis AND :toMillis ORDER BY timestamp ASC")
    suspend fun getCalendarNotifications(fromMillis: Long, toMillis: Long): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getNotificationsBetween(start: Long, end: Long): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastNotification(): NotificationEntity?
    
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(notification: NotificationEntity): Long
    
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(notifications: List<NotificationEntity>)
    
    @Update
    suspend fun update(notification: NotificationEntity)
    
    @Query("UPDATE notifications SET isProcessed = 1 WHERE id IN (:ids)")
    suspend fun markProcessed(ids: List<Long>)
    
    @Query("UPDATE notifications SET isProcessed = 1, processedAt = :processedAt WHERE id IN (:ids)")
    suspend fun markBatchProcessed(ids: List<Long>, processedAt: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOld(before: Instant)
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAll(): List<NotificationEntity>
    
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}



/**
 * Conversation Data Access Object
 * Retention: 10 days
 */
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationEntity?
    
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentForSession(sessionId: String = "default", limit: Int = 50): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getMessagesSince(since: Long): List<ConversationEntity>
    
    @Insert
    suspend fun insert(message: ConversationEntity): Long
    
    /**
     * Delete conversations older than 10 days (retention policy)
     */
    @Query("DELETE FROM conversations WHERE timestamp < :before")
    suspend fun deleteOld(before: Instant)
    
    /**
     * Get last conversation timestamp to check inactivity
     */
    @Query("SELECT timestamp FROM conversations ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastConversationTimestamp(): Instant?
    
    @Query("SELECT * FROM conversations WHERE isProactive = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastProactive(): ConversationEntity?
    
    @Query("SELECT COUNT(*) FROM conversations WHERE isProactive = 1 AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun countProactiveToday(): Int
    
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    suspend fun getAll(): List<ConversationEntity>
    
    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

/**
 * Core Memory Data Access Object
 */
@Dao
interface CoreMemoryDao {
    @Query("SELECT * FROM core_memory WHERE category = :category")
    suspend fun getByCategory(category: String): List<CoreMemoryEntity>
    
    @Query("SELECT * FROM core_memory WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): CoreMemoryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(memory: CoreMemoryEntity)
    
    @Delete
    suspend fun delete(memory: CoreMemoryEntity)
    
    @Query("SELECT * FROM core_memory")
    suspend fun getAll(): List<CoreMemoryEntity>
    
    @Query("DELETE FROM core_memory")
    suspend fun deleteAll()
}


