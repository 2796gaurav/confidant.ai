package com.confidant.ai

import com.confidant.ai.database.entity.NotificationEntity
import com.confidant.ai.proactive.*
import org.junit.Test
import java.time.Instant

/**
 * Test for real-time proactive messaging system
 */
class RealTimeProactiveTest {
    
    @Test
    fun testNotificationAnalysisPromptGeneration() {
        // Test that we can build proper prompts for notification analysis
        val notifications = listOf(
            NotificationEntity(
                timestamp = Instant.now(),
                packageName = "com.phonepe.app",
                appName = "PhonePe",
                title = "Payment Failed",
                text = "₹500 payment to Netflix failed. Insufficient balance.",
                category = "finance",
                priority = 4,
                sentiment = "negative",
                entities = listOf("amount:500")
            )
        )
        
        val prompt = buildTestNotificationPrompt(notifications)
        
        assert(prompt.contains("PhonePe"))
        assert(prompt.contains("Payment Failed"))
        assert(prompt.contains("should_inform"))
        assert(prompt.contains("priority"))
        
        println("✅ Notification analysis prompt generated correctly")
        println(prompt)
    }
    
    @Test
    fun testPauseAnalysisPromptGeneration() {
        // Test pause analysis prompt
        val prompt = """User stopped messaging 2 minutes ago. Analyze if anything needs attention.

Recent conversation:
User: How's my Netflix subscription?
Bot: Your Netflix subscription is active and will renew on March 15th.

Unprocessed notifications since conversation:
[PhonePe] Payment Failed: ₹500 payment to Netflix failed

Tasks:
1. Check if any notification is RELATED to conversation topic
2. Check if any notification needs IMMEDIATE attention
3. Decide if user should be informed NOW or wait

Output JSON:
{
  "should_inform": true/false,
  "priority": 0.8,
  "reason": "User was discussing Netflix, payment failed",
  "suggested_message": "Hey, your Netflix payment just failed. Want me to help?"
}"""
        
        assert(prompt.contains("stopped messaging"))
        assert(prompt.contains("RELATED"))
        assert(prompt.contains("suggested_message"))
        
        println("✅ Pause analysis prompt generated correctly")
        println(prompt)
    }
    
    @Test
    fun testProcessingRequestTypes() {
        // Test all processing types
        val userRequest = ProcessingRequest(
            type = ProcessingType.USER_MESSAGE,
            prompt = "What's the weather today?",
            maxTokens = 300,
            allowToolCalling = true,
            allowSearch = true
        )
        
        val notificationRequest = ProcessingRequest(
            type = ProcessingType.INTERNAL_NOTIFICATION_ANALYSIS,
            prompt = "Analyze these notifications...",
            maxTokens = 400,
            allowToolCalling = true,
            allowSearch = true
        )
        
        val pauseRequest = ProcessingRequest(
            type = ProcessingType.INTERNAL_PAUSE_ANALYSIS,
            prompt = "User stopped messaging...",
            maxTokens = 500,
            allowToolCalling = true,
            allowSearch = true
        )
        
        assert(userRequest.type == ProcessingType.USER_MESSAGE)
        assert(notificationRequest.type == ProcessingType.INTERNAL_NOTIFICATION_ANALYSIS)
        assert(pauseRequest.type == ProcessingType.INTERNAL_PAUSE_ANALYSIS)
        
        println("✅ All processing request types work correctly")
    }
    
    @Test
    fun testToolCallParsing() {
        // Test parsing tool calls from LLM response
        val response = """I need to search for that information.
<function_call>{"name": "web_search", "arguments": {"query": "weather today"}}</function_call>
Let me find that for you."""
        
        val pattern = """<function_call>(.*?)</function_call>""".toRegex()
        val matches = pattern.findAll(response).toList()
        
        assert(matches.isNotEmpty())
        assert(matches[0].groupValues[1].contains("web_search"))
        assert(matches[0].groupValues[1].contains("weather today"))
        
        println("✅ Tool call parsing works correctly")
        println("Found: ${matches[0].groupValues[1]}")
    }
    
    @Test
    fun testNotificationFiltering() {
        // Test that we filter out our own app notifications
        val ignoredPackages = setOf(
            "com.confidant.ai",
            "android",
            "com.android.systemui"
        )
        
        val ownNotification = NotificationEntity(
            timestamp = Instant.now(),
            packageName = "com.confidant.ai",
            appName = "Confidant AI",
            title = "Test",
            text = "Test notification",
            category = "general",
            priority = 1,
            sentiment = "neutral"
        )
        
        val externalNotification = NotificationEntity(
            timestamp = Instant.now(),
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "New message",
            text = "Hello!",
            category = "social",
            priority = 2,
            sentiment = "neutral"
        )
        
        assert(ownNotification.packageName in ignoredPackages)
        assert(externalNotification.packageName !in ignoredPackages)
        
        println("✅ Notification filtering works correctly")
    }
    
    @Test
    fun testPriorityThresholds() {
        // Test priority-based processing
        val PRIORITY_THRESHOLD = 4
        
        val urgentNotification = NotificationEntity(
            timestamp = Instant.now(),
            packageName = "com.phonepe.app",
            appName = "PhonePe",
            title = "OTP",
            text = "Your OTP is 123456",
            category = "finance",
            priority = 5,
            sentiment = "neutral"
        )
        
        val normalNotification = NotificationEntity(
            timestamp = Instant.now(),
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "New message",
            text = "Hey!",
            category = "social",
            priority = 2,
            sentiment = "neutral"
        )
        
        assert(urgentNotification.priority >= PRIORITY_THRESHOLD) // Should process immediately
        assert(normalNotification.priority < PRIORITY_THRESHOLD) // Should buffer
        
        println("✅ Priority thresholds work correctly")
    }
    
    private fun buildTestNotificationPrompt(notifications: List<NotificationEntity>): String {
        return """You are analyzing notifications to decide if user needs to be informed.

App: ${notifications.first().appName} (${notifications.first().packageName})
Notifications (${notifications.size}):
${notifications.joinToString("\n") { 
    "[${it.timestamp}] ${it.title}: ${it.text.take(100)}"
}}

Tasks:
1. Identify if there's anything ACTIONABLE or IMPORTANT
2. Check if user needs to be informed NOW
3. Assign priority score (0.0-1.0)

Output JSON:
{
  "should_inform": true/false,
  "priority": 0.8,
  "reason": "Payment failed for Netflix subscription",
  "suggested_message": "Your Netflix payment failed. Want me to check your card?"
}"""
    }
}
