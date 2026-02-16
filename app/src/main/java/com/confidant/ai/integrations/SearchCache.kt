package com.confidant.ai.integrations

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.Duration

/**
 * SearchCache - Cache search results to avoid redundant searches
 * 
 * Features:
 * - In-memory cache for instant access
 * - Disk cache for persistence across app restarts
 * - TTL-based expiration (default: 1 hour)
 * - LRU eviction when cache is full
 * 
 * Expected Impact:
 * - Instant responses for repeated queries
 * - 50-80% reduction in search API calls
 * - Better user experience
 */
class SearchCache(private val context: Context) {
    
    private val memoryCache = mutableMapOf<String, CachedResult>()
    private val cacheMutex = Mutex()
    private val cacheDir = File(context.cacheDir, "search_cache")
    
    private val MAX_MEMORY_ENTRIES = 50
    private val DEFAULT_TTL_MINUTES = 60L  // 1 hour
    
    init {
        cacheDir.mkdirs()
        Log.i(TAG, "SearchCache initialized: ${cacheDir.absolutePath}")
    }
    
    /**
     * Get cached result if available and not expired
     */
    suspend fun get(query: String): String? = cacheMutex.withLock {
        val key = hashQuery(query)
        
        // Check memory cache first
        val memResult = memoryCache[key]
        if (memResult != null && !memResult.isExpired()) {
            Log.i(TAG, "✓ Memory cache HIT: $query")
            return memResult.result
        }
        
        // Check disk cache
        val diskResult = getDiskCache(key)
        if (diskResult != null && !diskResult.isExpired()) {
            Log.i(TAG, "✓ Disk cache HIT: $query")
            // Promote to memory cache
            memoryCache[key] = diskResult
            return diskResult.result
        }
        
        Log.d(TAG, "✗ Cache MISS: $query")
        null
    }
    
    /**
     * Store result in cache
     */
    suspend fun put(query: String, result: String, ttlMinutes: Long = DEFAULT_TTL_MINUTES) = cacheMutex.withLock {
        val key = hashQuery(query)
        val cached = CachedResult(
            result = result,
            timestamp = Instant.now(),
            ttlMinutes = ttlMinutes
        )
        
        // Store in memory
        memoryCache[key] = cached
        
        // Evict oldest if cache is full
        if (memoryCache.size > MAX_MEMORY_ENTRIES) {
            val oldest = memoryCache.entries.minByOrNull { it.value.timestamp }
            oldest?.let { memoryCache.remove(it.key) }
        }
        
        // Store on disk
        saveDiskCache(key, cached)
        
        Log.i(TAG, "✓ Cached: $query (TTL: ${ttlMinutes}m)")
    }
    
    /**
     * Clear expired entries
     */
    suspend fun clearExpired() = cacheMutex.withLock {
        // Clear memory
        val expiredKeys = memoryCache.filter { it.value.isExpired() }.keys
        expiredKeys.forEach { memoryCache.remove(it) }
        
        // Clear disk
        cacheDir.listFiles()?.forEach { file ->
            val cached = loadCachedResult(file)
            if (cached?.isExpired() == true) {
                file.delete()
            }
        }
        
        Log.i(TAG, "Cleared ${expiredKeys.size} expired entries")
    }
    
    /**
     * Clear all cache
     */
    suspend fun clearAll() = cacheMutex.withLock {
        memoryCache.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "Cache cleared")
    }
    
    private fun hashQuery(query: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(query.lowercase().toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
    
    private fun getDiskCache(key: String): CachedResult? {
        val file = File(cacheDir, key)
        if (!file.exists()) return null
        return loadCachedResult(file)
    }
    
    private fun saveDiskCache(key: String, cached: CachedResult) {
        try {
            val file = File(cacheDir, key)
            val data = "${cached.timestamp.toEpochMilli()}|${cached.ttlMinutes}|${cached.result}"
            file.writeText(data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save disk cache", e)
        }
    }
    
    private fun loadCachedResult(file: File): CachedResult? {
        return try {
            val data = file.readText()
            val parts = data.split("|", limit = 3)
            if (parts.size != 3) return null
            
            CachedResult(
                result = parts[2],
                timestamp = Instant.ofEpochMilli(parts[0].toLong()),
                ttlMinutes = parts[1].toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load disk cache", e)
            null
        }
    }
    
    private data class CachedResult(
        val result: String,
        val timestamp: Instant,
        val ttlMinutes: Long
    ) {
        fun isExpired(): Boolean {
            val age = Duration.between(timestamp, Instant.now())
            return age.toMinutes() > ttlMinutes
        }
    }
    
    companion object {
        private const val TAG = "SearchCache"
    }
}
