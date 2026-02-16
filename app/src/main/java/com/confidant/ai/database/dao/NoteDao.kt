package com.confidant.ai.database.dao

import androidx.room.*
import com.confidant.ai.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * NoteDao - Data access for notes
 */
@Dao
interface NoteDao {
    
    @Insert
    suspend fun insert(note: NoteEntity): Long
    
    @Update
    suspend fun update(note: NoteEntity)
    
    @Delete
    suspend fun delete(note: NoteEntity)
    
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?
    
    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllActive(): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit")
    suspend fun getRecentNotes(limit: Int = 20): List<NoteEntity>
    
    @Query("SELECT * FROM notes WHERE category = :category AND isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getNotesByCategory(category: String): List<NoteEntity>
    
    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY updatedAt DESC")
    suspend fun getArchivedNotes(): List<NoteEntity>
    
    @Query("SELECT * FROM notes WHERE isPinned = 1 AND isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getPinnedNotes(): List<NoteEntity>
    
    /**
     * Full-text search in title and content - OPTIMIZED with FTS4
     * Uses FTS virtual table for 10-100x faster search
     */
    @Query("""
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :query
        AND notes.isArchived = 0
        ORDER BY 
            CASE WHEN notes.title LIKE '%' || :query || '%' THEN 1 ELSE 2 END,
            notes.isPinned DESC,
            notes.priority DESC,
            notes.updatedAt DESC
        LIMIT :limit
    """)
    suspend fun searchNotesFts(query: String, limit: Int = 20): List<NoteEntity>
    
    /**
     * Full-text search in title and content - FALLBACK (no FTS)
     * Used if FTS table is not available
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isArchived = 0 
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY 
            CASE WHEN title LIKE '%' || :query || '%' THEN 1 ELSE 2 END,
            isPinned DESC,
            priority DESC,
            updatedAt DESC
        LIMIT :limit
    """)
    suspend fun searchNotesLike(query: String, limit: Int = 20): List<NoteEntity>
    
    /**
     * Smart search - tries FTS first, falls back to LIKE
     */
    suspend fun searchNotes(query: String, limit: Int = 20): List<NoteEntity> {
        return try {
            // Try FTS first (10-100x faster)
            searchNotesFts(query, limit)
        } catch (e: Exception) {
            // Fallback to LIKE if FTS not available
            searchNotesLike(query, limit)
        }
    }
    
    /**
     * Search by tags
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isArchived = 0 
        AND tags LIKE '%' || :tag || '%'
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun searchByTag(tag: String, limit: Int = 20): List<NoteEntity>
    
    /**
     * Get notes with reminders
     */
    @Query("""
        SELECT * FROM notes 
        WHERE reminder IS NOT NULL 
        AND reminder > :now
        AND isArchived = 0
        ORDER BY reminder ASC
    """)
    suspend fun getUpcomingReminders(now: Instant): List<NoteEntity>
    
    /**
     * Get notes by priority
     */
    @Query("""
        SELECT * FROM notes 
        WHERE priority >= :minPriority 
        AND isArchived = 0
        ORDER BY priority DESC, updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getHighPriorityNotes(minPriority: Int = 1, limit: Int = 20): List<NoteEntity>
    
    /**
     * Get all notes with embeddings (for semantic search)
     */
    @Query("SELECT * FROM notes WHERE embedding IS NOT NULL AND isArchived = 0")
    suspend fun getNotesWithEmbeddings(): List<NoteEntity>
    
    /**
     * Archive a note
     */
    @Query("UPDATE notes SET isArchived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archiveNote(id: Long, now: Instant)
    
    /**
     * Unarchive a note
     */
    @Query("UPDATE notes SET isArchived = 0, updatedAt = :now WHERE id = :id")
    suspend fun unarchiveNote(id: Long, now: Instant)
    
    /**
     * Pin/unpin a note
     */
    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Instant)
    
    /**
     * Delete old archived notes
     */
    @Query("DELETE FROM notes WHERE isArchived = 1 AND updatedAt < :before")
    suspend fun deleteOldArchivedNotes(before: Instant)
    
    /**
     * Get note count
     */
    @Query("SELECT COUNT(*) FROM notes WHERE isArchived = 0")
    suspend fun getActiveNoteCount(): Int
    
    /**
     * Get distinct categories
     */
    @Query("SELECT DISTINCT category FROM notes WHERE isArchived = 0 ORDER BY category")
    suspend fun getCategories(): List<String>
}
