package com.confidant.ai.response

import android.util.Log

/**
 * CitationManager - Handles source attribution and citation formatting
 * 
 * Based on 2026 research:
 * - Inline citations for user-facing responses
 * - Clean responses for conversation history
 * - Source credibility tracking
 * - Multiple citation formats (inline, numbered, markdown)
 */
class CitationManager {
    
    private val sourceCache = mutableMapOf<String, SourceInfo>()
    
    /**
     * Extract sources from search results
     * Enhanced to handle DuckDuckGo multi-article format
     */
    fun extractSources(searchResults: String): List<SourceInfo> {
        val sources = mutableListOf<SourceInfo>()
        
        try {
            // Split by article markers to handle multi-article format
            val articles = searchResults.split("📰 ARTICLE ").drop(1)
            
            if (articles.isEmpty()) {
                Log.w(TAG, "No articles found in search results")
                return emptyList()
            }
            
            Log.d(TAG, "Found ${articles.size} articles to extract sources from")
            
            articles.forEachIndexed { index, article ->
                try {
                    // Extract title (first line after article number)
                    val titleMatch = """(\d+):\s*([^\n]+)""".toRegex().find(article)
                    val title = titleMatch?.groupValues?.get(2)?.trim() ?: ""
                    
                    // Extract source domain
                    val sourceMatch = """🔗 Source:\s*([^\n]+)""".toRegex().find(article)
                    val domain = sourceMatch?.groupValues?.get(1)?.trim() ?: ""
                    
                    // Extract URL
                    val urlMatch = """🌐 URL:\s*(https?://[^\s\n]+)""".toRegex().find(article)
                    val url = urlMatch?.groupValues?.get(1)?.trim() ?: ""
                    
                    if (title.isNotEmpty() && (domain.isNotEmpty() || url.isNotEmpty())) {
                        val finalDomain = domain.ifEmpty { extractDomain(url) }
                        val source = SourceInfo(
                            id = index + 1,
                            title = title,
                            url = url,
                            domain = finalDomain,
                            credibilityScore = calculateCredibility(finalDomain)
                        )
                        sources.add(source)
                        sourceCache[source.id.toString()] = source
                        
                        Log.d(TAG, "Extracted source ${index + 1}: $title from $finalDomain (credibility: ${source.credibilityScore})")
                    } else {
                        Log.w(TAG, "Incomplete source data for article ${index + 1}: title='$title', domain='$domain', url='$url'")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract source ${index + 1}", e)
                }
            }
            
            Log.i(TAG, "Successfully extracted ${sources.size} sources from ${articles.size} articles")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract sources from search results", e)
        }
        
        return sources
    }
    
    /**
     * Add inline citations to response
     * Format: "Bitcoin is currently at $43,250 [1], up 2.3% today [2]."
     */
    fun addInlineCitations(
        response: String,
        sources: List<SourceInfo>,
        format: CitationFormat = CitationFormat.NUMBERED
    ): String {
        if (sources.isEmpty()) return response
        
        // For now, add citations at the end of sentences
        // In production, use NLP to match facts to sources
        val sentences = response.split(". ").filter { it.isNotBlank() }
        
        return when (format) {
            CitationFormat.NUMBERED -> {
                // Add numbered citations: [1], [2], etc.
                sentences.mapIndexed { index, sentence ->
                    val sourceIndex = (index % sources.size) + 1
                    "$sentence [$sourceIndex]"
                }.joinToString(". ") + "."
            }
            
            CitationFormat.INLINE -> {
                // Add inline source mentions: (Source: domain.com)
                sentences.mapIndexed { index, sentence ->
                    val source = sources.getOrNull(index % sources.size)
                    if (source != null) {
                        "$sentence (${source.domain})"
                    } else {
                        sentence
                    }
                }.joinToString(". ") + "."
            }
            
            CitationFormat.MARKDOWN -> {
                // Add markdown links: [text](url)
                sentences.mapIndexed { index, sentence ->
                    val source = sources.getOrNull(index % sources.size)
                    if (source != null && source.url.isNotEmpty()) {
                        "$sentence [[source]](${source.url})"
                    } else {
                        sentence
                    }
                }.joinToString(". ") + "."
            }
        }
    }
    
    /**
     * Format source list for display with clickable Telegram markdown links
     * ENHANCED 2026: Adds clickable links for easy verification
     */
    fun formatSourceList(sources: List<SourceInfo>): String {
        if (sources.isEmpty()) return ""
        
        return buildString {
            appendLine()
            appendLine()
            appendLine("📚 Sources:")
            sources.forEach { source ->
                if (source.url.isNotEmpty()) {
                    // Telegram markdown format: [text](url)
                    appendLine("[${source.id}] [${source.title}](${source.url})")
                } else {
                    appendLine("[${source.id}] ${source.title}")
                }
            }
        }
    }
    
    /**
     * Create clean response without citations (for conversation history)
     */
    fun removeCitations(response: String): String {
        return response
            .replace(Regex("""\[\d+\]"""), "")  // Remove [1], [2], etc.
            .replace(Regex("""\([^)]+\.com\)"""), "")  // Remove (domain.com)
            .replace(Regex("""\[\[source\]\]\([^)]+\)"""), "")  // Remove [[source]](url)
            .replace(Regex("""\s+"""), " ")  // Clean up extra spaces
            .replace(Regex("""\s+\."""), ".")  // Fix spacing before periods
            .trim()
    }
    
    /**
     * Calculate credibility score for a domain
     * Based on 2026 research on trusted sources
     */
    private fun calculateCredibility(domain: String): Float {
        val lowerDomain = domain.lowercase()
        
        // Tier 1: Highly trusted (0.9-1.0)
        val tier1 = listOf(
            "reuters.com", "apnews.com", "bbc.com", "bloomberg.com",
            "wsj.com", "ft.com", "economist.com", "nature.com",
            "science.org", "nejm.org", "who.int", "cdc.gov",
            "gov", "edu", "org"  // TLDs
        )
        
        // Tier 2: Trusted (0.7-0.9)
        val tier2 = listOf(
            "cnbc.com", "forbes.com", "techcrunch.com", "theverge.com",
            "arstechnica.com", "wired.com", "nytimes.com", "washingtonpost.com",
            "guardian.com", "coindesk.com", "cointelegraph.com"
        )
        
        // Tier 3: Moderate (0.5-0.7)
        val tier3 = listOf(
            "medium.com", "substack.com", "reddit.com", "twitter.com",
            "linkedin.com", "youtube.com"
        )
        
        return when {
            tier1.any { lowerDomain.contains(it) } -> 0.95f
            tier2.any { lowerDomain.contains(it) } -> 0.80f
            tier3.any { lowerDomain.contains(it) } -> 0.60f
            lowerDomain.endsWith(".gov") || lowerDomain.endsWith(".edu") -> 0.90f
            lowerDomain.endsWith(".org") -> 0.75f
            else -> 0.50f  // Unknown sources
        }
    }
    
    /**
     * Extract domain from URL
     */
    private fun extractDomain(url: String): String {
        return try {
            val domain = url
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .split("/")[0]
            domain
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * Validate source quality with NaN protection
     */
    fun validateSources(sources: List<SourceInfo>): SourceValidation {
        if (sources.isEmpty()) {
            return SourceValidation(
                isValid = false,
                averageCredibility = 0.0f,
                hasHighQualitySources = false,
                hasLowQualitySources = false,
                recommendation = "No sources available"
            )
        }
        
        val avgCredibility = sources.map { it.credibilityScore }.average().toFloat()
        val hasHighQuality = sources.any { it.credibilityScore >= 0.8f }
        val hasLowQuality = sources.any { it.credibilityScore < 0.5f }
        
        // Ensure avgCredibility is valid
        val validAvgCredibility = if (avgCredibility.isFinite()) avgCredibility else 0.5f
        
        return SourceValidation(
            isValid = validAvgCredibility >= 0.6f,
            averageCredibility = validAvgCredibility,
            hasHighQualitySources = hasHighQuality,
            hasLowQualitySources = hasLowQuality,
            recommendation = when {
                validAvgCredibility >= 0.8f -> "Excellent sources"
                validAvgCredibility >= 0.6f -> "Good sources"
                validAvgCredibility >= 0.4f -> "Mixed quality sources"
                else -> "Low quality sources - verify information"
            }
        )
    }
    
    companion object {
        private const val TAG = "CitationManager"
    }
}

/**
 * Source information
 */
data class SourceInfo(
    val id: Int,
    val title: String,
    val url: String,
    val domain: String,
    val credibilityScore: Float
)

/**
 * Source validation result
 */
data class SourceValidation(
    val isValid: Boolean,
    val averageCredibility: Float,
    val hasHighQualitySources: Boolean,
    val hasLowQualitySources: Boolean,
    val recommendation: String
)

/**
 * Citation format options
 */
enum class CitationFormat {
    NUMBERED,    // [1], [2], [3]
    INLINE,      // (source.com)
    MARKDOWN     // [[source]](url)
}
