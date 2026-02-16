package com.confidant.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.integrations.IntelligentToolManager
import com.confidant.ai.integrations.ToolIntent
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test suite for personalized query detection
 * 
 * Verifies that the LLM-based intent detection correctly identifies:
 * 1. Personalized queries (appointments, passwords, reminders) → RETRIEVE_NOTE
 * 2. General queries (news, prices, weather) → WEB_SEARCH
 * 3. Save requests (notes, reminders) → SAVE_NOTE
 * 4. Conversational (greetings, thanks) → NONE
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizedQueryTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var llmEngine: LLMEngine
    private lateinit var toolManager: IntelligentToolManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        llmEngine = LLMEngine(context)
        toolManager = IntelligentToolManager(
            context = context,
            database = database,
            llmEngine = llmEngine,
            statusCallback = { }
        )
    }
    
    @Test
    fun `test personalized appointment query`() = runBlocking {
        // User asking about their saved appointment
        val query = "when is my dentist appointment"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.RETRIEVE_NOTE) {
            "Expected RETRIEVE_NOTE for personalized appointment query, got $intent"
        }
    }
    
    @Test
    fun `test personalized password query`() = runBlocking {
        // User asking about their saved password
        val query = "what's my wifi password"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.RETRIEVE_NOTE) {
            "Expected RETRIEVE_NOTE for personalized password query, got $intent"
        }
    }
    
    @Test
    fun `test personalized reminder query`() = runBlocking {
        // User asking about their saved reminder
        val query = "remind me what i need to buy"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.RETRIEVE_NOTE) {
            "Expected RETRIEVE_NOTE for personalized reminder query, got $intent"
        }
    }
    
    @Test
    fun `test personalized call reminder query`() = runBlocking {
        // User asking about their saved call reminder
        val query = "when do i need to call my mom"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.RETRIEVE_NOTE) {
            "Expected RETRIEVE_NOTE for personalized call reminder, got $intent"
        }
    }
    
    @Test
    fun `test general weather query`() = runBlocking {
        // General information query (not personalized)
        val query = "what's the weather today"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.WEB_SEARCH) {
            "Expected WEB_SEARCH for general weather query, got $intent"
        }
    }
    
    @Test
    fun `test general price query`() = runBlocking {
        // General information query (not personalized)
        val query = "bitcoin price"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.WEB_SEARCH) {
            "Expected WEB_SEARCH for general price query, got $intent"
        }
    }
    
    @Test
    fun `test general news query`() = runBlocking {
        // General information query (not personalized)
        val query = "latest news"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.WEB_SEARCH) {
            "Expected WEB_SEARCH for general news query, got $intent"
        }
    }
    
    @Test
    fun `test save note request`() = runBlocking {
        // User wants to save information
        val query = "save note to call dentist tomorrow"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.SAVE_NOTE) {
            "Expected SAVE_NOTE for save request, got $intent"
        }
    }
    
    @Test
    fun `test remember request`() = runBlocking {
        // User wants to save information
        val query = "remember my password is abc123"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.SAVE_NOTE) {
            "Expected SAVE_NOTE for remember request, got $intent"
        }
    }
    
    @Test
    fun `test conversational greeting`() = runBlocking {
        // Simple greeting (no tool needed)
        val query = "hello"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == null) {
            "Expected null for conversational greeting, got $intent"
        }
    }
    
    @Test
    fun `test conversational thanks`() = runBlocking {
        // Simple acknowledgment (no tool needed)
        val query = "thanks"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == null) {
            "Expected null for conversational thanks, got $intent"
        }
    }
    
    @Test
    fun `test edge case - my favorite movie`() = runBlocking {
        // Edge case: "my" doesn't always mean personalized
        val query = "when is my favorite movie coming out"
        val intent = toolManager.detectToolIntent(query)
        
        // Should be WEB_SEARCH (general information about movie release)
        // Not RETRIEVE_NOTE (user hasn't saved this)
        assert(intent == ToolIntent.WEB_SEARCH) {
            "Expected WEB_SEARCH for general movie query, got $intent"
        }
    }
    
    @Test
    fun `test variation - appointment time`() = runBlocking {
        // Variation of appointment query
        val query = "what time is my dentist appointment"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.RETRIEVE_NOTE) {
            "Expected RETRIEVE_NOTE for appointment time query, got $intent"
        }
    }
    
    @Test
    fun `test variation - password retrieval`() = runBlocking {
        // Variation of password query
        val query = "can you tell me my wifi password"
        val intent = toolManager.detectToolIntent(query)
        
        assert(intent == ToolIntent.RETRIEVE_NOTE) {
            "Expected RETRIEVE_NOTE for password retrieval, got $intent"
        }
    }
    
    @Test
    fun `test typo handling`() = runBlocking {
        // Query with typo
        val query = "when is my dentst appointment"  // "dentst" instead of "dentist"
        val intent = toolManager.detectToolIntent(query)
        
        // Should still detect as RETRIEVE_NOTE (LLM handles typos)
        assert(intent == ToolIntent.RETRIEVE_NOTE) {
            "Expected RETRIEVE_NOTE even with typo, got $intent"
        }
    }
}
