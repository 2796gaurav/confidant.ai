package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.notes.NotesManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * NotesTool - Tool for saving and retrieving notes
 * 
 * Provides LLM with ability to:
 * - Save notes from user requests
 * - Retrieve notes by search
 * - List recent notes
 * - Manage reminders
 */
class NotesTool(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val notesManager = NotesManager(context, database)
    private val smartRetrieval by lazy {
        com.confidant.ai.notes.SmartNoteRetrieval(
            context,
            database,
            com.confidant.ai.ConfidantApplication.instance.llmEngine
        )
    }
    
    init {
        // Initialize notes manager
        runBlocking {
            notesManager.initialize()
            smartRetrieval.initialize()
        }
    }
    
    /**
     * Get tool definitions for LFM2.5
     */
    fun getDefinitions(): List<ToolDefinition> {
        return listOf(
            // Save note - SIMPLIFIED
            ToolDefinition(
                name = "save_note",
                description = "Save anything the user wants to remember. Works with ANY content - passwords, reminders, ideas, notes, etc. No need to specify category or tags, they're auto-detected.",
                parameters = listOf(
                    ToolParameter("content", "string", "The content to save (required). Can be anything - password, reminder, idea, note, etc.", required = true),
                    ToolParameter("title", "string", "Optional title. If not provided, will be auto-generated from content.", required = false),
                    ToolParameter("reminder", "string", "Optional reminder time in natural language (e.g., 'tomorrow at 3pm')", required = false)
                )
            ),
            
            // Retrieve notes - ENHANCED
            ToolDefinition(
                name = "retrieve_notes",
                description = "Search and retrieve saved notes. Uses smart hybrid search (semantic + keyword + fuzzy) for best results. Use when user asks to find, recall, or look up saved information.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search query (keywords or natural language)", required = true),
                    ToolParameter("category", "string", "Filter by category: passwords, work, personal, reminders, shopping, health, ideas, general (optional)", required = false),
                    ToolParameter("limit", "string", "Maximum number of results (default: 10)", required = false)
                )
            ),
            
            // List notes
            ToolDefinition(
                name = "list_notes",
                description = "List recent notes. Use when user asks to see their notes or what they've saved.",
                parameters = listOf(
                    ToolParameter("category", "string", "Filter by category (optional)", required = false),
                    ToolParameter("limit", "string", "Maximum number of results (default: 10)", required = false)
                )
            ),
            
            // Update note
            ToolDefinition(
                name = "update_note",
                description = "Update an existing note. Use when user asks to modify or edit a saved note.",
                parameters = listOf(
                    ToolParameter("note_id", "string", "ID of the note to update", required = true),
                    ToolParameter("title", "string", "New title (optional)", required = false),
                    ToolParameter("content", "string", "New content (optional)", required = false),
                    ToolParameter("tags", "string", "New tags (optional)", required = false),
                    ToolParameter("category", "string", "New category (optional)", required = false)
                )
            ),
            
            // Delete note
            ToolDefinition(
                name = "delete_note",
                description = "Delete a note. Use when user asks to remove or delete a saved note.",
                parameters = listOf(
                    ToolParameter("note_id", "string", "ID of the note to delete", required = true)
                )
            ),
            
            // Get reminders
            ToolDefinition(
                name = "get_reminders",
                description = "Get upcoming reminders. Use when user asks about their reminders or what they need to remember.",
                parameters = listOf()
            )
        )
    }
    
    /**
     * Execute note tool
     */
    suspend fun execute(toolName: String, arguments: Map<String, String>): Result<String> {
        return try {
            Log.i(TAG, "Executing note tool: $toolName with args: $arguments")
            
            when (toolName) {
                "save_note" -> executeSaveNote(arguments)
                "retrieve_notes" -> executeRetrieveNotes(arguments)
                "list_notes" -> executeListNotes(arguments)
                "update_note" -> executeUpdateNote(arguments)
                "delete_note" -> executeDeleteNote(arguments)
                "get_reminders" -> executeGetReminders(arguments)
                else -> Result.failure(Exception("Unknown note tool: $toolName"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Note tool execution failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save a note - SIMPLIFIED VERSION
     * Automatically detects category, tags, and priority from content
     */
    private suspend fun executeSaveNote(args: Map<String, String>): Result<String> {
        // Content is required, title is optional (will be auto-generated)
        val content = args["content"] ?: return Result.failure(Exception("Missing content"))
        
        // If no title provided, use quick save (auto-generates everything)
        if (args["title"] == null) {
            val result = notesManager.quickSave(content)
            return if (result.isSuccess) {
                val noteId = result.getOrNull()!!
                Result.success("""
                    ✅ Note saved successfully!
                    
                    ID: $noteId
                    
                    I've automatically organized this note for you. You can retrieve it later by searching for any keywords from the content.
                """.trimIndent())
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        }
        
        // If title provided, use regular save with auto-detection
        val title = args["title"]!!
        val tags = args["tags"]?.split(",")?.map { it.trim() } ?: emptyList()
        val category = args["category"] ?: "general"
        val reminderText = args["reminder"]
        val priorityText = args["priority"] ?: "normal"
        
        // Parse reminder time
        val reminder = reminderText?.let { notesManager.parseReminderTime(it) }
        
        // Parse priority
        val priority = when (priorityText.lowercase()) {
            "high" -> 1
            "urgent" -> 2
            else -> 0
        }
        
        val result = notesManager.saveNote(
            title = title,
            content = content,
            tags = tags,
            category = category,
            reminder = reminder,
            priority = priority
        )
        
        return if (result.isSuccess) {
            val noteId = result.getOrNull()!!
            val reminderInfo = if (reminder != null) {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")
                    .withZone(ZoneId.systemDefault())
                val reminderStr = formatter.format(reminder)
                " with reminder set for $reminderStr"
            } else ""
            
            Result.success("""
                ✅ Note saved successfully!
                
                ID: $noteId
                Title: $title
                
                I've automatically organized this note for you.$reminderInfo
                You can retrieve it later by searching for keywords from the title or content.
            """.trimIndent())
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    /**
     * Retrieve notes by search - FAST PATH with BM25/keyword search
     * NO LLM CALLS - Returns exact DB results directly
     * Max 5 results with proper formatting
     */
    private suspend fun executeRetrieveNotes(args: Map<String, String>): Result<String> {
        val query = args["query"] ?: return Result.failure(Exception("Missing query"))
        val category = args["category"]
        val limit = args["limit"]?.toIntOrNull() ?: 5  // Default to 5, max 5
        val searchType = args["search_type"] ?: "keyword"  // Default to fast keyword search
        
        // Use BM25 + fuzzy search for generalizable, accurate retrieval
        // This works for any query - handles partial matches, typos, different word order
        // Both semanticSearch and searchNotes now use BM25 + fuzzy, so use searchNotes for consistency
        val result = notesManager.searchNotes(
            query = query,
            category = category,
            limit = limit.coerceAtMost(5)
        )
        
        return if (result.isSuccess) {
            val notes = result.getOrNull()!!.take(5)  // Ensure max 5 results
            
            if (notes.isEmpty()) {
                Result.success("No notes found matching '$query'.\n\nTry searching with different keywords or check if you've saved any notes yet.")
            } else {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")
                    .withZone(ZoneId.systemDefault())
                
                // Format results showing exact details from saved notes
                val notesText = notes.mapIndexed { index, note ->
                    val dateStr = formatter.format(note.createdAt)
                    val tagsJson = JSONArray(note.tags)
                    val tags = (0 until tagsJson.length()).map { tagsJson.getString(it) }
                    val tagsStr = if (tags.isNotEmpty()) " [${tags.joinToString(", ")}]" else ""
                    
                    val reminderStr = note.reminder?.let {
                        " | Reminder: ${formatter.format(it)}"
                    } ?: ""
                    
                    """
                    📝 ${index + 1}. ${note.title}$tagsStr
                       ID: ${note.id} | Category: ${note.category} | Created: $dateStr$reminderStr
                       Content: ${note.content}
                    """.trimIndent()
                }.joinToString("\n\n")
                
                Result.success("""
                    ✅ Found ${notes.size} note(s) matching your query:
                    
                    $notesText
                    
                    💡 Tip: Use "show note [ID]" to view full details, or "update note [ID]" to modify.
                """.trimIndent())
            }
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    /**
     * Format smart search result
     */
    private fun formatSmartSearchResult(searchResult: com.confidant.ai.notes.SearchResult): String {
        return buildString {
            appendLine(searchResult.summary)
            appendLine()
            
            if (searchResult.notes.isNotEmpty()) {
                searchResult.notes.forEachIndexed { index, formattedNote ->
                    appendLine("${index + 1}. ${formattedNote.display}")
                    appendLine()
                }
                
                appendLine("💡 Quick actions:")
                appendLine("• To view full note: 'show note [ID]'")
                appendLine("• To update: 'update note [ID]'")
                appendLine("• To delete: 'delete note [ID]'")
            }
            
            if (searchResult.suggestions.isNotEmpty()) {
                appendLine()
                appendLine("💭 Suggestions:")
                searchResult.suggestions.forEach { suggestion ->
                    appendLine("• $suggestion")
                }
            }
        }
    }
    
    /**
     * List recent notes
     */
    private suspend fun executeListNotes(args: Map<String, String>): Result<String> {
        val category = args["category"]
        val limit = args["limit"]?.toIntOrNull() ?: 10
        
        val result = notesManager.listRecentNotes(category, limit)
        
        return if (result.isSuccess) {
            val notes = result.getOrNull()!!
            
            if (notes.isEmpty()) {
                val categoryStr = category?.let { " in category '$it'" } ?: ""
                Result.success("No notes found$categoryStr")
            } else {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                    .withZone(ZoneId.systemDefault())
                
                val notesText = notes.mapIndexed { index, note ->
                    val dateStr = formatter.format(note.createdAt)
                    val preview = note.content.take(100)
                    
                    """
                    ${index + 1}. ${note.title}
                       Category: ${note.category} | Created: $dateStr | ID: ${note.id}
                       ${preview}${if (note.content.length > 100) "..." else ""}
                    """.trimIndent()
                }.joinToString("\n\n")
                
                val categoryStr = category?.let { " in category '$it'" } ?: ""
                Result.success("""
                    Recent notes$categoryStr (${notes.size}):
                    
                    $notesText
                """.trimIndent())
            }
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    /**
     * Update a note
     */
    private suspend fun executeUpdateNote(args: Map<String, String>): Result<String> {
        val noteId = args["note_id"]?.toLongOrNull() 
            ?: return Result.failure(Exception("Missing or invalid note_id"))
        
        val title = args["title"]
        val content = args["content"]
        val tags = args["tags"]?.split(",")?.map { it.trim() }
        val category = args["category"]
        
        val result = notesManager.updateNote(
            id = noteId,
            title = title,
            content = content,
            tags = tags,
            category = category
        )
        
        return if (result.isSuccess) {
            Result.success("✅ Note $noteId updated successfully!")
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    /**
     * Delete a note
     */
    private suspend fun executeDeleteNote(args: Map<String, String>): Result<String> {
        val noteId = args["note_id"]?.toLongOrNull() 
            ?: return Result.failure(Exception("Missing or invalid note_id"))
        
        val result = notesManager.deleteNote(noteId)
        
        return if (result.isSuccess) {
            Result.success("✅ Note $noteId deleted successfully!")
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    /**
     * Get upcoming reminders
     */
    private suspend fun executeGetReminders(args: Map<String, String>): Result<String> {
        val result = notesManager.getUpcomingReminders()
        
        return if (result.isSuccess) {
            val notes = result.getOrNull()!!
            
            if (notes.isEmpty()) {
                Result.success("No upcoming reminders")
            } else {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")
                    .withZone(ZoneId.systemDefault())
                
                val remindersText = notes.mapIndexed { index, note ->
                    val reminderStr = formatter.format(note.reminder!!)
                    
                    """
                    ${index + 1}. ${note.title}
                       Reminder: $reminderStr
                       ${note.content.take(100)}${if (note.content.length > 100) "..." else ""}
                    """.trimIndent()
                }.joinToString("\n\n")
                
                Result.success("""
                    Upcoming reminders (${notes.size}):
                    
                    $remindersText
                """.trimIndent())
            }
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    companion object {
        private const val TAG = "NotesTool"
    }
}
