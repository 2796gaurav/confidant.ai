package com.confidant.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import com.confidant.ai.integrations.IntelligentToolManager
import com.confidant.ai.integrations.ToolIntent
import com.confidant.ai.thermal.ThermalManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Comprehensive tests for the intelligent tool calling system
 * 
 * Tests:
 * 1. Intent detection accuracy
 * 2. Natural language understanding
 * 3. Context-aware classification
 * 4. Edge cases and ambiguous queries
 * 5. Performance benchmarks
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IntelligentToolCallingTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var llmEngine: LLMEngine
    private lateinit var toolManager: IntelligentToolManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        
        val thermalManager = ThermalManager(context)
        llmEngine = LLMEngine(context, thermalManager)
        
        toolManager = IntelligentToolManager(
            context = context,
            database = database,
            llmEngine = llmEngine,
            statusCallback = { /* no-op */ }
        )
    }
    
    // ========== SAVE NOTE TESTS ==========
    
    @Test
    fun `test explicit save note command`() = runBlocking {
        val queries = listOf(
            "save note of my dentist appointment",
            "save my wifi password is 123pass",
            "remember to call my mother",
            "remind me to buy groceries",
            "write down meeting notes"
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                ToolIntent.SAVE_NOTE,
                intent,
                "Failed for query: '$query'"
            )
        }
    }
    
    @Test
    fun `test implicit save note intent`() = runBlocking {
        val queries = listOf(
            "my dentist appointment is tomorrow at 3pm",
            "my password is abc123",
            "meeting with John next Tuesday",
            "don't forget to call mom"
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                ToolIntent.SAVE_NOTE,
                intent,
                "Failed for query: '$query' - should understand implicit save intent"
            )
        }
    }
    
    // ========== RETRIEVE NOTE TESTS ==========
    
    @Test
    fun `test explicit retrieve note command`() = runBlocking {
        val queries = listOf(
            "find my note about dentist",
            "show my notes",
            "search my notes for password",
            "list my reminders"
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                ToolIntent.RETRIEVE_NOTE,
                intent,
                "Failed for query: '$query'"
            )
        }
    }
    
    @Test
    fun `test personalized retrieval queries`() = runBlocking {
        val queries = listOf(
            "when is my dentist appointment",
            "what's my wifi password",
            "when do I need to call mom",
            "what did I save about groceries"
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                ToolIntent.RETRIEVE_NOTE,
                intent,
                "Failed for query: '$query' - should recognize personalized retrieval"
            )
        }
    }
    
    // ========== WEB SEARCH TESTS ==========
    
    @Test
    fun `test web search for current information`() = runBlocking {
        val queries = listOf(
            "bitcoin price today",
            "latest news",
            "weather in London",
            "what's happening in the world"
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                ToolIntent.WEB_SEARCH,
                intent,
                "Failed for query: '$query'"
            )
        }
    }
    
    @Test
    fun `test web search for general knowledge`() = runBlocking {
        val queries = listOf(
            "what is quantum computing",
            "how to make pasta",
            "population of India",
            "who invented the telephone"
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                ToolIntent.WEB_SEARCH,
                intent,
                "Failed for query: '$query' - should search web for general knowledge"
            )
        }
    }
    
    // ========== CONVERSATIONAL TESTS ==========
    
    @Test
    fun `test simple greetings`() = runBlocking {
        val queries = listOf(
            "hi",
            "hello",
            "hey",
            "thanks",
            "thank you",
            "bye"
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                null,
                intent,
                "Failed for query: '$query' - should be conversational (no tool)"
            )
        }
    }
    
    // ========== CONTEXT-AWARE TESTS ==========
    
    @Test
    fun `test context-aware disambiguation`() = runBlocking {
        // Same phrase, different contexts
        
        // Context 1: User wants to save
        val saveQuery = "my dentist appointment"
        val saveIntent = toolManager.detectToolIntent(saveQuery)
        assertEquals(
            ToolIntent.SAVE_NOTE,
            saveIntent,
            "Should understand implicit save intent"
        )
        
        // Context 2: User wants to retrieve
        val retrieveQuery = "when is my dentist appointment"
        val retrieveIntent = toolManager.detectToolIntent(retrieveQuery)
        assertEquals(
            ToolIntent.RETRIEVE_NOTE,
            retrieveIntent,
            "Should understand retrieval intent"
        )
        
        // Context 3: User wants general info
        val searchQuery = "dentist appointment cost"
        val searchIntent = toolManager.detectToolIntent(searchQuery)
        assertEquals(
            ToolIntent.WEB_SEARCH,
            searchIntent,
            "Should understand web search intent"
        )
    }
    
    // ========== EDGE CASES ==========
    
    @Test
    fun `test ambiguous queries`() = runBlocking {
        // These queries could be interpreted multiple ways
        // System should make intelligent decisions
        
        val testCases = mapOf(
            "my password" to ToolIntent.SAVE_NOTE, // Implicit: wants to save
            "what's my password" to ToolIntent.RETRIEVE_NOTE, // Explicit: asking
            "password requirements" to ToolIntent.WEB_SEARCH, // General info
            "my appointment" to ToolIntent.SAVE_NOTE, // Implicit: wants to save
            "when is my appointment" to ToolIntent.RETRIEVE_NOTE // Explicit: asking
        )
        
        testCases.forEach { (query, expectedIntent) ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                expectedIntent,
                intent,
                "Failed for ambiguous query: '$query'"
            )
        }
    }
    
    @Test
    fun `test typos and variations`() = runBlocking {
        // System should handle typos gracefully
        val queries = listOf(
            "save notte about meeting", // typo: notte
            "remeber to call mom", // typo: remeber
            "whats my pasword", // typo: pasword
            "latst news" // typo: latst
        )
        
        queries.forEach { query ->
            val intent = toolManager.detectToolIntent(query)
            assertNotNull(
                intent,
                "Should handle typo in query: '$query'"
            )
        }
    }
    
    // ========== PERFORMANCE TESTS ==========
    
    @Test
    fun `test fast path performance`() = runBlocking {
        val greetings = listOf("hi", "hello", "thanks", "bye")
        
        greetings.forEach { query ->
            val startTime = System.currentTimeMillis()
            val intent = toolManager.detectToolIntent(query)
            val duration = System.currentTimeMillis() - startTime
            
            assertEquals(null, intent, "Should be conversational")
            assert(duration < 10) {
                "Fast path should be < 10ms, was ${duration}ms for '$query'"
            }
        }
    }
    
    @Test
    fun `test LLM classification performance`() = runBlocking {
        val queries = listOf(
            "save my dentist appointment",
            "when is my appointment",
            "bitcoin price today"
        )
        
        queries.forEach { query ->
            val startTime = System.currentTimeMillis()
            val intent = toolManager.detectToolIntent(query)
            val duration = System.currentTimeMillis() - startTime
            
            assertNotNull(intent, "Should detect intent for '$query'")
            assert(duration < 5000) {
                "LLM classification should be < 5s, was ${duration}ms for '$query'"
            }
        }
    }
    
    // ========== REAL-WORLD SCENARIOS ==========
    
    @Test
    fun `test real-world save scenarios`() = runBlocking {
        val scenarios = mapOf(
            // Passwords
            "my gmail password is SecurePass123" to ToolIntent.SAVE_NOTE,
            "wifi password for home network is abc123" to ToolIntent.SAVE_NOTE,
            
            // Appointments
            "dentist appointment tomorrow at 3pm" to ToolIntent.SAVE_NOTE,
            "meeting with John next Tuesday at 10am" to ToolIntent.SAVE_NOTE,
            
            // Reminders
            "remind me to call mom this evening" to ToolIntent.SAVE_NOTE,
            "don't forget to buy milk" to ToolIntent.SAVE_NOTE,
            
            // Ideas/Notes
            "idea for new feature: dark mode" to ToolIntent.SAVE_NOTE,
            "meeting notes: discussed Q4 targets" to ToolIntent.SAVE_NOTE
        )
        
        scenarios.forEach { (query, expectedIntent) ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                expectedIntent,
                intent,
                "Failed for real-world scenario: '$query'"
            )
        }
    }
    
    @Test
    fun `test real-world retrieval scenarios`() = runBlocking {
        val scenarios = mapOf(
            // Passwords
            "what's my gmail password" to ToolIntent.RETRIEVE_NOTE,
            "show me my wifi password" to ToolIntent.RETRIEVE_NOTE,
            
            // Appointments
            "when is my dentist appointment" to ToolIntent.RETRIEVE_NOTE,
            "what time is my meeting with John" to ToolIntent.RETRIEVE_NOTE,
            
            // Reminders
            "what do I need to remember today" to ToolIntent.RETRIEVE_NOTE,
            "show my reminders" to ToolIntent.RETRIEVE_NOTE,
            
            // General
            "find my notes about the project" to ToolIntent.RETRIEVE_NOTE,
            "what did I save about groceries" to ToolIntent.RETRIEVE_NOTE
        )
        
        scenarios.forEach { (query, expectedIntent) ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                expectedIntent,
                intent,
                "Failed for real-world scenario: '$query'"
            )
        }
    }
    
    @Test
    fun `test real-world search scenarios`() = runBlocking {
        val scenarios = mapOf(
            // Current events
            "latest news today" to ToolIntent.WEB_SEARCH,
            "what's happening in the world" to ToolIntent.WEB_SEARCH,
            
            // Prices/Markets
            "bitcoin price" to ToolIntent.WEB_SEARCH,
            "stock market today" to ToolIntent.WEB_SEARCH,
            
            // Weather
            "weather in London" to ToolIntent.WEB_SEARCH,
            "will it rain tomorrow" to ToolIntent.WEB_SEARCH,
            
            // General knowledge
            "what is quantum computing" to ToolIntent.WEB_SEARCH,
            "how to make pasta" to ToolIntent.WEB_SEARCH
        )
        
        scenarios.forEach { (query, expectedIntent) ->
            val intent = toolManager.detectToolIntent(query)
            assertEquals(
                expectedIntent,
                intent,
                "Failed for real-world scenario: '$query'"
            )
        }
    }
}
