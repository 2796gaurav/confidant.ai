package com.confidant.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.database.entity.IntegrationConfigEntity
import com.confidant.ai.database.entity.UserProfileEntity
import com.confidant.ai.integrations.DuckDuckGoSearchTool
import com.confidant.ai.integrations.FunctionCall
import com.confidant.ai.integrations.FunctionCallingSystem
import com.confidant.ai.personalization.PersonalizationManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Comprehensive integration tests for personalization and tool calling
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PersonalizationIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var personalizationManager: PersonalizationManager
    private lateinit var functionCallingSystem: FunctionCallingSystem
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        personalizationManager = PersonalizationManager(context, database)
        functionCallingSystem = FunctionCallingSystem(context)
        
        // Clean database
        runBlocking {
            database.clearAllTables()
        }
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun test01_UserProfileCreation() = runBlocking {
        println("\n=== Test 1: User Profile Creation ===")
        
        // Create profile
        personalizationManager.updateProfile(
            userName = "Rahul",
            userGender = "male",
            botName = "Confidant",
            botGender = "neutral",
            interests = listOf("technology", "finance", "cricket"),
            age = 28,
            occupation = "Software Engineer",
            location = "Bangalore"
        )
        
        // Verify
        val profile = personalizationManager.getProfile()
        assertEquals("Rahul", profile.userName)
        assertEquals("male", profile.userGender)
        assertEquals("he/him", profile.userPronoun)
        assertEquals("Confidant", profile.botName)
        assertEquals("neutral", profile.botGender)
        assertEquals("they/them", profile.botPronoun)
        assertEquals(28, profile.age)
        
        val interests = personalizationManager.getInterests()
        assertEquals(3, interests.size)
        assertTrue(interests.contains("technology"))
        assertTrue(interests.contains("finance"))
        assertTrue(interests.contains("cricket"))
        
        println("✓ Profile created successfully")
        println("  User: ${profile.userName} (${profile.userPronoun})")
        println("  Bot: ${profile.botName} (${profile.botPronoun})")
        println("  Interests: ${interests.joinToString(", ")}")
    }
    
    @Test
    fun test02_GenderPronounMapping() = runBlocking {
        println("\n=== Test 2: Gender Pronoun Mapping ===")
        
        // Test male
        personalizationManager.updateProfile(userName = "John", userGender = "male")
        var profile = personalizationManager.getProfile()
        assertEquals("he/him", profile.userPronoun)
        println("✓ Male → he/him")
        
        // Test female
        personalizationManager.updateProfile(userName = "Jane", userGender = "female")
        profile = personalizationManager.getProfile()
        assertEquals("she/her", profile.userPronoun)
        println("✓ Female → she/her")
        
        // Test neutral
        personalizationManager.updateProfile(userName = "Alex", userGender = "neutral")
        profile = personalizationManager.getProfile()
        assertEquals("they/them", profile.userPronoun)
        println("✓ Neutral → they/them")
        
        // Test non-binary
        personalizationManager.updateProfile(userName = "Sam", userGender = "non-binary")
        profile = personalizationManager.getProfile()
        assertEquals("they/them", profile.userPronoun)
        println("✓ Non-binary → they/them")
    }
    
    @Test
    fun test03_InterestsManagement() = runBlocking {
        println("\n=== Test 3: Interests Management ===")
        
        // Create profile with initial interests
        personalizationManager.updateProfile(
            userName = "Test User",
            interests = listOf("tech", "finance")
        )
        
        var interests = personalizationManager.getInterests()
        assertEquals(2, interests.size)
        println("✓ Initial interests: ${interests.joinToString(", ")}")
        
        // Add more interests
        personalizationManager.addInterests(listOf("sports", "music"))
        interests = personalizationManager.getInterests()
        assertEquals(4, interests.size)
        assertTrue(interests.contains("sports"))
        assertTrue(interests.contains("music"))
        println("✓ Added interests: ${interests.joinToString(", ")}")
        
        // Verify no duplicates
        personalizationManager.addInterests(listOf("tech", "gaming"))
        interests = personalizationManager.getInterests()
        assertEquals(5, interests.size) // tech not duplicated
        println("✓ No duplicates: ${interests.joinToString(", ")}")
    }
    
    @Test
    fun test04_PersonalizedSystemPrompt() = runBlocking {
        println("\n=== Test 4: Personalized System Prompt ===")
        
        personalizationManager.updateProfile(
            userName = "Rahul",
            userGender = "male",
            botName = "Confidant",
            interests = listOf("technology", "finance")
        )
        
        val systemPrompt = personalizationManager.buildPersonalizedSystemPrompt()
        
        // Verify prompt contains personalization
        assertTrue(systemPrompt.contains("Rahul"))
        assertTrue(systemPrompt.contains("he/him"))
        assertTrue(systemPrompt.contains("Confidant"))
        assertTrue(systemPrompt.contains("they/them"))
        assertTrue(systemPrompt.contains("technology"))
        assertTrue(systemPrompt.contains("finance"))
        
        println("✓ System prompt generated (${systemPrompt.length} chars)")
        println("✓ Contains user name: Rahul")
        println("✓ Contains user pronouns: he/him")
        println("✓ Contains bot name: Confidant")
        println("✓ Contains interests: technology, finance")
    }
    
    @Test
    fun test05_IntegrationConfiguration() = runBlocking {
        println("\n=== Test 5: Integration Configuration ===")
        
        // Configure search (always enabled)
        database.integrationConfigDao().insert(
            IntegrationConfigEntity(
                integrationName = "search",
                isEnabled = true,
                isActive = true
            )
        )
        
        // Verify
        val activeIntegrations = database.integrationConfigDao().getActiveIntegrations()
        assertEquals(1, activeIntegrations.size)
        assertEquals("search", activeIntegrations[0].integrationName)
        
        val searchConfig = database.integrationConfigDao().getConfig("search")
        assertNotNull(searchConfig)
        assertTrue(searchConfig!!.isEnabled)
        assertTrue(searchConfig.isActive)
        
        println("✓ Search integration: ACTIVE")
    }
    
    @Test
    fun test06_FunctionCallParsing_JSON() = runBlocking {
        println("\n=== Test 6: Function Call Parsing - JSON Format ===")
        
        val jsonResponse = """{"tool": "web_search", "query": "Bitcoin price", "max_results": 5}"""
        val functionCall = functionCallingSystem.parseFunctionCall(jsonResponse)
        
        assertNotNull(functionCall)
        assertEquals("web_search", functionCall!!.name)
        assertEquals("Bitcoin price", functionCall.arguments["query"])
        assertEquals("5", functionCall.arguments["max_results"])
        
        println("✓ JSON format parsed successfully")
        println("  Tool: ${functionCall.name}")
        println("  Query: ${functionCall.arguments["query"]}")
        println("  Max results: ${functionCall.arguments["max_results"]}")
    }
    
    @Test
    fun test07_FunctionCallParsing_XML() = runBlocking {
        println("\n=== Test 7: Function Call Parsing - XML Format ===")
        
        val xmlResponse = """
            <function_call>
            <name>web_search</name>
            <arguments>
            <query>Bitcoin price</query>
            <max_results>5</max_results>
            </arguments>
            </function_call>
        """.trimIndent()
        
        val functionCall = functionCallingSystem.parseFunctionCall(xmlResponse)
        
        assertNotNull(functionCall)
        assertEquals("web_search", functionCall!!.name)
        assertEquals("Bitcoin price", functionCall.arguments["query"])
        assertEquals("5", functionCall.arguments["max_results"])
        
        println("✓ XML format parsed successfully")
        println("  Tool: ${functionCall.name}")
        println("  Query: ${functionCall.arguments["query"]}")
    }
    
    @Test
    fun test08_FunctionCallParsing_WithNoise() = runBlocking {
        println("\n=== Test 8: Function Call Parsing - With Noise ===")
        
        // Test with explanatory text before
        val noisyResponse1 = """
            Let me search for that information.
            {"tool": "web_search", "query": "Bitcoin price", "max_results": 5}
        """.trimIndent()
        
        val call1 = functionCallingSystem.parseFunctionCall(noisyResponse1)
        assertNotNull(call1)
        assertEquals("web_search", call1!!.name)
        println("✓ Parsed with text before")
        
        // Test with explanatory text after
        val noisyResponse2 = """
            {"tool": "web_search", "query": "Bitcoin price", "max_results": 5}
            I'll get you the latest price.
        """.trimIndent()
        
        val call2 = functionCallingSystem.parseFunctionCall(noisyResponse2)
        assertNotNull(call2)
        assertEquals("web_search", call2!!.name)
        println("✓ Parsed with text after")
        
        // Test with both
        val noisyResponse3 = """
            Let me search for that.
            {"tool": "web_search", "query": "Bitcoin price", "max_results": 5}
            I'll get you the information.
        """.trimIndent()
        
        val call3 = functionCallingSystem.parseFunctionCall(noisyResponse3)
        assertNotNull(call3)
        assertEquals("web_search", call3!!.name)
        println("✓ Parsed with text before and after")
    }
    
    @Test
    fun test09_SearchToolExecution() = runBlocking {
        println("\n=== Test 9: Search Tool Execution ===")
        
        val searchTool = DuckDuckGoSearchTool()
        
        val result = searchTool.execute(mapOf(
            "query" to "Kotlin programming language",
            "max_results" to "3"
        ))
        
        assertTrue(result.isSuccess)
        val searchData = result.getOrNull()
        assertNotNull(searchData)
        assertTrue(searchData!!.isNotEmpty())
        assertTrue(searchData.contains("Kotlin") || searchData.contains("kotlin"))
        
        println("✓ Search executed successfully")
        println("  Results length: ${searchData.length} chars")
        println("  Sample: ${searchData.take(150)}...")
    }
    
    @Test
    fun test10_ToolExecutionLogging() = runBlocking {
        println("\n=== Test 10: Tool Execution Logging ===")
        
        // Insert a test log
        database.toolExecutionLogDao().insert(
            com.confidant.ai.database.entity.ToolExecutionLogEntity(
                toolName = "web_search",
                arguments = """{"query": "test", "max_results": 5}""",
                result = "Test result",
                success = true,
                executionTimeMs = 1234,
                userQuery = "What is test?",
                timestamp = Instant.now()
            )
        )
        
        // Retrieve logs
        val logs = database.toolExecutionLogDao().getRecentExecutions(limit = 10)
        assertEquals(1, logs.size)
        
        val log = logs[0]
        assertEquals("web_search", log.toolName)
        assertTrue(log.success)
        assertEquals(1234, log.executionTimeMs)
        
        println("✓ Tool execution logged successfully")
        println("  Tool: ${log.toolName}")
        println("  Success: ${log.success}")
        println("  Execution time: ${log.executionTimeMs}ms")
    }
    
    @Test
    fun test11_DefaultProfileCreation() = runBlocking {
        println("\n=== Test 11: Default Profile Creation ===")
        
        // Get profile without creating one (should create default)
        val profile = personalizationManager.getProfile()
        
        assertNotNull(profile)
        assertEquals("User", profile.userName)
        assertEquals("neutral", profile.userGender)
        assertEquals("they/them", profile.userPronoun)
        assertEquals("Confidant Bot", profile.botName)
        
        println("✓ Default profile created automatically")
        println("  User: ${profile.userName} (${profile.userPronoun})")
        println("  Bot: ${profile.botName} (${profile.botPronoun})")
    }
    
    @Test
    fun test12_SystemPromptWithTools() = runBlocking {
        println("\n=== Test 12: System Prompt With Tools ===")
        
        personalizationManager.updateProfile(
            userName = "Rahul",
            userGender = "male",
            botName = "Confidant"
        )
        
        val toolDefinitions = """
            Tool: web_search
            Description: Search the web
            Parameters: query (string), max_results (integer)
        """.trimIndent()
        
        val systemPrompt = personalizationManager.buildSystemPromptWithTools(
            enabledIntegrations = listOf("search"),
            toolDefinitions = toolDefinitions
        )
        
        assertTrue(systemPrompt.contains("Rahul"))
        assertTrue(systemPrompt.contains("Confidant"))
        assertTrue(systemPrompt.contains("AVAILABLE TOOLS"))
        assertTrue(systemPrompt.contains("web_search"))
        assertTrue(systemPrompt.contains("TOOL CALLING RULES"))
        
        println("✓ Tool-enabled prompt generated (${systemPrompt.length} chars)")
        println("✓ Contains personalization")
        println("✓ Contains tool definitions")
        println("✓ Contains calling rules")
    }
}
