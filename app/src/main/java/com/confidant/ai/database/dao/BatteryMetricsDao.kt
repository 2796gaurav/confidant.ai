package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.BatteryMetricsEntity

/**
 * BatteryMetricsDao - Data access object for battery usage metrics
 */
@Dao
interface BatteryMetricsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metrics: BatteryMetricsEntity): Long
    
    @Query("SELECT * FROM battery_metrics WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getMetricsBetween(startDate: Long, endDate: Long): List<BatteryMetricsEntity>
    
    @Query("SELECT * FROM battery_metrics ORDER BY date DESC LIMIT 30")
    suspend fun getLast30Days(): List<BatteryMetricsEntity>
    
    @Query("SELECT * FROM battery_metrics WHERE date = :date LIMIT 1")
    suspend fun getMetricsForDate(date: Long): BatteryMetricsEntity?
    
    @Query("DELETE FROM battery_metrics WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: Long)
    
    @Query("SELECT COUNT(*) FROM battery_metrics")
    suspend fun getCount(): Int
    
    @Query("SELECT AVG(wakeLockDurationMs) FROM battery_metrics WHERE date >= :startDate")
    suspend fun getAverageWakeLockDuration(startDate: Long): Long?
}
