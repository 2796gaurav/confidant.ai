package com.confidant.ai.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * DateTimeProvider - Provides current date/time for context-aware responses
 * 
 * Used for:
 * - News queries ("today", "this week", etc.)
 * - Time-sensitive information
 * - Contextual awareness
 * - Search query enhancement
 */
object DateTimeProvider {
    
    /**
     * Get current date in human-readable format
     * Example: "Monday, February 9, 2026"
     */
    fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
        return formatter.format(Date())
    }
    
    /**
     * Get current time in human-readable format
     * Example: "3:47 PM"
     */
    fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        return formatter.format(Date())
    }
    
    /**
     * Get current date and time combined
     * Example: "Monday, February 9, 2026 at 3:47 PM"
     */
    fun getCurrentDateTime(): String {
        return "${getCurrentDate()} at ${getCurrentTime()}"
    }
    
    /**
     * Get ISO 8601 format for APIs
     * Example: "2026-02-09T15:47:00Z"
     */
    fun getCurrentISO8601(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
    
    /**
     * Get short date for search queries
     * Example: "Feb 9, 2026"
     */
    fun getShortDate(): String {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
        return formatter.format(Date())
    }
    
    /**
     * Get day of week
     * Example: "Monday"
     */
    fun getDayOfWeek(): String {
        val formatter = SimpleDateFormat("EEEE", Locale.ENGLISH)
        return formatter.format(Date())
    }
    
    /**
     * Get month and year
     * Example: "February 2026"
     */
    fun getMonthYear(): String {
        val formatter = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        return formatter.format(Date())
    }
    
    /**
     * Check if query is time-sensitive
     */
    fun isTimeSensitiveQuery(query: String): Boolean {
        val lowerQuery = query.lowercase()
        return lowerQuery.contains("today") ||
                lowerQuery.contains("now") ||
                lowerQuery.contains("current") ||
                lowerQuery.contains("latest") ||
                lowerQuery.contains("recent") ||
                lowerQuery.contains("this week") ||
                lowerQuery.contains("this month") ||
                lowerQuery.contains("this year") ||
                lowerQuery.contains("news")
    }
    
    /**
     * Enhance search query with date context
     */
    fun enhanceSearchQuery(query: String): String {
        return if (isTimeSensitiveQuery(query)) {
            "$query ${getShortDate()}"
        } else {
            query
        }
    }
    
    /**
     * Get contextual date string for prompts
     * Example: "Today is Monday, February 9, 2026"
     */
    fun getContextString(): String {
        return "Today is ${getCurrentDate()}"
    }
}
