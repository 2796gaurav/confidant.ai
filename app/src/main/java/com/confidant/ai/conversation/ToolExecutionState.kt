package com.confidant.ai.conversation

import java.time.Instant

/**
 * ToolExecutionState - Tracks multi-turn tool execution flow
 * 
 * Manages the state of tool operations that require multiple user interactions:
 * - Parameter collection (asking follow-up questions)
 * - Confirmation (showing preview and getting approval)
 * - Modification (allowing user to change parameters)
 * - Cancellation (allowing user to abort)
 */
data class ToolExecutionState(
    val toolName: String,
    val originalQuery: String,
    val collectedParameters: MutableMap<String, String> = mutableMapOf(),
    val missingParameters: List<ParameterRequest> = emptyList(),
    val stage: ExecutionStage = ExecutionStage.COLLECTING_PARAMETERS,
    val createdAt: Instant = Instant.now(),
    val lastUpdated: Instant = Instant.now()
) {
    
    /**
     * Check if all required parameters are collected
     */
    fun isComplete(): Boolean {
        return missingParameters.isEmpty()
    }
    
    /**
     * Add a collected parameter
     */
    fun addParameter(name: String, value: String): ToolExecutionState {
        collectedParameters[name] = value
        return copy(lastUpdated = Instant.now())
    }
    
    /**
     * Move to next stage
     */
    fun moveToStage(newStage: ExecutionStage): ToolExecutionState {
        return copy(stage = newStage, lastUpdated = Instant.now())
    }
    
    /**
     * Update missing parameters list
     */
    fun updateMissingParameters(params: List<ParameterRequest>): ToolExecutionState {
        return copy(missingParameters = params, lastUpdated = Instant.now())
    }
}

/**
 * Execution stages for tool operations
 */
enum class ExecutionStage {
    COLLECTING_PARAMETERS,  // Asking follow-up questions
    AWAITING_CONFIRMATION,  // Showing preview, waiting for approval
    AWAITING_MODIFICATION,  // User wants to change something
    CONFIRMED,              // User approved, ready to execute
    CANCELLED,              // User cancelled operation
    EXECUTED                // Tool executed successfully
}

/**
 * Parameter request for follow-up questions
 */
data class ParameterRequest(
    val name: String,
    val description: String,
    val required: Boolean,
    val suggestedValues: List<String> = emptyList(),
    val question: String
)
