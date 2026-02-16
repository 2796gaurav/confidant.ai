package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
// ToolExecutionLogEntity removed - tool calls stored in conversations table
// import com.confidant.ai.database.entity.ToolExecutionLogEntity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.Instant

/**
 * EnhancedFunctionCallingSystem - Advanced tool execution with:
 * - Real-time user notifications
 * - Parallel execution support
 * - Execution logging
 * - Tool chaining
 * - Status updates
 */
class EnhancedFunctionCallingSystem(
    private val context: Context,
    private val database: AppDatabase,
    private val statusCallback: suspend (String) -> Unit
) {
    
    private val searchTool = DuckDuckGoSearchTool()
    private val contentFetcher = WebContentFetcher()
    
    /**
     * Execute function with real-time status updates
     */
    suspend fun executeWithNotifications(
        call: FunctionCall,
        userQuery: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Notify user that tool is being invoked
            statusCallback("🔧 Invoking tool: ${call.name}...")
            Log.i(TAG, "=== Tool Execution Started ===")
            Log.i(TAG, "Tool: ${call.name}")
            Log.i(TAG, "Arguments: ${call.arguments}")
            
            // Provide specific status based on tool
            val toolDescription = getToolDescription(call.name)
            statusCallback("⚙️ $toolDescription")
            
            // Execute the tool
            val result = executeFunction(call)
            
            val executionTime = System.currentTimeMillis() - startTime
            
            // Log execution
            logExecution(
                toolName = call.name,
                arguments = call.arguments,
                result = result,
                executionTimeMs = executionTime,
                userQuery = userQuery
            )
            
            if (result.isSuccess) {
                statusCallback("✅ Tool execution completed in ${executionTime}ms")
                Log.i(TAG, "✅ Tool execution successful (${executionTime}ms)")
            } else {
                statusCallback("❌ Tool execution failed: ${result.exceptionOrNull()?.message}")
                Log.e(TAG, "❌ Tool execution failed", result.exceptionOrNull())
            }
            
            result
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "Tool execution exception", e)
            statusCallback("❌ Error: ${e.message}")
            
            logExecution(
                toolName = call.name,
                arguments = call.arguments,
                result = Result.failure(e),
                executionTimeMs = executionTime,
                userQuery = userQuery
            )
            
            Result.failure(e)
        }
    }
    
    /**
     * Execute multiple tools in parallel
     */
    suspend fun executeParallel(
        calls: List<FunctionCall>,
        userQuery: String? = null
    ): List<Result<String>> = withContext(Dispatchers.IO) {
        statusCallback("🔧 Executing ${calls.size} tools in parallel...")
        
        val results = calls.map { call ->
            async {
                executeWithNotifications(call, userQuery)
            }
        }.awaitAll()
        
        val successCount = results.count { it.isSuccess }
        statusCallback("✅ Completed: $successCount/${calls.size} successful")
        
        results
    }
    
    /**
     * Execute function with enhanced web search (parallel URL fetching)
     */
    private suspend fun executeFunction(call: FunctionCall): Result<String> {
        return when {
            call.name == "web_search" -> executeEnhancedSearch(call.arguments)
            else -> Result.failure(Exception("Unknown function: ${call.name}"))
        }
    }
    
    /**
     * Enhanced web search with parallel content fetching
     */
    private suspend fun executeEnhancedSearch(arguments: Map<String, String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val query = arguments["query"] ?: return@withContext Result.failure(
                Exception("Missing required parameter: query")
            )
            val maxResults = arguments["max_results"]?.toIntOrNull() ?: 5
            val deepFetch = arguments["deep_fetch"]?.toBoolean() ?: shouldDeepFetch(query)
            
            statusCallback("🔍 Searching for: \"$query\"...")
            
            // Execute search
            val searchResult = searchTool.execute(arguments)
            
            if (searchResult.isFailure) {
                return@withContext searchResult
            }
            
            val searchData = searchResult.getOrNull() ?: return@withContext searchResult
            
            // If deep fetch is enabled, fetch content in parallel
            if (deepFetch) {
                statusCallback("🌐 Fetching content from multiple sources in parallel...")
                
                // Extract URLs from search results
                val urls = extractUrls(searchData).take(3)
                
                if (urls.isNotEmpty()) {
                    statusCallback("📥 Fetching ${urls.size} articles...")
                    
                    // Parallel fetch
                    val contents = contentFetcher.fetchMultipleUrls(urls)
                    
                    val successCount = contents.size
                    statusCallback("✅ Fetched $successCount/${urls.size} articles successfully")
                    
                    // Combine search results with fetched content
                    val enhancedResult = combineSearchAndContent(searchData, contents)
                    return@withContext Result.success(enhancedResult)
                }
            }
            
            searchResult
        } catch (e: Exception) {
            Log.e(TAG, "Enhanced search failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Log tool execution to database
     */
    private suspend fun logExecution(
        toolName: String,
        arguments: Map<String, String>,
        result: Result<String>,
        executionTimeMs: Long,
        userQuery: String?
    ) {
        try {
            // Tool execution log table removed - logging skipped
            // Tool calls are now stored in conversations table
            /*
            val log = ToolExecutionLogEntity(
                toolName = toolName,
                arguments = JSONObject(arguments).toString(),
                result = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Unknown error",
                success = result.isSuccess,
                executionTimeMs = executionTimeMs,
                userQuery = userQuery,
                timestamp = Instant.now()
            )
            
            database.toolExecutionLogDao().insert(log)
            */
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log execution", e)
        }
    }
    
    /**
     * Get human-readable tool description
     */
    private fun getToolDescription(toolName: String): String {
        return when (toolName) {
            "web_search" -> "Searching the web for current information..."
            else -> "Executing $toolName..."
        }
    }
    
    /**
     * Determine if query needs deep content fetching
     */
    private fun shouldDeepFetch(query: String): Boolean {
        val deepFetchKeywords = listOf(
            "news", "article", "latest", "today", "market", "stock",
            "price", "analysis", "report", "update", "breaking"
        )
        return deepFetchKeywords.any { query.contains(it, ignoreCase = true) }
    }
    
    /**
     * Extract URLs from search results
     */
    private fun extractUrls(searchData: String): List<String> {
        val urlRegex = """https?://[^\s<>"]+""".toRegex()
        return urlRegex.findAll(searchData).map { it.value }.toList()
    }
    
    /**
     * Combine search results with fetched content
     */
    private fun combineSearchAndContent(
        searchData: String,
        contents: List<com.confidant.ai.integrations.PageContent>
    ): String {
        return buildString {
            appendLine("=== SEARCH RESULTS ===")
            appendLine(searchData)
            appendLine()
            
            if (contents.isNotEmpty()) {
                appendLine("=== DETAILED CONTENT ===")
                contents.forEachIndexed { index, pageContent ->
                    appendLine("--- Article ${index + 1}: ${pageContent.title} ---")
                    appendLine("Source: ${pageContent.domain}")
                    appendLine(pageContent.content.take(1000)) // Limit content length
                    appendLine()
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "EnhancedFunctionCalling"
    }
}
