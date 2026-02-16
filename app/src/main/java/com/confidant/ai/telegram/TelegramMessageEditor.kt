package com.confidant.ai.telegram

import android.util.Log
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.delay

/**
 * TelegramMessageEditor - Smart message editing for status updates
 * 
 * Features:
 * - Edits existing status messages instead of creating new ones
 * - Reduces message spam from 13+ messages to 3-4 messages per query
 * - Throttles rapid updates to prevent API rate limiting
 * - Falls back to new message if edit fails
 * - Tracks message lifecycle for clean state management
 * 
 * Usage:
 * ```
 * val editor = TelegramMessageEditor(bot, chatId)
 * editor.updateStatus("🤔 Analyzing...")
 * editor.updateStatus("🔍 Searching...")  // Edits previous message
 * editor.sendFinalResponse("Here's your answer")  // New message
 * ```
 * 
 * Or with initial message ID:
 * ```
 * val editor = TelegramMessageEditor(bot, chatId, thinkingMessageId)
 * editor.updateStatus("🔍 Searching...")  // Edits the thinking message
 * ```
 */
class TelegramMessageEditor(
    private val bot: Bot?,
    private val chatId: Long,
    initialMessageId: Long? = null  // NEW: Optional initial message ID to edit
) {
    private var lastStatusMessageId: Long? = initialMessageId  // Initialize with dimension provided ID
    private var lastUpdateTime: Long = 0
    private var updateCount: Int = 0
    var lastContent: String? = null // Track last content to prevent duplicates
    
    // Configuration
    private val MIN_UPDATE_INTERVAL_MS = 500L  // Minimum 500ms between updates
    private val MAX_EDITS_PER_MESSAGE = 10     // Telegram limit is ~10 edits/sec
    
    /**
     * Update status message (edits existing or creates new)
     * 
     * @param message Status message to display
     * @param forceNew Force creation of new message instead of editing
     * @return true if successful
     */
    suspend fun updateStatus(message: String, forceNew: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        
        // Throttle rapid updates to prevent API rate limiting
        val timeSinceLastUpdate = now - lastUpdateTime
        if (timeSinceLastUpdate < MIN_UPDATE_INTERVAL_MS) {
            val delayMs = MIN_UPDATE_INTERVAL_MS - timeSinceLastUpdate
            Log.d(TAG, "Throttling update, waiting ${delayMs}ms")
            delay(delayMs)
        }
        
        return try {
            if (!forceNew && lastStatusMessageId != null && updateCount < MAX_EDITS_PER_MESSAGE) {
                // Try to edit existing message
                editExistingMessage(message)
            } else {
                // Send new message (first update or edit limit reached)
                sendNewStatusMessage(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update status", e)
            false
        }
    }
    
    /**
     * Edit existing status message
     */
    private suspend fun editExistingMessage(message: String): Boolean {
        return try {
            Log.d(TAG, "Editing message $lastStatusMessageId: $message")
            
            val result = bot?.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = lastStatusMessageId!!,
                text = message
            )
            
            if (result != null) {
                lastUpdateTime = System.currentTimeMillis()
                updateCount++
                lastContent = message // Update tracked content
                Log.d(TAG, "✓ Message edited successfully (edit #$updateCount)")
                true
            } else {
                Log.w(TAG, "Edit returned null, falling back to new message")
                sendNewStatusMessage(message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to edit message, sending new one: ${e.message}")
            sendNewStatusMessage(message)
        }
    }
    
    /**
     * Send new status message and track ID
     */
    private suspend fun sendNewStatusMessage(message: String): Boolean {
        return try {
            Log.d(TAG, "Sending new status message: $message")
            
            val result = bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = message
            )
            
            val messageId = result?.get()?.messageId
            if (messageId != null) {
                lastStatusMessageId = messageId
                lastUpdateTime = System.currentTimeMillis()
                updateCount = 0  // Reset edit counter for new message
                lastContent = message // Update tracked content
                Log.d(TAG, "✓ New status message sent (ID: $messageId)")
                true
            } else {
                Log.e(TAG, "Failed to get message ID from send result")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send status message", e)
            false
        }
    }
    
    /**
     * Send final response (always creates new message)
     * Clears status message tracking so next status starts fresh
     * 
     * @param message Final response to send
     * @return true if successful
     */
    suspend fun sendFinalResponse(message: String): Boolean {
        // Clear status message tracking
        lastStatusMessageId = null
        updateCount = 0
        
        return try {
            Log.d(TAG, "Sending final response (${message.length} chars)")
            
            bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = message
            )
            
            lastUpdateTime = System.currentTimeMillis()
            Log.d(TAG, "✓ Final response sent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send final response", e)
            false
        }
    }
    
    /**
     * Reset editor state (useful for new conversation)
     */
    fun reset() {
        lastStatusMessageId = null
        lastUpdateTime = 0
        updateCount = 0
        Log.d(TAG, "Editor state reset")
    }
    
    /**
     * Get current state for debugging
     */
    fun getState(): EditorState {
        return EditorState(
            hasActiveMessage = lastStatusMessageId != null,
            messageId = lastStatusMessageId,
            updateCount = updateCount,
            timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
        )
    }
    
    companion object {
        private const val TAG = "TelegramMessageEditor"
    }
}

/**
 * Editor state for debugging
 */
data class EditorState(
    val hasActiveMessage: Boolean,
    val messageId: Long?,
    val updateCount: Int,
    val timeSinceLastUpdate: Long
)
