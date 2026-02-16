package com.confidant.ai.integrations

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * WebContentFetcher - Fetches and extracts content from web pages in parallel
 * Provides deep content extraction for richer context
 */
class WebContentFetcher {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    /**
     * Fetch content from multiple URLs in parallel
     */
    suspend fun fetchMultipleUrls(
        urls: List<String>,
        maxConcurrent: Int = 3,
        maxContentPerPage: Int = 2000
    ): List<PageContent> = withContext(Dispatchers.IO) {
        
        Log.i(TAG, "=== Parallel Web Content Fetching ===")
        Log.i(TAG, "Fetching ${urls.size} URLs with max $maxConcurrent concurrent requests")
        
        val startTime = System.currentTimeMillis()
        
        // Fetch URLs in parallel with concurrency limit
        val results = urls.mapIndexed { index, url ->
            async {
                try {
                    Log.d(TAG, "[$index] Fetching: $url")
                    val content = fetchPageContent(url, maxContentPerPage)
                    Log.i(TAG, "[$index] ✓ Fetched ${content.contentLength} chars from ${content.domain}")
                    content
                } catch (e: Exception) {
                    Log.w(TAG, "[$index] ✗ Failed to fetch $url: ${e.message}")
                    PageContent(
                        url = url,
                        domain = extractDomain(url),
                        title = "Failed to fetch",
                        content = "Error: ${e.message}",
                        contentLength = 0,
                        fetchTimeMs = 0
                    )
                }
            }
        }.awaitAll()
        
        val totalTime = System.currentTimeMillis() - startTime
        val successCount = results.count { it.contentLength > 0 }
        
        Log.i(TAG, "✓ Fetched $successCount/${urls.size} pages in ${totalTime}ms")
        Log.i(TAG, "Total content: ${results.sumOf { it.contentLength }} chars")
        
        results.filter { it.contentLength > 0 }
    }
    
    /**
     * Fetch content from multiple URLs in parallel with progress callback
     */
    suspend fun fetchMultipleUrlsWithProgress(
        urls: List<String>,
        maxConcurrent: Int = 5,
        maxContentPerPage: Int = 3000,
        progressCallback: (suspend (completed: Int, total: Int) -> Unit)? = null
    ): List<PageContent> = withContext(Dispatchers.IO) {
        
        Log.i(TAG, "=== Parallel Web Content Fetching with Progress ===")
        Log.i(TAG, "Fetching ${urls.size} URLs with max $maxConcurrent concurrent requests")
        
        val startTime = System.currentTimeMillis()
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        
        // Fetch URLs in parallel with concurrency limit and progress tracking
        val results = urls.mapIndexed { index, url ->
            async {
                try {
                    Log.d(TAG, "[$index] Fetching: $url")
                    val content = fetchPageContent(url, maxContentPerPage)
                    Log.i(TAG, "[$index] ✓ Fetched ${content.contentLength} chars from ${content.domain}")
                    
                    // Update progress
                    val count = completed.incrementAndGet()
                    progressCallback?.invoke(count, urls.size)
                    
                    content
                } catch (e: Exception) {
                    Log.w(TAG, "[$index] ✗ Failed to fetch $url: ${e.message}")
                    
                    // Update progress even on failure
                    val count = completed.incrementAndGet()
                    progressCallback?.invoke(count, urls.size)
                    
                    PageContent(
                        url = url,
                        domain = extractDomain(url),
                        title = "Failed to fetch",
                        content = "Error: ${e.message}",
                        contentLength = 0,
                        fetchTimeMs = 0
                    )
                }
            }
        }.awaitAll()
        
        val totalTime = System.currentTimeMillis() - startTime
        val successCount = results.count { it.contentLength > 0 }
        
        Log.i(TAG, "✓ Fetched $successCount/${urls.size} pages in ${totalTime}ms")
        Log.i(TAG, "Total content: ${results.sumOf { it.contentLength }} chars")
        
        results.filter { it.contentLength > 0 }
    }
    
    /**
     * Fetch and extract content from a single page
     */
    private fun fetchPageContent(url: String, maxContent: Int): PageContent {
        val startTime = System.currentTimeMillis()
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile) AppleWebKit/537.36")
            .build()
        
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: throw Exception("Empty response")
        
        val doc = Jsoup.parse(html)
        val domain = extractDomain(url)
        
        // Extract title
        val title = doc.title().take(200)
        
        // Extract main content using multiple strategies
        val content = extractMainContent(doc, maxContent)
        
        val fetchTime = System.currentTimeMillis() - startTime
        
        return PageContent(
            url = url,
            domain = domain,
            title = title,
            content = content,
            contentLength = content.length,
            fetchTimeMs = fetchTime
        )
    }
    
    /**
     * Extract main content from HTML document
     * Uses multiple strategies to find the most relevant content
     */
    private fun extractMainContent(doc: Document, maxLength: Int): String {
        // Strategy 1: Look for article tags
        val article = doc.select("article").firstOrNull()
        if (article != null) {
            val text = cleanText(article.text())
            if (text.length > 300) {  // Lowered threshold from 200
                Log.d(TAG, "✓ Extracted content from <article> tag (${text.length} chars)")
                return text.take(maxLength)
            }
        }
        
        // Strategy 2: Look for main content areas
        val mainSelectors = listOf(
            "main",
            "[role=main]",
            ".article-content",
            ".article-body",
            ".post-content",
            ".post-body",
            ".entry-content",
            ".story-body",
            ".content-body",
            ".content",
            "#content",
            ".text-content"
        )
        
        for (selector in mainSelectors) {
            val element = doc.select(selector).firstOrNull()
            if (element != null) {
                val text = cleanText(element.text())
                if (text.length > 300) {  // Lowered threshold
                    Log.d(TAG, "✓ Extracted content from selector: $selector (${text.length} chars)")
                    return text.take(maxLength)
                }
            }
        }
        
        // Strategy 3: Look for paragraphs in body (improved)
        val paragraphs = doc.select("p")
        if (paragraphs.size >= 3) {  // Need at least 3 paragraphs for quality content
            val text = paragraphs
                .map { cleanText(it.text()) }
                .filter { it.length > 50 }  // Filter out short paragraphs
                .joinToString("\n\n")
            
            if (text.length > 300) {
                Log.d(TAG, "✓ Extracted content from ${paragraphs.size} paragraphs (${text.length} chars)")
                return text.take(maxLength)
            }
        }
        
        // Strategy 4: Look for div with lots of text
        val textDivs = doc.select("div")
            .toList()  // Convert to Kotlin List first
            .filter { it.text().length > 500 }
            .sortedByDescending { it.text().length }
            .firstOrNull()
        
        if (textDivs != null) {
            val text = cleanText(textDivs.text())
            Log.d(TAG, "✓ Extracted content from text-heavy div (${text.length} chars)")
            return text.take(maxLength)
        }
        
        // Fallback: Use body text
        val bodyText = cleanText(doc.body().text())
        Log.d(TAG, "⚠️ Using fallback body text (${bodyText.length} chars)")
        return bodyText.take(maxLength)
    }
    
    /**
     * Clean extracted text
     */
    private fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .replace(Regex("\\n{3,}"), "\n\n")  // Max 2 newlines
            .trim()
    }
    
    /**
     * Extract domain from URL
     */
    private fun extractDomain(url: String): String {
        return try {
            val domain = url.substringAfter("://").substringBefore("/")
            domain.removePrefix("www.")
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Generate a comprehensive summary from multiple page contents
     */
    fun generateContextSummary(pages: List<PageContent>, maxLength: Int = 3000): String {
        if (pages.isEmpty()) {
            return "No content fetched."
        }
        
        return buildString {
            appendLine("=== Deep Web Research Results ===")
            appendLine()
            appendLine("Fetched ${pages.size} sources:")
            appendLine()
            
            pages.forEachIndexed { index, page ->
                appendLine("${index + 1}. ${page.title}")
                appendLine("   Source: ${page.domain}")
                appendLine("   Content (${page.contentLength} chars):")
                
                // Include first portion of content
                val preview = page.content.take(500)
                appendLine("   $preview")
                if (page.content.length > 500) {
                    appendLine("   ... (truncated)")
                }
                appendLine()
            }
            
            // Limit total length
            if (length > maxLength) {
                setLength(maxLength)
                appendLine("\n... (truncated for context size)")
            }
        }
    }
    
    companion object {
        private const val TAG = "WebContentFetcher"
    }
}

/**
 * Represents fetched page content
 */
data class PageContent(
    val url: String,
    val domain: String,
    val title: String,
    val content: String,
    val contentLength: Int,
    val fetchTimeMs: Long
)
