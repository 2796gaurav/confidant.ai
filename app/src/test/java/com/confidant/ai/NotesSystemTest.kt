package com.confidant.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.notes.NotesManager
import com.confidant.ai.notes.SmartNoteRetrieval
import com.confidant.ai.notes.ProactiveNoteRetrieval
import com.confidant.ai.notes.RelevanceLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Comprehensive tests for the enhanced notes system
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotesSystemTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var notesManager: NotesManager
    private lateinit var smartRetrieval: SmartNoteRetrieval
    private lateinit var proactiveRetrieval: ProactiveNoteRetrieval
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        notesManager = NotesManager(context, database)
        smartRetrieval = SmartNoteRetrieval(
            context,
            database,
            ConfidantApplication.instance.llmEngine
        )
        proactiveRetrieval = ProactiveNoteRetrieval(context, database)
        
        // Initialize
        runBlocking {
            notesManager.initialize()
            smartRetrieval.initialize()
            proactiveRetrieval.initialize()
        }
    }
    
    @After
    fun cleanup() {
        database.close()
    }
    
    // ========== AUTO-DETECTION TESTS ==========
    
    @Test
    fun `test auto-detect password category`() = runBlocking {
        val result = notesManager.quickSave("my wifi password is abc123")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals("passwords", note.category)
        assertTrue(note.tags.contains("password"))
    }
    
    @Test
    fun `test auto-detect work category`() = runBlocking {
        val result = notesManager.quickSave("meeting with client tomorrow at office")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals("work", note.category)
    }
    
    @Test
    fun `test auto-detect reminder category`() = runBlocking {
        val result = notesManager.quickSave("remind me about dentist appointment")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals("reminders", note.category)
        assertTrue(note.tags.contains("reminder"))
    }
    
    @Test
    fun `test auto-detect health category`() = runBlocking {
        val result = notesManager.quickSave("doctor appointment next week for checkup")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals("health", note.category)
    }
    
    @Test
    fun `test auto-detect shopping category`() = runBlocking {
        val result = notesManager.quickSave("need to buy groceries and milk")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals("shopping", note.category)
    }
    
    @Test
    fun `test auto-detect priority urgent`() = runBlocking {
        val result = notesManager.quickSave("urgent: submit report asap")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals(2, note.priority) // Urgent
    }
    
    @Test
    fun `test auto-detect priority high`() = runBlocking {
        val result = notesManager.quickSave("important meeting tomorrow")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals(1, note.priority) // High
    }
    
    @Test
    fun `test auto-generate title`() = runBlocking {
        val result = notesManager.quickSave("This is a test note with some content")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertTrue(note.title.isNotEmpty())
        assertTrue(note.title.contains("test note"))
    }
    
    @Test
    fun `test auto-extract multiple tags`() = runBlocking {
        val result = notesManager.quickSave("important work task: finish project by deadline")
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertTrue(note.tags.contains("important"))
        assertTrue(note.tags.contains("work"))
        assertTrue(note.tags.contains("todo"))
    }
    
    // ========== HYBRID SEARCH TESTS ==========
    
    @Test
    fun `test hybrid search finds notes`() = runBlocking {
        // Save test notes
        notesManager.quickSave("my wifi password is abc123")
        notesManager.quickSave("login credentials for work email")
        notesManager.quickSave("bank account password xyz789")
        
        // Search
        val result = smartRetrieval.smartSearch("password", limit = 10)
        assertTrue(result.isSuccess)
        
        val searchResult = result.getOrNull()!!
        assertTrue(searchResult.notes.isNotEmpty())
        assertTrue(searchResult.notes.size >= 2)
    }
    
    @Test
    fun `test hybrid search with typo`() = runBlocking {
        // Save test note
        notesManager.quickSave("dentist appointment next week")
        
        // Search with typo
        val result = smartRetrieval.smartSearch("dentst", limit = 10)
        assertTrue(result.isSuccess)
        
        val searchResult = result.getOrNull()!!
        // Fuzzy search should find it despite typo
        assertTrue(searchResult.notes.isNotEmpty())
    }
    
    @Test
    fun `test search by category`() = runBlocking {
        // Save notes in different categories
        notesManager.quickSave("my wifi password is abc123")
        notesManager.quickSave("meeting with client tomorrow")
        notesManager.quickSave("buy groceries")
        
        // Search work category
        val result = smartRetrieval.smartSearch("work", limit = 10)
        assertTrue(result.isSuccess)
        
        val searchResult = result.getOrNull()!!
        assertTrue(searchResult.notes.isNotEmpty())
    }
    
    @Test
    fun `test context-aware search`() = runBlocking {
        // Save test note
        notesManager.quickSave("home wifi network password is abc123")
        
        // Search with context
        val result = smartRetrieval.contextAwareSearch(
            query = "password",
            conversationContext = "home network wifi",
            limit = 10
        )
        assertTrue(result.isSuccess)
        
        val searchResult = result.getOrNull()!!
        assertTrue(searchResult.notes.isNotEmpty())
    }
    
    // ========== PROACTIVE RETRIEVAL TESTS ==========
    
    @Test
    fun `test proactive retrieval high relevance`() = runBlocking {
        // Save test note
        notesManager.quickSave("my wifi password is abc123")
        
        // Proactive retrieval with high relevance query
        val result = proactiveRetrieval.analyzeAndRetrieve("What's my wifi password?")
        
        assertNotNull(result)
        assertEquals(RelevanceLevel.HIGH, result!!.relevanceLevel)
        assertTrue(result.notes.isNotEmpty())
        assertTrue(result.confidence > 0.3f)
    }
    
    @Test
    fun `test proactive retrieval medium relevance`() = runBlocking {
        // Save test note
        notesManager.quickSave("dentist appointment next Tuesday at 3pm")
        
        // Proactive retrieval with medium relevance query
        val result = proactiveRetrieval.analyzeAndRetrieve("When is my dentist appointment?")
        
        assertNotNull(result)
        assertTrue(result!!.relevanceLevel in listOf(
            RelevanceLevel.HIGH,
            RelevanceLevel.MEDIUM
        ))
    }
    
    @Test
    fun `test proactive retrieval no relevance`() = runBlocking {
        // Save test note
        notesManager.quickSave("some random note")
        
        // Proactive retrieval with irrelevant query
        val result = proactiveRetrieval.analyzeAndRetrieve("What's the weather today?")
        
        // Should return null for irrelevant queries
        assertTrue(result == null || result.notes.isEmpty())
    }
    
    @Test
    fun `test proactive retrieval with conversation history`() = runBlocking {
        // Save test note
        notesManager.quickSave("home wifi password is abc123")
        
        // Proactive retrieval with conversation context
        val result = proactiveRetrieval.analyzeAndRetrieve(
            userQuery = "what's the password?",
            conversationHistory = listOf("talking about home network", "wifi setup")
        )
        
        assertNotNull(result)
        assertTrue(result.notes.isNotEmpty())
    }
    
    @Test
    fun `test get recent context`() = runBlocking {
        // Save recent notes
        notesManager.quickSave("note 1")
        notesManager.quickSave("note 2")
        notesManager.quickSave("note 3")
        
        // Get recent context
        val notes = proactiveRetrieval.getRecentContext(limit = 5)
        
        assertTrue(notes.isNotEmpty())
        assertTrue(notes.size <= 5)
    }
    
    @Test
    fun `test get high priority context`() = runBlocking {
        // Save high priority note
        notesManager.saveNote(
            title = "Urgent Task",
            content = "Complete urgent project",
            priority = 2
        )
        
        // Get high priority context
        val notes = proactiveRetrieval.getHighPriorityContext()
        
        assertTrue(notes.isNotEmpty())
        assertTrue(notes.all { it.priority >= 1 })
    }
    
    // ========== EDGE CASES ==========
    
    @Test
    fun `test save empty content fails gracefully`() = runBlocking {
        val result = notesManager.quickSave("")
        // Should still succeed but with default title
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `test save very long content`() = runBlocking {
        val longContent = "a".repeat(10000)
        val result = notesManager.quickSave(longContent)
        assertTrue(result.isSuccess)
        
        val noteId = result.getOrNull()!!
        val note = database.noteDao().getById(noteId)
        
        assertNotNull(note)
        assertEquals(longContent, note.content)
    }
    
    @Test
    fun `test search with no results`() = runBlocking {
        val result = smartRetrieval.smartSearch("nonexistent query xyz", limit = 10)
        assertTrue(result.isSuccess)
        
        val searchResult = result.getOrNull()!!
        assertTrue(searchResult.notes.isEmpty())
        assertTrue(searchResult.suggestions.isNotEmpty())
    }
    
    @Test
    fun `test update note`() = runBlocking {
        // Save note
        val saveResult = notesManager.quickSave("original content")
        assertTrue(saveResult.isSuccess)
        
        val noteId = saveResult.getOrNull()!!
        
        // Update note
        val updateResult = notesManager.updateNote(
            id = noteId,
            content = "updated content"
        )
        assertTrue(updateResult.isSuccess)
        
        // Verify update
        val note = database.noteDao().getById(noteId)
        assertNotNull(note)
        assertEquals("updated content", note.content)
    }
    
    @Test
    fun `test delete note`() = runBlocking {
        // Save note
        val saveResult = notesManager.quickSave("test note")
        assertTrue(saveResult.isSuccess)
        
        val noteId = saveResult.getOrNull()!!
        
        // Delete note
        val deleteResult = notesManager.deleteNote(noteId)
        assertTrue(deleteResult.isSuccess)
        
        // Verify deletion
        val note = database.noteDao().getById(noteId)
        assertTrue(note == null)
    }
    
    @Test
    fun `test archive note`() = runBlocking {
        // Save note
        val saveResult = notesManager.quickSave("test note")
        assertTrue(saveResult.isSuccess)
        
        val noteId = saveResult.getOrNull()!!
        
        // Archive note
        val archiveResult = notesManager.archiveNote(noteId)
        assertTrue(archiveResult.isSuccess)
        
        // Verify archive
        val note = database.noteDao().getById(noteId)
        assertNotNull(note)
        assertTrue(note.isArchived)
    }
    
    // ========== PERFORMANCE TESTS ==========
    
    @Test
    fun `test bulk save performance`() = runBlocking {
        val startTime = System.currentTimeMillis()
        
        // Save 100 notes
        repeat(100) { i ->
            notesManager.quickSave("test note $i with some content")
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Should complete in reasonable time (< 10 seconds)
        assertTrue(duration < 10000, "Bulk save took ${duration}ms")
    }
    
    @Test
    fun `test search performance`() = runBlocking {
        // Save test notes
        repeat(50) { i ->
            notesManager.quickSave("test note $i with password and work content")
        }
        
        val startTime = System.currentTimeMillis()
        
        // Perform search
        val result = smartRetrieval.smartSearch("password work", limit = 10)
        
        val duration = System.currentTimeMillis() - startTime
        
        assertTrue(result.isSuccess)
        // Search should be fast (< 2 seconds)
        assertTrue(duration < 2000, "Search took ${duration}ms")
    }
    
    // ========== INTEGRATION TESTS ==========
    
    @Test
    fun `test complete workflow - save and retrieve`() = runBlocking {
        // 1. Save note
        val saveResult = notesManager.quickSave("my important wifi password is secret123")
        assertTrue(saveResult.isSuccess)
        
        // 2. Search for it
        val searchResult = smartRetrieval.smartSearch("wifi password", limit = 10)
        assertTrue(searchResult.isSuccess)
        
        val notes = searchResult.getOrNull()!!.notes
        assertTrue(notes.isNotEmpty())
        
        // 3. Verify content
        val note = notes.first().note
        assertTrue(note.content.contains("secret123"))
        assertEquals("passwords", note.category)
    }
    
    @Test
    fun `test proactive workflow`() = runBlocking {
        // 1. Save multiple notes
        notesManager.quickSave("home wifi password is abc123")
        notesManager.quickSave("work email login is xyz789")
        notesManager.quickSave("bank account password is secret456")
        
        // 2. User asks question
        val result = proactiveRetrieval.analyzeAndRetrieve("What's my home wifi password?")
        
        // 3. Verify proactive retrieval
        assertNotNull(result)
        assertTrue(result.notes.isNotEmpty())
        
        // 4. Format for response
        val formatted = proactiveRetrieval.formatNotesForResponse(result)
        assertTrue(formatted.contains("abc123"))
    }
}
