package com.confidant.ai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.integrations.NotesTool
import com.confidant.ai.notes.NotesManager
import com.confidant.ai.notes.ProactiveNoteRetrieval
import com.confidant.ai.notes.SmartNoteRetrieval
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Integration tests for notes system on real Android device
 */
@RunWith(AndroidJUnit4::class)
class NotesIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var notesManager: NotesManager
    private lateinit var smartRetrieval: SmartNoteRetrieval
    private lateinit var proactiveRetrieval: ProactiveNoteRetrieval
    private lateinit var notesTool: NotesTool
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.getDatabase(context)
        notesManager = NotesManager(context, database)
        smartRetrieval = SmartNoteRetrieval(
            context,
            database,
            ConfidantApplication.instance.llmEngine
        )
        proactiveRetrieval = ProactiveNoteRetrieval(context, database)
        notesTool = NotesTool(context)
        
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
    
    @Test
    fun testEndToEndNoteSaving() = runBlocking {
        // Simulate user saving note via tool
        val result = notesTool.execute(
            toolName = "save_note",
            arguments = mapOf(
                "content" to "my wifi password is testpass123"
            )
        )
        
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertTrue(response.contains("saved successfully"))
    }
    
    @Test
    fun testEndToEndNoteRetrieval() = runBlocking {
        // Save note
        notesManager.quickSave("my important wifi password is abc123")
        
        // Retrieve via tool
        val result = notesTool.execute(
            toolName = "retrieve_notes",
            arguments = mapOf(
                "query" to "wifi password"
            )
        )
        
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertTrue(response.contains("abc123"))
    }
    
    @Test
    fun testProactiveRetrievalRealDevice() = runBlocking {
        // Save test notes
        notesManager.quickSave("home wifi password is secret123")
        notesManager.quickSave("work email login credentials")
        
        // Proactive retrieval
        val result = proactiveRetrieval.analyzeAndRetrieve("What's my wifi password?")
        
        assertTrue(result != null)
        assertTrue(result!!.notes.isNotEmpty())
    }
    
    @Test
    fun testHybridSearchRealDevice() = runBlocking {
        // Save multiple notes
        repeat(10) { i ->
            notesManager.quickSave("test note $i with password and work content")
        }
        
        // Hybrid search
        val result = smartRetrieval.smartSearch("password work", limit = 5)
        
        assertTrue(result.isSuccess)
        val searchResult = result.getOrNull()!!
        assertTrue(searchResult.notes.isNotEmpty())
    }
    
    @Test
    fun testKeywordSearchAccuracy() = runBlocking {
        // Save notes with specific keywords
        notesManager.quickSave("my wifi network password is abc123")
        notesManager.quickSave("login credentials for email")
        notesManager.quickSave("bank account details")
        
        // BM25 keyword search
        val result = smartRetrieval.smartSearch("password credentials", limit = 5)
        
        assertTrue(result.isSuccess)
        val searchResult = result.getOrNull()!!
        assertTrue(searchResult.notes.isNotEmpty())
        // Should find notes with matching keywords
        assertTrue(searchResult.notes.any { it.content.contains("password") || it.content.contains("credentials") })
    }
}
