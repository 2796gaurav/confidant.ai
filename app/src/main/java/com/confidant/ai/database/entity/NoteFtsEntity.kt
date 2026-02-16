package com.confidant.ai.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * NoteFtsEntity - Full-Text Search virtual table for notes
 * 
 * This is a virtual FTS4 table for ultra-fast keyword search
 * (10-100x faster than LIKE queries)
 * 
 * FTS4 provides:
 * - Instant full-text search across title and content
 * - Automatic tokenization and indexing
 * - Prefix matching support
 * - Relevance ranking
 * 
 * Performance: ~1-5ms vs 100-500ms for LIKE queries
 * 
 * Note: Standalone FTS4 table (not linked to NoteEntity) for compatibility
 */
@Entity(tableName = "notes_fts")
@Fts4
data class NoteFtsEntity(
    val title: String,
    val content: String,
    val category: String,
    val tags: String
)
