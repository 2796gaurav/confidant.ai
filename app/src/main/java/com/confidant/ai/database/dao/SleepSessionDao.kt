package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.SleepSessionEntity

@Dao
interface SleepSessionDao {
    
    @Insert
    suspend fun insert(session: SleepSessionEntity): Long
    
    @Query("SELECT * FROM sleep_sessions ORDER BY sleepStart DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<SleepSessionEntity>
    
    @Query("SELECT * FROM sleep_sessions WHERE sleepStart >= :sinceMillis ORDER BY sleepStart DESC")
    suspend fun getSince(sinceMillis: Long): List<SleepSessionEntity>
    
    @Query("SELECT * FROM sleep_sessions WHERE sleepStart >= :startMillis AND sleepEnd <= :endMillis")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<SleepSessionEntity>
    
    @Query("SELECT * FROM sleep_sessions WHERE sleepStart >= :thirtyDaysAgo ORDER BY sleepStart DESC")
    suspend fun getLast30Days(thirtyDaysAgo: Long = System.currentTimeMillis() - 30L * 24 * 3600 * 1000): List<SleepSessionEntity>
    
    @Query("SELECT AVG(durationHours) FROM sleep_sessions WHERE sleepStart >= :sinceMillis")
    suspend fun getAverageDuration(sinceMillis: Long): Float?
    
    @Query("SELECT * FROM sleep_sessions WHERE quality = :quality ORDER BY sleepStart DESC LIMIT :limit")
    suspend fun getByQuality(quality: String, limit: Int = 10): List<SleepSessionEntity>
    
    @Query("DELETE FROM sleep_sessions WHERE sleepStart < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
    
    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun count(): Int
    
    @Query("SELECT * FROM sleep_sessions ORDER BY sleepStart DESC")
    suspend fun getAll(): List<SleepSessionEntity>
    
    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()
}
