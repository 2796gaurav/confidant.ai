package com.confidant.ai.conversation

import android.content.Context
import android.util.Log
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * ConversationStateManager - Manages multi-turn conversation state
 * 
 * Tracks ongoing tool executions that require multiple user interactions.
 * Handles:
 * - Parameter collection across multiple messages
 * - Confirmation flows
 * - Modification requests
 * - Timeout and cleanup
 * 
 * Example flow:
 * 1. User: "save my wifi password is 123pass"
 * 2. Bot: "Which network is this for?" [COLLECTING_PARAMETERS]
 * 3. User: "Home network"
 * 4. Bot: "Here's what I'll save: ... Confirm?" [AWAITING_CONFIRMATION]
 * 5. User: "Yes"
 * 6. Bot saves and responds [EXECUTED]
 */
class ConversationStateManager(private val context: Context) {
    
    // Active tool execution states by user ID
    private val activeStates = ConcurrentHashMap<Long, ToolExecutionState>()
    
    // Timeout for inactive states (5 minutes)
    private val stateTimeout = Duration.ofMinutes(5)
    
    /**
     * Check if user has an active tool execution
     */
    fun hasActiveExecution(userId: Long): Boolean {
        cleanupExpiredStates()
        return activeStates.containsKey(userId)
    }
    
    /**
     * Get active execution state for user
     */
    fun getActiveExecution(userId: Long): ToolExecutionState? {
        cleanupExpiredStates()
        return activeStates[userId]
    }
    
    /**
     * Start a new tool execution
     */
    fun startExecution(
        userId: Long,
        toolName: String,
        originalQuery: String,
        initialParameters: Map<String, String> = emptyMap()
    ): ToolExecutionState {
        val state = ToolExecutionState(
            toolName = toolName,
            originalQuery = originalQuery,
            collectedParameters = initialParameters.toMutableMap()
        )
        
        activeStates[userId] = state
        Log.i(TAG, "Started tool execution for user $userId: $toolName")
        
        return state
    }
    
    /**
     * Update execution state
     */
    fun updateExecution(userId: Long, updatedState: ToolExecutionState) {
        activeStates[userId] = updatedState
        Log.d(TAG, "Updated execution state for user $userId: stage=${updatedState.stage}")
    }
    
    /**
     * Add parameter to active execution
     */
    fun addParameter(userId: Long, paramName: String, paramValue: String): ToolExecutionState? {
        val state = activeStates[userId] ?: return null
        val updated = state.addParameter(paramName, paramValue)
        activeStates[userId] = updated
        return updated
    }
    
    /**
     * Move execution to next stage
     */
    fun moveToStage(userId: Long, newStage: ExecutionStage): ToolExecutionState? {
        val state = activeStates[userId] ?: return null
        val updated = state.moveToStage(newStage)
        activeStates[userId] = updated
        return updated
    }
    
    /**
     * Complete and remove execution
     */
    fun completeExecution(userId: Long) {
        activeStates.remove(userId)
        Log.i(TAG, "Completed tool execution for user $userId")
    }
    
    /**
     * Cancel execution
     */
    fun cancelExecution(userId: Long) {
        val state = activeStates[userId]
        if (state != null) {
            activeStates[userId] = state.moveToStage(ExecutionStage.CANCELLED)
            Log.i(TAG, "Cancelled tool execution for user $userId: ${state.toolName}")
        }
    }
    
    /**
     * Clean up expired states (older than timeout)
     */
    private fun cleanupExpiredStates() {
        val now = Instant.now()
        val expired = activeStates.filter { (_, state) ->
            Duration.between(state.lastUpdated, now) > stateTimeout
        }
        
        expired.forEach { (userId, state) ->
            activeStates.remove(userId)
            Log.i(TAG, "Cleaned up expired execution for user $userId: ${state.toolName}")
        }
    }
    
    /**
     * Get all active executions (for debugging)
     */
    fun getAllActiveExecutions(): Map<Long, ToolExecutionState> {
        cleanupExpiredStates()
        return activeStates.toMap()
    }
    
    /**
     * Clear all states (for testing)
     */
    fun clearAll() {
        activeStates.clear()
        Log.i(TAG, "Cleared all execution states")
    }
    
    companion object {
        private const val TAG = "ConversationStateMgr"
    }
}
