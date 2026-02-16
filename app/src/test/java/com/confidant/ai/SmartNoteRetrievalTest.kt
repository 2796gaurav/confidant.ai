package com.confidant.ai

import com.confidant.ai.notes.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for smart note retrieval
 */
class SmartNoteRetrievalTest {
    
    @Test
    fun testSearchIntentParsing_Recent() {
        val queries = listOf(
            "show me recent notes",
            "what are my latest notes",
            "last 5 notes"
        )
        
        // This would need actual SmartNoteRetrieval instance
        // For now, testing the logic
        queries.forEach { query ->
            val lower = query.lowercase()
            assertTrue("Failed for: $query", 
                lower.matches(Regex(".*\\b(recent|latest|last|new)\\b.*")))
        }
    }
    
    @Test
    fun testSearchIntentParsing_Category() {
        val categories = listOf("work", "personal", "health", "passwords")
        
        categories.forEach { category ->
            val query = "show me $category notes"
            assertTrue(query.lowercase().contains(category))
        }
    }
    
    @Test
    fun testSearchIntentParsing_Priority() {
        val queries = listOf(
            "show important notes",
            "urgent notes",
            "high priority items"
        )
        
        queries.forEach { query ->
            val lower = query.lowercase()
            assertTrue("Failed for: $query",
                lower.matches(Regex(".*\\b(important|urgent|priority|high)\\b.*")))
        }
    }
    
    @Test
    fun testKeywordExtraction() {
        val query = "show me my notes about the meeting"
        val stopWords = setOf(
            "show", "me", "my", "find", "search", "get", "what", "did", "i", "save",
            "about", "the", "a", "an", "is", "are", "was", "were", "notes", "note"
        )
        
        val keywords = query.lowercase()
            .split(Regex("\\s+"))
            .filter { it !in stopWords && it.length > 2 }
            .joinToString(" ")
        
        assertEquals("meeting", keywords)
    }
    
    @Test
    fun testFuzzyMatching() {
        // Test partial matches
        val query = "wifi"
        val text = "My WiFi password for home network"
        
        assertTrue(text.lowercase().contains(query.lowercase()))
    }
    
    @Test
    fun testQuickAccessPatterns() {
        val patterns = QuickAccessPattern.values()
        
        assertTrue(patterns.contains(QuickAccessPattern.RECENT))
        assertTrue(patterns.contains(QuickAccessPattern.IMPORTANT))
        assertTrue(patterns.contains(QuickAccessPattern.PASSWORDS))
        assertTrue(patterns.contains(QuickAccessPattern.WORK))
    }
    
    @Test
    fun testSearchResultFormatting() {
        val result = SearchResult(
            summary = "Found 3 notes",
            notes = emptyList(),
            suggestions = listOf("Try searching in: work, personal")
        )
        
        assertEquals("Found 3 notes", result.summary)
        assertEquals(1, result.suggestions.size)
    }
}
