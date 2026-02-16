package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    
    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?
    
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfileFlow(): Flow<UserProfileEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)
    
    @Update
    suspend fun update(profile: UserProfileEntity)
    
    @Query("UPDATE user_profile SET interests = :interests, updatedAt = :now WHERE id = 1")
    suspend fun updateInterests(interests: String, now: Long = System.currentTimeMillis())
}
