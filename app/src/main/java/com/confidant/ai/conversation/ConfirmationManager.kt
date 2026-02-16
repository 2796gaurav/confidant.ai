package com.confidant.ai.conversation

import android.content.Context
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ConfirmationManager - Generates confirmation messages and handles user responses
 * 
 * Creates clear, formatted previews of what will be saved/executed
 * and interprets user confirmation responses.
 */
class ConfirmationManager(private val context: Context) {
    
    /**
     * Generate confirmation message for note saving
     */
    fun generateNoteConfirmation(
        title: String,
        content: String,
        category: String,
        tags: List<String> = emptyList(),
        reminder: Instant? = null,
        priority: String = "normal",
        additionalContext: Map<String, String> = emptyMap()
    ): String {
        return buildString {
            appendLine("📝 Here's what I'll save:")
            appendLine()
            appendLine("**Title:** $title")
            appendLine("**Content:** $content")
            appendLine("**Category:** $category")
            
            if (tags.isNotEmpty()) {
                appendLine("**Tags:** ${tags.joinToString(", ")}")
            }
            
            if (priority != "normal") {
                appendLine("**Priority:** ${priority.uppercase()}")
            }
            
            if (reminder != null) {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")
                    .withZone(ZoneId.systemDefault())
                appendLine("**Reminder:** ${formatter.format(reminder)}")
            }
            
            // Add any additional context collected
            additionalContext.forEach { (key, value) ->
                val displayKey = key.replace("_", " ").capitalize()
                appendLine("**$displayKey:** $value")
            }
            
            appendLine()
            appendLine("Does this look good?")
            appendLine("• Reply **'yes'** or **'confirm'** to save")
            appendLine("• Reply **'no'** or **'cancel'** to cancel")
            appendLine("• Reply **'change [field]'** to modify something")
        }
    }
    
    /**
     * Parse user confirmation response
     */
    fun parseConfirmationResponse(userResponse: String): ConfirmationResponse {
        val lower = userResponse.trim().lowercase()
        
        return when {
            // Positive confirmations
            lower in listOf("yes", "y", "confirm", "ok", "okay", "sure", "yep", "yeah", "correct", "good", "looks good", "perfect") -> {
                ConfirmationResponse.CONFIRMED
            }
            
            // Negative/cancel
            lower in listOf("no", "n", "cancel", "nope", "nah", "stop", "abort", "drop", "forget it", "never mind") -> {
                ConfirmationResponse.CANCELLED
            }
            
            // Modification request
            lower.startsWith("change") || lower.startsWith("modify") || lower.startsWith("edit") || lower.startsWith("update") -> {
                val field = extractFieldToModify(lower)
                ConfirmationResponse.MODIFY(field)
            }
            
            // Unclear response
            else -> {
                ConfirmationResponse.UNCLEAR
            }
        }
    }
    
    /**
     * Extract which field user wants to modify
     */
    private fun extractFieldToModify(response: String): String? {
        val fields = listOf("title", "content", "category", "tags", "reminder", "priority")
        
        for (field in fields) {
            if (response.contains(field)) {
                return field
            }
        }
        
        // Try to extract after "change" or "modify"
        val words = response.split(" ")
        val changeIndex = words.indexOfFirst { it in listOf("change", "modify", "edit", "update") }
        if (changeIndex >= 0 && changeIndex < words.size - 1) {
            return words[changeIndex + 1]
        }
        
        return null
    }
    
    /**
     * Generate message asking what to change
     */
    fun generateModificationPrompt(field: String?): String {
        return if (field != null) {
            "What would you like to change the $field to?"
        } else {
            buildString {
                appendLine("What would you like to change?")
                appendLine()
                appendLine("You can modify:")
                appendLine("• title")
                appendLine("• content")
                appendLine("• category")
                appendLine("• tags")
                appendLine("• reminder")
                appendLine("• priority")
                appendLine()
                append("Just tell me which field and the new value.")
            }
        }
    }
    
    /**
     * Generate unclear response message
     */
    fun generateUnclearResponseMessage(): String {
        return buildString {
            appendLine("I'm not sure what you mean. Please reply with:")
            appendLine("• **'yes'** or **'confirm'** to save")
            appendLine("• **'no'** or **'cancel'** to cancel")
            appendLine("• **'change [field]'** to modify something")
        }
    }
    
    companion object {
        private const val TAG = "ConfirmationManager"
    }
}

/**
 * Confirmation response types
 */
sealed class ConfirmationResponse {
    object CONFIRMED : ConfirmationResponse()
    object CANCELLED : ConfirmationResponse()
    data class MODIFY(val field: String?) : ConfirmationResponse()
    object UNCLEAR : ConfirmationResponse()
}
