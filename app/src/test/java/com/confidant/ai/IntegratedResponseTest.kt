package com.confidant.ai

import com.confidant.ai.response.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive tests for the 2026 integrated response system
 */
class IntegratedResponseTest {
    
    @Test
    fun testQueryClassification() {
        val manager = ResponseQualityManager()
        
        // Test greetings
        assertEquals(QueryType.GREETING, manager.classifyQuery("hi"))
        assertEquals(QueryType.GREETING, manager.classifyQuery("hello there"))
        assertEquals(QueryType.GREETING, manager.classifyQuery("good morning"))
        
        // Test recipes
        assertEquals(QueryType.RECIPE, manager.classifyQuery("chicken shwarma recipe"))
        assertEquals(QueryType.RECIPE, manager.classifyQuery("how to cook pasta"))
        assertEquals(QueryType.RECIPE, manager.classifyQuery("how do i make pizza"))
        
        // Test comparisons
        assertEquals(QueryType.COMPARISON, manager.classifyQuery("bitcoin vs ethereum"))
        assertEquals(QueryType.COMPARISON, manager.classifyQuery("compare tesla and apple stock"))
        assertEquals(QueryType.COMPARISON, manager.classifyQuery("difference between java and python"))
        
        // Test lists
        assertEquals(QueryType.LIST, manager.classifyQuery("top 10 programming languages"))
        assertEquals(QueryType.LIST, manager.classifyQuery("list of best restaurants"))
        assertEquals(QueryType.LIST, manager.classifyQuery("5 best laptops"))
        
        // Test explanations
        assertEquals(QueryType.EXPLANATION, manager.classifyQuery("how does blockchain work"))
        assertEquals(QueryType.EXPLANATION, manager.classifyQuery("why is the sky blue"))
        assertEquals(QueryType.EXPLANATION, manager.classifyQuery("explain quantum computing"))
    }
    
    @Test
    fun testTokenAllocation() {
        val manager = ResponseQualityManager()
        
        // Verify optimized token limits (2026 optimization - 25% reduction)
        assertEquals(24, manager.calculateOptimalTokens(QueryType.GREETING))
        assertEquals(48, manager.calculateOptimalTokens(QueryType.SIMPLE_FACT))
        assertEquals(96, manager.calculateOptimalTokens(QueryType.EXPLANATION))
        assertEquals(192, manager.calculateOptimalTokens(QueryType.RECIPE))
        assertEquals(150, manager.calculateOptimalTokens(QueryType.DETAILED_INFO))
        assertEquals(192, manager.calculateOptimalTokens(QueryType.SEARCH_SUMMARY))
        assertEquals(135, manager.calculateOptimalTokens(QueryType.COMPARISON))
        assertEquals(112, manager.calculateOptimalTokens(QueryType.LIST))
    }
    
    @Test
    fun testResponseCompleteness() {
        val manager = ResponseQualityManager()
        
        // Recipe completeness
        val completeRecipe = """
            Chicken Shwarma Recipe
            
            Ingredients:
            - 500g chicken breast
            - 2 tablespoons olive oil
            - 1 teaspoon cumin
            
            Steps:
            1. Marinate chicken for 2 hours
            2. Grill for 15 minutes
            3. Slice and serve
            
            Cooking time: 30 minutes
            Serves: 4
        """.trimIndent()
        
        assertTrue(manager.checkResponseCompleteness(
            completeRecipe,
            "chicken shwarma recipe",
            QueryType.RECIPE
        ))
        
        // Incomplete recipe
        val incompleteRecipe = "I'll share a recipe with you."
        assertFalse(manager.checkResponseCompleteness(
            incompleteRecipe,
            "chicken shwarma recipe",
            QueryType.RECIPE
        ))
        
        // Price query completeness
        val completePrice = "Bitcoin is currently at $43,250, up 2.3% today."
        assertTrue(manager.checkResponseCompleteness(
            completePrice,
            "bitcoin price",
            QueryType.SIMPLE_FACT
        ))
        
        // Incomplete price
        val incompletePrice = "Let me check the price for you."
        assertFalse(manager.checkResponseCompleteness(
            incompletePrice,
            "bitcoin price",
            QueryType.SIMPLE_FACT
        ))
    }
    
    @Test
    fun testKnowledgeGapDetection() {
        val manager = ResponseQualityManager()
        
        // Should fallback to search
        assertTrue(manager.shouldFallbackToSearch(
            "chicken shwarma recipe",
            "I'll share a recipe with you."
        ))
        
        assertTrue(manager.shouldFallbackToSearch(
            "bitcoin price today",
            "I'm not sure about the current price."
        ))
        
        assertTrue(manager.shouldFallbackToSearch(
            "latest news on AI",
            "Let me know if you need more information."
        ))
        
        // Should NOT fallback
        assertFalse(manager.shouldFallbackToSearch(
            "hello",
            "Hello! How can I help you today?"
        ))
        
        assertFalse(manager.shouldFallbackToSearch(
            "what is 2+2",
            "2+2 equals 4."
        ))
    }
    
    @Test
    fun testHallucinationDetection() {
        val manager = ResponseQualityManager()
        
        // Hallucinated conversation
        val hallucinated1 = """
            user: What's the weather?
            assistant: It's sunny today.
            user: Thanks!
        """.trimIndent()
        assertTrue(manager.detectHallucination(hallucinated1))
        
        // Leaked prompt
        val hallucinated2 = "Reply in 1-2 sentences. Be concise."
        assertTrue(manager.detectHallucination(hallucinated2))
        
        // Clean response
        val clean = "The weather is sunny today with a high of 75°F."
        assertFalse(manager.detectHallucination(clean))
    }
    
    @Test
    fun testRelevanceCheck() {
        val manager = ResponseQualityManager()
        
        // Relevant response
        assertTrue(manager.checkRelevance(
            "Bitcoin is a cryptocurrency that uses blockchain technology.",
            "what is bitcoin"
        ))
        
        // Irrelevant response
        assertFalse(manager.checkRelevance(
            "The weather is nice today.",
            "what is bitcoin"
        ))
    }
    
    @Test
    fun testCitationExtraction() {
        val manager = CitationManager()
        
        val searchResults = """
            📰 ARTICLE 1: Bitcoin Price Analysis
            🔗 Source: coindesk.com
            🌐 URL: https://coindesk.com/price/bitcoin
            
            Bitcoin is currently at $43,250...
            
            📰 ARTICLE 2: Crypto Market Update
            🔗 Source: bloomberg.com
            🌐 URL: https://bloomberg.com/crypto
            
            The crypto market is showing strength...
        """.trimIndent()
        
        val sources = manager.extractSources(searchResults)
        
        assertEquals(2, sources.size)
        assertEquals("Bitcoin Price Analysis", sources[0].title)
        assertEquals("coindesk.com", sources[0].domain)
        assertTrue(sources[0].url.contains("coindesk.com"))
    }
    
    @Test
    fun testCitationFormatting() {
        val manager = CitationManager()
        
        val sources = listOf(
            SourceInfo(1, "Source 1", "https://example.com/1", "example.com", 0.9f),
            SourceInfo(2, "Source 2", "https://example.com/2", "example.com", 0.8f)
        )
        
        val response = "Bitcoin is currently at $43,250. The market is bullish."
        
        // Numbered citations
        val numbered = manager.addInlineCitations(response, sources, CitationFormat.NUMBERED)
        assertTrue(numbered.contains("[1]"))
        assertTrue(numbered.contains("[2]"))
        
        // Inline citations
        val inline = manager.addInlineCitations(response, sources, CitationFormat.INLINE)
        assertTrue(inline.contains("(example.com)"))
        
        // Markdown citations
        val markdown = manager.addInlineCitations(response, sources, CitationFormat.MARKDOWN)
        assertTrue(markdown.contains("[[source]]"))
    }
    
    @Test
    fun testCitationRemoval() {
        val manager = CitationManager()
        
        val withCitations = "Bitcoin is currently at $43,250 [1]. The market is bullish [2]."
        val clean = manager.removeCitations(withCitations)
        
        assertFalse(clean.contains("[1]"))
        assertFalse(clean.contains("[2]"))
        assertEquals("Bitcoin is currently at $43,250. The market is bullish.", clean)
    }
    
    @Test
    fun testSourceCredibility() {
        val manager = CitationManager()
        
        val sources = listOf(
            SourceInfo(1, "Reuters", "https://reuters.com", "reuters.com", 0.95f),
            SourceInfo(2, "Medium", "https://medium.com", "medium.com", 0.60f),
            SourceInfo(3, "Unknown", "https://unknown.com", "unknown.com", 0.45f)  // Changed to 0.45f to be < 0.5f
        )
        
        val validation = manager.validateSources(sources)
        
        assertTrue(validation.hasHighQualitySources)
        assertTrue(validation.hasLowQualitySources)
        assertTrue(validation.averageCredibility > 0.6f)
    }
    
    @Test
    fun testSourceListFormatting() {
        val manager = CitationManager()
        
        val sources = listOf(
            SourceInfo(1, "Bitcoin Analysis", "https://coindesk.com/btc", "coindesk.com", 0.9f),
            SourceInfo(2, "Market Update", "https://bloomberg.com/crypto", "bloomberg.com", 0.95f)
        )
        
        val formatted = manager.formatSourceList(sources)
        
        assertTrue(formatted.contains("📚 Sources:"))
        assertTrue(formatted.contains("[1] Bitcoin Analysis"))
        assertTrue(formatted.contains("[2] Market Update"))
        assertTrue(formatted.contains("https://coindesk.com/btc"))
        assertTrue(formatted.contains("https://bloomberg.com/crypto"))
    }
    
    @Test
    fun testTemperatureSettings() {
        val manager = ResponseQualityManager()
        
        // Verify temperature settings
        assertEquals(0.8f, manager.getOptimalTemperature(QueryType.GREETING), 0.01f)
        assertEquals(0.4f, manager.getOptimalTemperature(QueryType.SIMPLE_FACT), 0.01f)
        assertEquals(0.7f, manager.getOptimalTemperature(QueryType.EXPLANATION), 0.01f)
        assertEquals(0.6f, manager.getOptimalTemperature(QueryType.RECIPE), 0.01f)
        assertEquals(0.7f, manager.getOptimalTemperature(QueryType.SEARCH_SUMMARY), 0.01f)
    }
}
