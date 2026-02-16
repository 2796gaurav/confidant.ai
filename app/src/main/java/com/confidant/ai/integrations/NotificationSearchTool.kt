package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * NotificationSearchTool - Tool for searching user's notifications
 * 
 * Provides LLM with ability to:
 * - Search notifications by app, content, or time
 * - Get recent notifications
 * - Find notifications from specific apps (mail, messages, etc.)
 */
class NotificationSearchTool(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    
    /**
     * Get tool definition for LFM2.5
     */
    fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = "search_notifications",
            description = "Search and retrieve user's notifications. Use when user asks about notifications, updates, mail, messages, alerts, or any app notifications. Can search by app name, content, or time period.",
            parameters = listOf(
                ToolParameter("query", "string", "Search query - can be app name (e.g., 'gmail', 'whatsapp'), content keywords, or 'recent'/'latest' for recent notifications", required = false),
                ToolParameter("app_name", "string", "Filter by specific app name (e.g., 'Gmail', 'WhatsApp', 'Telegram')", required = false),
                ToolParameter("hours", "string", "Search notifications from last N hours (default: 24)", required = false),
                ToolParameter("limit", "string", "Maximum number of results (default: 10)", required = false)
            )
        )
    }
    
    /**
     * Execute notification search
     */
    suspend fun execute(arguments: Map<String, String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Executing notification search with args: $arguments")
            
            val query = arguments["query"]?.lowercase()?.trim()
            val appName = arguments["app_name"]
            val hours = arguments["hours"]?.toIntOrNull() ?: 24
            val limit = arguments["limit"]?.toIntOrNull() ?: 10
            
            val notifications = when {
                // Search by app name if specified
                appName != null -> {
                    database.notificationDao().searchNotifications(
                        query = appName,
                        hours = hours
                    )
                }
                // Search by query if provided
                query != null && query.isNotBlank() -> {
                    when {
                        query == "recent" || query == "latest" || query == "new" -> {
                            // Get recent notifications
                            database.notificationDao().getRecentNotifications(limit)
                        }
                        query.contains("mail") || query.contains("email") || query.contains("gmail") -> {
                            // Search for mail-related notifications
                            database.notificationDao().searchNotifications(
                                query = "mail",
                                hours = hours
                            )
                        }
                        else -> {
                            // General search
                            database.notificationDao().searchNotifications(
                                query = query,
                                hours = hours
                            )
                        }
                    }
                }
                // Default: get recent notifications
                else -> {
                    database.notificationDao().getRecentNotifications(limit)
                }
            }.take(limit)
            
            if (notifications.isEmpty()) {
                val queryDesc = when {
                    appName != null -> "from app '$appName'"
                    query != null -> "matching '$query'"
                    else -> "recent"
                }
                return@withContext Result.success(
                    "No notifications found $queryDesc in the last $hours hours.\n\n" +
                    "Try searching with different keywords or check a different time period."
                )
            }
            
            // Format notifications
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")
                .withZone(ZoneId.systemDefault())
            
            val notificationsText = notifications.mapIndexed { index, notification ->
                val timeStr = formatter.format(notification.timestamp)
                val text = notification.text.ifEmpty { notification.bigText ?: "" }
                val title = notification.title.ifEmpty { "Notification" }
                
                """
                📱 ${index + 1}. $title
                   App: ${notification.appName} | Time: $timeStr
                   ${text.take(150)}${if (text.length > 150) "..." else ""}
                """.trimIndent()
            }.joinToString("\n\n")
            
            val summary = when {
                appName != null -> "Found ${notifications.size} notification(s) from $appName"
                query != null -> "Found ${notifications.size} notification(s) matching '$query'"
                else -> "Found ${notifications.size} recent notification(s)"
            }
            
            Result.success("""
                ✅ $summary:
                
                $notificationsText
                
                💡 Tip: You can search by app name (e.g., "Gmail notifications") or content keywords.
            """.trimIndent())
            
        } catch (e: Exception) {
            Log.e(TAG, "Notification search failed", e)
            Result.failure(e)
        }
    }
    
    companion object {
        private const val TAG = "NotificationSearchTool"
    }
}
