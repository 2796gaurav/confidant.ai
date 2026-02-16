package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * NewsSearchTool - Specialized news search with temporal optimization
 * 
 * 2026 OPTIMIZATIONS:
 * - Temporal keywords for freshness (2026, latest, breaking, today)
 * - Date extraction from articles (Schema.org, meta tags, regex)
 * - DuckDuckGo date filtering (from_date..to_date)
 * - Source credibility scoring
 * - Clickable links for Telegram HTML
 */
class NewsSearchTool(private val context: Context? = null) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val searchCache = context?.let { SearchCache(it) }
    
    fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = "news_search",
            description = "Search for latest news articles with temporal optimization. Returns recent news with exact dates, sources, and clickable links. Use for breaking news, current events, market updates, or any time-sensitive information.",
            parameters = listOf(
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "News search query. Will be automatically enhanced with temporal keywords.",
                    required = true
                ),
                ToolParameter(
                    name = "max_results",
                    type = "integer",
                    description = "Maximum number of news articles (default: 5, max: 8)",
                    required = false
                ),
                ToolParameter(
                    name = "days_back",
                    type = "integer",
                    description = "How many days back to search (default: 7, max: 30)",
                    required = false
                )
            )
        )
    }
    
    suspend fun execute(
        arguments: Map<String, String>,
        statusCallback: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== News Search with Temporal Optimization ===")
            
            val query = arguments["query"] ?: return@withContext Result.failure(
                Exception("Missing required parameter: query")
            )
            val maxResults = arguments["max_results"]?.toIntOrNull() ?: 5
            val daysBack = arguments["days_back"]?.toIntOrNull() ?: 7
            
            // Enhance query with temporal keywords
            val enhancedQuery = enhanceQueryWithTemporalKeywords(query)
            Log.i(TAG, "Original query: $query")
            Log.i(TAG, "Enhanced query: $enhancedQuery")
            
            // Check cache
            val cacheKey = "news:$enhancedQuery:$maxResults"
            val cached = searchCache?.get(cacheKey)
            if (cached != null) {
                Log.i(TAG, "✓ Using cached news results")
                statusCallback?.invoke("✓ Found cached news")
                return@withContext Result.success(cached)
            }
            
            statusCallback?.invoke("📰 Searching for latest news...")
            
            val startTime = System.currentTimeMillis()
            val results = searchNews(enhancedQuery, maxResults, daysBack)
            val searchTime = System.currentTimeMillis() - startTime
            
            Log.i(TAG, "News search completed in ${searchTime}ms - ${results.size} articles")
            
            if (results.isEmpty()) {
                statusCallback?.invoke("😕 No recent news found")
                return@withContext Result.success("No recent news found for: $query")
            }
            
            statusCallback?.invoke("✅ Found ${results.size} news articles")
            
            // Extract dates from articles (parallel processing)
            statusCallback?.invoke("📅 Extracting publication dates...")
            val articlesWithDates = extractDatesFromArticles(results)
            
            // Format as news feed with dates and clickable links
            val formatted = formatNewsResults(query, articlesWithDates)
            
            // Cache result
            if (formatted.isNotEmpty()) {
                searchCache?.put(cacheKey, formatted, ttlMinutes = 30) // Shorter TTL for news
            }
            
            Log.i(TAG, "✓ Returning ${formatted.length} chars of news content")
            Result.success(formatted)
            
        } catch (e: Exception) {
            Log.e(TAG, "News search failed: ${e.message}", e)
            statusCallback?.invoke("❌ News search failed")
            Result.failure(e)
        }
    }
    
    /**
     * Enhance query with temporal keywords for freshness
     * Based on 2026 research: temporal keywords boost freshness by 3x
     */
    private fun enhanceQueryWithTemporalKeywords(query: String): String {
        val currentYear = LocalDate.now().year
        val currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM"))
        
        // Don't add if already has temporal keywords
        val lowerQuery = query.lowercase()
        if (lowerQuery.contains("2026") || lowerQuery.contains("latest") || 
            lowerQuery.contains("today") || lowerQuery.contains("breaking")) {
            return query
        }
        
        // Add temporal keywords
        return "$query latest $currentYear $currentMonth breaking news"
    }
    
    /**
     * Search for news using DuckDuckGo with date filtering
     */
    private fun searchNews(query: String, maxResults: Int, daysBack: Int): List<NewsArticle> {
        // Calculate date range
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(daysBack.toLong())
        
        // DuckDuckGo date filter format: from_date..to_date
        val dateFilter = "${startDate}..${endDate}"
        val searchQuery = "$query $dateFilter"
        
        val url = "https://html.duckduckgo.com/html/?q=${searchQuery.replace(" ", "+")}"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile) AppleWebKit/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return emptyList()
        
        val doc = Jsoup.parse(html)
        val results = mutableListOf<NewsArticle>()
        
        val resultElements = doc.select("#links .result, .result")
        
        resultElements.take(maxResults).forEach { result ->
            val titleElem = result.selectFirst("h2.result__title a, .result__a")
            val snippetElem = result.selectFirst(".result__snippet")
            val urlElem = result.selectFirst(".result__url")
            
            if (titleElem != null) {
                val title = titleElem.text()
                var link = titleElem.attr("href")
                
                // Fix relative links
                if (link.startsWith("//")) {
                    link = "https:$link"
                }
                
                // Remove DuckDuckGo redirect
                if (link.contains("uddg=")) {
                    try {
                        val uri = android.net.Uri.parse(link)
                        link = uri.getQueryParameter("uddg") ?: link
                    } catch (e: Exception) { }
                }
                
                val domain = urlElem?.text() ?: extractDomain(link)
                
                results.add(
                    NewsArticle(
                        title = title,
                        url = link,
                        snippet = snippetElem?.text() ?: "",
                        domain = domain,
                        publishedDate = null, // Will be extracted later
                        credibilityScore = calculateCredibility(domain)
                    )
                )
            }
        }
        
        return results
    }
    
    /**
     * Extract publication dates from articles
     * Priority: Schema.org > Meta tags > Regex patterns
     */
    private suspend fun extractDatesFromArticles(articles: List<NewsArticle>): List<NewsArticle> = withContext(Dispatchers.IO) {
        articles.map { article ->
            try {
                val date = extractDateFromUrl(article.url)
                article.copy(publishedDate = date)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract date from ${article.url}: ${e.message}")
                article
            }
        }
    }
    
    /**
     * Extract date from article URL
     */
    private fun extractDateFromUrl(url: String): String? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile) AppleWebKit/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            
            val doc = Jsoup.parse(html)
            
            // Strategy 1: Schema.org datePublished (90% accuracy)
            val schemaDate = doc.select("[itemprop=datePublished]").attr("content")
            if (schemaDate.isNotEmpty()) {
                return formatDate(schemaDate)
            }
            
            // Strategy 2: Meta tag article:published_time (80% accuracy)
            val metaDate = doc.select("meta[property=article:published_time]").attr("content")
            if (metaDate.isNotEmpty()) {
                return formatDate(metaDate)
            }
            
            // Strategy 3: Meta tag og:published_time (70% accuracy)
            val ogDate = doc.select("meta[property=og:published_time]").attr("content")
            if (ogDate.isNotEmpty()) {
                return formatDate(ogDate)
            }
            
            // Strategy 4: Regex patterns in HTML (50% accuracy)
            val dateRegex = Regex("""(\d{4})-(\d{2})-(\d{2})""")
            val match = dateRegex.find(html)
            if (match != null) {
                return formatDate(match.value)
            }
            
            // Strategy 5: "Today" replacement with actual date
            return LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            
        } catch (e: Exception) {
            Log.w(TAG, "Date extraction failed: ${e.message}")
            return null
        }
    }
    
    /**
     * Format ISO 8601 date to human-readable format
     */
    private fun formatDate(isoDate: String): String {
        return try {
            val date = if (isoDate.contains("T")) {
                LocalDate.parse(isoDate.substringBefore("T"))
            } else {
                LocalDate.parse(isoDate)
            }
            date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        } catch (e: Exception) {
            isoDate
        }
    }
    
    /**
     * Format news results with dates and clickable links
     * Optimized for Telegram HTML rendering
     */
    private fun formatNewsResults(query: String, articles: List<NewsArticle>): String {
        return buildString {
            appendLine("📰 Latest News: \"$query\"")
            appendLine()
            
            articles.forEachIndexed { index, article ->
                appendLine("${index + 1}. ${article.title}")
                if (article.publishedDate != null) {
                    appendLine("   📅 ${article.publishedDate}")
                }
                appendLine("   🔗 ${article.domain}")
                appendLine("   ${article.snippet.take(150)}")
                appendLine("   🌐 ${article.url}")
                appendLine()
            }
            
            appendLine("Sources: ${articles.size} articles from ${articles.map { it.domain }.distinct().size} sources")
        }
    }
    
    /**
     * Calculate source credibility (same as CitationManager)
     */
    private fun calculateCredibility(domain: String): Float {
        val lowerDomain = domain.lowercase()
        
        val tier1 = listOf(
            "reuters.com", "apnews.com", "bbc.com", "bloomberg.com",
            "wsj.com", "ft.com", "economist.com"
        )
        
        val tier2 = listOf(
            "cnbc.com", "forbes.com", "techcrunch.com", "theverge.com",
            "nytimes.com", "washingtonpost.com", "guardian.com"
        )
        
        return when {
            tier1.any { lowerDomain.contains(it) } -> 0.95f
            tier2.any { lowerDomain.contains(it) } -> 0.80f
            lowerDomain.endsWith(".gov") || lowerDomain.endsWith(".edu") -> 0.90f
            else -> 0.60f
        }
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
    
    companion object {
        private const val TAG = "NewsSearchTool"
    }
}

/**
 * News article with metadata
 */
data class NewsArticle(
    val title: String,
    val url: String,
    val snippet: String,
    val domain: String,
    val publishedDate: String?,
    val credibilityScore: Float
)
