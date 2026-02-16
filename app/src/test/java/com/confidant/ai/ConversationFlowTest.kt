package com.confidant.ai

import com.confidant.ai.conversation.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests for multi-turn conversation flow
 */
@RunWith(RobolectricTestRunner::class)
class ConversationFlowTest {
    
    private lateinit var stateManager: ConversationStateManager
    private lateinit var confirmationManager: ConfirmationManager
    
    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        stateManager = ConversationStateManager(context)
        confirmationManager = ConfirmationManager(context)
    }
    
    @Test
    fun testStateCreation() {
        val userId = 12345L
        val state = stateManager.startExecution(
            userId = userId,
            toolName = "save_note",
            originalQuery = "save my wifi password",
            initialParameters = mapOf("title" to "wifi password", "content" to "123pass")
        )
        
        assertNotNull(state)
        assertEquals("save_note", state.toolName)
        assertEquals(ExecutionStage.COLLECTING_PARAMETERS, state.stage)
        assertEquals(2, state.collectedParameters.size)
    }
    
    @Test
    fun testParameterAddition() {
        val userId = 12345L
        stateManager.startExecution(
            userId = userId,
            toolName = "save_note",
            originalQuery = "test",
            initialParameters = emptyMap()
        )
        
        val updated = stateManager.addParameter(userId, "title", "Test Note")
        assertNotNull(updated)
        assertEquals("Test Note", updated!!.collectedParameters["title"])
    }
    
    @Test
    fun testStageTransition() {
        val userId = 12345L
        stateManager.startExecution(
            userId = userId,
            toolName = "save_note",
            originalQuery = "test"
        )
        
        val updated = stateManager.moveToStage(userId, ExecutionStage.AWAITING_CONFIRMATION)
        assertNotNull(updated)
        assertEquals(ExecutionStage.AWAITING_CONFIRMATION, updated!!.stage)
    }
    
    @Test
    fun testStateCleanup() {
        val userId = 12345L
        stateManager.startExecution(
            userId = userId,
            toolName = "save_note",
            originalQuery = "test"
        )
        
        assertTrue(stateManager.hasActiveExecution(userId))
        
        stateManager.completeExecution(userId)
        assertFalse(stateManager.hasActiveExecution(userId))
    }
    
    @Test
    fun testConfirmationParsing_Positive() {
        val responses = listOf("yes", "y", "confirm", "ok", "sure", "yep", "correct", "good")
        
        responses.forEach { response ->
            val result = confirmationManager.parseConfirmationResponse(response)
            assertEquals("Failed for: $response", ConfirmationResponse.CONFIRMED, result)
        }
    }
    
    @Test
    fun testConfirmationParsing_Negative() {
        val responses = listOf("no", "n", "cancel", "nope", "stop", "abort")
        
        responses.forEach { response ->
            val result = confirmationManager.parseConfirmationResponse(response)
            assertEquals("Failed for: $response", ConfirmationResponse.CANCELLED, result)
        }
    }
    
    @Test
    fun testConfirmationParsing_Modify() {
        val response = confirmationManager.parseConfirmationResponse("change title")
        assertTrue(response is ConfirmationResponse.MODIFY)
        assertEquals("title", (response as ConfirmationResponse.MODIFY).field)
    }
    
    @Test
    fun testConfirmationParsing_Unclear() {
        val response = confirmationManager.parseConfirmationResponse("maybe later")
        assertEquals(ConfirmationResponse.UNCLEAR, response)
    }
    
    @Test
    fun testNoteConfirmationGeneration() {
        val confirmation = confirmationManager.generateNoteConfirmation(
            title = "Test Note",
            content = "This is a test",
            category = "personal",
            tags = listOf("test", "example"),
            reminder = null,
            priority = "normal"
        )
        
        assertTrue(confirmation.contains("Test Note"))
        assertTrue(confirmation.contains("This is a test"))
        assertTrue(confirmation.contains("personal"))
        assertTrue(confirmation.contains("test, example"))
        assertTrue(confirmation.contains("yes"))
        assertTrue(confirmation.contains("no"))
    }
    
    @Test
    fun testMultipleUsersIndependentStates() {
        val user1 = 111L
        val user2 = 222L
        
        stateManager.startExecution(user1, "save_note", "user1 query")
        stateManager.startExecution(user2, "save_note", "user2 query")
        
        assertTrue(stateManager.hasActiveExecution(user1))
        assertTrue(stateManager.hasActiveExecution(user2))
        
        val state1 = stateManager.getActiveExecution(user1)
        val state2 = stateManager.getActiveExecution(user2)
        
        assertNotNull(state1)
        assertNotNull(state2)
        assertEquals("user1 query", state1!!.originalQuery)
        assertEquals("user2 query", state2!!.originalQuery)
    }
}
