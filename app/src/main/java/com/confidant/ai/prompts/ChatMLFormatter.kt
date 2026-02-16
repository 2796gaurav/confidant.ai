package com.confidant.ai.prompts

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ChatMLFormatter - Proper chat template formatting for LFM2.5-1.2B-Instruct
 * 
 * LFM2.5 uses ChatML format with special tokens:
 * - <|startoftext|> - Start of conversation
 * - <|im_start|> - Start of message
 * - <|im_end|> - End of message
 * - Roles: system, user, assistant, tool
 * 
 * Reference: https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct
 */
object ChatMLFormatter {
    private const val START_OF_TEXT = "<|startoftext|>"
    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"
    private const val TOOL_CALL_START = "<|tool_call_start|>"
    private const val TOOL_CALL_END = "<|tool_call_end|>"
    
    /**
     * Format a basic chat prompt with system and user messages
     */
    fun formatChatPrompt(
        systemPrompt: String,
        userMessage: String,
        includeAssistantStart: Boolean = true
    ): String {
        return buildString {
            append(START_OF_TEXT)
            append(IM_START)
            append("system\n")
            append(systemPrompt)
            append(IM_END)
            append("\n")
            append(IM_START)
            append("user\n")
            append(userMessage)
            append(IM_END)
            append("\n")
            if (includeAssistantStart) {
                append(IM_START)
                append("assistant\n")
            }
        }
    }
    
    /**
     * Format prompt with search context for factual queries
     * Includes current date and structured search results
     */
    fun formatWithSearchContext(
        systemPrompt: String,
        query: String,
        searchContext: String,
        currentDate: String = getCurrentDate()
    ): String {
        val userMessage = buildString {
            appendLine("Current date: $currentDate")
            appendLine()
            appendLine("User question: $query")
            appendLine()
            appendLine("Search results:")
            appendLine(searchContext)
            appendLine()
            appendLine("Provide a direct, factual answer with specific numbers and dates from the search results.")
        }
        
        return formatChatPrompt(systemPrompt, userMessage)
    }
    
    /**
     * Format conversational prompt (no search)
     */
    fun formatConversational(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        return buildString {
            append(START_OF_TEXT)
            append(IM_START)
            append("system\n")
            append(systemPrompt)
            append(IM_END)
            append("\n")
            
            // Add conversation history
            conversationHistory.takeLast(3).forEach { (role, content) ->
                append(IM_START)
                append("$role\n")
                append(content)
                append(IM_END)
                append("\n")
            }
            
            // Add current user message
            append(IM_START)
            append("user\n")
            append(userMessage)
            append(IM_END)
            append("\n")
            append(IM_START)
            append("assistant\n")
        }
    }
    
    /**
     * Get current date in readable format
     */
    private fun getCurrentDate(): String {
        val now = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
        return now.format(formatter)  // "February 13, 2026"
    }
    
    /**
     * Format prompt with tool definitions for proper LFM2.5 tool calling
     * STRICT LFM2.5 FORMAT: List of tools: [{...}] in system prompt
     * Used for intent detection and function calling
     */
    fun formatWithTools(
        systemPrompt: String,
        userMessage: String,
        tools: List<Map<String, Any>>? = null,
        includeAssistantStart: Boolean = true
    ): String {
        return buildString {
            append(START_OF_TEXT)
            append(IM_START)
            append("system\n")
            
            // STRICT LFM2.5 FORMAT: Tool definitions must be "List of tools: [{...}]"
            if (tools != null && tools.isNotEmpty()) {
                val toolsJson = com.google.gson.Gson().toJson(tools)
                append("List of tools: ")
                append(toolsJson)
                append("\n\n")
            }
            
            append(systemPrompt)
            append(IM_END)
            append("\n")
            append(IM_START)
            append("user\n")
            append(userMessage)
            append(IM_END)
            append("\n")
            if (includeAssistantStart) {
                append(IM_START)
                append("assistant\n")
            }
        }
    }
    
    /**
     * Format tool execution result as LFM2.5 tool message
     * STRICT FORMAT: <|im_start|>tool\n[result]<|im_end|>
     */
    fun formatToolResult(toolResult: String): String {
        return buildString {
            append(IM_START)
            append("tool\n")
            append(toolResult)
            append(IM_END)
            append("\n")
        }
    }
    
    /**
     * Format complete tool calling conversation flow
     * 1. System with tools
     * 2. User query
     * 3. Assistant tool call
     * 4. Tool result
     * 5. Assistant final response
     */
    fun formatToolCallingFlow(
        systemPrompt: String,
        toolsJson: String,
        userQuery: String,
        toolCall: String,
        toolResult: String,
        assistantResponse: String? = null
    ): String {
        return buildString {
            // System prompt with tools
            append(START_OF_TEXT)
            append(IM_START)
            append("system\n")
            append("List of tools: ")
            append(toolsJson)
            append("\n\n")
            append(systemPrompt)
            append(IM_END)
            append("\n")
            
            // User query
            append(IM_START)
            append("user\n")
            append(userQuery)
            append(IM_END)
            append("\n")
            
            // Assistant tool call
            append(IM_START)
            append("assistant\n")
            append(toolCall)
            if (assistantResponse != null) {
                append(assistantResponse)
            }
            append(IM_END)
            append("\n")
            
            // Tool result
            append(formatToolResult(toolResult))
            
            // Assistant final response (if provided)
            if (assistantResponse != null) {
                append(IM_START)
                append("assistant\n")
                append(assistantResponse)
                append(IM_END)
            }
        }
    }
    
    /**
     * Parse tool call from LFM2.5 response
     * Format: <|tool_call_start|>[function_name(param="value")]<|tool_call_end|>
     */
    fun parseToolCall(response: String): ToolCall? {
        val toolCallRegex = Regex("""<\|tool_call_start\|>\[(.*?)\((.*?)\)\]<\|tool_call_end\|>""")
        val match = toolCallRegex.find(response) ?: return null
        
        val functionName = match.groupValues[1]
        val paramsString = match.groupValues[2]
        
        // Parse parameters (simple key="value" format)
        val params = mutableMapOf<String, String>()
        val paramRegex = Regex("""(\w+)="([^"]*?)"""")
        paramRegex.findAll(paramsString).forEach { paramMatch ->
            val key = paramMatch.groupValues[1]
            val value = paramMatch.groupValues[2]
            params[key] = value
        }
        
        return ToolCall(functionName, params)
    }
    
    /**
     * Check if response contains a tool call
     */
    fun hasToolCall(response: String): Boolean {
        return response.contains(TOOL_CALL_START) && response.contains(TOOL_CALL_END)
    }
}

/**
 * Represents a parsed tool call from LFM2.5
 */
data class ToolCall(
    val functionName: String,
    val parameters: Map<String, String>
)
