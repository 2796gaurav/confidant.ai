package com.confidant.ai.search

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * BM25Search - Lightweight keyword-based search algorithm
 * 
 * BM25 (Best Matching 25) is a probabilistic ranking function that:
 * - Considers term frequency with saturation
 * - Normalizes for document length
 * - Weights terms by inverse document frequency
 * 
 * Advantages over semantic embeddings:
 * - No model download required (0 MB vs 25+ MB)
 * - Instant search (microseconds vs milliseconds)
 * - Low memory footprint
 * - 90%+ effective for keyword-based retrieval
 * - Pure Kotlin, no native dependencies
 * 
 * Perfect for mobile apps where speed and resource efficiency matter.
 */
class BM25Search(
    private val k1: Float = 1.5f,  // Term frequency saturation parameter
    private val b: Float = 0.75f    // Document length normalization
) {
    
    private val documents = mutableListOf<Document>()
    private val invertedIndex = mutableMapOf<String, MutableList<Int>>()
    private var avgDocLength = 0.0
    
    /**
     * Index a document for search
     */
    fun indexDocument(id: Long, text: String, metadata: Map<String, Any> = emptyMap()) {
        val tokens = tokenize(text)
        val docIndex = documents.size
        
        documents.add(Document(
            id = id,
            tokens = tokens,
            length = tokens.size,
            metadata = metadata
        ))
        
        // Update inverted index
        tokens.toSet().forEach { token ->
            invertedIndex.getOrPut(token) { mutableListOf() }.add(docIndex)
        }
        
        // Update average document length
        avgDocLength = documents.sumOf { it.length }.toDouble() / documents.size
    }
    
    /**
     * Search documents using BM25 ranking
     */
    fun search(query: String, limit: Int = 10, minScore: Float = 0.0f): List<SearchResult> {
        val queryTokens = tokenize(query)
        
        if (queryTokens.isEmpty() || documents.isEmpty()) {
            return emptyList()
        }
        
        // Calculate BM25 scores for all documents
        val scores = mutableMapOf<Int, Float>()
        
        queryTokens.toSet().forEach { token ->
            val docIndices = invertedIndex[token] ?: return@forEach
            val idf = calculateIDF(docIndices.size)
            
            docIndices.forEach { docIndex ->
                val doc = documents[docIndex]
                val tf = doc.tokens.count { it == token }
                val score = calculateBM25Score(tf, doc.length, idf)
                scores[docIndex] = scores.getOrDefault(docIndex, 0f) + score
            }
        }
        
        // Sort by score and return top results
        return scores.entries
            .filter { it.value >= minScore }
            .sortedByDescending { it.value }
            .take(limit)
            .map { (docIndex, score) ->
                val doc = documents[docIndex]
                SearchResult(
                    id = doc.id,
                    score = score,
                    metadata = doc.metadata
                )
            }
    }
    
    /**
     * Calculate BM25 score for a term in a document
     */
    private fun calculateBM25Score(tf: Int, docLength: Int, idf: Float): Float {
        val numerator = tf * (k1 + 1)
        val denominator = tf + k1 * (1 - b + b * (docLength / avgDocLength))
        return idf * (numerator / denominator).toFloat()
    }
    
    /**
     * Calculate Inverse Document Frequency
     */
    private fun calculateIDF(docFreq: Int): Float {
        val n = documents.size
        return ln((n - docFreq + 0.5) / (docFreq + 0.5) + 1.0).toFloat()
    }
    
    /**
     * Tokenize text into searchable terms
     * - Lowercase
     * - Remove punctuation
     * - Split on whitespace
     * - Remove stop words
     * - Stem words (basic)
     */
    private fun tokenize(text: String): List<String> {
        val stopWords = setOf(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
            "to", "was", "will", "with", "the", "this", "but", "they", "have"
        )
        
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .map { stem(it) }
    }
    
    /**
     * Basic stemming (Porter-like)
     * Removes common suffixes
     */
    private fun stem(word: String): String {
        var result = word
        
        // Remove common suffixes
        val suffixes = listOf("ing", "ed", "es", "s", "ly", "er", "est", "tion", "ness")
        for (suffix in suffixes) {
            if (result.endsWith(suffix) && result.length > suffix.length + 2) {
                result = result.dropLast(suffix.length)
                break
            }
        }
        
        return result
    }
    
    /**
     * Clear all indexed documents
     */
    fun clear() {
        documents.clear()
        invertedIndex.clear()
        avgDocLength = 0.0
    }
    
    /**
     * Get number of indexed documents
     */
    fun size(): Int = documents.size
    
    /**
     * Document representation
     */
    private data class Document(
        val id: Long,
        val tokens: List<String>,
        val length: Int,
        val metadata: Map<String, Any>
    )
}

/**
 * Search result with score
 */
data class SearchResult(
    val id: Long,
    val score: Float,
    val metadata: Map<String, Any>
)
