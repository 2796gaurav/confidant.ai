package com.confidant.ai.telegram

/**
 * TelegramFormatter - HTML formatting utilities for Telegram messages
 * 
 * Telegram supports HTML formatting which is MORE RELIABLE than Markdown:
 * - Fewer escaping issues
 * - More forgiving parser
 * - Better error handling
 * 
 * Supported HTML tags:
 * <b>bold</b>
 * <i>italic</i>
 * <u>underline</u>
 * <s>strikethrough</s>
 * <code>inline code</code>
 * <pre>code block</pre>
 * <a href="url">link</a>
 * 
 * IMPORTANT: Must escape <, >, & in user content!
 */
object TelegramFormatter {
    
    /**
     * Escape HTML special characters in user content
     * Must be called on any user-generated text before sending
     */
    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")   // Must be first!
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
    
    /**
     * Format text as bold
     */
    fun bold(text: String): String {
        return "<b>${escapeHtml(text)}</b>"
    }
    
    /**
     * Format text as italic
     */
    fun italic(text: String): String {
        return "<i>${escapeHtml(text)}</i>"
    }
    
    /**
     * Format text as underline
     */
    fun underline(text: String): String {
        return "<u>${escapeHtml(text)}</u>"
    }
    
    /**
     * Format text as strikethrough
     */
    fun strikethrough(text: String): String {
        return "<s>${escapeHtml(text)}</s>"
    }
    
    /**
     * Format text as inline code
     */
    fun code(text: String): String {
        return "<code>${escapeHtml(text)}</code>"
    }
    
    /**
     * Format text as code block
     */
    fun codeBlock(text: String, language: String = ""): String {
        val langAttr = if (language.isNotEmpty()) " class=\"language-$language\"" else ""
        return "<pre$langAttr>${escapeHtml(text)}</pre>"
    }
    
    /**
     * Format text as link
     */
    fun link(text: String, url: String): String {
        return "<a href=\"${escapeHtml(url)}\">${escapeHtml(text)}</a>"
    }
    
    /**
     * Clean response from LLM - remove any markdown formatting
     * LLM might still generate markdown despite instructions
     */
    fun cleanLLMResponse(text: String): String {
        var cleaned = text
        
        // Remove markdown bold (**text** or __text__)
        cleaned = cleaned.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        cleaned = cleaned.replace(Regex("__(.+?)__"), "$1")
        
        // Remove markdown italic (*text* or _text_)
        cleaned = cleaned.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
        cleaned = cleaned.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "$1")
        
        // Remove markdown code (`text`)
        cleaned = cleaned.replace(Regex("`(.+?)`"), "$1")
        
        // Remove markdown links [text](url) - keep just text
        cleaned = cleaned.replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
        
        return cleaned
    }
    
    /**
     * Format status message with emoji
     */
    fun statusMessage(emoji: String, text: String): String {
        return "$emoji ${escapeHtml(text)}"
    }
    
    /**
     * Format error message
     */
    fun errorMessage(text: String): String {
        return "❌ ${escapeHtml(text)}"
    }
    
    /**
     * Format success message
     */
    fun successMessage(text: String): String {
        return "✅ ${escapeHtml(text)}"
    }
    
    /**
     * Format info message
     */
    fun infoMessage(text: String): String {
        return "ℹ️ ${escapeHtml(text)}"
    }
    
    /**
     * Format warning message
     */
    fun warningMessage(text: String): String {
        return "⚠️ ${escapeHtml(text)}"
    }
    
    /**
     * Truncate text to Telegram's message limit (4096 chars)
     * Adds "..." if truncated
     */
    fun truncateToLimit(text: String, limit: Int = 4096): String {
        if (text.length <= limit) return text
        
        val truncated = text.substring(0, limit - 3)
        return "$truncated..."
    }
    
    /**
     * Split long message into multiple parts
     * Respects 4096 character limit
     */
    fun splitLongMessage(text: String, limit: Int = 4000): List<String> {
        if (text.length <= limit) return listOf(text)
        
        val parts = mutableListOf<String>()
        var remaining = text
        
        while (remaining.length > limit) {
            // Find last space before limit
            var splitIndex = limit
            val lastSpace = remaining.substring(0, limit).lastIndexOf(' ')
            if (lastSpace > limit / 2) {
                splitIndex = lastSpace
            }
            
            parts.add(remaining.substring(0, splitIndex))
            remaining = remaining.substring(splitIndex).trimStart()
        }
        
        if (remaining.isNotEmpty()) {
            parts.add(remaining)
        }
        
        return parts
    }
    
    /**
     * Format citation/source with clickable link
     */
    fun formatCitation(title: String, url: String): String {
        return "📚 ${link(title, url)}"
    }
    
    /**
     * Format numbered citation with clickable link
     * Example: [1] Title - domain.com
     */
    fun formatNumberedCitation(index: Int, title: String, url: String, domain: String): String {
        return "[$index] ${link(title, url)} - $domain"
    }
    
    /**
     * Format inline citation reference
     * Example: [1]
     */
    fun formatInlineCitation(index: Int): String {
        return "[$index]"
    }
    
    /**
     * Format news article with date and clickable link
     * Optimized for Telegram HTML rendering
     */
    fun formatNewsArticle(
        index: Int,
        title: String,
        url: String,
        domain: String,
        date: String?,
        snippet: String
    ): String {
        return buildString {
            appendLine("${index}. ${link(title, url)}")
            if (date != null) {
                appendLine("   📅 $date")
            }
            appendLine("   🔗 $domain")
            if (snippet.isNotEmpty()) {
                appendLine("   ${escapeHtml(snippet.take(150))}")
            }
        }
    }
    
    /**
     * Format weather data with emoji
     */
    fun formatWeatherData(
        location: String,
        temperature: String,
        conditions: String,
        windSpeed: String
    ): String {
        return buildString {
            appendLine(bold("🌤️ Weather for $location"))
            appendLine()
            appendLine("Temperature: ${bold(temperature)}")
            appendLine("Conditions: $conditions")
            appendLine("Wind Speed: $windSpeed")
        }
    }
    
    /**
     * Format search results with clickable links
     * Converts plain text URLs to HTML links
     */
    fun formatSearchResultsWithLinks(text: String): String {
        // Find all URLs in text and convert to clickable links
        val urlRegex = """https?://[^\s<>"]+""".toRegex()
        var formatted = text
        
        urlRegex.findAll(text).forEach { match ->
            val url = match.value
            val domain = extractDomain(url)
            // Create clickable link with domain as text
            val htmlLink = link(domain, url)
            formatted = formatted.replace(url, htmlLink)
        }
        
        return formatted
    }
    
    /**
     * Extract domain from URL for display
     */
    private fun extractDomain(url: String): String {
        return try {
            url.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .split("/")[0]
                .take(30) // Limit length
        } catch (e: Exception) {
            "link"
        }
    }
    
    /**
     * Format response with inline citations
     * Adds [1], [2], etc. and source list at bottom
     */
    fun formatResponseWithCitations(
        response: String,
        sources: List<Pair<String, String>> // (title, url)
    ): String {
        if (sources.isEmpty()) return response
        
        return buildString {
            appendLine(response)
            appendLine()
            appendLine(bold("📚 Sources:"))
            sources.forEachIndexed { index, (title, url) ->
                appendLine(formatNumberedCitation(index + 1, title, url, extractDomain(url)))
            }
        }
    }
    
    /**
     * Format list of items
     */
    fun formatList(items: List<String>): String {
        return items.mapIndexed { index, item ->
            "${index + 1}. ${escapeHtml(item)}"
        }.joinToString("\n")
    }
    
    /**
     * Format key-value pair
     */
    fun formatKeyValue(key: String, value: String): String {
        return "${bold(key)}: ${escapeHtml(value)}"
    }
}
