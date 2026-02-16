package com.confidant.ai.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.confidant.ai.database.Converters
import java.time.Instant

/**
 * NoteEntity - User notes with semantic search support
 * 
 * Features:
 * - Full-text search via title/content
 * - Semantic search via embeddings
 * - Categorization and tagging
 * - Archive support
 * - Timestamps for sorting
 */
@Entity(tableName = "notes")
@TypeConverters(Converters::class)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,
    val content: String,
    val tags: String,  // JSON array: ["work", "important", "todo"]
    val category: String,  // personal, work, ideas, reminders, etc.
    
    val createdAt: Instant,
    val updatedAt: Instant,
    
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    
    // Semantic search support (384-dim MiniLM embeddings)
    val embedding: FloatArray? = null,
    
    // Optional metadata
    val reminder: Instant? = null,  // For reminder notes
    val priority: Int = 0,  // 0=normal, 1=high, 2=urgent
    val color: String? = null  // For UI categorization
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoteEntity

        if (id != other.id) return false
        if (title != other.title) return false
        if (content != other.content) return false
        if (tags != other.tags) return false
        if (category != other.category) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (isArchived != other.isArchived) return false
        if (isPinned != other.isPinned) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (reminder != other.reminder) return false
        if (priority != other.priority) return false
        if (color != other.color) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + isArchived.hashCode()
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (reminder?.hashCode() ?: 0)
        result = 31 * result + priority
        result = 31 * result + (color?.hashCode() ?: 0)
        return result
    }
}
