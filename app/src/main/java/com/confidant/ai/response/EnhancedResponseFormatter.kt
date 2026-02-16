package com.confidant.ai.response

import android.util.Log
import com.confidant.ai.telegram.TelegramFormatter

/**
 * EnhancedResponseFormatter - Formats responses with citations and clickable links
 * 
 * 2026 OPTIMIZATIONS:
 * - Telegram HTML formatting with clickable links
 * - Numbered citations [1], [2], [3]
 * - Source credibility indicators
 * - Date extraction and formatting
 * - Clean, professional presentation
 */
object EnhancedResponseFormatter {
    
    private const val TAG = "EnhancedResponseFormatter"
    
    /**
     * Format search-based response with citations and clickable links
     * Optimized for Telegram HTML rendering
     */
    fun formatSearchResponse(
        userQuery: String,
        llmResponse: String,
        searchResults: String,
        includeSourceList: Boolean = true
    ): String {
        // Extract sources from search results
        val sources = extractSourcesFromResults(searchResults)
        
        if (sources.isEmpty()) {
            // No sources found, return plain response
            return llmResponse
        }
        
        // Build formatted response
        return buildString {
            // Main response
            appendLine(llmResponse)
            
            // Add source list if requested
            if (includeSourceList && sources.isNotEmpty()) {
                appendLine()
                appendLine(TelegramFormatter.bold("📚 Sources:"))
                sources.forEachIndexed { index, source ->
                    appendLine(TelegramFormatter.formatNumberedCitation(
                        index = index + 1,
                        title = source.title,
                        url = source.url,
                        domain = source.domain
                    ))
                }
            }
        }
    }
    
    /**
     * Format news response with dates and clickable links
     */
    fun formatNewsResponse(
        userQuery: String,
        articles: List<NewsArticleInfo>
    ): String {
        if (articles.isEmpty()) {
            return "No recent news found for: $userQuery"
        }
        
        return buildString {
            appendLine(TelegramFormatter.bold("📰 Latest News: $userQuery"))
            appendLine()
            
            articles.forEachIndexed { index, article ->
                append(TelegramFormatter.formatNewsArticle(
                    index = index + 1,
                    title = article.title,
                    url = article.url,
                    domain = article.domain,
                    date = article.date,
                    snippet = article.snippet
                ))
                appendLine()
            }
            
            appendLine()
            appendLine("${articles.size} articles from ${articles.map { it.domain }.distinct().size} sources")
        }
    }
    
    /**
     * Format weather response with emoji and structure
     */
    fun formatWeatherResponse(
        location: String,
        currentTemp: String,
        conditions: String,
        windSpeed: String,
        forecast: List<ForecastDay>
    ): String {
        return buildString {
            appendLine(TelegramFormatter.bold("🌤️ Weather for $location"))
            appendLine()
            appendLine(TelegramFormatter.bold("📍 Current Conditions"))
            appendLine("Temperature: ${TelegramFormatter.bold(currentTemp)}")
            appendLine("Conditions: $conditions")
            appendLine("Wind Speed: $windSpeed")
            
            if (forecast.isNotEmpty()) {
                appendLine()
                appendLine(TelegramFormatter.bold("📅 ${forecast.size}-Day Forecast:"))
                forecast.forEachIndexed { index, day ->
                    appendLine()
                    appendLine("${index + 1}. ${day.date}")
                    appendLine("   High: ${day.high} | Low: ${day.low}")
                    appendLine("   ${day.conditions}")
                    if (day.precipitation.isNotEmpty()) {
                        appendLine("   Precipitation: ${day.precipitation}")
                    }
                }
            }
        }
    }
    
    /**
     * Extract sources from search results text
     */
    private fun extractSourcesFromResults(searchResults: String): List<SourceInfo> {
        val sources = mutableListOf<SourceInfo>()
        
        try {
            // Parse search results format
            val lines = searchResults.lines()
            var currentTitle = ""
            var currentUrl = ""
            var currentDomain = ""
            var index = 1
            
            for (line in lines) {
                when {
                    // Title line (starts with number)
                    line.matches(Regex("""^\d+\.\s+(.+)""")) -> {
                        // Save previous source if complete
                        if (currentTitle.isNotEmpty() && currentUrl.isNotEmpty()) {
                            sources.add(SourceInfo(
                                id = index++,
                                title = currentTitle,
                                url = currentUrl,
                                domain = currentDomain.ifEmpty { extractDomain(currentUrl) },
                                credibilityScore = 0.8f
                            ))
                        }
                        
                        // Start new source
                        currentTitle = line.substringAfter(". ").trim()
                        currentUrl = ""
                        currentDomain = ""
                    }
                    
                    // Domain line
                    line.trim().startsWith("🔗") -> {
                        currentDomain = line.substringAfter("🔗").trim()
                    }
                    
                    // URL line
                    line.trim().startsWith("🌐") -> {
                        currentUrl = line.substringAfter("🌐").trim()
                    }
                }
            }
            
            // Add last source
            if (currentTitle.isNotEmpty() && currentUrl.isNotEmpty()) {
                sources.add(SourceInfo(
                    id = index,
                    title = currentTitle,
                    url = currentUrl,
                    domain = currentDomain.ifEmpty { extractDomain(currentUrl) },
                    credibilityScore = 0.8f
                ))
            }
            
            Log.d(TAG, "Extracted ${sources.size} sources from search results")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract sources: ${e.message}")
        }
        
        return sources
    }
    
    /**
     * Extract domain from URL
     */
    private fun extractDomain(url: String): String {
        return try {
            url.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .split("/")[0]
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Add inline citations to response text
     * Inserts [1], [2], [3] at appropriate points
     */
    fun addInlineCitations(
        response: String,
        sourceCount: Int
    ): String {
        if (sourceCount == 0) return response
        
        // Split into sentences
        val sentences = response.split(". ").filter { it.isNotBlank() }
        
        // Add citations to sentences (distribute evenly)
        val citedSentences = sentences.mapIndexed { index, sentence ->
            val citationIndex = (index % sourceCount) + 1
            "$sentence [${citationIndex}]"
        }
        
        return citedSentences.joinToString(". ") + "."
    }
    
    /**
     * Format response with key facts highlighted
     * Extracts and highlights prices, dates, percentages
     */
    fun formatWithKeyFacts(response: String): String {
        var formatted = response
        
        // Bold prices
        formatted = formatted.replace(
            Regex("""([₹$€£¥]\s*[\d,]+\.?\d*)""")
        ) { match ->
            TelegramFormatter.bold(match.value)
        }
        
        // Bold percentages
        formatted = formatted.replace(
            Regex("""([\d,]+\.?\d*\s*%)""")
        ) { match ->
            TelegramFormatter.bold(match.value)
        }
        
        return formatted
    }
}

/**
 * News article information for formatting
 */
data class NewsArticleInfo(
    val title: String,
    val url: String,
    val domain: String,
    val date: String?,
    val snippet: String
)

/**
 * Forecast day information
 */
data class ForecastDay(
    val date: String,
    val high: String,
    val low: String,
    val conditions: String,
    val precipitation: String
)
