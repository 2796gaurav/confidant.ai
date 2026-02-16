package com.confidant.ai.telegram

import kotlin.random.Random

/**
 * StandardizedMessages - Provides standardized bot status messages
 * Simple, friendly messages without personalization
 */
object StandardizedMessages {
    
    /**
     * Get "thinking" status message with random variation
     * Returns different phrases to avoid repetition
     */
    fun getThinkingMessage(): String {
        val variations = listOf(
            "💭 Thinking...",
            "🤔 Let me think about that...",
            "🧠 Processing...",
            "💡 Hmm, let me see...",
            "🔍 Analyzing...",
            "⚡ Working on it...",
            "🎯 Got it, thinking...",
            "📝 Let me figure this out...",
            "🌟 One moment...",
            "✨ Considering...",
            "🔮 Pondering...",
            "🚀 On it...",
            "💫 Give me a sec...",
            "🎨 Crafting a response...",
            "🔬 Analyzing your question..."
        )
        
        return variations[Random.nextInt(variations.size)]
    }
    
    /**
     * Server started message
     */
    fun getServerStartedMessage(): String {
        return buildString {
            appendLine("Hey there! 👋")
            appendLine()
            appendLine("I've just woken up and I'm ready to go! You can send me messages now and I'll respond right away.")
        }
    }
    
    /**
     * Server stopped message
     */
    fun getServerStoppedMessage(): String {
        return buildString {
            appendLine("Hey there,")
            appendLine()
            appendLine("I'm going offline for now. If you need me, just start the server again from the app and I'll be right back!")
        }
    }
    
    /**
     * Server restarted message
     */
    fun getServerRestartedMessage(): String {
        return buildString {
            appendLine("Hey there! 🔄")
            appendLine()
            appendLine("I just restarted and I'm back online. Everything's fresh and ready to go!")
        }
    }
    
    /**
     * Server crashed and auto-restarting message
     */
    fun getServerCrashedMessage(): String {
        return buildString {
            appendLine("Hey there,")
            appendLine()
            appendLine("I had a little hiccup and crashed, but I'm automatically restarting now. Give me a moment and I'll be back!")
        }
    }
    
    /**
     * Going to sleep message
     */
    fun getGoingToSleepMessage(wakeTime: String): String {
        return buildString {
            appendLine("Hey there, 😴")
            appendLine()
            appendLine("I'm going to sleep now to save battery. I'll wake up automatically at $wakeTime.")
            appendLine()
            appendLine("If you need me urgently, you can wake me up from the app anytime!")
            appendLine()
            appendLine("Good night! 🌙")
        }
    }
    
    /**
     * Waking up message
     */
    fun getWakingUpMessage(): String {
        return buildString {
            appendLine("Good morning! ☀️")
            appendLine()
            appendLine("I've just woken up and I'm ready to help you with whatever you need today!")
        }
    }
    
    /**
     * Server not started message (when user sends message before server is running)
     */
    fun getServerNotStartedMessage(): String {
        return buildString {
            appendLine("Hey there! 👋")
            appendLine()
            appendLine("I'm not quite awake yet. Please start the AI server from the app dashboard, then I'll be ready to chat with you!")
        }
    }
    
    /**
     * Model not downloaded message
     */
    fun getModelNotDownloadedMessage(): String {
        return buildString {
            appendLine("Hey there! 🤖")
            appendLine()
            appendLine("I'm not quite ready yet. Please download the AI model from the app dashboard first, then we can start chatting!")
        }
    }
}
