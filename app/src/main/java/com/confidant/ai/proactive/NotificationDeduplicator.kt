package com.confidant.ai.proactive

import android.util.Log
import com.confidant.ai.database.entity.NotificationEntity
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * NotificationDeduplicator - Intelligent duplicate detection and filtering
 * 
 * Research-based implementation:
 * - Content-based hashing (NotificationAPI best practices)
 * - 5-minute deduplication window (industry standard)
 * - Multi-level deduplication (ID, content, time)
 * 
 * Prevents:
 * - Duplicate Telegram messages
 * - Repeated app notifications
 * - Batch processing of same content
 * - Wasted LLM inference
 * 
 * THREAD SAFETY: Uses ConcurrentHashMap for safe concurrent access
 */
class NotificationDeduplicator {
    
    // Track seen notifications with timestamp - THREAD-SAFE
    private val seenNotifications = ConcurrentHashMap<String, Instant>()
    
    // Deduplication window (5 minutes - industry standard)
    private val DEDUP_WINDOW_MINUTES = 5L
    
    /**
     * Check if notification is duplicate
     * Returns true if should be filtered out
     */
    fun isDuplicate(notification: NotificationEntity): Boolean {
        // Generate content hash
        val contentHash = generateContentHash(notification)
        
        // Clean up old entries (older than dedup window)
        cleanupOldEntries()
        
        // Check if we've seen this content recently
        val lastSeen = seenNotifications[contentHash]
        
        if (lastSeen != null) {
            val timeSince = Duration.between(lastSeen, Instant.now())
            
            if (timeSince.toMinutes() < DEDUP_WINDOW_MINUTES) {
                Log.d(TAG, "🚫 Duplicate detected: ${notification.appName} - ${notification.title} (seen ${timeSince.seconds}s ago)")
                return true
            }
        }
        
        // Not a duplicate - record it
        seenNotifications[contentHash] = Instant.now()
        return false
    }
    
    /**
     * Generate content hash for notification
     * ENHANCED 2026: Content-level deduplication (not app-level)
     * 
     * Uses: title + text (normalized) WITHOUT package name
     * This allows Gmail emails with same content to be deduplicated
     * while still allowing different emails from Gmail to pass through
     */
    private fun generateContentHash(notification: NotificationEntity): String {
        // Normalize content (lowercase, trim, remove extra spaces)
        val normalizedTitle = notification.title.lowercase().trim().replace("\\s+".toRegex(), " ")
        val normalizedText = notification.text.lowercase().trim().replace("\\s+".toRegex(), " ")
        
        // CRITICAL FIX: Don't include packageName in hash
        // This allows content-level deduplication across apps
        // Example: Gmail notification "New email from John" should dedupe
        // even if it's the same email shown twice
        val content = "$normalizedTitle|$normalizedText"
        
        // Generate SHA-256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray())
        
        // Convert to hex string
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Clean up entries older than deduplication window
     * FIXED: Thread-safe iteration using ConcurrentHashMap
     */
    private fun cleanupOldEntries() {
        val cutoff = Instant.now().minusSeconds(DEDUP_WINDOW_MINUTES * 60)
        
        // Thread-safe: ConcurrentHashMap allows safe iteration during modification
        val toRemove = mutableListOf<String>()
        seenNotifications.forEach { (hash, timestamp) ->
            if (timestamp.isBefore(cutoff)) {
                toRemove.add(hash)
            }
        }
        
        // Remove in separate pass to avoid concurrent modification
        toRemove.forEach { seenNotifications.remove(it) }
        
        if (toRemove.isNotEmpty()) {
            Log.d(TAG, "🧹 Cleaned up ${toRemove.size} old deduplication entries")
        }
    }
    
    /**
     * Get deduplication statistics
     */
    fun getStats(): DeduplicationStats {
        return DeduplicationStats(
            trackedHashes = seenNotifications.size,
            oldestEntry = seenNotifications.values.minOrNull(),
            newestEntry = seenNotifications.values.maxOrNull()
        )
    }
    
    /**
     * Clear all deduplication state (for testing)
     */
    fun clear() {
        seenNotifications.clear()
        Log.d(TAG, "🗑️ Deduplication state cleared")
    }
    
    companion object {
        private const val TAG = "NotificationDeduplicator"
    }
}

/**
 * Deduplication statistics
 */
data class DeduplicationStats(
    val trackedHashes: Int,
    val oldestEntry: Instant?,
    val newestEntry: Instant?
)
