package com.confidant.ai.response

import android.util.Log

/**
 * ResponseQualityManager - Ensures high-quality, complete responses
 * 
 * Key features:
 * 1. Dynamic token allocation based on query type
 * 2. Response completeness validation
 * 3. Hallucination detection
 * 4. Knowledge gap detection
 * 5. Quality-based retry logic
 */
class ResponseQualityManager {
    
    /**
     * Classify query type for optimal token allocation
     */
    fun classifyQuery(query: String): QueryType {
        val lower = query.lowercase().trim()
        
        // Greetings
        val greetings = listOf("hi", "hello", "hey", "good morning", "good afternoon", "good evening")
        if (greetings.any { lower.startsWith(it) && lower.length < 20 }) {
            return QueryType.GREETING
        }
        
        // Recipes - check BEFORE explanations
        if (lower.contains("recipe") || lower.contains("how to cook") || 
            lower.contains("how to make") || lower.contains("how do i cook") ||
            lower.contains("how do i make")) {
            return QueryType.RECIPE
        }
        
        // Comparisons
        if (lower.contains(" vs ") || lower.contains(" versus ") || 
            lower.contains("compare") || lower.contains("difference between")) {
            return QueryType.COMPARISON
        }
        
        // Lists
        if (lower.contains("list") || lower.contains("top ") || 
            lower.matches(Regex(".*\\d+\\s+(best|worst|top).*"))) {
            return QueryType.LIST
        }
        
        // Explanations
        if (lower.startsWith("how ") || lower.startsWith("why ") || 
            lower.contains("explain") || lower.contains("what is")) {
            return QueryType.EXPLANATION
        }
        
        // Detailed info
        if (lower.contains("tell me about") || lower.contains("information about") ||
            lower.contains("details about") || lower.contains("describe")) {
            return QueryType.DETAILED_INFO
        }
        
        // Search summaries (when processing search results)
        if (lower.contains("search results") || lower.contains("web results")) {
            return QueryType.SEARCH_SUMMARY
        }
        
        // Simple facts (short questions)
        if (lower.split(" ").size <= 5 && 
            (lower.contains("what") || lower.contains("when") || lower.contains("where"))) {
            return QueryType.SIMPLE_FACT
        }
        
        // Default to explanation for longer queries
        return if (lower.split(" ").size > 10) {
            QueryType.DETAILED_INFO
        } else {
            QueryType.EXPLANATION
        }
    }
    
    /**
     * Calculate optimal token count for query type
     * OPTIMIZED 2026: Reduced tokens for faster responses
     */
    fun calculateOptimalTokens(queryType: QueryType): Int {
        return when (queryType) {
            QueryType.GREETING -> 24          // REDUCED from 32 (25% faster)
            QueryType.SIMPLE_FACT -> 48       // REDUCED from 64 (25% faster)
            QueryType.EXPLANATION -> 96       // REDUCED from 128 (25% faster)
            QueryType.RECIPE -> 192           // REDUCED from 256 (25% faster)
            QueryType.DETAILED_INFO -> 150    // REDUCED from 200 (25% faster)
            QueryType.SEARCH_SUMMARY -> 192   // REDUCED from 256 (25% faster)
            QueryType.COMPARISON -> 135       // REDUCED from 180 (25% faster)
            QueryType.LIST -> 112             // REDUCED from 150 (25% faster)
        }
    }
    
    /**
     * Check if response is complete and satisfactory
     */
    fun checkResponseCompleteness(response: String, query: String, queryType: QueryType): Boolean {
        val lower = query.lowercase()
        val responseLower = response.lowercase()
        
        // Minimum length check
        if (response.trim().length < 20) {
            Log.w(TAG, "Response too short: ${response.length} chars")
            return false
        }
        
        // Type-specific checks
        when (queryType) {
            QueryType.RECIPE -> {
                // Recipe must have ingredients or steps
                val hasIngredients = responseLower.contains("ingredient") || 
                                    responseLower.contains("cup") ||
                                    responseLower.contains("tablespoon") ||
                                    responseLower.contains("teaspoon")
                val hasSteps = responseLower.contains("step") || 
                              responseLower.contains("1.") ||
                              responseLower.contains("first") ||
                              responseLower.contains("then")
                
                if (!hasIngredients && !hasSteps && response.length < 200) {
                    Log.w(TAG, "Recipe incomplete: no ingredients or steps")
                    return false
                }
            }
            
            QueryType.SIMPLE_FACT -> {
                // Price queries need numbers
                if (lower.contains("price") || lower.contains("cost")) {
                    val hasNumber = response.contains(Regex("[\\$₹€£]\\s*\\d+|\\d+\\s*[\\$₹€£]"))
                    if (!hasNumber) {
                        Log.w(TAG, "Price query missing number")
                        return false
                    }
                }
            }
            
            QueryType.COMPARISON -> {
                // Comparison should mention both items
                val words = lower.split(" ")
                val items = words.filter { it.length > 3 && it[0].isUpperCase() }
                if (items.size >= 2) {
                    val mentionsBoth = items.all { responseLower.contains(it.lowercase()) }
                    if (!mentionsBoth) {
                        Log.w(TAG, "Comparison doesn't mention all items")
                        return false
                    }
                }
            }
            
            else -> {
                // General: should be substantial
                if (response.length < 50) {
                    Log.w(TAG, "Response not substantial enough")
                    return false
                }
            }
        }
        
        // Check for incomplete promises
        val promises = listOf(
            "i'll share", "i'll provide", "i'll give", "i'll tell",
            "let me share", "let me provide", "let me give",
            "i will share", "i will provide"
        )
        
        if (promises.any { responseLower.contains(it) } && response.length < 100) {
            Log.w(TAG, "Response makes promise but doesn't deliver")
            return false
        }
        
        // Check for deflection
        val deflections = listOf(
            "would you like", "do you want", "should i",
            "let me know if", "tell me if you need"
        )
        
        if (deflections.any { responseLower.contains(it) } && response.length < 80) {
            Log.w(TAG, "Response deflects instead of answering")
            return false
        }
        
        return true
    }
    
    /**
     * Detect if bot lacks knowledge and should search
     */
    fun shouldFallbackToSearch(query: String, response: String): Boolean {
        val responseLower = response.lowercase()
        
        // Explicit uncertainty
        val uncertaintyPhrases = listOf(
            "i don't know", "i'm not sure", "i don't have",
            "i can't provide", "i'm unable to", "i cannot"
        )
        
        if (uncertaintyPhrases.any { responseLower.contains(it) }) {
            Log.i(TAG, "Detected uncertainty - should search")
            return true
        }
        
        // Promises without delivery
        val promises = listOf("i'll share", "i'll provide", "let me share")
        if (promises.any { responseLower.contains(it) } && response.length < 100) {
            Log.i(TAG, "Detected unfulfilled promise - should search")
            return true
        }
        
        // Query needs current information
        val needsCurrentInfo = listOf(
            "recipe", "price", "cost", "news", "latest", "current",
            "today", "now", "market", "stock", "weather", "forecast"
        ).any { query.lowercase().contains(it) }
        
        // Response is too vague for specific query
        val isVague = response.length < 60 && needsCurrentInfo
        
        if (isVague) {
            Log.i(TAG, "Response too vague for specific query - should search")
            return true
        }
        
        return false
    }
    
    /**
     * Detect hallucinated content
     */
    fun detectHallucination(response: String): Boolean {
        val lower = response.lowercase()
        
        // Fake conversations
        val hasConversation = lower.contains("user:") || 
                             lower.contains("assistant:") ||
                             lower.contains("you said:") ||
                             lower.contains("i replied:")
        
        if (hasConversation) {
            Log.w(TAG, "Detected hallucinated conversation")
            return true
        }
        
        // Leaked prompts
        val hasPromptLeak = lower.contains("respond in") ||
                           lower.contains("reply in") ||
                           lower.contains("your task is") ||
                           lower.contains("you are a")
        
        if (hasPromptLeak) {
            Log.w(TAG, "Detected prompt leak")
            return true
        }
        
        return false
    }
    
    /**
     * Check if response is relevant to query
     */
    fun checkRelevance(response: String, query: String): Boolean {
        val queryWords = query.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 3 }
            .toSet()
        
        val responseWords = response.lowercase()
            .split(Regex("\\W+"))
            .toSet()
        
        // At least 20% of query words should appear in response
        val overlap = queryWords.intersect(responseWords).size
        val relevanceScore = if (queryWords.isNotEmpty()) {
            overlap.toFloat() / queryWords.size
        } else {
            1.0f
        }
        
        val isRelevant = relevanceScore >= 0.2f
        
        if (!isRelevant) {
            Log.w(TAG, "Low relevance score: $relevanceScore")
        }
        
        return isRelevant
    }
    
    /**
     * Get optimal temperature for query type
     */
    fun getOptimalTemperature(queryType: QueryType): Float {
        return when (queryType) {
            QueryType.GREETING -> 0.8f          // Natural, varied
            QueryType.SIMPLE_FACT -> 0.4f       // Precise
            QueryType.EXPLANATION -> 0.7f       // Balanced
            QueryType.RECIPE -> 0.6f            // Structured but natural
            QueryType.DETAILED_INFO -> 0.7f     // Comprehensive
            QueryType.SEARCH_SUMMARY -> 0.7f    // Balanced
            QueryType.COMPARISON -> 0.6f        // Structured
            QueryType.LIST -> 0.6f              // Organized
        }
    }
    
    companion object {
        private const val TAG = "ResponseQualityMgr"
    }
}

/**
 * Query types for optimal response generation
 */
enum class QueryType {
    GREETING,           // Hi, hello, hey
    SIMPLE_FACT,        // Quick factual answer
    EXPLANATION,        // How/why questions
    RECIPE,             // Cooking instructions
    DETAILED_INFO,      // Tell me about, what is
    SEARCH_SUMMARY,     // Summarizing web results
    COMPARISON,         // X vs Y, compare
    LIST                // Top N, list of
}
