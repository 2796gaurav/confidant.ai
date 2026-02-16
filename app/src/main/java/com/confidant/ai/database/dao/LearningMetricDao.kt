package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.LearningMetricEntity

@Dao
interface LearningMetricDao {
    
    @Insert
    suspend fun insert(metric: LearningMetricEntity): Long
    
    @Query("SELECT * FROM learning_metrics WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByCategory(category: String, limit: Int = 100): List<LearningMetricEntity>
    
    @Query("SELECT * FROM learning_metrics WHERE metricType = :metricType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByMetricType(metricType: String, limit: Int = 100): List<LearningMetricEntity>
    
    @Query("SELECT * FROM learning_metrics WHERE category = :category AND metricType = :metricType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByCategoryAndType(category: String, metricType: String, limit: Int = 50): List<LearningMetricEntity>
    
    @Query("SELECT * FROM learning_metrics WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun getSince(sinceMillis: Long): List<LearningMetricEntity>
    
    @Query("SELECT AVG(value) FROM learning_metrics WHERE category = :category AND metricType = :metricType")
    suspend fun getAverageValue(category: String, metricType: String): Float?
    
    @Query("DELETE FROM learning_metrics WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
    
    @Query("SELECT COUNT(*) FROM learning_metrics")
    suspend fun count(): Int
}
