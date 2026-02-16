package com.confidant.ai.prompts

/**
 * LFM2Prompts - Optimized prompts for LFM2.5-1.2B-Instruct
 * 
 * Based on Liquid AI research and documentation:
 * - LFM2.5 excels at instruction following
 * - Native tool calling support with specific format
 * - Optimized for agentic tasks
 * - Best performance with concise, structured prompts
 * 
 * Key optimizations:
 * 1. Proper ChatML format with special tokens
 * 2. Concise instructions (150-200 tokens vs 286-448)
 * 3. Few-shot examples for tool calling
 * 4. Clear output format specifications
 * 5. Temperature 0.7 (optimal for LFM2.5)
 */
object LFM2Prompts {
    
    /**
     * Notification analysis prompt - OPTIMIZED for LFM2.5
     * Reduced from 286-448 tokens to ~100 tokens (65% reduction)
     * Based on LFM2.5 research: shorter prompts = faster + more accurate
     */
    fun buildNotificationAnalysisPrompt(
        appName: String,
        packageName: String,
        notificationsSummary: String,
        notificationCount: Int
    ): String {
        // ULTRA-OPTIMIZED: Minimal prompt for maximum speed and accuracy
        return """<|startoftext|><|im_start|>system
Analyze notifications. Priority: 1.0=OTP/security, 0.9=Payment fail, 0.7=Important, 0.5=Update, 0.3=Routine, 0.0=Spam
JSON: {"should_inform":bool,"priority":float,"reason":"brief","message":"text"}
<|im_end|>
<|im_start|>user
$appName ($notificationCount): $notificationsSummary
<|im_end|>
<|im_start|>assistant
"""
    }
    
    /**
     * Conversation pause analysis - ULTRA-OPTIMIZED
     * Reduced from ~200 tokens to ~100 tokens (50% reduction)
     */
    fun buildPauseAnalysisPrompt(
        conversationContext: String,
        notificationsSummary: String,
        notificationCount: Int
    ): String {
        return """<|startoftext|><|im_start|>system
Analyze if user needs to know about notifications based on recent conversation.

Consider: Related to topic? Time-sensitive? Actionable?

JSON: {"should_inform":bool,"priority":float,"reason":"why","message":"text"}
<|im_end|>
<|im_start|>user
Recent:
$conversationContext

New ($notificationCount):
$notificationsSummary
<|im_end|>
<|im_start|>assistant
"""
    }
    
    /**
     * Priority check for single high-priority notification
     * ULTRA-FAST: ~50 tokens for sub-second analysis
     */
    fun buildPriorityCheckPrompt(
        appName: String,
        title: String,
        text: String,
        extractedInfo: String?
    ): String {
        val info = if (extractedInfo != null) "\n$extractedInfo" else ""
        
        return """<|startoftext|><|im_start|>system
Quick priority check.
JSON: {"priority":float,"is_urgent":bool,"reason":"brief"}
<|im_end|>
<|im_start|>user
$appName: $title
$text$info
<|im_end|>
<|im_start|>assistant
"""
    }
    
    /**
     * Tool calling format for LFM2.5 - STRICT LFM2.5 FORMAT
     * MANDATORY FORMAT: <|startoftext|><|im_start|>system\nList of tools: [{...}]\n\n[instructions]<|im_end|>
     * Uses exact LFM2.5 specification with proper ChatML structure
     */
    fun buildToolCallingPrompt(
        systemPrompt: String,
        userMessage: String,
        availableTools: List<ToolDefinition>
    ): String {
        // STRICT FORMAT: Tool definitions as JSON array
        val toolsJsonArray = org.json.JSONArray()
        availableTools.forEach { tool ->
            val toolJson = org.json.JSONObject().apply {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", org.json.JSONObject(tool.parametersJson))
            }
            toolsJsonArray.put(toolJson)
        }
        
        // STRICT LFM2.5 CHATML FORMAT
        return buildString {
            append("<|startoftext|>")
            append("<|im_start|>system\n")
            append("List of tools: ")
            append(toolsJsonArray.toString())
            append("\n\n")
            append(systemPrompt)
            append("\n\n")
            append("Output format: <|tool_call_start|>[function_name(arg=\"value\")]<|tool_call_end|>")
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userMessage)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }
    
    /**
     * Conversational response - ULTRA-MINIMAL for speed
     * Reduced from ~100 tokens to ~50 tokens (50% reduction)
     * Based on LFM2.5 research: minimal system prompts work best
     */
    fun buildConversationalPrompt(
        userMessage: String,
        recentContext: String? = null,
        currentDate: String = "Feb 15, 2026"
    ): String {
        val ctx = if (recentContext != null) "\nContext: $recentContext" else ""
        
        return """<|startoftext|><|im_start|>system
Confidant AI. Date: $currentDate. Be helpful, concise, honest.
<|im_end|>
<|im_start|>user
$userMessage$ctx
<|im_end|>
<|im_start|>assistant
"""
    }
    
    /**
     * Search-augmented response - with grounding instructions
     * Prevents hallucination by enforcing source usage
     */
    fun buildSearchResponsePrompt(
        userQuery: String,
        searchResults: String,
        currentDate: String
    ): String {
        // CRITICAL FIX: Must end with "assistant\n" (with newline) for LFM2.5
        return """<|startoftext|><|im_start|>system
You are Confidant Bot, a helpful AI assistant.
Current date: $currentDate

CRITICAL RULES:
1. Use ONLY information from the search results
2. Include specific numbers, prices, and dates
3. If results don't contain the answer, say 'I don't have current information'
4. NEVER make up information
5. Always mention dates for time-sensitive data

Be direct and concise (2-3 sentences).
<|im_end|>
<|im_start|>user
Current date: $currentDate
User question: $userQuery

Search results:
$searchResults

Answer the question using ONLY the search results.
<|im_end|>
<|im_start|>assistant
"""
    }
    
    /**
     * Parse tool calls from LFM2.5 output
     * Handles both formats: special tokens and JSON
     */
    fun parseToolCalls(response: String): List<ParsedToolCall> {
        val toolCalls = mutableListOf<ParsedToolCall>()
        
        // Format 1: Special tokens (preferred)
        val tokenPattern = "<\\|tool_call_start\\|>\\s*\\[([^\\]]+)\\]\\s*<\\|tool_call_end\\|>".toRegex()
        tokenPattern.findAll(response).forEach { match ->
            val callStr = match.groupValues[1]
            val parsed = parseToolCallString(callStr)
            if (parsed != null) {
                toolCalls.add(parsed)
            }
        }
        
        // Format 2: JSON (fallback)
        if (toolCalls.isEmpty()) {
            val jsonPattern = "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{([^}]+)\\}\\s*\\}".toRegex()
            jsonPattern.findAll(response).forEach { match ->
                val name = match.groupValues[1]
                val argsStr = match.groupValues[2]
                val args = parseJsonArguments(argsStr)
                toolCalls.add(ParsedToolCall(name, args))
            }
        }
        
        return toolCalls
    }
    
    /**
     * Parse tool call string: function_name(arg1="value1", arg2="value2")
     */
    private fun parseToolCallString(callStr: String): ParsedToolCall? {
        val functionPattern = "([a-zA-Z_]+)\\((.*)\\)".toRegex()
        val match = functionPattern.matchEntire(callStr.trim()) ?: return null
        
        val functionName = match.groupValues[1]
        val argsStr = match.groupValues[2]
        
        val args = mutableMapOf<String, String>()
        val argPattern = "([a-zA-Z_]+)\\s*=\\s*\"([^\"]+)\"".toRegex()
        argPattern.findAll(argsStr).forEach { argMatch ->
            val key = argMatch.groupValues[1]
            val value = argMatch.groupValues[2]
            args[key] = value
        }
        
        return ParsedToolCall(functionName, args)
    }
    
    /**
     * Parse JSON arguments: "key1": "value1", "key2": "value2"
     */
    private fun parseJsonArguments(argsStr: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        val argPattern = "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        argPattern.findAll(argsStr).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            args[key] = value
        }
        return args
    }
    
    /**
     * Validate LFM2.5 response quality
     */
    fun isValidResponse(response: String): Boolean {
        // Check if response is empty or just EOS
        if (response.isBlank() || response.trim() == "<|im_end|>") {
            return false
        }
        
        // Check if response contains actual content
        val contentLength = response
            .replace("<|im_end|>", "")
            .replace("<|tool_call_start|>", "")
            .replace("<|tool_call_end|>", "")
            .trim()
            .length
        
        return contentLength > 5
    }
}

/**
 * Tool definition for LFM2.5
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String
)

/**
 * Parsed tool call from LFM2.5 output
 */
data class ParsedToolCall(
    val name: String,
    val arguments: Map<String, String>
)
