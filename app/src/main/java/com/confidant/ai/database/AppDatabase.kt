package com.confidant.ai.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.confidant.ai.database.dao.*
import com.confidant.ai.database.entity.*

/**
 * Main application database - Simplified schema
 * Stores only: notifications, conversations, core_memory, proactive_messages, notes
 * 
 * Version 14: Simplified schema - removed analytics and unused tables
 * Retention: 10 days for notifications and conversations
 */
@Database(
    entities = [
        NotificationEntity::class,
        ConversationEntity::class,
        CoreMemoryEntity::class,
        ProactiveMessageEntity::class,
        NoteEntity::class,
        NoteFtsEntity::class  // FTS4 virtual table for fast search
    ],
    version = 14,  // Simplified schema
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun notificationDao(): NotificationDao
    abstract fun conversationDao(): ConversationDao
    abstract fun coreMemoryDao(): CoreMemoryDao
    abstract fun proactiveMessageDao(): ProactiveMessageDao
    abstract fun noteDao(): NoteDao
    
    companion object {
        private const val TAG = "AppDatabase"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Migration 13 → 14: Simplified schema - remove unused tables
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "Migrating database 13 → 14: Simplifying schema")
                
                try {
                    // Drop unused tables
                    database.execSQL("DROP TABLE IF EXISTS telegram_messages")
                    database.execSQL("DROP TABLE IF EXISTS financial_transactions")
                    database.execSQL("DROP TABLE IF EXISTS sleep_sessions")
                    database.execSQL("DROP TABLE IF EXISTS feedback")
                    database.execSQL("DROP TABLE IF EXISTS learning_metrics")
                    database.execSQL("DROP TABLE IF EXISTS user_profile")
                    database.execSQL("DROP TABLE IF EXISTS integration_config")
                    database.execSQL("DROP TABLE IF EXISTS tool_execution_log")
                    database.execSQL("DROP TABLE IF EXISTS battery_metrics")
                    
                    // Add new columns to conversations table
                    database.execSQL("ALTER TABLE conversations ADD COLUMN toolCalls TEXT DEFAULT '[]'")
                    database.execSQL("ALTER TABLE conversations ADD COLUMN toolResults TEXT")
                    
                    // Update proactive_messages table structure
                    database.execSQL("DROP TABLE IF EXISTS proactive_messages")
                    database.execSQL("""
                        CREATE TABLE proactive_messages (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            notificationId INTEGER,
                            thought TEXT NOT NULL,
                            confidence REAL NOT NULL,
                            shouldTrigger INTEGER NOT NULL DEFAULT 0,
                            message TEXT,
                            shouldSaveToNotes INTEGER NOT NULL DEFAULT 0,
                            noteContent TEXT,
                            wasSent INTEGER NOT NULL DEFAULT 0,
                            wasResponded INTEGER NOT NULL DEFAULT 0,
                            responseTimeMinutes INTEGER
                        )
                    """)
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_proactive_timestamp ON proactive_messages(timestamp DESC)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_proactive_confidence ON proactive_messages(confidence DESC)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_proactive_notification ON proactive_messages(notificationId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_proactive_trigger ON proactive_messages(shouldTrigger)")
                    
                    Log.i(TAG, "✅ Migration 13 → 14 complete: Schema simplified")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Migration 13 → 14 failed", e)
                    throw e
                }
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "confidant_database"
                )
                    .addMigrations(MIGRATION_13_14)
                    .fallbackToDestructiveMigration()  // Last resort if migrations fail
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}