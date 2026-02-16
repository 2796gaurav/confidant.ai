package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.IntegrationConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntegrationConfigDao {
    
    @Query("SELECT * FROM integration_config WHERE integrationName = :name")
    suspend fun getConfig(name: String): IntegrationConfigEntity?
    
    @Query("SELECT * FROM integration_config WHERE isEnabled = 1 AND isActive = 1")
    suspend fun getActiveIntegrations(): List<IntegrationConfigEntity>
    
    @Query("SELECT * FROM integration_config")
    fun getAllConfigsFlow(): Flow<List<IntegrationConfigEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: IntegrationConfigEntity)
    
    @Update
    suspend fun update(config: IntegrationConfigEntity)
    
    @Query("UPDATE integration_config SET isEnabled = :enabled WHERE integrationName = :name")
    suspend fun setEnabled(name: String, enabled: Boolean)
    
    @Query("UPDATE integration_config SET accessToken = :token, isActive = 1, lastAuthenticated = :now WHERE integrationName = :name")
    suspend fun updateAccessToken(name: String, token: String, now: Long = System.currentTimeMillis())
}
