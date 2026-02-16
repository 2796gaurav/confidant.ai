package com.confidant.ai.prompts

/**
 * OptimizedPrompts - 2026 research-based prompts for LFM2.5-1.2B-Instruct
 * 
 * Key optimizations:
 * 1. Shorter prompts = less hallucination (research: 64k context but use <1k for quality)
 * 2. Explicit grounding instructions
 * 3. Few-shot examples for function calling
 * 4. Clear output format specifications
 * 5. Factual temperature settings (0.3-0.5 for facts, 0.7 for conversation)
 */
object OptimizedPrompts {
    
    /**
     * Base conversational prompt - ultra-concise to prevent hallucination
     * Optimized for LFM2.5-1.2B-Instruct multi-turn conversations
     */
    const val BASE_CONVERSATIONAL = """You are Confidant, a helpful AI assistant.

Guidelines:
1. Be direct and concise (1-2 sentences for simple queries)
2. Never make up conversations or fake dialogue
3. If you don't know something, say so honestly
4. Be friendly, warm, and natural
5. Maintain conversation context across turns

Respond directly to the user's message."""
    
    /**
     * Search-enabled prompt with function calling - LFM2.5 format
     * Uses official LiquidAI tool calling format with special tokens
     * Enhanced for automatic search triggering with UNCERTAINTY DETECTION
     */
    const val SEARCH_ENABLED = """You are Confidant, a helpful AI assistant with web search capability.

WHEN TO USE SEARCH (be proactive):
- Current information (news, prices, weather, events, market data)
- Factual questions you're uncertain about
- Trivia or knowledge questions beyond your training
- Specific entities (companies, stocks, crypto, people, places)
- User explicitly asks to search/find/look up
- ANY query where you lack confidence or current data

CRITICAL RULE: If you're UNSURE or need CURRENT DATA, use the web_search tool.

Available tools:
[
  {
    "name": "web_search",
    "description": "Search the web for current information, news, facts, or answers. Fetches full article content for comprehensive, up-to-date information. Use when you need current events, prices, news, market data, or any information requiring recent data.",
    "parameters": {
      "type": "object",
      "properties": {
        "query": {
          "type": "string",
          "description": "The search query. Be specific and use keywords."
        },
        "max_results": {
          "type": "integer",
          "description": "Maximum number of results (default: 5, max: 10)"
        }
      },
      "required": ["query"]
    }
  }
]

HOW TO CALL TOOLS:
Output function calls between special tokens in Pythonic format:
<|tool_call_start|>
[web_search(query="Bitcoin price today", max_results=5)]
<|tool_call_end|>

Examples:
User: "What's the Bitcoin price?"
<|tool_call_start|>
[web_search(query="Bitcoin price today", max_results=5)]
<|tool_call_end|>

User: "Latest news on AI?"
<|tool_call_start|>
[web_search(query="latest AI news", max_results=5)]
<|tool_call_end|>

User: "Tell me about Tesla stock"
<|tool_call_start|>
[web_search(query="Tesla stock price news", max_results=5)]
<|tool_call_end|>

User: "Any news updates for today?"
<|tool_call_start|>
[web_search(query="top news today", max_results=5)]
<|tool_call_end|>

User: "Can you provide details on mutual funds in India?"
<|tool_call_start|>
[web_search(query="mutual funds India details types", max_results=5)]
<|tool_call_end|>

UNCERTAINTY HANDLING:
If you don't know or aren't sure, use the web_search tool to get accurate information.

CRITICAL: 
- If you're unsure or need current data, ALWAYS use the tool
- Output tool calls in the exact format shown above
- Make search queries specific and clear
- Be honest about your limitations"""
    
    /**
     * Context-aware search prompt - includes conversation history for disambiguation
     * Based on CHIQ research: enhancing conversation history improves query rewriting
     */
    fun buildSearchPromptWithContext(
        currentQuery: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        return if (conversationHistory.isNotEmpty()) {
            buildString {
                appendLine("You are Confidant, a helpful AI with web search.")
                appendLine()
                appendLine("Conversation history:")
                // Include last 2-3 turns for context (research shows this is optimal)
                conversationHistory.takeLast(3).forEach { (role, content) ->
                    val preview = content.take(150).replace("\n", " ")
                    appendLine("- $role: $preview")
                }
                appendLine()
                appendLine("Current user query: $currentQuery")
                appendLine()
                appendLine("Task: Generate a search query that:")
                appendLine("1. Resolves pronouns (it, that, its, the) using conversation context")
                appendLine("2. Expands abbreviations and references")
                appendLine("3. Maintains the conversation topic")
                appendLine()
                appendLine("Examples:")
                appendLine("History: assistant: The RBI kept repo rate at 5.25%")
                appendLine("Query: does that affect stock price")
                appendLine("""{"tool": "web_search", "query": "RBI repo rate impact on Indian stock market", "max_results": 5}""")
                appendLine()
                appendLine("History: assistant: TCS reported strong earnings")
                appendLine("Query: what about its competitors")
                appendLine("""{"tool": "web_search", "query": "TCS competitors Infosys Wipro earnings comparison", "max_results": 5}""")
                appendLine()
                appendLine("CRITICAL: Output ONLY the JSON. Use conversation context to create a self-contained search query.")
            }
        } else {
            SEARCH_ENABLED
        }
    }
    
    /**
     * Summary generation prompt - for processing search results
     */
    const val SUMMARIZE_SEARCH_RESULTS = """Summarize the search results below.

Rules:
1. Be factual and specific
2. Include numbers, dates, and sources
3. Keep it 2-3 sentences
4. Don't make up information

Search results:
"""
    
    /**
     * Build optimized system prompt based on context
     * Enhanced with conversation history for better query reformulation
     */
    fun buildSystemPrompt(
        needsSearch: Boolean,
        recentContext: String? = null,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        currentQuery: String? = null
    ): String {
        return if (needsSearch) {
            // Use context-aware search prompt with conversation history
            if (conversationHistory.isNotEmpty() && currentQuery != null) {
                buildSearchPromptWithContext(currentQuery, conversationHistory)
            } else {
                SEARCH_ENABLED
            }
        } else {
            buildString {
                appendLine(BASE_CONVERSATIONAL)
            }
        }
    }
    
    /**
     * Extract topic/entity from a query for context tracking
     * Returns the main subject being discussed (company, crypto, person, etc.)
     */
    fun extractTopic(query: String): String? {
        val lowerQuery = query.lowercase()
        
        // Common stock/crypto symbols and companies
        val knownEntities = listOf(
            // Indian stocks
            "tcs", "infosys", "wipro", "reliance", "hdfc", "icici", "sbi",
            "nifty", "sensex", "bank nifty",
            // Crypto
            "bitcoin", "btc", "ethereum", "eth", "crypto", "dogecoin", "doge",
            // Tech companies
            "tesla", "apple", "google", "microsoft", "amazon", "meta", "nvidia",
            // General
            "stock", "market", "gold", "silver", "oil"
        )
        
        // Find first matching entity
        val matchedEntity = knownEntities.firstOrNull { lowerQuery.contains(it) }
        
        if (matchedEntity != null) {
            // Normalize to proper case
            return when (matchedEntity) {
                "tcs" -> "TCS"
                "btc" -> "Bitcoin"
                "eth" -> "Ethereum"
                "nifty" -> "Nifty"
                "sensex" -> "Sensex"
                else -> matchedEntity.replaceFirstChar { it.uppercase() }
            }
        }
        
        // Try to extract capitalized words (likely proper nouns)
        val words = query.split("\\s+".toRegex())
        val capitalizedWord = words.firstOrNull { word ->
            word.length > 2 && word[0].isUpperCase() && word.drop(1).any { it.isLowerCase() }
        }
        
        return capitalizedWord
    }
    
    /**
     * Expand query with context if it contains pronouns
     */
    fun expandQueryWithContext(query: String, lastTopic: String?): String {
        if (lastTopic == null) return query
        
        val lowerQuery = query.lowercase()
        
        // Check if query contains pronouns or generic references
        val hasPronoun = lowerQuery.contains("it") || 
                        lowerQuery.contains("its") || 
                        lowerQuery.contains("the news") ||
                        lowerQuery.contains("the price") ||
                        lowerQuery.contains("the stock") ||
                        lowerQuery.contains("latest") && !lowerQuery.contains(lastTopic.lowercase())
        
        if (hasPronoun) {
            // Expand query with topic
            return when {
                lowerQuery.contains("news") -> "$lastTopic news"
                lowerQuery.contains("price") -> "$lastTopic price"
                lowerQuery.contains("stock") -> "$lastTopic stock"
                lowerQuery.contains("update") -> "$lastTopic update"
                lowerQuery.contains("latest") -> "$lastTopic latest"
                else -> "$lastTopic $query"
            }
        }
        
        return query
    }
    
    /**
     * Build user message with minimal context to prevent hallucination
     */
    fun buildUserMessage(
        userQuery: String,
        recentMessages: List<Pair<String, String>> = emptyList()
    ): String {
        return buildString {
            // Only include last 1-2 messages for context (not more!)
            if (recentMessages.isNotEmpty()) {
                appendLine("Recent:")
                recentMessages.takeLast(1).forEach { (role, content) ->
                    val preview = content.take(40).replace("\n", " ")
                    appendLine("- $role: $preview")
                }
                appendLine()
            }
            
            // Current message
            appendLine("User: $userQuery")
            appendLine()
            appendLine("Your response:")
        }
    }
    
    /**
     * Build search summary prompt - optimized for factual responses
     */
    fun buildSearchSummaryPrompt(
        userQuery: String,
        searchResults: String,
        maxLength: Int = 1000
    ): String {
        // Limit search results to prevent token overflow
        val limitedResults = if (searchResults.length > maxLength) {
            searchResults.take(maxLength) + "\n... (truncated)"
        } else {
            searchResults
        }
        
        return buildString {
            appendLine("User asked: $userQuery")
            appendLine()
            appendLine("Search results:")
            appendLine(limitedResults)
            appendLine()
            appendLine("Provide a helpful 1-2 sentence answer with specific facts.")
        }
    }
    
    /**
     * Detect if query needs search
     */
    fun needsSearch(query: String): Boolean {
        val lowerQuery = query.lowercase()
        
        // Keywords that indicate need for current information
        val searchKeywords = listOf(
            "news", "latest", "today", "current", "now", "recent",
            "market", "stock", "price", "weather", "forecast",
            "update", "what's", "whats", "happening", "search",
            "find", "look up", "tell me about", "information about",
            "bitcoin", "crypto", "ethereum", "price", "market",
            "sensex", "dow", "nasdaq", "gold", "silver"
        )
        
        return searchKeywords.any { lowerQuery.contains(it) }
    }
    
    /**
     * Detect if query is context-dependent (contains pronouns or references)
     * Based on CHIQ research: these queries need conversation history for disambiguation
     */
    fun isContextDependent(query: String): Boolean {
        val lowerQuery = query.lowercase()
        
        // Pronouns and references that indicate context dependency
        val contextIndicators = listOf(
            // Pronouns
            "it", "its", "that", "this", "these", "those", "they", "them",
            // Generic references
            "the stock", "the price", "the news", "the company", "the market",
            // Comparative/follow-up phrases
            "what about", "how about", "does that", "does it", "will it",
            "affect", "impact", "change", "influence",
            // Question words without specific subject
            "why", "how", "when", "where"
        )
        
        // Check if query contains context indicators
        val hasIndicator = contextIndicators.any { lowerQuery.contains(it) }
        
        // Also check if query is very short (likely a follow-up)
        val isShort = query.split("\\s+".toRegex()).size <= 5
        
        return hasIndicator || (isShort && needsSearch(query))
    }
    
    /**
     * Clean response to remove hallucinated content
     */
    fun cleanResponse(response: String): String {
        var cleaned = response
        
        // Remove leaked prompts and instructions
        cleaned = cleaned
            .replace(Regex("Reply in \\d+-\\d+ sentences.*"), "")
            .replace(Regex("Do NOT make up conversations.*"), "")
            .replace(Regex("Respond naturally.*"), "")
            .replace(Regex("Current message from user:.*"), "")
            .replace(Regex("Your response:.*"), "")
            .replace(Regex("User:.*"), "")
            .replace(Regex("Recent:.*"), "")
            .trim()
        
        // Detect hallucinated conversations
        val hasHallucination = cleaned.contains("user:") || 
                              cleaned.contains("User:") ||
                              cleaned.contains("assistant:") ||
                              cleaned.contains("You replied:") ||
                              cleaned.contains("- User said:") ||
                              cleaned.lines().count { it.trim().startsWith("user:") || it.trim().startsWith("User:") } > 1
        
        if (hasHallucination) {
            // Extract only the first real sentence
            val lines = cleaned.lines()
            val firstRealLine = lines.firstOrNull { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && 
                !trimmed.startsWith("user:") && 
                !trimmed.startsWith("User:") &&
                !trimmed.startsWith("assistant:") &&
                !trimmed.startsWith("-") &&
                !trimmed.contains("Previous context:")
            }
            
            cleaned = firstRealLine?.take(200) ?: "I'm not sure how to respond to that."
        }
        
        return cleaned.trim()
    }
    
    /**
     * Validate response quality
     */
    fun isValidResponse(response: String): Boolean {
        val cleaned = cleanResponse(response)
        
        // Check if response is too short or invalid
        if (cleaned.length < 3 || cleaned.startsWith("Error:") || cleaned.isBlank()) {
            return false
        }
        
        // Check for hallucinated conversations
        if (cleaned.contains("user:") || cleaned.contains("User:") || cleaned.contains("assistant:")) {
            return false
        }
        
        return true
    }
}
