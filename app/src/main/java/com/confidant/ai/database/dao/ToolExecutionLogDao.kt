package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.ToolExecutionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolExecutionLogDao {
    
    @Query("SELECT * FROM tool_execution_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentExecutions(limit: Int = 50): List<ToolExecutionLogEntity>
    
    @Query("SELECT * FROM tool_execution_log WHERE toolName = :toolName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getExecutionsByTool(toolName: String, limit: Int = 20): List<ToolExecutionLogEntity>
    
    @Query("SELECT * FROM tool_execution_log ORDER BY timestamp DESC LIMIT 100")
    fun getRecentExecutionsFlow(): Flow<List<ToolExecutionLogEntity>>
    
    @Insert
    suspend fun insert(log: ToolExecutionLogEntity)
    
    @Query("DELETE FROM tool_execution_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
    
    @Query("SELECT COUNT(*) FROM tool_execution_log WHERE toolName = :toolName AND success = 1")
    suspend fun getSuccessCount(toolName: String): Int
    
    @Query("SELECT COUNT(*) FROM tool_execution_log WHERE toolName = :toolName AND success = 0")
    suspend fun getFailureCount(toolName: String): Int
}
