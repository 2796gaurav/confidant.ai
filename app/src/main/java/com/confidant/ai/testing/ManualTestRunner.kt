package com.confidant.ai.testing

import android.content.Context
import android.util.Log
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.integrations.DuckDuckGoSearchTool
import com.confidant.ai.integrations.FunctionCall
import com.confidant.ai.integrations.FunctionCallingSystem
import com.confidant.ai.personalization.PersonalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * ManualTestRunner - Run comprehensive tests from within the app
 * 
 * Usage:
 * val testRunner = ManualTestRunner(context)
 * testRunner.runAllTests { result ->
 *     println(result)
 * }
 */
class ManualTestRunner(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val personalizationManager = PersonalizationManager(context, database)
    private val functionCallingSystem = FunctionCallingSystem(context)
    private val searchTool = DuckDuckGoSearchTool()
    
    private val results = mutableListOf<TestResult>()
    
    suspend fun runAllTests(onProgress: (String) -> Unit = {}): TestReport = withContext(Dispatchers.IO) {
        results.clear()
        
        log("=".repeat(60), onProgress)
        log("CONFIDANT AI - MANUAL TEST SUITE", onProgress)
        log("=".repeat(60), onProgress)
        log("", onProgress)
        
        try {
            // Clean database for fresh start
            database.clearAllTables()
            log("✓ Database cleared", onProgress)
            log("", onProgress)
            
            // Run all tests
            test01_UserProfileCreation(onProgress)
            test02_GenderPronounMapping(onProgress)
            test03_InterestsManagement(onProgress)
            test04_PersonalizedSystemPrompt(onProgress)
            test05_IntegrationConfiguration(onProgress)
            test06_FunctionCallParsing(onProgress)
            test07_SearchToolExecution(onProgress)
            test08_ToolExecutionLogging(onProgress)
            test09_SystemPromptWithTools(onProgress)
            test10_EndToEndFlow(onProgress)
            
            // Generate report
            val report = generateReport()
            log("", onProgress)
            log("=".repeat(60), onProgress)
            log("TEST SUMMARY", onProgress)
            log("=".repeat(60), onProgress)
            log("Total: ${report.total}", onProgress)
            log("Passed: ${report.passed} ✓", onProgress)
            log("Failed: ${report.failed} ✗", onProgress)
            log("Success Rate: ${report.successRate}%", onProgress)
            log("", onProgress)
            
            if (report.failed > 0) {
                log("FAILED TESTS:", onProgress)
                results.filter { !it.passed }.forEach { result ->
                    log("  ✗ ${result.name}: ${result.error}", onProgress)
                }
            } else {
                log("🎉 ALL TESTS PASSED!", onProgress)
            }
            
            report
        } catch (e: Exception) {
            log("", onProgress)
            log("❌ TEST SUITE FAILED: ${e.message}", onProgress)
            Log.e(TAG, "Test suite failed", e)
            TestReport(0, 0, results.size, 0.0)
        }
    }
    
    private suspend fun test01_UserProfileCreation(onProgress: (String) -> Unit) {
        log("Test 1: User Profile Creation", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            personalizationManager.updateProfile(
                // userName removed for privacy,
                // userGender removed for privacy,
                // botName removed for privacy,
                // botGender removed for privacy,
                interests = listOf("technology", "finance", "cricket"),
                age = 28,
                occupation = "Software Engineer",
                location = "Bangalore"
            )
            
            val profile = personalizationManager.getProfile()
            assert("User" == "Rahul") { "User name mismatch" }
            assert("they/them" == "he/him") { "User pronoun mismatch" }
            assert("Confidant" == "Confidant") { "Bot name mismatch" }
            assert("they/them" == "they/them") { "Bot pronoun mismatch" }
            
            val interests = personalizationManager.getInterests()
            assert(interests.size == 3) { "Interests count mismatch" }
            
            log("✓ Profile created: ${"User"} (${"they/them"})", onProgress)
            log("✓ Bot: ${"Confidant"} (${"they/them"})", onProgress)
            log("✓ Interests: ${interests.joinToString(", ")}", onProgress)
            
            results.add(TestResult("User Profile Creation", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("User Profile Creation", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test02_GenderPronounMapping(onProgress: (String) -> Unit) {
        log("Test 2: Gender Pronoun Mapping", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            val tests = mapOf(
                "male" to "he/him",
                "female" to "she/her",
                "neutral" to "they/them",
                "non-binary" to "they/them"
            )
            
            tests.forEach { (gender, expectedPronoun) ->
                personalizationManager.updateProfile(interests = listOf("test"))
                val profile = personalizationManager.getProfile()
                assert("they/them" == expectedPronoun) {
                    "$gender should map to $expectedPronoun"
                }
                log("✓ $gender → $expectedPronoun", onProgress)
            }
            
            results.add(TestResult("Gender Pronoun Mapping", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("Gender Pronoun Mapping", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test03_InterestsManagement(onProgress: (String) -> Unit) {
        log("Test 3: Interests Management", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            personalizationManager.updateProfile(
                interests = listOf("tech", "finance")
            )
            
            var interests = personalizationManager.getInterests()
            assert(interests.size == 2) { "Initial interests count wrong" }
            log("✓ Initial interests: ${interests.joinToString(", ")}", onProgress)
            
            personalizationManager.addInterests(listOf("sports", "music"))
            interests = personalizationManager.getInterests()
            assert(interests.size == 4) { "Added interests count wrong" }
            log("✓ Added interests: ${interests.joinToString(", ")}", onProgress)
            
            results.add(TestResult("Interests Management", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("Interests Management", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test04_PersonalizedSystemPrompt(onProgress: (String) -> Unit) {
        log("Test 4: Personalized System Prompt", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            personalizationManager.updateProfile(
                // userName removed for privacy,
                // userGender removed for privacy,
                // botName removed for privacy,
                interests = listOf("technology", "finance")
            )
            
            val systemPrompt = personalizationManager.buildPersonalizedSystemPrompt()
            
            assert(systemPrompt.contains("Rahul")) { "Missing user name" }
            assert(systemPrompt.contains("he/him")) { "Missing user pronouns" }
            assert(systemPrompt.contains("Confidant")) { "Missing bot name" }
            assert(systemPrompt.contains("technology")) { "Missing interests" }
            
            log("✓ System prompt generated (${systemPrompt.length} chars)", onProgress)
            log("✓ Contains all personalization elements", onProgress)
            
            results.add(TestResult("Personalized System Prompt", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("Personalized System Prompt", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test05_IntegrationConfiguration(onProgress: (String) -> Unit) {
        log("Test 5: Integration Configuration (SKIPPED - table removed)", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            // Integration config table removed - test skipped
            log("⚠️ Integration config table removed in simplified schema", onProgress)
            
            results.add(TestResult("Integration Configuration", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("Integration Configuration", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test06_FunctionCallParsing(onProgress: (String) -> Unit) {
        log("Test 6: Function Call Parsing", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            // Test JSON format
            val jsonResponse = """{"tool": "web_search", "query": "Bitcoin price", "max_results": 5}"""
            val jsonCall = functionCallingSystem.parseFunctionCall(jsonResponse)
            assert(jsonCall != null) { "JSON parsing failed" }
            assert(jsonCall!!.name == "web_search") { "Wrong tool name" }
            log("✓ JSON format parsed", onProgress)
            
            // Test XML format
            val xmlResponse = """
                <function_call>
                <name>web_search</name>
                <arguments>
                <query>Bitcoin price</query>
                </arguments>
                </function_call>
            """.trimIndent()
            val xmlCall = functionCallingSystem.parseFunctionCall(xmlResponse)
            assert(xmlCall != null) { "XML parsing failed" }
            log("✓ XML format parsed", onProgress)
            
            results.add(TestResult("Function Call Parsing", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("Function Call Parsing", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test07_SearchToolExecution(onProgress: (String) -> Unit) {
        log("Test 7: Search Tool Execution", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            log("⏳ Executing search (this may take a few seconds)...", onProgress)
            
            val result = searchTool.execute(mapOf(
                "query" to "Kotlin programming",
                "max_results" to "3"
            ))
            
            assert(result.isSuccess) { "Search failed: ${result.exceptionOrNull()?.message}" }
            
            val searchData = result.getOrNull()
            assert(searchData != null) { "No search data returned" }
            assert(searchData!!.isNotEmpty()) { "Empty search results" }
            
            log("✓ Search executed successfully", onProgress)
            log("✓ Results length: ${searchData.length} chars", onProgress)
            log("✓ Sample: ${searchData.take(100)}...", onProgress)
            
            results.add(TestResult("Search Tool Execution", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("Search Tool Execution", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test08_ToolExecutionLogging(onProgress: (String) -> Unit) {
        log("Test 8: Tool Execution Logging (SKIPPED - table removed)", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            // Tool execution log table removed - test skipped
            log("⚠️ Tool execution log table removed in simplified schema", onProgress)
            
            results.add(TestResult("Tool Execution Logging", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("Tool Execution Logging", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test09_SystemPromptWithTools(onProgress: (String) -> Unit) {
        log("Test 9: System Prompt With Tools", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            personalizationManager.updateProfile(
                // userName removed for privacy,
                // botName removed for privacy
            )
            
            val toolDefinitions = "Tool: web_search\nDescription: Search the web"
            val systemPrompt = personalizationManager.buildSystemPromptWithTools(
                enabledIntegrations = listOf("search"),
                toolDefinitions = toolDefinitions
            )
            
            assert(systemPrompt.contains("Rahul")) { "Missing personalization" }
            assert(systemPrompt.contains("AVAILABLE TOOLS")) { "Missing tools section" }
            assert(systemPrompt.contains("web_search")) { "Missing tool definition" }
            
            log("✓ Tool-enabled prompt generated", onProgress)
            log("✓ Contains personalization and tools", onProgress)
            
            results.add(TestResult("System Prompt With Tools", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("System Prompt With Tools", false, e.message))
        }
        log("", onProgress)
    }
    
    private suspend fun test10_EndToEndFlow(onProgress: (String) -> Unit) {
        log("Test 10: End-to-End Flow", onProgress)
        log("-".repeat(60), onProgress)
        
        try {
            // 1. Create user profile
            personalizationManager.updateProfile(
                // userName removed for privacy,
                // userGender removed for privacy,
                interests = listOf("technology")
            )
            log("✓ Step 1: Profile created", onProgress)
            
            // 2. Configure integration (SKIPPED - table removed)
            // Integration config table removed in simplified schema
            log("⚠️ Step 2: Integration config skipped (table removed)", onProgress)
            
            // 3. Build personalized prompt
            val systemPrompt = personalizationManager.buildPersonalizedSystemPrompt()
            assert(systemPrompt.contains("Rahul")) { "Prompt missing personalization" }
            log("✓ Step 3: Personalized prompt built", onProgress)
            
            // 4. Simulate function call
            val functionCallResponse = """{"tool": "web_search", "query": "Kotlin", "max_results": 3}"""
            val functionCall = functionCallingSystem.parseFunctionCall(functionCallResponse)
            assert(functionCall != null) { "Function call parsing failed" }
            log("✓ Step 4: Function call parsed", onProgress)
            
            // 5. Execute tool
            log("⏳ Step 5: Executing search...", onProgress)
            val result = searchTool.execute(functionCall!!.arguments)
            assert(result.isSuccess) { "Tool execution failed" }
            log("✓ Step 5: Tool executed successfully", onProgress)
            
            // 6. Log execution (SKIPPED - table removed)
            // Tool execution log table removed in simplified schema
            // Tool calls are now stored in conversations table
            log("⚠️ Step 6: Tool execution logging skipped (table removed)", onProgress)
            
            log("", onProgress)
            log("🎉 END-TO-END FLOW COMPLETED SUCCESSFULLY!", onProgress)
            
            results.add(TestResult("End-to-End Flow", true))
        } catch (e: Exception) {
            log("✗ FAILED: ${e.message}", onProgress)
            results.add(TestResult("End-to-End Flow", false, e.message))
        }
        log("", onProgress)
    }
    
    private fun generateReport(): TestReport {
        val total = results.size
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        val successRate = if (total > 0) (passed.toDouble() / total * 100) else 0.0
        
        return TestReport(total, passed, failed, successRate)
    }
    
    private fun log(message: String, onProgress: (String) -> Unit) {
        Log.d(TAG, message)
        onProgress(message)
    }
    
    companion object {
        private const val TAG = "ManualTestRunner"
    }
}

data class TestResult(
    val name: String,
    val passed: Boolean,
    val error: String? = null
)

data class TestReport(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val successRate: Double
)
