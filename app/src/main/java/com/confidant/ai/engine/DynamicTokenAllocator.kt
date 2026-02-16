package com.confidant.ai.engine

import android.util.Log

/**
 * DynamicTokenAllocator - Intelligent token budget allocation
 * 
 * 2026 OPTIMIZATION:
 * - Allocates tokens based on query complexity and type
 * - Prevents token overflow while maximizing response quality
 * - Adapts to different query types (factual, conversational, search-based)
 * 
 * Token Budget Breakdown (2048 total):
 * - System prompt: 200-400 tokens (compressed)
 * - Search results: 400-800 tokens (dynamic based on query)
 * - User context: 100-200 tokens
 * - Generation: 128-384 tokens (dynamic based on query)
 * - Buffer: 200 tokens (safety margin)
 */
object DynamicTokenAllocator {
    
    private const val TAG = "DynamicTokenAllocator"
    
    // Total context window for LFM2.5-1.2B-Instruct
    private const val TOTAL_CONTEXT_WINDOW = 2048
    
    // Minimum buffer to prevent overflow
    private const val SAFETY_BUFFER = 200
    
    // Available tokens after buffer
    private const val AVAILABLE_TOKENS = TOTAL_CONTEXT_WINDOW - SAFETY_BUFFER
    
    /**
     * Query complexity levels
     */
    enum class QueryComplexity {
        SIMPLE,      // Greetings, yes/no, simple facts
        MODERATE,    // General questions, explanations
        COMPLEX,     // Multi-part questions, analysis, comparisons
        SEARCH_BASED // Queries requiring web search
    }
    
    /**
     * Token allocation result
     */
    data class TokenAllocation(
        val systemPromptTokens: Int,
        val searchResultsTokens: Int,
        val userContextTokens: Int,
        val generationTokens: Int,
        val totalUsed: Int,
        val complexity: QueryComplexity
    )
    
    /**
     * Allocate tokens based on query type and complexity
     */
    fun allocateTokens(
        query: String,
        hasSearchResults: Boolean,
        conversationHistorySize: Int
    ): TokenAllocation {
        val complexity = detectComplexity(query, hasSearchResults)
        
        Log.d(TAG, "Query complexity: $complexity")
        Log.d(TAG, "Has search results: $hasSearchResults")
        Log.d(TAG, "Conversation history: $conversationHistorySize messages")
        
        return when (complexity) {
            QueryComplexity.SIMPLE -> allocateSimple(conversationHistorySize)
            QueryComplexity.MODERATE -> allocateModerate(conversationHistorySize)
            QueryComplexity.COMPLEX -> allocateComplex(conversationHistorySize)
            QueryComplexity.SEARCH_BASED -> allocateSearchBased(conversationHistorySize)
        }
    }
    
    /**
     * Detect query complexity
     */
    private fun detectComplexity(query: String, hasSearchResults: Boolean): QueryComplexity {
        val lowerQuery = query.lowercase()
        val wordCount = query.split("\\s+".toRegex()).size
        
        // Search-based queries
        if (hasSearchResults) {
            return QueryComplexity.SEARCH_BASED
        }
        
        // Simple queries (greetings, short questions)
        val simplePatterns = listOf(
            "hi", "hello", "hey", "thanks", "thank you", "ok", "okay",
            "yes", "no", "sure", "great", "cool", "nice"
        )
        if (wordCount <= 3 && simplePatterns.any { lowerQuery.contains(it) }) {
            return QueryComplexity.SIMPLE
        }
        
        // Complex queries (multi-part, comparisons, analysis)
        val complexIndicators = listOf(
            "compare", "difference between", "pros and cons", "analyze",
            "explain in detail", "how does", "why does", "what are the",
            "list all", "tell me everything", "comprehensive"
        )
        if (complexIndicators.any { lowerQuery.contains(it) } || wordCount > 15) {
            return QueryComplexity.COMPLEX
        }
        
        // Default to moderate
        return QueryComplexity.MODERATE
    }
    
    /**
     * Allocate tokens for simple queries
     * Minimal system prompt, no search, short generation
     */
    private fun allocateSimple(historySize: Int): TokenAllocation {
        val systemPrompt = 200  // Minimal conversational prompt
        val searchResults = 0   // No search
        val userContext = minOf(100, historySize * 30)  // Minimal context
        val generation = 64     // Short response
        
        return TokenAllocation(
            systemPromptTokens = systemPrompt,
            searchResultsTokens = searchResults,
            userContextTokens = userContext,
            generationTokens = generation,
            totalUsed = systemPrompt + searchResults + userContext + generation,
            complexity = QueryComplexity.SIMPLE
        ).also { logAllocation(it) }
    }
    
    /**
     * Allocate tokens for moderate queries
     * Standard system prompt, no search, moderate generation
     */
    private fun allocateModerate(historySize: Int): TokenAllocation {
        val systemPrompt = 300  // Standard conversational prompt
        val searchResults = 0   // No search
        val userContext = minOf(150, historySize * 40)  // Moderate context
        val generation = 128    // Standard response
        
        return TokenAllocation(
            systemPromptTokens = systemPrompt,
            searchResultsTokens = searchResults,
            userContextTokens = userContext,
            generationTokens = generation,
            totalUsed = systemPrompt + searchResults + userContext + generation,
            complexity = QueryComplexity.MODERATE
        ).also { logAllocation(it) }
    }
    
    /**
     * Allocate tokens for complex queries
     * Detailed system prompt, no search, longer generation
     */
    private fun allocateComplex(historySize: Int): TokenAllocation {
        val systemPrompt = 350  // Detailed prompt with examples
        val searchResults = 0   // No search
        val userContext = minOf(200, historySize * 50)  // More context
        val generation = 256    // Longer response for detailed answers
        
        return TokenAllocation(
            systemPromptTokens = systemPrompt,
            searchResultsTokens = searchResults,
            userContextTokens = userContext,
            generationTokens = generation,
            totalUsed = systemPrompt + searchResults + userContext + generation,
            complexity = QueryComplexity.COMPLEX
        ).also { logAllocation(it) }
    }
    
    /**
     * Allocate tokens for search-based queries
     * Compact system prompt, large search results, moderate generation
     */
    private fun allocateSearchBased(historySize: Int): TokenAllocation {
        val systemPrompt = 250  // Compact factual prompt
        val userContext = minOf(100, historySize * 30)  // Minimal context
        val generation = 192    // Moderate response with facts
        
        // Calculate remaining tokens for search results
        val searchResults = AVAILABLE_TOKENS - systemPrompt - userContext - generation
        
        return TokenAllocation(
            systemPromptTokens = systemPrompt,
            searchResultsTokens = searchResults,
            userContextTokens = userContext,
            generationTokens = generation,
            totalUsed = systemPrompt + searchResults + userContext + generation,
            complexity = QueryComplexity.SEARCH_BASED
        ).also { logAllocation(it) }
    }
    
    /**
     * Estimate tokens for text (rough approximation)
     * Rule of thumb: ~4 characters per token
     */
    fun estimateTokens(text: String): Int {
        return (text.length / 4.0).toInt()
    }
    
    /**
     * Truncate text to fit token budget
     */
    fun truncateToTokenBudget(text: String, maxTokens: Int): String {
        val estimatedTokens = estimateTokens(text)
        
        if (estimatedTokens <= maxTokens) {
            return text
        }
        
        // Calculate target character count
        val targetChars = maxTokens * 4
        
        // Truncate and add indicator
        return text.take(targetChars) + "\n... (truncated for token limit)"
    }
    
    /**
     * Compress search results to fit token budget
     * Extracts key facts and limits snippet length
     */
    fun compressSearchResults(searchResults: String, maxTokens: Int): String {
        val targetChars = maxTokens * 4
        
        if (searchResults.length <= targetChars) {
            return searchResults
        }
        
        // Strategy: Keep key facts, truncate snippets
        val lines = searchResults.lines()
        val compressed = mutableListOf<String>()
        var currentLength = 0
        
        for (line in lines) {
            // Always keep headers and key facts
            if (line.startsWith("Search:") || 
                line.startsWith("Key Facts:") ||
                line.startsWith("Sources:") ||
                line.contains("Price:") ||
                line.contains("Change:") ||
                line.contains("Date:")) {
                compressed.add(line)
                currentLength += line.length
                continue
            }
            
            // Truncate long snippets
            if (line.length > 150) {
                val truncated = line.take(150) + "..."
                compressed.add(truncated)
                currentLength += truncated.length
            } else {
                compressed.add(line)
                currentLength += line.length
            }
            
            // Stop if we've reached target
            if (currentLength >= targetChars) {
                compressed.add("... (truncated for token limit)")
                break
            }
        }
        
        return compressed.joinToString("\n")
    }
    
    /**
     * Log token allocation for debugging
     */
    private fun logAllocation(allocation: TokenAllocation) {
        Log.d(TAG, "=== Token Allocation (${allocation.complexity}) ===")
        Log.d(TAG, "System Prompt: ${allocation.systemPromptTokens} tokens")
        Log.d(TAG, "Search Results: ${allocation.searchResultsTokens} tokens")
        Log.d(TAG, "User Context: ${allocation.userContextTokens} tokens")
        Log.d(TAG, "Generation: ${allocation.generationTokens} tokens")
        Log.d(TAG, "Total Used: ${allocation.totalUsed} / $AVAILABLE_TOKENS tokens")
        Log.d(TAG, "Buffer: $SAFETY_BUFFER tokens")
        Log.d(TAG, "Total: ${allocation.totalUsed + SAFETY_BUFFER} / $TOTAL_CONTEXT_WINDOW tokens")
        
        val percentage = (allocation.totalUsed.toFloat() / AVAILABLE_TOKENS * 100).toInt()
        Log.d(TAG, "Usage: $percentage%")
    }
    
    /**
     * Get recommended max tokens for generation based on query
     */
    fun getRecommendedMaxTokens(query: String, hasSearchResults: Boolean): Int {
        val complexity = detectComplexity(query, hasSearchResults)
        
        return when (complexity) {
            QueryComplexity.SIMPLE -> 64
            QueryComplexity.MODERATE -> 128
            QueryComplexity.COMPLEX -> 256
            QueryComplexity.SEARCH_BASED -> 192
        }
    }
    
    /**
     * Get recommended temperature based on query type
     */
    fun getRecommendedTemperature(query: String, hasSearchResults: Boolean): Float {
        val lowerQuery = query.lowercase()
        
        return when {
            // Factual queries need low temperature (less hallucination)
            hasSearchResults -> 0.3f
            lowerQuery.contains("price") || lowerQuery.contains("cost") -> 0.3f
            lowerQuery.contains("when") || lowerQuery.contains("date") -> 0.3f
            lowerQuery.contains("how many") || lowerQuery.contains("number") -> 0.3f
            
            // Creative queries can use higher temperature
            lowerQuery.contains("imagine") || lowerQuery.contains("create") -> 0.7f
            lowerQuery.contains("story") || lowerQuery.contains("poem") -> 0.7f
            
            // Conversational queries use moderate temperature
            else -> 0.5f
        }
    }
}
