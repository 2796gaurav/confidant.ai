package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * FunctionCallingSystem - Manages tool definitions and execution
 * Supports web search and notes management
 */
class FunctionCallingSystem(private val context: Context) {
    
    private val searchTool = DuckDuckGoSearchTool(context)
    private val newsTool = NewsSearchTool(context)
    private val weatherTool = WeatherTool(context)
    private val notesTool = NotesTool(context)
    private val notificationSearchTool = NotificationSearchTool(context)
    
    /**
     * Get available tools - STRICT: Only 4 tools allowed
     * 1. web_search - General web search
     * 2. save_note - Save personal notes/data
     * 3. retrieve_notes - Retrieve saved notes
     * 4. search_notifications - Search notifications
     */
    fun getAvailableTools(
        searchEnabled: Boolean = true,
        newsEnabled: Boolean = false,  // DISABLED - not in allowed list
        weatherEnabled: Boolean = false,  // DISABLED - not in allowed list
        notesEnabled: Boolean = true,
        notificationsEnabled: Boolean = true
    ): List<ToolDefinition> {
        val tools = mutableListOf<ToolDefinition>()
        
        if (searchEnabled) {
            tools.add(searchTool.getDefinition())
        }
        // newsEnabled and weatherEnabled are disabled - not in allowed tool list
        if (notesEnabled) {
            // ONLY expose save_note and retrieve_notes - remove other note tools
            val noteDefinitions = notesTool.getDefinitions()
            tools.addAll(noteDefinitions.filter { 
                it.name == "save_note" || it.name == "retrieve_notes" 
            })
        }
        if (notificationsEnabled) {
            tools.add(notificationSearchTool.getDefinition())
        }
        
        return tools
    }
    
    /**
     * Get system prompt with tool descriptions (STRICT LFM2.5 FORMAT)
     * MANDATORY FORMAT: <|startoftext|><|im_start|>system\nList of tools: [{...}]\n\n[instructions]<|im_end|>
     * This follows the exact LFM2.5 specification for tool calling
     */
    fun getSystemPromptWithTools(
        basePrompt: String,
        searchEnabled: Boolean = true,
        newsEnabled: Boolean = false,  // DISABLED - not in allowed list
        weatherEnabled: Boolean = false,  // DISABLED - not in allowed list
        notesEnabled: Boolean = true,
        notificationsEnabled: Boolean = true
    ): String {
        val tools = getAvailableTools(searchEnabled, newsEnabled, weatherEnabled, notesEnabled, notificationsEnabled)
        
        if (tools.isEmpty()) {
            return basePrompt
        }
        
        // STRICT LFM2.5 FORMAT: Tool definitions as JSON array
        val toolsJsonArray = org.json.JSONArray()
        tools.forEach { tool ->
            val propertiesJson = org.json.JSONObject()
            tool.parameters.forEach { param ->
                propertiesJson.put(param.name, org.json.JSONObject().apply {
                    put("type", param.type)
                    put("description", param.description)
                })
            }
            
            val requiredJson = org.json.JSONArray()
            tool.parameters.filter { it.required }.forEach { requiredJson.put(it.name) }
            
            val toolJson = org.json.JSONObject().apply {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", org.json.JSONObject().apply {
                    put("type", "object")
                    put("properties", propertiesJson)
                    put("required", requiredJson)
                })
            }
            toolsJsonArray.put(toolJson)
        }
        
        // STRICT LFM2.5 FORMAT: "List of tools: [{...}]" exactly as specified
        // Format matches: <|startoftext|><|im_start|>system\nList of tools: [{...}]\n\n[instructions]<|im_end|>
        // Note: ChatML wrapper (<|startoftext|><|im_start|>system\n...<|im_end|>) is added by native code
        return buildString {
            append("List of tools: ")
            append(toolsJsonArray.toString())
            append("\n\n")
            append(basePrompt)
            append("\n\n")
            append("Output format: <|tool_call_start|>[function_name(arg=\"value\")]<|tool_call_end|>")
            append("\n")
            append("Available functions: web_search, save_note, retrieve_notes, search_notifications")
        }
    }
    
    /**
     * Parse function call from LLM response
     * Supports multiple formats: LFM2.5 Pythonic, JSON, XML
     * Priority: LFM2.5 > JSON > XML (for best compatibility)
     */
    fun parseFunctionCall(response: String): FunctionCall? {
        return try {
            Log.d(TAG, "Parsing response for function call...")
            Log.d(TAG, "Response length: ${response.length} chars")
            Log.d(TAG, "Full response:\n$response")
            
            // Try LFM2.5 Pythonic format first (official format)
            val lfmResult = parseLfm25Format(response)
            if (lfmResult != null) {
                Log.i(TAG, "✓ Successfully parsed LFM2.5 Pythonic function call: ${lfmResult.name}")
                return lfmResult
            }
            
            // Try JSON format (legacy/fallback)
            val jsonResult = parseJsonFormat(response)
            if (jsonResult != null) {
                Log.i(TAG, "✓ Successfully parsed JSON function call: ${jsonResult.name}")
                return jsonResult
            }
            
            // Try XML format (legacy)
            val xmlResult = parseXmlFormat(response)
            if (xmlResult != null) {
                Log.i(TAG, "✓ Successfully parsed XML function call: ${xmlResult.name}")
                return xmlResult
            }
            
            Log.w(TAG, "No function call found in any supported format")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse function call", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            null
        }
    }
    
    /**
     * Parse LFM2.5 Pythonic format (STRICT OFFICIAL FORMAT)
     * MANDATORY FORMAT: <|tool_call_start|>[function_name(arg1="value1", arg2="value2")]<|tool_call_end|>
     * 
     * This is the ONLY format we accept - no fallbacks to maintain strict compliance
     * Examples:
     * <|tool_call_start|>[web_search(query="Bitcoin price", max_results="5")]<|tool_call_end|>
     * <|tool_call_start|>[get_weather(location="Paris")]<|tool_call_end|>
     * <|tool_call_start|>[save_note(title="Test", content="Content")]<|tool_call_end|>
     */
    private fun parseLfm25Format(response: String): FunctionCall? {
        return try {
            // STRICT PATTERN: Only accept exact LFM2.5 format
            // Pattern: <|tool_call_start|>[function_name(args)]<|tool_call_end|>
            val strictPattern = """<\|tool_call_start\|>\[(\w+)\((.*?)\)\]<\|tool_call_end\|>"""
            val regex = strictPattern.toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(response.trim())
            
            if (match == null) {
                Log.d(TAG, "Response does not match strict LFM2.5 format")
                return null
            }
            
            val name = match.groupValues[1]
            val argsStr = match.groupValues[2]
            
            // STRICT: Only allow these 4 functions
            val knownFunctions = listOf(
                "web_search",      // General web search
                "save_note",       // Save personal notes
                "retrieve_notes",  // Retrieve saved notes
                "search_notifications"  // Search notifications
            )
            
            if (name !in knownFunctions) {
                Log.w(TAG, "Unknown function name: $name")
                return null
            }
            
            Log.d(TAG, "Found function call: $name with args: $argsStr")
            
            // Parse arguments: arg1="value1", arg2="value2"
            val arguments = mutableMapOf<String, String>()
            
            // Handle both quoted and unquoted values
            val argRegex = """(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\d+))""".toRegex()
            argRegex.findAll(argsStr).forEach { argMatch ->
                val argName = argMatch.groupValues[1]
                // Try quoted values first, then numeric
                val argValue = argMatch.groupValues[2].ifEmpty {
                    argMatch.groupValues[3].ifEmpty {
                        argMatch.groupValues[4]
                    }
                }
                arguments[argName] = argValue
            }
            
            Log.d(TAG, "Parsed function call - name: $name, args: $arguments")
            FunctionCall(name, arguments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LFM2.5 format: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse JSON format: {"tool": "web_search", "query": "...", "max_results": 5}
     */
    private fun parseJsonFormat(response: String): FunctionCall? {
        return try {
            // Look for JSON object
            val jsonRegex = """\{[^}]*"tool"\s*:\s*"([^"]+)"[^}]*\}""".toRegex()
            val match = jsonRegex.find(response) ?: return null
            
            val jsonStr = match.value
            Log.d(TAG, "Found JSON: $jsonStr")
            
            val json = org.json.JSONObject(jsonStr)
            val toolName = json.getString("tool")
            
            val arguments = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                if (key != "tool") {
                    arguments[key] = json.get(key).toString()
                }
            }
            
            Log.d(TAG, "Parsed JSON - tool: $toolName, args: $arguments")
            FunctionCall(toolName, arguments)
        } catch (e: Exception) {
            Log.d(TAG, "Not JSON format: ${e.message}")
            null
        }
    }
    
    /**
     * Parse XML format: <function_call><name>...</name><arguments>...</arguments></function_call>
     */
    private fun parseXmlFormat(response: String): FunctionCall? {
        return try {
            val functionCallRegex = """<function_call>(.*?)</function_call>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = functionCallRegex.find(response) ?: return null
            
            val callContent = match.groupValues[1]
            Log.d(TAG, "Found XML function call content: $callContent")
            
            val nameRegex = """<name>(.*?)</name>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val name = nameRegex.find(callContent)?.groupValues?.get(1)?.trim() ?: return null
            
            val argsRegex = """<arguments>(.*?)</arguments>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val argsContent = argsRegex.find(callContent)?.groupValues?.get(1) ?: ""
            
            val arguments = mutableMapOf<String, String>()
            val argRegex = """<(\w+)>(.*?)</\1>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            argRegex.findAll(argsContent).forEach { argMatch ->
                val argName = argMatch.groupValues[1]
                val argValue = argMatch.groupValues[2].trim()
                arguments[argName] = argValue
            }
            
            Log.d(TAG, "Parsed XML - name: $name, args: $arguments")
            FunctionCall(name, arguments)
        } catch (e: Exception) {
            Log.d(TAG, "Not XML format: ${e.message}")
            null
        }
    }
    
    /**
     * Parse LFM2.5 format: <|tool_call_start|>function_name(arg="value")<|tool_call_end|>
     * DEPRECATED: Use parseLfm25Format instead (renamed for clarity)
     */
    private fun parseLfmFormat(response: String): FunctionCall? {
        return parseLfm25Format(response)
    }
    
    /**
     * Execute function call
     */
    suspend fun executeFunction(call: FunctionCall): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Executing function: ${call.name} with args: ${call.arguments}")
            
            // STRICT: Only execute allowed functions
            val result = when (call.name) {
                "web_search" -> searchTool.execute(call.arguments)
                "save_note" -> notesTool.execute(call.name, call.arguments)
                "retrieve_notes" -> notesTool.execute(call.name, call.arguments)
                "search_notifications" -> notificationSearchTool.execute(call.arguments)
                else -> Result.failure(Exception("Unknown or disabled function: ${call.name}. Allowed functions: web_search, save_note, retrieve_notes, search_notifications"))
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Function execution failed", e)
            Result.failure(e)
        }
    }
    
    companion object {
        private const val TAG = "FunctionCallingSystem"
    }
}

/**
 * Tool definition
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
)

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

/**
 * Function call parsed from LLM response
 */
data class FunctionCall(
    val name: String,
    val arguments: Map<String, String>
)
