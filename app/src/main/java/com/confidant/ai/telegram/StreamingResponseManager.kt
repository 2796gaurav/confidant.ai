package com.confidant.ai.telegram

import android.util.Log
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * StreamingResponseManager - Progressive message streaming for Telegram
 * 
 * OPTIMIZED FOR TELEGRAM RATE LIMITS:
 * - Enforces 1 message per second per chat
 * - Maximum 8 edits per message (under 10 limit)
 * - 500ms between edits (safe margin)
 * - HTML formatting (more reliable than Markdown)
 * - Graceful degradation on errors
 * 
 * Features:
 * - ChatGPT-style streaming responses for active chats
 * - Instant send for cold starts and conversation starters
 * - Smart chunking with word boundaries
 * - Rate limit compliance
 * - HTML-safe streaming
 * 
 * Usage:
 * ```
 * val manager = StreamingResponseManager(bot, chatId, rateLimiter)
 * manager.streamResponse("Long response text...", isActiveChat = true)
 * ```
 */
class StreamingResponseManager(
    private val bot: Bot?,
    private val chatId: Long,
    private val rateLimiter: TelegramRateLimiter,
    private val initialMessageId: Long? = null  // NEW: Optional initial message ID to edit
) {
    // Configuration - ULTRA-OPTIMIZED FOR RATE LIMITS 2026
    private val CHUNK_SIZE = 200 // INCREASED: Larger chunks = fewer updates (2.5x increase)
    private val UPDATE_INTERVAL_MS = 1000L // 1s = maximum safety margin
    private val MIN_CHUNK_WORDS = 10 // More words per chunk for fewer edits
    private val MAX_EDITS = 5 // Conservative limit (reduced from 7)
    private val MIN_LENGTH_FOR_STREAMING = 250 // Only stream longer responses
    
    // State
    private var currentMessageId: Long? = null
    private var editCount = 0
    
    /**
     * Stream response with progressive updates
     * RATE LIMIT COMPLIANT
     * 
     * @param fullText Complete response text to stream (will be HTML-escaped)
     * @param isActiveChat Whether this is an active conversation (enables streaming)
     * @return Result indicating success or failure
     */
    suspend fun streamResponse(
        fullText: String,
        isActiveChat: Boolean
    ): Result<Unit> {
        return try {
            // Clean any markdown from LLM response
            val cleanText = TelegramFormatter.cleanLLMResponse(fullText)
            
            // Truncate if too long
            val finalText = TelegramFormatter.truncateToLimit(cleanText)
            
            if (!isActiveChat || finalText.length < MIN_LENGTH_FOR_STREAMING) {
                // Cold start or short message - send instantly
                Log.d(TAG, "Sending instant message (active=$isActiveChat, length=${finalText.length})")
                sendInstant(finalText)
            } else {
                // Active chat with long response - stream progressively
                Log.d(TAG, "Streaming message (${finalText.length} chars)")
                streamWithEditAPI(finalText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in streamResponse", e)
            // Fallback to instant send
            sendInstant(TelegramFormatter.cleanLLMResponse(fullText))
        } finally {
            // CRITICAL FIX: Always cleanup state to prevent memory leaks
            cleanup()
        }
    }
    
    /**
     * Cleanup streaming state - prevents memory leaks
     */
    private fun cleanup() {
        currentMessageId = null
        editCount = 0
    }
    
    /**
     * Stream using traditional editMessageText API (fallback)
     * RATE LIMIT COMPLIANT - enforces edit limits and timing
     */
    private suspend fun streamWithEditAPI(fullText: String): Result<Unit> {
        // Use initial message ID if provided, otherwise send new message
        var messageId = initialMessageId
        
        if (messageId == null) {
            // RATE LIMIT: Wait before sending initial message
            rateLimiter.waitBeforeSend(chatId)
            
            // Send initial placeholder message
            val initialMsg = bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = TelegramFormatter.statusMessage("💭", "Thinking..."),
                parseMode = ParseMode.HTML
            )
            
            messageId = initialMsg?.get()?.messageId
            if (messageId == null) {
                Log.e(TAG, "Failed to send initial message")
                return Result.failure(Exception("Failed to send initial message"))
            }
        } else {
            Log.d(TAG, "Using existing message ID: $messageId")
        }
        
        currentMessageId = messageId
        
        // Reset edit tracking for this message
        rateLimiter.resetEditTracking(currentMessageId!!)
        
        Log.d(TAG, "Streaming with Edit API (message ID: $currentMessageId)")
        
        val chunks = chunkText(fullText)
        var accumulated = ""
        editCount = 0
        
        for ((index, chunk) in chunks.withIndex()) {
            accumulated += chunk
            
            // RATE LIMIT: Check if we can edit
            val canEdit = rateLimiter.waitBeforeEdit(currentMessageId!!)
            if (!canEdit) {
                Log.d(TAG, "Edit limit reached, sending final message")
                // RATE LIMIT: Wait before sending new message
                rateLimiter.waitBeforeSend(chatId)
                bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = fullText,
                    parseMode = ParseMode.HTML
                )
                break
            }
            
            try {
                bot?.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = currentMessageId!!,
                    text = accumulated,
                    parseMode = ParseMode.HTML
                )
                
                editCount++
                Log.d(TAG, "Edit ${editCount}/${MAX_EDITS} - chunk ${index + 1}/${chunks.size}")
                
                // Don't delay on last chunk (rate limiter already handled it)
                
            } catch (e: Exception) {
                Log.w(TAG, "Edit failed at chunk ${index + 1}: ${e.message}")
                
                // Check if it's a 429 error
                if (e.message?.contains("429") == true) {
                    Log.e(TAG, "Rate limit exceeded! This shouldn't happen with rate limiter.")
                    // Extract retry_after if possible and wait
                    rateLimiter.handle429Error(60) // Default 60 seconds
                }
                
                // Send complete message as new message
                rateLimiter.waitBeforeSend(chatId)
                bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = fullText,
                    parseMode = ParseMode.HTML
                )
                break
            }
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Send instant message (no streaming)
     * RATE LIMIT COMPLIANT
     * Used for cold starts and short messages
     */
    private suspend fun sendInstant(text: String): Result<Unit> {
        return try {
            // RATE LIMIT: Wait before sending
            rateLimiter.waitBeforeSend(chatId)
            
            bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = text,
                parseMode = ParseMode.HTML
            )
            Log.d(TAG, "Instant message sent")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send instant message", e)
            
            // Check if it's a 429 error
            if (e.message?.contains("429") == true) {
                Log.e(TAG, "Rate limit exceeded on instant send!")
                rateLimiter.handle429Error(60)
            }
            
            Result.failure(e)
        }
    }
    
    /**
     * Chunk text for progressive display
     * Respects word boundaries for natural flow
     */
    private fun chunkText(text: String): List<String> {
        val words = text.split(" ")
        val chunks = mutableListOf<String>()
        var current = ""
        var wordCount = 0
        
        for (word in words) {
            val testLength = if (current.isEmpty()) word.length else current.length + 1 + word.length
            
            if (testLength > CHUNK_SIZE && wordCount >= MIN_CHUNK_WORDS) {
                // Chunk is big enough, save it
                chunks.add(current)
                current = word
                wordCount = 1
            } else {
                // Add word to current chunk
                current += if (current.isEmpty()) word else " $word"
                wordCount++
            }
        }
        
        // Add remaining text
        if (current.isNotEmpty()) {
            chunks.add(current)
        }
        
        // Ensure we don't have too many chunks (would hit edit limit)
        if (chunks.size > MAX_EDITS) {
            // Merge chunks to fit within limit
            return mergeChunks(chunks, MAX_EDITS)
        }
        
        return chunks
    }
    
    /**
     * Merge chunks to fit within edit limit
     */
    private fun mergeChunks(chunks: List<String>, maxChunks: Int): List<String> {
        if (chunks.size <= maxChunks) return chunks
        
        val merged = mutableListOf<String>()
        val chunkSize = chunks.size / maxChunks + 1
        
        for (i in chunks.indices step chunkSize) {
            val end = minOf(i + chunkSize, chunks.size)
            val mergedChunk = chunks.subList(i, end).joinToString(" ")
            merged.add(mergedChunk)
        }
        
        return merged
    }
    

    
    /**
     * Reset streaming state
     */
    fun reset() {
        currentMessageId = null
        editCount = 0
        Log.d(TAG, "Streaming state reset")
    }
    
    /**
     * Stream tokens as they arrive from LLM (REAL streaming)
     * Batches tokens and edits message every 500ms or 5-10 tokens
     * 
     * @param tokenFlow Flow of tokens from LLM engine
     * @param isActiveChat Whether this is an active conversation
     * @return Result with the complete response text (or failure)
     */
    suspend fun streamTokenFlow(
        tokenFlow: kotlinx.coroutines.flow.Flow<String>,
        isActiveChat: Boolean
    ): Result<String> {
        return try {
            if (!isActiveChat) {
                // Cold start - collect all tokens and send instantly
                val fullResponse = buildString {
                    tokenFlow.collect { token ->
                        append(token)
                    }
                }
                Log.d(TAG, "Cold start - sending complete response (${fullResponse.length} chars)")
                sendInstant(TelegramFormatter.truncateToLimit(fullResponse))
                Result.success(fullResponse)
            } else {
                // Active chat - REAL streaming with progressive edits
                Log.d(TAG, "Active chat - streaming tokens progressively")
                streamTokensWithEdits(tokenFlow)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in streamTokenFlow", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stream tokens with progressive message edits - OPTIMIZED 2026
     * Uses adaptive batching to respect rate limits while maintaining responsiveness
     * Returns the complete accumulated response
     * 
     * CRITICAL: Updates the SAME message progressively, not creating new messages
     */
    private suspend fun streamTokensWithEdits(
        tokenFlow: kotlinx.coroutines.flow.Flow<String>
    ): Result<String> {
        var accumulated = ""
        var tokensSinceLastEdit = 0
        var lastEditTime = System.currentTimeMillis()
        var totalTokens = 0
        
        // Use initial message ID if provided, otherwise send new message
        var messageId = initialMessageId
        
        if (messageId == null) {
            // RATE LIMIT: Wait before sending initial message
            rateLimiter.waitBeforeSend(chatId)
            
            // Send initial placeholder message
            val initialMsg = bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = TelegramFormatter.statusMessage("💭", "Thinking..."),
                parseMode = ParseMode.HTML
            )
            
            messageId = initialMsg?.get()?.messageId
            if (messageId == null) {
                Log.e(TAG, "Failed to send initial message")
                return Result.failure(Exception("Failed to send initial message"))
            }
        } else {
            Log.d(TAG, "Using existing message ID: $messageId for streaming")
        }
        
        currentMessageId = messageId
        
        // Reset edit tracking for this message
        rateLimiter.resetEditTracking(currentMessageId!!)
        
        Log.d(TAG, "Streaming tokens to message ID: $currentMessageId (ADAPTIVE BATCHING)")
        
        // Collect tokens and edit progressively with ADAPTIVE BATCHING
        try {
            tokenFlow.collect { token ->
                accumulated += token
                tokensSinceLastEdit++
                totalTokens++
                
                val now = System.currentTimeMillis()
                val timeSinceEdit = now - lastEditTime
                
                // Get adaptive batch size from rate limiter
                val recommendedBatchSize = rateLimiter.getRecommendedBatchSize(currentMessageId!!)
                val currentEditCount = MAX_EDITS - rateLimiter.getRemainingEdits(currentMessageId!!)
                
                // ADAPTIVE DECISION: Edit when we have enough tokens OR enough time has passed
                val hasEnoughTokens = tokensSinceLastEdit >= recommendedBatchSize
                val hasEnoughTime = timeSinceEdit >= 800  // Minimum 800ms between edits
                val shouldEdit = hasEnoughTokens && hasEnoughTime
                
                if (shouldEdit) {
                    // RATE LIMIT: Check if we can edit
                    val canEdit = rateLimiter.waitBeforeEdit(currentMessageId!!)
                    if (!canEdit) {
                        Log.d(TAG, "Edit limit reached at $totalTokens tokens, will send final message")
                        // Continue collecting tokens, will send final message at end
                    } else {
                        try {
                            // Add "..." suffix to indicate more content coming
                            val displayText = if (accumulated.endsWith("...")) {
                                accumulated
                            } else {
                                "$accumulated..."
                            }
                            
                            bot?.editMessageText(
                                chatId = ChatId.fromId(chatId),
                                messageId = currentMessageId!!,
                                text = TelegramFormatter.truncateToLimit(displayText),
                                parseMode = ParseMode.HTML
                            )
                            lastEditTime = now
                            tokensSinceLastEdit = 0
                            
                            val velocity = rateLimiter.getEditVelocity(currentMessageId!!)
                            val newEditCount = MAX_EDITS - rateLimiter.getRemainingEdits(currentMessageId!!)
                            Log.d(TAG, "Edit $newEditCount - ${accumulated.length} chars, $totalTokens tokens (batch: $recommendedBatchSize, velocity: %.2f e/s)".format(velocity))
                        } catch (e: Exception) {
                            Log.w(TAG, "Edit failed: ${e.message}")
                            // Continue collecting tokens
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Streaming was interrupted by new message
            Log.i(TAG, "Streaming interrupted at $totalTokens tokens")
            
            // Send partial response with interruption indicator
            try {
                bot?.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = currentMessageId!!,
                    text = TelegramFormatter.truncateToLimit("$accumulated\n\n[Interrupted by new message]"),
                    parseMode = ParseMode.HTML
                )
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to send interruption message: ${e2.message}")
            }
            
            return Result.failure(e)
        }
        
        // Final edit with complete response (remove "..." suffix)
        val canEdit = rateLimiter.waitBeforeEdit(currentMessageId!!)
        if (canEdit) {
            try {
                bot?.editMessageText(
                    chatId = ChatId.fromId(chatId),
                    messageId = currentMessageId!!,
                    text = TelegramFormatter.truncateToLimit(accumulated),
                    parseMode = ParseMode.HTML
                )
                Log.d(TAG, "Final edit - ${accumulated.length} chars, $totalTokens tokens")
            } catch (e: Exception) {
                Log.w(TAG, "Final edit failed, sending new message: ${e.message}")
                // Send as new message
                rateLimiter.waitBeforeSend(chatId)
                bot?.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = TelegramFormatter.truncateToLimit(accumulated),
                    parseMode = ParseMode.HTML
                )
            }
        } else {
            // Edit limit reached, send as new message
            Log.i(TAG, "Sending final response as new message (edit limit reached)")
            rateLimiter.waitBeforeSend(chatId)
            bot?.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = TelegramFormatter.truncateToLimit(accumulated),
                parseMode = ParseMode.HTML
            )
        }
        
        // Return the complete accumulated response
        return Result.success(accumulated)
    }
    
    companion object {
        private const val TAG = "StreamingResponseMgr"
    }
}
