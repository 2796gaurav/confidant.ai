package com.confidant.ai.conversation

import android.content.Context
import android.util.Log
import com.confidant.ai.engine.LLMEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ParameterCollector - Intelligently collects missing parameters
 * 
 * Analyzes user queries and collected parameters to:
 * - Identify what information is missing
 * - Generate natural follow-up questions
 * - Extract parameters from user responses
 * - Validate parameter completeness
 */
class ParameterCollector(
    private val context: Context,
    private val llmEngine: LLMEngine
) {
    
    /**
     * Analyze note parameters and identify what's missing
     * IMPORTANT: Validates that extracted params actually exist in original query
     */
    fun analyzeMissingNoteParameters(
        originalQuery: String,
        collectedParams: Map<String, String>
    ): List<ParameterRequest> {
        val missing = mutableListOf<ParameterRequest>()
        
        val title = collectedParams["title"] ?: ""
        val content = collectedParams["content"] ?: originalQuery
        val category = collectedParams["category"]
        val reminder = collectedParams["reminder"]
        
        // Check if this is a password/credential
        if (isPasswordOrCredential(content)) {
            if (!hasNetworkOrServiceContext(content, title)) {
                missing.add(
                    ParameterRequest(
                        name = "network_or_service",
                        description = "Which network, service, or account is this for?",
                        required = true,
                        question = "Which network, service, or account is this password for?"
                    )
                )
            }
            
            if (!hasLocationOrDeviceContext(content, title)) {
                missing.add(
                    ParameterRequest(
                        name = "location_or_device",
                        description = "Where is this used? (e.g., home, office, phone)",
                        required = false,
                        question = "Where do you use this? (e.g., home, office, specific device)"
                    )
                )
            }
        }
        
        // Check if this is an appointment/reminder
        if (isAppointmentOrReminder(content)) {
            // CRITICAL: Check if date/time is in ORIGINAL query, not just extracted params
            if (!hasDateTime(originalQuery)) {
                missing.add(
                    ParameterRequest(
                        name = "datetime",
                        description = "When is this appointment?",
                        required = true,
                        question = "When is this appointment? (e.g., tomorrow at 3pm, next Tuesday at 8pm)"
                    )
                )
            }
            
            if (!hasLocation(originalQuery)) {
                missing.add(
                    ParameterRequest(
                        name = "location",
                        description = "Where is this appointment?",
                        required = false,
                        question = "Where is this appointment?"
                    )
                )
            }
            
            // Ask about reminder preference if not explicitly mentioned
            // Don't trust LLM-generated reminder if not in original query
            if (reminder != null && !hasDateTime(originalQuery)) {
                // LLM hallucinated a reminder - ask for real date/time
                missing.add(
                    ParameterRequest(
                        name = "reminder_time",
                        description = "When should this reminder be for?",
                        required = true,
                        question = "When should I remind you? Please provide the date and time."
                    )
                )
            } else if (reminder == null && hasDateTime(originalQuery)) {
                // Has date/time but no reminder preference
                missing.add(
                    ParameterRequest(
                        name = "reminder_preference",
                        description = "When should I remind you?",
                        required = false,
                        suggestedValues = listOf("1 hour before", "1 day before", "Same day at 9am", "No reminder"),
                        question = "When should I remind you about this?"
                    )
                )
            }
        }
        
        // Check if category is appropriate
        if (category == null || category == "general") {
            val suggestedCategory = suggestCategory(content, title)
            if (suggestedCategory != "general") {
                missing.add(
                    ParameterRequest(
                        name = "category",
                        description = "What category should this be in?",
                        required = false,
                        suggestedValues = listOf("personal", "work", "health", "passwords", "reminders", "shopping"),
                        question = "I suggest categorizing this as '$suggestedCategory'. Is that correct, or would you prefer a different category?"
                    )
                )
            }
        }
        
        return missing
    }
    
    /**
     * Generate a natural follow-up question for missing parameters
     */
    fun generateFollowUpQuestion(
        missingParams: List<ParameterRequest>,
        collectedParams: Map<String, String>
    ): String {
        if (missingParams.isEmpty()) {
            return ""
        }
        
        // Ask about the first required parameter, or first optional if no required
        val nextParam = missingParams.firstOrNull { it.required } ?: missingParams.first()
        
        return buildString {
            appendLine(nextParam.question)
            
            if (nextParam.suggestedValues.isNotEmpty()) {
                appendLine()
                appendLine("Suggestions:")
                nextParam.suggestedValues.forEach { suggestion ->
                    appendLine("• $suggestion")
                }
            }
            
            if (!nextParam.required) {
                appendLine()
                append("(Optional - reply 'skip' to skip this)")
            }
        }
    }
    
    /**
     * Extract parameter value from user response
     */
    suspend fun extractParameterFromResponse(
        parameterName: String,
        userResponse: String,
        context: Map<String, String>
    ): String? = withContext(Dispatchers.IO) {
        // Handle skip
        if (userResponse.trim().lowercase() in listOf("skip", "no", "none", "n/a")) {
            return@withContext null
        }
        
        // For simple responses, use directly
        if (userResponse.length < 100) {
            return@withContext userResponse.trim()
        }
        
        // For complex responses, use LLM to extract
        try {
            val prompt = """
                Extract the value for parameter "$parameterName" from the user's response.
                
                User response: "$userResponse"
                
                Return ONLY the extracted value, nothing else.
                If the value cannot be extracted, return "UNKNOWN".
            """.trimIndent()
            
            val result = llmEngine.generateWithCache(
                systemPrompt = "You are a parameter extraction assistant.",
                userMessage = prompt,
                maxTokens = 32,
                temperature = 0.3f
            )
            
            val extracted = result.getOrNull()?.trim() ?: userResponse.trim()
            if (extracted == "UNKNOWN") null else extracted
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract parameter", e)
            userResponse.trim()
        }
    }
    
    /**
     * Check if content is a password or credential
     */
    private fun isPasswordOrCredential(content: String): Boolean {
        val lower = content.lowercase()
        return lower.contains("password") || 
               lower.contains("pass") ||
               lower.contains("credential") ||
               lower.contains("login") ||
               lower.contains("pin") ||
               lower.contains("code")
    }
    
    /**
     * Check if content has network/service context
     */
    private fun hasNetworkOrServiceContext(content: String, title: String): Boolean {
        val combined = "$content $title".lowercase()
        val indicators = listOf(
            "wifi", "network", "router", "ssid",
            "gmail", "email", "account", "service",
            "bank", "website", "app", "portal"
        )
        return indicators.any { combined.contains(it) }
    }
    
    /**
     * Check if content has location/device context
     */
    private fun hasLocationOrDeviceContext(content: String, title: String): Boolean {
        val combined = "$content $title".lowercase()
        val indicators = listOf(
            "home", "office", "work", "house",
            "phone", "laptop", "computer", "device",
            "room", "floor", "building"
        )
        return indicators.any { combined.contains(it) }
    }
    
    /**
     * Check if content is an appointment or reminder
     */
    private fun isAppointmentOrReminder(content: String): Boolean {
        val lower = content.lowercase()
        return lower.contains("appointment") ||
               lower.contains("meeting") ||
               lower.contains("remind") ||
               lower.contains("dentist") ||
               lower.contains("doctor") ||
               lower.contains("visit") ||
               lower.contains("call") ||
               lower.contains("schedule")
    }
    
    /**
     * Check if content has date/time information
     */
    private fun hasDateTime(content: String): Boolean {
        val lower = content.lowercase()
        val timeIndicators = listOf(
            "am", "pm", "o'clock", ":",
            "morning", "afternoon", "evening", "night",
            "today", "tomorrow", "monday", "tuesday", "wednesday", 
            "thursday", "friday", "saturday", "sunday",
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "next week", "next month", "at"
        )
        return timeIndicators.any { lower.contains(it) } || 
               Regex("""\d{1,2}(am|pm|:\d{2})""").containsMatchIn(lower)
    }
    
    /**
     * Check if content has location information
     */
    private fun hasLocation(content: String): Boolean {
        val lower = content.lowercase()
        val locationIndicators = listOf(
            "at ", "in ", "on ",
            "clinic", "hospital", "office", "home",
            "street", "road", "avenue", "building",
            "room", "floor"
        )
        return locationIndicators.any { lower.contains(it) }
    }
    
    /**
     * Suggest appropriate category based on content
     */
    private fun suggestCategory(content: String, title: String): String {
        val combined = "$content $title".lowercase()
        
        return when {
            combined.contains("password") || combined.contains("credential") -> "passwords"
            combined.contains("appointment") || combined.contains("remind") -> "reminders"
            combined.contains("doctor") || combined.contains("health") || combined.contains("medical") -> "health"
            combined.contains("work") || combined.contains("office") || combined.contains("meeting") -> "work"
            combined.contains("buy") || combined.contains("shop") || combined.contains("purchase") -> "shopping"
            combined.contains("idea") || combined.contains("thought") -> "ideas"
            else -> "general"
        }
    }
    
    companion object {
        private const val TAG = "ParameterCollector"
    }
}
