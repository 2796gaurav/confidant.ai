package com.confidant.ai.personalization

import android.content.Context
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.engine.LLMEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * InterestOptimizer - Manages user interests with intelligent optimization
 * 
 * Features:
 * - Tracks interests from user messages and notifications
 * - Limits to maximum 10 interests
 * - Daily LLM-based optimization to refine and prioritize interests
 * - Learns from user behavior patterns
 */
class InterestOptimizer(
    private val context: Context,
    private val database: AppDatabase,
    private val llmEngine: LLMEngine
) {
    
    private val personalizationManager = PersonalizationManager(context, database)
    private val preferencesManager = ConfidantApplication.instance.preferencesManager
    
    // Maximum number of interests to maintain
    private val MAX_INTERESTS = 10
    
    // Preference key for last optimization timestamp
    private val KEY_LAST_OPTIMIZATION = "last_interest_optimization"
    
    /**
     * Add new interests from user interaction
     * Automatically triggers optimization if limit exceeded
     */
    suspend fun addInterests(newInterests: List<String>) = withContext(Dispatchers.IO) {
        val currentInterests = personalizationManager.getInterests().toMutableList()
        
        // Add new interests (deduplicate)
        newInterests.forEach { interest ->
            val normalized = interest.trim().lowercase()
            if (normalized.isNotBlank() && !currentInterests.any { it.lowercase() == normalized }) {
                currentInterests.add(interest.trim())
            }
        }
        
        // If exceeded limit, optimize immediately
        if (currentInterests.size > MAX_INTERESTS) {
            Log.i(TAG, "Interest limit exceeded (${currentInterests.size}/$MAX_INTERESTS), optimizing...")
            optimizeInterests(currentInterests)
        } else {
            // Just save the updated list
            personalizationManager.updateProfile(interests = currentInterests)
            Log.i(TAG, "Added interests. Total: ${currentInterests.size}/$MAX_INTERESTS")
        }
    }
    
    /**
     * Check if daily optimization is needed and run it
     * Should be called once per day by scheduler
     */
    suspend fun runDailyOptimization(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val lastOptimization = getLastOptimizationTime()
            val now = Instant.now()
            
            // Check if 24 hours have passed
            if (lastOptimization != null) {
                val hoursSince = ChronoUnit.HOURS.between(lastOptimization, now)
                if (hoursSince < 24) {
                    Log.d(TAG, "Skipping optimization - last run $hoursSince hours ago")
                    return@withContext Result.success(Unit)
                }
            }
            
            Log.i(TAG, "Running daily interest optimization...")
            
            val currentInterests = personalizationManager.getInterests()
            
            if (currentInterests.isEmpty()) {
                Log.d(TAG, "No interests to optimize")
                return@withContext Result.success(Unit)
            }
            
            // Run optimization
            optimizeInterests(currentInterests)
            
            // Update last optimization time
            setLastOptimizationTime(now)
            
            Log.i(TAG, "Daily optimization completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during daily optimization", e)
            Result.failure(e)
        }
    }
    
    /**
     * Optimize interests using LLM
     * Reduces to MAX_INTERESTS by prioritizing most relevant
     */
    private suspend fun optimizeInterests(interests: List<String>) {
        try {
            // Get recent conversation context
            val recentMessages = database.conversationDao().getRecent(limit = 50)
            val recentNotifications = database.notificationDao().getRecentNotifications(limit = 100)
            
            // Build optimization prompt
            val prompt = buildOptimizationPrompt(interests, recentMessages, recentNotifications)
            
            // Generate optimization using LLM
            val result = llmEngine.generateWithCache(
                systemPrompt = OPTIMIZATION_SYSTEM_PROMPT,
                userMessage = prompt,
                maxTokens = 256,
                temperature = 0.3f  // Lower temperature for consistent results
            )
            
            if (result.isFailure) {
                Log.e(TAG, "LLM optimization failed: ${result.exceptionOrNull()?.message}")
                // Fallback: just trim to limit
                fallbackOptimization(interests)
                return
            }
            
            val response = result.getOrNull() ?: ""
            
            // Parse optimized interests from response
            val optimizedInterests = parseOptimizedInterests(response)
            
            if (optimizedInterests.isEmpty()) {
                Log.w(TAG, "Failed to parse optimized interests, using fallback")
                fallbackOptimization(interests)
                return
            }
            
            // Save optimized interests
            personalizationManager.updateProfile(interests = optimizedInterests)
            
            Log.i(TAG, "Interests optimized: ${interests.size} → ${optimizedInterests.size}")
            Log.d(TAG, "Optimized interests: ${optimizedInterests.joinToString(", ")}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing interests", e)
            fallbackOptimization(interests)
        }
    }
    
    /**
     * Build prompt for LLM optimization
     */
    private fun buildOptimizationPrompt(
        interests: List<String>,
        recentMessages: List<com.confidant.ai.database.entity.ConversationEntity>,
        recentNotifications: List<com.confidant.ai.database.entity.NotificationEntity>
    ): String {
        return buildString {
            appendLine("Current interests (${interests.size}):")
            interests.forEachIndexed { index, interest ->
                appendLine("${index + 1}. $interest")
            }
            appendLine()
            
            if (recentMessages.isNotEmpty()) {
                appendLine("Recent conversation topics:")
                recentMessages.take(10).forEach { msg ->
                    if (msg.role == "user") {
                        appendLine("- ${msg.content.take(100)}")
                    }
                }
                appendLine()
            }
            
            if (recentNotifications.isNotEmpty()) {
                appendLine("Recent notification patterns:")
                val appCounts = recentNotifications.groupingBy { it.packageName }.eachCount()
                appCounts.entries.take(5).forEach { (pkg, count) ->
                    appendLine("- $pkg: $count notifications")
                }
                appendLine()
            }
            
            appendLine("Task: Optimize to the $MAX_INTERESTS most relevant interests based on recent activity.")
            appendLine("Respond with ONLY a comma-separated list of interests, nothing else.")
        }
    }
    
    /**
     * Parse optimized interests from LLM response
     */
    private fun parseOptimizedInterests(response: String): List<String> {
        // Try to extract comma-separated list
        val cleaned = response.trim()
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        
        return cleaned.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_INTERESTS)
    }
    
    /**
     * Fallback optimization: keep most recent interests
     */
    private suspend fun fallbackOptimization(interests: List<String>) {
        val trimmed = interests.takeLast(MAX_INTERESTS)
        personalizationManager.updateProfile(interests = trimmed)
        Log.i(TAG, "Fallback optimization: ${interests.size} → ${trimmed.size}")
    }
    
    /**
     * Get last optimization timestamp
     */
    private suspend fun getLastOptimizationTime(): Instant? {
        // Store in preferences as epoch millis
        val millis = preferencesManager.getLastMemoryConsolidation() // Reuse this key for now
        return millis?.let { Instant.ofEpochMilli(it) }
    }
    
    /**
     * Set last optimization timestamp
     */
    private suspend fun setLastOptimizationTime(instant: Instant) {
        preferencesManager.setLastMemoryConsolidation(instant.toEpochMilli())
    }
    
    companion object {
        private const val TAG = "InterestOptimizer"
        
        private const val OPTIMIZATION_SYSTEM_PROMPT = """You are an interest optimization assistant.

Your task: Analyze user interests and recent activity to identify the 10 MOST RELEVANT interests.

Rules:
1. Prioritize interests that appear in recent conversations
2. Merge similar interests (e.g., "stocks" and "investing" → "stock investing")
3. Keep specific interests over generic ones
4. Remove outdated or inactive interests
5. Respond with ONLY a comma-separated list, no explanations

Example output:
software engineering, stock investing, fitness, cooking, photography, travel, music production, gaming, reading, meditation"""
    }
}
