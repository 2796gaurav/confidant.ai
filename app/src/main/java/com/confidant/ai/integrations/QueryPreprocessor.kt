package com.confidant.ai.integrations

import com.confidant.ai.utils.DateTimeProvider

/**
 * QueryPreprocessor - Pattern-based query optimization
 * 
 * Eliminates slow LLM calls for 80% of common queries by using pattern matching.
 * 
 * Expected Impact:
 * - 80% of queries processed instantly (no LLM call)
 * - 500-1000ms saved per query
 * - Better user experience
 */
object QueryPreprocessor {
    
    private val patterns = mapOf(
        // News queries
        """(?:latest|recent|today'?s?) (?:news|updates?)""".toRegex(RegexOption.IGNORE_CASE) to 
            { _: MatchResult -> "latest news ${DateTimeProvider.getShortDate()}" },
        
        // Price queries - crypto
        """(?:what'?s|whats|what is) (?:the )?(\w+) price""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "${match.groupValues[1]} price today USD" },
        
        """(\w+) price""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "${match.groupValues[1]} price today USD" },
        
        // Weather queries
        """weather (?:in|for|at) (\w+)""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "${match.groupValues[1]} weather forecast today" },
        
        // Stock queries
        """(\w+) stock""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "${match.groupValues[1]} stock price news today" },
        
        // Recipe queries
        """(?:recipe for|how to (?:cook|make)) (.+?)(?:\?|$)""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "${match.groupValues[1]} recipe" },
        
        // Sports scores
        """(\w+) (?:vs|versus) (\w+) score""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "${match.groupValues[1]} vs ${match.groupValues[2]} score today" },
        
        // Movie/TV queries
        """(?:watch|stream) (.+?)(?:\?|$)""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "where to watch ${match.groupValues[1]}" },
        
        // Definition queries
        """(?:what is|define) (.+?)(?:\?|$)""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "${match.groupValues[1]} definition meaning" },
        
        // How-to queries
        """how to (.+?)(?:\?|$)""".toRegex(RegexOption.IGNORE_CASE) to 
            { match: MatchResult -> "how to ${match.groupValues[1]}" }
    )
    
    /**
     * Preprocess query using pattern matching
     * Returns optimized query if pattern matched, null otherwise
     */
    fun preprocess(query: String): String? {
        val trimmed = query.trim()
        
        for ((pattern, transformer) in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val optimized = transformer(match)
                android.util.Log.i("QueryPreprocessor", "✓ Pattern matched: \"$query\" → \"$optimized\"")
                return optimized
            }
        }
        
        return null  // No pattern matched, use LLM
    }
    
    /**
     * Check if query is likely to need search
     * Fast heuristic to avoid unnecessary LLM calls
     */
    fun needsSearch(query: String): Boolean {
        val lower = query.lowercase()
        
        // Keywords that indicate search need
        val searchKeywords = listOf(
            "what", "when", "where", "who", "how", "why",
            "price", "news", "latest", "today", "current",
            "weather", "stock", "market", "score", "result"
        )
        
        return searchKeywords.any { lower.contains(it) }
    }
    
    /**
     * Check if query is conversational (doesn't need search)
     */
    fun isConversational(query: String): Boolean {
        val lower = query.lowercase().trim()
        
        // Common conversational patterns
        val conversationalPatterns = listOf(
            "hi", "hello", "hey", "thanks", "thank you",
            "bye", "goodbye", "ok", "okay", "yes", "no",
            "how are you", "what's up", "good morning",
            "good night", "good evening"
        )
        
        return conversationalPatterns.any { lower.startsWith(it) || lower == it }
    }
}
