package com.confidant.ai.personalization

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.database.entity.CoreMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * PersonalizationManager - Manages user profile and interests
 * Uses core_memory instead of user_profile table
 * Privacy-first: Only handles interests for personalization, no names or gender
 */
class PersonalizationManager(
    private val context: Context,
    private val database: AppDatabase
) {
    
    /**
     * Data class for user profile (compatibility)
     */
    data class UserProfile(
        val interests: String = "[]",
        val age: Int? = null,
        val occupation: String? = null,
        val location: String? = null
    )
    
    /**
     * Get user profile from core_memory
     */
    suspend fun getProfile(): UserProfile = withContext(Dispatchers.IO) {
        val interestsJson = database.coreMemoryDao().getByKey("preferences.interests")?.value ?: "[]"
        val age = database.coreMemoryDao().getByKey("preferences.age")?.value?.toIntOrNull()
        val occupation = database.coreMemoryDao().getByKey("preferences.occupation")?.value
        val location = database.coreMemoryDao().getByKey("preferences.location")?.value
        
        UserProfile(
            interests = interestsJson,
            age = age,
            occupation = occupation,
            location = location
        )
    }
    
    /**
     * Update user profile (interests only)
     */
    suspend fun updateProfile(
        interests: List<String>? = null,
        age: Int? = null,
        occupation: String? = null,
        location: String? = null
    ) = withContext(Dispatchers.IO) {
        val current = getProfile()
        
        // Update interests
        if (interests != null) {
            val interestsJson = JSONArray(interests).toString()
            database.coreMemoryDao().insertOrUpdate(
                CoreMemoryEntity(
                    key = "preferences.interests",
                    value = interestsJson,
                    category = "preferences"
                )
            )
        }
        
        // Update age
        if (age != null) {
            database.coreMemoryDao().insertOrUpdate(
                CoreMemoryEntity(
                    key = "preferences.age",
                    value = age.toString(),
                    category = "preferences"
                )
            )
        }
        
        // Update occupation
        if (occupation != null) {
            database.coreMemoryDao().insertOrUpdate(
                CoreMemoryEntity(
                    key = "preferences.occupation",
                    value = occupation,
                    category = "preferences"
                )
            )
        }
        
        // Update location
        if (location != null) {
            database.coreMemoryDao().insertOrUpdate(
                CoreMemoryEntity(
                    key = "preferences.location",
                    value = location,
                    category = "preferences"
                )
            )
        }
        
        Log.i(TAG, "Profile updated with ${interests?.size ?: 0} interests")
    }
    
    /**
     * Add interests to user profile
     */
    suspend fun addInterests(newInterests: List<String>) = withContext(Dispatchers.IO) {
        val profile = getProfile()
        val currentInterests = parseInterests(profile.interests).toMutableSet()
        currentInterests.addAll(newInterests)
        
        val interestsJson = JSONArray(currentInterests.toList()).toString()
        database.coreMemoryDao().insertOrUpdate(
            CoreMemoryEntity(
                key = "preferences.interests",
                value = interestsJson,
                category = "preferences"
            )
        )
        Log.i(TAG, "Added interests: $newInterests. Total: ${currentInterests.size}")
    }
    
    /**
     * Get interests as list
     */
    suspend fun getInterests(): List<String> = withContext(Dispatchers.IO) {
        val profile = getProfile()
        parseInterests(profile.interests)
    }
    
    /**
     * Build system prompt with interests
     * Includes user interests for context
     */
    suspend fun buildPersonalizedSystemPrompt(
        basePrompt: String = DEFAULT_BASE_PROMPT
    ): String = withContext(Dispatchers.IO) {
        val profile = getProfile()
        val interests = parseInterests(profile.interests)
        
        buildString {
            appendLine(basePrompt)
            appendLine()
            
            if (interests.isNotEmpty()) {
                appendLine("=== USER CONTEXT ===")
                appendLine("User Interests: ${interests.joinToString(", ")}")
                appendLine()
            }
            
            if (profile.age != null) {
                appendLine("User Age: ${profile.age}")
            }
            if (profile.occupation != null) {
                appendLine("User Occupation: ${profile.occupation}")
            }
            if (profile.location != null) {
                appendLine("User Location: ${profile.location}")
            }
            
            if (interests.isNotEmpty() || profile.age != null || profile.occupation != null || profile.location != null) {
                appendLine()
                appendLine("IMPORTANT:")
                appendLine("- Be friendly, accurate, and helpful")
                appendLine("- Keep responses concise (1-3 sentences unless more detail is needed)")
                if (interests.isNotEmpty()) {
                    appendLine("- Consider user's interests when providing recommendations")
                }
            }
        }
    }
    
    /**
     * Build system prompt with tool calling support
     */
    suspend fun buildSystemPromptWithTools(
        enabledIntegrations: List<String>,
        toolDefinitions: String
    ): String = withContext(Dispatchers.IO) {
        val personalizedPrompt = buildPersonalizedSystemPrompt()
        
        buildString {
            appendLine(personalizedPrompt)
            appendLine()
            appendLine("=== AVAILABLE TOOLS ===")
            appendLine(toolDefinitions)
            appendLine()
            appendLine("TOOL CALLING INSTRUCTIONS:")
            appendLine()
            appendLine("When to Use Tools:")
            appendLine("- web_search: For ANY current information, news, prices, facts, or data")
            appendLine("- notes: For saving or retrieving user notes")
            appendLine()
            appendLine("How to Call Tools:")
            appendLine("1. Respond ONLY with the XML function call (no other text)")
            appendLine("2. Format: <function_call><name>tool_name</name><arguments><arg_name>value</arg_name></arguments></function_call>")
            appendLine("3. For web_search, always set deep_fetch=true for news/market queries")
            appendLine()
            appendLine("Examples:")
            appendLine("<function_call><name>web_search</name><arguments><query>Bitcoin price today</query><max_results>5</max_results><deep_fetch>true</deep_fetch></arguments></function_call>")
            appendLine()
            appendLine("After Tool Execution:")
            appendLine("- You'll receive comprehensive results with full article content")
            appendLine("- Synthesize information from multiple sources")
            appendLine("- Provide specific facts, numbers, and dates")
            appendLine("- Cite sources when relevant")
            appendLine("- Give the user a thorough, well-researched answer")
        }
    }
    
    /**
     * Parse interests from JSON string
     */
    private fun parseInterests(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    companion object {
        private const val TAG = "PersonalizationManager"
        
        private const val DEFAULT_BASE_PROMPT = """You are Confidant, a warm and intelligent AI assistant. You're helpful, accurate, and genuinely care about assisting users.

CORE PRINCIPLES:
1. Be conversational and friendly - talk like a knowledgeable friend
2. Be accurate and thorough - provide well-researched, factual information
3. Be concise when appropriate - match response length to what's needed
4. Be proactive - anticipate needs and offer helpful suggestions

CONVERSATION GUIDELINES:

For Greetings (hi, hello, hey):
- Respond warmly and naturally
- Ask how you can help
- Keep it brief (1-2 sentences)
- Example: "Hey! Good to hear from you. What can I help you with today?"

For Simple Questions:
- Give direct, clear answers (2-3 sentences)
- Include key facts and numbers
- Mention sources when relevant

For Complex Questions:
- Provide comprehensive, well-organized answers
- Break down into clear sections
- Include specific details, data, and examples
- Explain your reasoning when it helps

For Current Information Queries:
- Always use web search for latest data
- Get full article content for thorough answers
- Combine information from multiple sources
- Provide specific numbers, dates, and facts

IMPORTANT:
- Never make up information - use tools to get current data
- Be honest about what you know and don't know
- Ask clarifying questions when needed
- Match your tone to how the user communicates"""
    }
}
