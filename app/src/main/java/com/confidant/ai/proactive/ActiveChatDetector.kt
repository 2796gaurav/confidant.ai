package com.confidant.ai.proactive

import android.util.Log
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ActiveChatDetector - Detects when user is actively chatting
 * 
 * Research-based implementation:
 * - Tracks user message timestamps
 * - Tracks pending bot responses
 * - Prevents proactive interruptions during active sessions
 * 
 * Based on AI Context Alerts research (brandjet.ai):
 * - Check user state before sending
 * - Suppress proactive during active sessions
 * - Wait for natural conversation breaks
 * 
 * THREAD SAFETY: Uses AtomicReference for safe concurrent access
 */
class ActiveChatDetector {
    
    // Track last user activity - THREAD-SAFE
    private val lastUserMessageTime = AtomicReference<Instant?>(null)
    private val lastBotResponseTime = AtomicReference<Instant?>(null)
    private val isResponsePending = AtomicBoolean(false)
    
    // Configuration
    private val ACTIVE_CHAT_WINDOW_MINUTES = 2L
    private val MIN_IDLE_TIME_SECONDS = 120L // 2 minutes
    
    /**
     * Called when user sends a message
     * THREAD-SAFE: Uses atomic operations
     */
    fun onUserMessage() {
        lastUserMessageTime.set(Instant.now())
        isResponsePending.set(true)
        Log.d(TAG, "👤 User message detected - marking chat as active")
    }
    
    /**
     * Called when bot sends a response
     * THREAD-SAFE: Uses atomic operations
     */
    fun onBotResponse() {
        lastBotResponseTime.set(Instant.now())
        isResponsePending.set(false)
        Log.d(TAG, "🤖 Bot response sent - response no longer pending")
    }
    
    /**
     * Check if chat is currently active
     * Returns true if user is actively chatting (should NOT send proactive)
     * THREAD-SAFE: Uses atomic reads
     */
    fun isActive(): Boolean {
        val now = Instant.now()
        
        // Check 1: Is response pending?
        if (isResponsePending.get()) {
            Log.d(TAG, "⏳ Chat active: Response pending")
            return true
        }
        
        // Check 2: Recent user message?
        lastUserMessageTime.get()?.let { lastUser ->
            val timeSinceUser = Duration.between(lastUser, now)
            
            if (timeSinceUser.seconds < MIN_IDLE_TIME_SECONDS) {
                Log.d(TAG, "⏳ Chat active: User messaged ${timeSinceUser.seconds}s ago (< ${MIN_IDLE_TIME_SECONDS}s)")
                return true
            }
        }
        
        // Check 3: Recent bot response?
        lastBotResponseTime.get()?.let { lastBot ->
            val timeSinceBot = Duration.between(lastBot, now)
            
            if (timeSinceBot.seconds < MIN_IDLE_TIME_SECONDS) {
                Log.d(TAG, "⏳ Chat active: Bot responded ${timeSinceBot.seconds}s ago (< ${MIN_IDLE_TIME_SECONDS}s)")
                return true
            }
        }
        
        // Chat is idle - safe to send proactive
        Log.d(TAG, "✅ Chat idle - safe for proactive messaging")
        return false
    }
    
    /**
     * Get time since last user activity
     * THREAD-SAFE: Uses atomic reads
     */
    fun getTimeSinceLastActivity(): Duration? {
        val activities = listOfNotNull(lastUserMessageTime.get(), lastBotResponseTime.get())
        if (activities.isEmpty()) return null
        
        val lastActivity = activities.maxOrNull() ?: return null
        return Duration.between(lastActivity, Instant.now())
    }
    
    /**
     * Get detailed status for debugging
     * THREAD-SAFE: Uses atomic reads
     */
    fun getStatus(): ActiveChatStatus {
        val now = Instant.now()
        val lastUser = lastUserMessageTime.get()
        val lastBot = lastBotResponseTime.get()
        
        return ActiveChatStatus(
            isActive = isActive(),
            isResponsePending = isResponsePending.get(),
            timeSinceUserMessage = lastUser?.let { Duration.between(it, now) },
            timeSinceBotResponse = lastBot?.let { Duration.between(it, now) },
            lastUserMessageTime = lastUser,
            lastBotResponseTime = lastBot
        )
    }
    
    /**
     * Reset state (for testing)
     * THREAD-SAFE: Uses atomic operations
     */
    fun reset() {
        lastUserMessageTime.set(null)
        lastBotResponseTime.set(null)
        isResponsePending.set(false)
        Log.d(TAG, "🔄 Active chat detector reset")
    }
    
    companion object {
        private const val TAG = "ActiveChatDetector"
    }
}

/**
 * Active chat status for debugging
 */
data class ActiveChatStatus(
    val isActive: Boolean,
    val isResponsePending: Boolean,
    val timeSinceUserMessage: Duration?,
    val timeSinceBotResponse: Duration?,
    val lastUserMessageTime: Instant?,
    val lastBotResponseTime: Instant?
)
