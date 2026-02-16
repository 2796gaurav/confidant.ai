package com.confidant.ai.notes

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.database.entity.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ProactiveNoteRetrieval - Allows the bot to internally fetch relevant notes
 * 
 * This component enables the AI to:
 * 1. Automatically search notes when conversation context suggests relevance
 * 2. Retrieve notes without explicit user request
 * 3. Use notes to enhance responses with saved information
 * 4. Provide context-aware suggestions based on saved notes
 * 
 * Example scenarios:
 * - User: "What's my wifi password?" -> Bot internally searches for wifi/password notes
 * - User: "When is my dentist appointment?" -> Bot searches for dentist/appointment notes
 * - User: "What did I save about that project?" -> Bot searches for project-related notes
 */
class ProactiveNoteRetrieval(
    private val context: Context,
    private val database: AppDatabase
) {
    
    private val notesManager = NotesManager(context, database)
    private val smartRetrieval = SmartNoteRetrieval(
        context,
        database,
        com.confidant.ai.ConfidantApplication.instance.llmEngine
    )
    
    suspend fun initialize() {
        notesManager.initialize()
        smartRetrieval.initialize()
    }
    
    /**
     * Analyze user query and automatically retrieve relevant notes
     * Returns notes if relevant, null if no notes needed
     */
    suspend fun analyzeAndRetrieve(
        userQuery: String,
        conversationHistory: List<String> = emptyList()
    ): RetrievalResult? = withContext(Dispatchers.IO) {
        try {
            // Check if query suggests note retrieval
            val relevance = assessRelevance(userQuery)
            
            if (relevance == RelevanceLevel.NONE) {
                Log.d(TAG, "Query not relevant for note retrieval")
                return@withContext null
            }
            
            Log.i(TAG, "Query relevant for notes (level: $relevance), searching...")
            
            // Extract search keywords
            val keywords = extractSearchKeywords(userQuery, conversationHistory)
            
            // Perform search based on relevance level
            val notes = when (relevance) {
                RelevanceLevel.HIGH -> {
                    // Use hybrid search for high relevance
                    val result = smartRetrieval.smartSearch(keywords, limit = 5)
                    result.getOrNull()?.notes?.map { it.note } ?: emptyList()
                }
                RelevanceLevel.MEDIUM -> {
                    // Use semantic search for medium relevance
                    val result = notesManager.semanticSearch(keywords, limit = 3, threshold = 0.5f)
                    result.getOrNull() ?: emptyList()
                }
                RelevanceLevel.LOW -> {
                    // Use keyword search for low relevance
                    val result = notesManager.searchNotes(keywords, limit = 2)
                    result.getOrNull() ?: emptyList()
                }
                RelevanceLevel.NONE -> emptyList()
            }
            
            if (notes.isEmpty()) {
                Log.d(TAG, "No relevant notes found")
                return@withContext null
            }
            
            Log.i(TAG, "Found ${notes.size} relevant notes")
            
            RetrievalResult(
                notes = notes,
                relevanceLevel = relevance,
                searchQuery = keywords,
                confidence = calculateConfidence(notes, keywords)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Proactive retrieval failed", e)
            null
        }
    }
    
    /**
     * Assess how relevant the query is for note retrieval
     */
    private fun assessRelevance(query: String): RelevanceLevel {
        val lower = query.lowercase()
        
        // HIGH relevance - explicit note/memory requests
        val highPatterns = listOf(
            Regex("\\b(what did i save|what did i note|my notes|saved|remember|noted)\\b"),
            Regex("\\b(password|login|credential|pin|code)\\b"),
            Regex("\\b(when is|what time|appointment|reminder|schedule)\\b"),
            Regex("\\b(find|search|look up|retrieve|get)\\b.*\\b(note|saved|stored)\\b")
        )
        
        if (highPatterns.any { it.containsMatchIn(lower) }) {
            return RelevanceLevel.HIGH
        }
        
        // MEDIUM relevance - questions about specific information
        val mediumPatterns = listOf(
            Regex("\\b(what's|what is|where is|how do i)\\b"),
            Regex("\\b(tell me about|info on|information about)\\b"),
            Regex("\\b(my|our)\\b.*\\b(address|phone|email|account)\\b")
        )
        
        if (mediumPatterns.any { it.containsMatchIn(lower) }) {
            return RelevanceLevel.MEDIUM
        }
        
        // LOW relevance - general questions that might have saved context
        val lowPatterns = listOf(
            Regex("\\b(how|why|when|where|who)\\b"),
            Regex("\\b(explain|describe|tell)\\b")
        )
        
        if (lowPatterns.any { it.containsMatchIn(lower) }) {
            return RelevanceLevel.LOW
        }
        
        return RelevanceLevel.NONE
    }
    
    /**
     * Extract search keywords from query and conversation history
     */
    private fun extractSearchKeywords(
        query: String,
        conversationHistory: List<String>
    ): String {
        // Remove question words and common phrases
        val stopWords = setOf(
            "what", "when", "where", "who", "why", "how", "is", "are", "was", "were",
            "the", "a", "an", "my", "your", "our", "their", "this", "that",
            "did", "i", "save", "note", "remember", "tell", "me", "about"
        )
        
        val keywords = query.lowercase()
            .split(Regex("\\s+"))
            .filter { it !in stopWords && it.length > 2 }
            .joinToString(" ")
        
        // Add context from recent conversation if available
        val contextKeywords = conversationHistory.takeLast(2)
            .flatMap { it.lowercase().split(Regex("\\s+")) }
            .filter { it !in stopWords && it.length > 3 }
            .distinct()
            .take(3)
            .joinToString(" ")
        
        return if (contextKeywords.isNotEmpty()) {
            "$keywords $contextKeywords"
        } else {
            keywords
        }
    }
    
    /**
     * Calculate confidence score for retrieved notes
     */
    private fun calculateConfidence(notes: List<NoteEntity>, query: String): Float {
        if (notes.isEmpty()) return 0f
        
        val queryWords = query.lowercase().split(Regex("\\s+")).toSet()
        
        // Calculate average relevance score
        val scores = notes.map { note ->
            val noteWords = "${note.title} ${note.content}".lowercase().split(Regex("\\s+")).toSet()
            val matchCount = queryWords.intersect(noteWords).size
            matchCount.toFloat() / queryWords.size
        }
        
        return scores.average().toFloat()
    }
    
    /**
     * Get recent notes for context (last 24 hours)
     */
    suspend fun getRecentContext(limit: Int = 5): List<NoteEntity> = withContext(Dispatchers.IO) {
        try {
            val yesterday = Instant.now().minus(24, ChronoUnit.HOURS)
            database.noteDao().getRecentNotes(limit)
                .filter { it.createdAt.isAfter(yesterday) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent context", e)
            emptyList()
        }
    }
    
    /**
     * Get high priority notes that might need attention
     */
    suspend fun getHighPriorityContext(): List<NoteEntity> = withContext(Dispatchers.IO) {
        try {
            database.noteDao().getHighPriorityNotes(minPriority = 1, limit = 3)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get high priority context", e)
            emptyList()
        }
    }
    
    /**
     * Get upcoming reminders for proactive suggestions
     */
    suspend fun getUpcomingReminders(): List<NoteEntity> = withContext(Dispatchers.IO) {
        try {
            database.noteDao().getUpcomingReminders(Instant.now())
                .filter { 
                    // Only reminders within next 24 hours
                    it.reminder?.let { reminderTime ->
                        val hoursUntil = ChronoUnit.HOURS.between(Instant.now(), reminderTime)
                        hoursUntil in 0..24
                    } ?: false
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get upcoming reminders", e)
            emptyList()
        }
    }
    
    /**
     * Format notes for inclusion in bot response
     */
    fun formatNotesForResponse(result: RetrievalResult): String {
        return buildString {
            appendLine("📝 I found ${result.notes.size} relevant note(s):")
            appendLine()
            
            result.notes.forEachIndexed { index, note ->
                appendLine("${index + 1}. ${note.title}")
                appendLine("   ${note.content.take(150)}${if (note.content.length > 150) "..." else ""}")
                if (note.reminder != null) {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")
                        .withZone(java.time.ZoneId.systemDefault())
                    appendLine("   ⏰ Reminder: ${formatter.format(note.reminder)}")
                }
                appendLine()
            }
        }
    }
    
    companion object {
        private const val TAG = "ProactiveNoteRetrieval"
    }
}

/**
 * Relevance level for note retrieval
 */
enum class RelevanceLevel {
    NONE,    // No notes needed
    LOW,     // Might be helpful
    MEDIUM,  // Likely helpful
    HIGH     // Definitely needed
}

/**
 * Result of proactive note retrieval
 */
data class RetrievalResult(
    val notes: List<NoteEntity>,
    val relevanceLevel: RelevanceLevel,
    val searchQuery: String,
    val confidence: Float
)
