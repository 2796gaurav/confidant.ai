package com.confidant.ai.onboarding

import android.content.Context
import android.util.Log
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.database.AppDatabase
import com.confidant.ai.personalization.PersonalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OnboardingMessageManager - Handles onboarding greeting messages
 * Sends welcome message after Telegram validation
 */
class OnboardingMessageManager(
    private val context: Context,
    private val database: AppDatabase
) {
    
    private val personalizationManager = PersonalizationManager(context, database)
    
    /**
     * Send onboarding greeting message to user
     * Called after Telegram validation is complete
     */
    suspend fun sendOnboardingGreeting(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val app = ConfidantApplication.instance
            val interests = personalizationManager.getInterests()
            
            // Build greeting message
            val greeting = buildOnboardingMessage(interests)
            
            // Send via Telegram
            val sent = app.telegramBotManager.sendSystemMessage(greeting)
            
            if (sent) {
                Log.i(TAG, "Onboarding greeting sent successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to send onboarding greeting")
                Result.failure(Exception("Failed to send greeting"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending onboarding greeting", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build onboarding message
     */
    private fun buildOnboardingMessage(
        interests: List<String>
    ): String {
        return buildString {
            appendLine("Hello! 👋")
            appendLine()
            appendLine("I'm Confidant, your personal AI companion.")
            appendLine()
            
            if (interests.isNotEmpty()) {
                val interestList = when {
                    interests.size == 1 -> interests[0]
                    interests.size == 2 -> "${interests[0]} and ${interests[1]}"
                    else -> {
                        val last = interests.last()
                        val others = interests.dropLast(1).joinToString(", ")
                        "$others, and $last"
                    }
                }
                appendLine("I noticed you're interested in $interestList. That's awesome! I'll keep these in mind as we chat.")
                appendLine()
            }
            
            appendLine("I'm here to be your assistant and help with whatever you need. I read all your notifications, have a brain that learns about you, and can help you with:")
            appendLine()
            appendLine("• Answering questions and finding information")
            appendLine("• Keeping track of important things")
            appendLine("• Providing insights based on your interests")
            appendLine("• Being a thinking partner whenever you need one")
            appendLine()
            appendLine("🔧 Next steps:")
            appendLine("1. Download the AI model from the app dashboard")
            appendLine("2. Start the server")
            appendLine("3. Then I'll be fully active and ready to chat!")
            appendLine()
            appendLine("Looking forward to getting to know you better! 🚀")
        }
    }
    
    companion object {
        private const val TAG = "OnboardingMessageMgr"
    }
}
