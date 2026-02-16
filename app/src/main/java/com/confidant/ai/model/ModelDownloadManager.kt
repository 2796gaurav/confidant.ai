package com.confidant.ai.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.confidant.ai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages LLM model download with progress notifications and persistent storage.
 * 
 * STORAGE STRATEGY:
 * - Android 10+ (API 29+): Uses MediaStore to save to Downloads folder (survives uninstall)
 * - Android 9 and below: Uses legacy external storage Downloads folder
 * - All downloads are saved to persistent storage that survives app uninstallation
 * 
 * DOWNLOAD BEHAVIOR:
 * - Only downloads when user explicitly clicks download button
 * - Supports resume if download is interrupted
 * - Validates GGUF format after download
 * - Shows progress notifications
 * - Prevents concurrent downloads with synchronized check
 */
class ModelDownloadManager(private val context: Context) {

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "model_download"
    private val NOTIFICATION_ID = 1001
    
    // Cache to prevent repeated logging - EXTENDED to 5 minutes
    private var lastModelCheckPath: String? = null
    private var lastModelCheckTime: Long = 0
    private val MODEL_CHECK_CACHE_MS = 300000L // Cache for 5 minutes (reduced logging noise)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of AI model downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Check if model exists in persistent storage (Downloads folder)
     * Also checks legacy internal storage location for migration
     * 
     * OPTIMIZED: Caches result for 30 seconds to prevent repeated MediaStore queries
     */
    fun isModelDownloaded(): Boolean {
        if (isDownloading.value) {
            return false
        }

        // First check persistent storage (Downloads folder)
        val persistentPath = getPersistentModelPath()
        if (persistentPath != null) {
            val persistentFile = File(persistentPath)
            if (persistentFile.exists() && persistentFile.length() >= MIN_MODEL_SIZE) {
                // Only log if path changed or cache expired
                val now = System.currentTimeMillis()
                if (persistentPath != lastModelCheckPath || now - lastModelCheckTime > MODEL_CHECK_CACHE_MS) {
                    android.util.Log.d(TAG, "✓ Model ready: ${persistentFile.name} (${persistentFile.length() / (1024 * 1024)} MB)")
                    lastModelCheckPath = persistentPath
                    lastModelCheckTime = now
                }
                return true
            }
        }

        // Check legacy internal storage location (for migration)
        val legacyFile = File(context.filesDir, MODEL_FILENAME)
        if (legacyFile.exists() && legacyFile.length() >= MIN_MODEL_SIZE) {
            android.util.Log.i(TAG, "✓ Model found in legacy location: ${legacyFile.absolutePath}")
            return true
        }

        // Check for temp file that needs recovery
        val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")
        if (tempFile.exists() && tempFile.length() >= MIN_MODEL_SIZE) {
            android.util.Log.w(TAG, "Found valid temp file, recovering...")
            try {
                if (tempFile.renameTo(legacyFile)) {
                    android.util.Log.i(TAG, "✓ Successfully recovered model from temp file")
                    return true
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error recovering temp file", e)
            }
        }

        android.util.Log.d(TAG, "✗ Model not found - download required")
        return false
    }
    
    /**
     * Force a fresh scan for the model file, bypassing cache.
     * Useful when user manually places file in Downloads folder.
     * 
     * Returns the path if found, null otherwise.
     */
    fun scanForModel(): String? {
        android.util.Log.i(TAG, "=== Manual Model Scan ===")
        android.util.Log.i(TAG, "Looking for: $MODEL_FILENAME")
        android.util.Log.i(TAG, "Minimum size: ${MIN_MODEL_SIZE / (1024 * 1024)} MB")
        android.util.Log.i(TAG, "Android version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        
        // Clear cache to force fresh scan
        lastModelCheckPath = null
        lastModelCheckTime = 0
        
        // List all possible download locations for debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.util.Log.d(TAG, "Scanning MediaStore and file system...")
            
            // Try to list all .gguf files in MediaStore for debugging
            try {
                val projection = arrayOf(
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATA
                )
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%.gguf")
                
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val count = cursor.count
                    android.util.Log.d(TAG, "MediaStore found $count .gguf files:")
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        val size = cursor.getLong(1)
                        val path = cursor.getString(2)
                        android.util.Log.d(TAG, "  - $name (${size / (1024 * 1024)} MB) at $path")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error listing MediaStore files", e)
            }
            
            // Also try direct file system scan
            val possiblePaths = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Downloads")
            )
            
            possiblePaths.forEach { dir ->
                if (dir.exists()) {
                    android.util.Log.d(TAG, "Checking directory: ${dir.absolutePath}")
                    val ggufFiles = dir.listFiles { file ->
                        file.extension.equals("gguf", ignoreCase = true)
                    }
                    if (ggufFiles != null && ggufFiles.isNotEmpty()) {
                        android.util.Log.d(TAG, "  Found ${ggufFiles.size} .gguf files:")
                        ggufFiles.forEach { file ->
                            android.util.Log.d(TAG, "    - ${file.name} (${file.length() / (1024 * 1024)} MB)")
                        }
                    } else {
                        android.util.Log.d(TAG, "  No .gguf files found")
                    }
                } else {
                    android.util.Log.d(TAG, "Directory does not exist: ${dir.absolutePath}")
                }
            }
        }
        
        // Scan persistent storage
        val path = getPersistentModelPath()
        if (path != null) {
            val file = File(path)
            android.util.Log.i(TAG, "✓ Model found!")
            android.util.Log.i(TAG, "  Path: $path")
            android.util.Log.i(TAG, "  Size: ${file.length() / (1024 * 1024)} MB")
            
            // Save to preferences
            try {
                kotlinx.coroutines.runBlocking {
                    val prefs = com.confidant.ai.data.PreferencesManager.getInstance(context)
                    prefs.setModelPath(path)
                    android.util.Log.i(TAG, "  ✓ Saved to preferences")
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to save to preferences", e)
            }
            
            return path
        }
        
        android.util.Log.w(TAG, "✗ Model not found in Downloads folder")
        android.util.Log.i(TAG, "Expected filename: $MODEL_FILENAME (case-insensitive)")
        android.util.Log.i(TAG, "Also accepts variants like: ${MODEL_FILENAME.substringBeforeLast(".")} (1).gguf")
        android.util.Log.i(TAG, "Please ensure the file is in the Downloads folder and has the correct name")
        
        return null
    }

    /**
     * Get model file path - checks persistent storage first, then legacy location
     * 
     * CRITICAL FIX: If preferences are null but model exists on disk, auto-recover
     * by saving the found path to preferences. This prevents re-downloads when
     * preferences are cleared but the model file still exists.
     */
    fun getModelPath(): String? {
        // Check persistent storage first
        val persistentPath = getPersistentModelPath()
        if (persistentPath != null) {
            val persistentFile = File(persistentPath)
            if (persistentFile.exists() && persistentFile.length() >= MIN_MODEL_SIZE) {
                // Only log if path changed or cache expired
                val now = System.currentTimeMillis()
                if (persistentPath != lastModelCheckPath || now - lastModelCheckTime > MODEL_CHECK_CACHE_MS) {
                    android.util.Log.d(TAG, "Using model from persistent storage: $persistentPath")
                    lastModelCheckPath = persistentPath
                    lastModelCheckTime = now
                    
                    // AUTO-RECOVERY: Save to preferences if not already saved
                    // This prevents re-downloads when preferences are cleared
                    // Use a background thread to avoid blocking
                    Thread {
                        try {
                            kotlinx.coroutines.runBlocking {
                                val prefs = com.confidant.ai.data.PreferencesManager.getInstance(context)
                                val savedPath = prefs.getModelPath()
                                if (savedPath != persistentPath) {
                                    prefs.setModelPath(persistentPath)
                                    android.util.Log.i(TAG, "✓ Auto-recovered model path to preferences: $persistentPath")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "Failed to auto-save model path", e)
                        }
                    }.start()
                }
                return persistentPath
            }
        }

        // Fallback to legacy internal storage
        val legacyFile = File(context.filesDir, MODEL_FILENAME)
        if (legacyFile.exists() && legacyFile.length() >= MIN_MODEL_SIZE) {
            android.util.Log.d(TAG, "Using model from legacy storage: ${legacyFile.absolutePath}")
            
            // AUTO-RECOVERY: Save to preferences
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        val prefs = com.confidant.ai.data.PreferencesManager.getInstance(context)
                        prefs.setModelPath(legacyFile.absolutePath)
                        android.util.Log.i(TAG, "✓ Auto-recovered legacy model path to preferences")
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to auto-save legacy model path", e)
                }
            }.start()
            
            return legacyFile.absolutePath
        }

        return null
    }

    /**
     * Get persistent model path from Downloads folder.
     * 
     * IMPORTANT: This location survives app uninstallation!
     * - Android 10+: Uses MediaStore to query Downloads folder
     * - Android 9-: Uses legacy external storage path
     * 
     * Searches for exact filename or variants with (1), (2), etc. that
     * Android automatically creates when files with same name exist.
     * 
     * OPTIMIZED: Only searches for variants if exact filename not found
     * 
     * ENHANCED: Also searches case-insensitive to handle manual downloads
     * 
     * FIXED: Added fallback to direct file system scan when MediaStore fails
     */
    private fun getPersistentModelPath(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - use MediaStore to find the file
                val projection = arrayOf(
                    MediaStore.Downloads._ID, 
                    MediaStore.Downloads.DISPLAY_NAME, 
                    MediaStore.Downloads.DATA,
                    MediaStore.Downloads.SIZE
                )
                
                // First try exact filename (case-sensitive)
                var selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                var selectionArgs = arrayOf(MODEL_FILENAME)

                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                        val path = cursor.getString(dataIndex)
                        val size = cursor.getLong(sizeIndex)
                        
                        // Verify file exists and has valid size
                        if (size >= MIN_MODEL_SIZE) {
                            val file = File(path)
                            if (file.exists() && file.length() >= MIN_MODEL_SIZE) {
                                // Only log if path changed or cache expired
                                val now = System.currentTimeMillis()
                                if (path != lastModelCheckPath || now - lastModelCheckTime > MODEL_CHECK_CACHE_MS) {
                                    android.util.Log.i(TAG, "✓ Found model in MediaStore: $path (${size / (1024 * 1024)} MB)")
                                    lastModelCheckPath = path
                                    lastModelCheckTime = now
                                }
                                return path
                            }
                        }
                    }
                }
                
                // If exact filename not found, search for variants and case-insensitive matches
                // This handles:
                // 1. Android auto-renames like "filename (1).gguf"
                // 2. Manual downloads with different casing like "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
                val baseNameWithoutExt = MODEL_FILENAME.substringBeforeLast(".")
                val extension = MODEL_FILENAME.substringAfterLast(".")
                
                // Search for any .gguf file that might be our model
                selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                selectionArgs = arrayOf("%.gguf")
                
                var foundInMediaStore = false
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC" // Get most recent
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                        val path = cursor.getString(dataIndex)
                        val name = cursor.getString(nameIndex)
                        val size = cursor.getLong(sizeIndex)
                        
                        // Check if this is our model file (case-insensitive match)
                        val isMatch = name.equals(MODEL_FILENAME, ignoreCase = true) ||
                                     name.startsWith(baseNameWithoutExt, ignoreCase = true)
                        
                        // Verify file exists and has valid size
                        if (isMatch && size >= MIN_MODEL_SIZE) {
                            val file = File(path)
                            if (file.exists() && file.length() >= MIN_MODEL_SIZE) {
                                foundInMediaStore = true
                                // Only log if path changed or cache expired
                                val now = System.currentTimeMillis()
                                if (path != lastModelCheckPath || now - lastModelCheckTime > MODEL_CHECK_CACHE_MS) {
                                    if (name.equals(MODEL_FILENAME, ignoreCase = false)) {
                                        android.util.Log.d(TAG, "✓ Model found: $name (${size / (1024 * 1024)} MB)")
                                    } else {
                                        // Use DEBUG level for variant detection to reduce log noise
                                        android.util.Log.d(TAG, "✓ Model variant found: $name (${size / (1024 * 1024)} MB)")
                                    }
                                    lastModelCheckPath = path
                                    lastModelCheckTime = now
                                }
                                return path
                            }
                        }
                    }
                }
                
                // CRITICAL FIX: If MediaStore query failed, try direct file system access
                // This handles cases where MediaStore is not yet updated or has permission issues
                if (!foundInMediaStore) {
                    android.util.Log.d(TAG, "MediaStore query found no matches, trying direct file system access...")
                    
                    // Try common Download folder locations
                    val possibleDownloadPaths = listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        File(Environment.getExternalStorageDirectory(), "Download"),
                        File(Environment.getExternalStorageDirectory(), "Downloads")
                    )
                    
                    for (downloadsDir in possibleDownloadPaths) {
                        if (!downloadsDir.exists()) continue
                        
                        android.util.Log.d(TAG, "Scanning directory: ${downloadsDir.absolutePath}")
                        
                        // Try exact filename first
                        val exactFile = File(downloadsDir, MODEL_FILENAME)
                        if (exactFile.exists() && exactFile.length() >= MIN_MODEL_SIZE) {
                            android.util.Log.i(TAG, "✓ Found model via direct access: ${exactFile.absolutePath} (${exactFile.length() / (1024 * 1024)} MB)")
                            return exactFile.absolutePath
                        }
                        
                        // Try case-insensitive and variants
                        val files = downloadsDir.listFiles { file ->
                            file.extension.equals("gguf", ignoreCase = true) && file.length() >= MIN_MODEL_SIZE
                        }
                        
                        files?.forEach { file ->
                            val fileName = file.name
                            val isMatch = fileName.equals(MODEL_FILENAME, ignoreCase = true) ||
                                         fileName.startsWith(baseNameWithoutExt, ignoreCase = true)
                            
                            if (isMatch) {
                                android.util.Log.i(TAG, "✓ Found model variant via direct access: ${file.absolutePath} (${file.length() / (1024 * 1024)} MB)")
                                return file.absolutePath
                            }
                        }
                    }
                }
            } else {
                // Android 9 and below - use legacy external storage
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                
                // Try exact filename first
                val modelFile = File(downloadsDir, MODEL_FILENAME)
                if (modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE) {
                    android.util.Log.d(TAG, "✓ Model found: ${modelFile.name}")
                    return modelFile.absolutePath
                }
                
                // Try variants and case-insensitive matches
                val baseNameWithoutExt = MODEL_FILENAME.substringBeforeLast(".")
                val extension = MODEL_FILENAME.substringAfterLast(".")
                
                // Check all .gguf files in Downloads folder
                downloadsDir.listFiles { file ->
                    file.extension.equals("gguf", ignoreCase = true)
                }?.forEach { file ->
                    if (file.length() >= MIN_MODEL_SIZE) {
                        val fileName = file.name
                        val isMatch = fileName.equals(MODEL_FILENAME, ignoreCase = true) ||
                                     fileName.startsWith(baseNameWithoutExt, ignoreCase = true)
                        
                        if (isMatch) {
                            if (fileName.equals(MODEL_FILENAME, ignoreCase = false)) {
                                android.util.Log.d(TAG, "✓ Model found: ${file.name}")
                            } else {
                                android.util.Log.d(TAG, "✓ Model variant found: ${file.name}")
                            }
                            return file.absolutePath
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting persistent model path", e)
            null
        }
    }

    /**
     * Create file handles for persistent storage (Android 10+)
     * Returns pair of (modelFile, tempFile)
     */
    private fun getPersistentStorageFiles(): Pair<File, File> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, we need to use MediaStore
            // But we'll use a temporary location first, then move to MediaStore
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            return Pair(
                File(cacheDir, MODEL_FILENAME),
                File(cacheDir, "$MODEL_FILENAME.tmp")
            )
        } else {
            // Android 9 and below
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            return Pair(
                File(downloadsDir, MODEL_FILENAME),
                File(downloadsDir, "$MODEL_FILENAME.tmp")
            )
        }
    }

    /**
     * Move file to persistent storage using MediaStore (Android 10+)
     */
    private fun moveToMediaStore(sourceFile: File): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Already in the right place for older Android
            return sourceFile.absolutePath
        }

        return try {
            android.util.Log.d(TAG, "Moving file to MediaStore...")

            // Check if file already exists in MediaStore
            val existingPath = getPersistentModelPath()
            if (existingPath != null) {
                val existingFile = File(existingPath)
                if (existingFile.exists()) {
                    android.util.Log.d(TAG, "Deleting existing file in MediaStore")
                    existingFile.delete()
                }
            }

            // Create new entry in MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, MODEL_FILENAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw Exception("Failed to create MediaStore entry")

            android.util.Log.d(TAG, "Created MediaStore entry: $uri")

            // Copy file to MediaStore
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                    outputStream.flush()
                }
            } ?: throw Exception("Failed to open output stream")

            // Mark as complete
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)

            android.util.Log.d(TAG, "File moved to MediaStore successfully")

            // Get the actual file path
            val projection = arrayOf(MediaStore.Downloads.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                    val path = cursor.getString(dataIndex)
                    android.util.Log.d(TAG, "MediaStore file path: $path")
                    
                    // Delete source file
                    sourceFile.delete()
                    
                    return path
                }
            }

            throw Exception("Failed to get file path from MediaStore")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error moving to MediaStore", e)
            null
        }
    }

    /**
     * Delete existing model file
     */
    fun deleteModel(): Boolean {
        return try {
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            if (modelFile.exists()) {
                modelFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("ModelDownloadManager", "Failed to delete model", e)
            false
        }
    }
    
    /**
     * Delete all model files including duplicates (e.g., "model.gguf", "model (1).gguf", etc.)
     * This prevents accumulation of duplicate files
     */
    private fun deleteAllModelFiles() {
        try {
            android.util.Log.d(TAG, "Cleaning up existing model files...")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - delete from MediaStore
                val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                val baseNameWithoutExt = MODEL_FILENAME.substringBeforeLast(".")
                val extension = MODEL_FILENAME.substringAfterLast(".")
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("$baseNameWithoutExt%.$extension")
                
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val uri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        try {
                            context.contentResolver.delete(uri, null, null)
                            android.util.Log.d(TAG, "Deleted existing file: $name")
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "Failed to delete $name: ${e.message}")
                        }
                    }
                }
            } else {
                // Android 9 and below - delete from Downloads folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val baseNameWithoutExt = MODEL_FILENAME.substringBeforeLast(".")
                val extension = MODEL_FILENAME.substringAfterLast(".")
                
                // Delete exact filename
                val exactFile = File(downloadsDir, MODEL_FILENAME)
                if (exactFile.exists()) {
                    exactFile.delete()
                    android.util.Log.d(TAG, "Deleted: $MODEL_FILENAME")
                }
                
                // Delete variants
                for (i in 1..10) {
                    val variantFile = File(downloadsDir, "$baseNameWithoutExt ($i).$extension")
                    if (variantFile.exists()) {
                        variantFile.delete()
                        android.util.Log.d(TAG, "Deleted: $baseNameWithoutExt ($i).$extension")
                    }
                }
            }
            
            // Also delete from cache
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val cacheFile = File(cacheDir, MODEL_FILENAME)
            if (cacheFile.exists()) {
                cacheFile.delete()
                android.util.Log.d(TAG, "Deleted cache file")
            }
            
            // Delete legacy internal storage
            val legacyFile = File(context.filesDir, MODEL_FILENAME)
            if (legacyFile.exists()) {
                legacyFile.delete()
                android.util.Log.d(TAG, "Deleted legacy file")
            }
            
            android.util.Log.i(TAG, "Model file cleanup complete")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during model file cleanup", e)
        }
    }

    /**
     * Download model with progress updates and resume capability
     * Saves to persistent storage (Downloads folder) that survives app uninstallation
     * No automatic retry - user must manually retry if it fails
     * 
     * CRITICAL: Prevents concurrent downloads with synchronized check
     */
    suspend fun downloadModel(modelUrl: String = DEFAULT_MODEL_URL): Result<Unit> {
        // CRITICAL: Check and set downloading flag BEFORE entering coroutine context
        // This prevents race condition where multiple downloads start simultaneously
        synchronized(this) {
            if (_isDownloading.value) {
                android.util.Log.w(TAG, "Download already in progress, ignoring duplicate request")
                return Result.failure(Exception("Download already in progress"))
            }
            _isDownloading.value = true
        }
        
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d(TAG, "Starting download from: $modelUrl")

                // Check if model already exists in persistent storage (including variants)
                val existingPath = getPersistentModelPath()
                if (existingPath != null) {
                    val existingFile = File(existingPath)
                    if (existingFile.exists() && existingFile.length() >= MIN_MODEL_SIZE) {
                        android.util.Log.i(TAG, "Model already exists in persistent storage: $existingPath")
                        
                        // CRITICAL: Save path to preferences and WAIT for completion
                        val prefs = com.confidant.ai.data.PreferencesManager.getInstance(context)
                        try {
                            prefs.setModelPath(existingPath)
                            android.util.Log.i(TAG, "Model path saved to preferences: $existingPath")
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Failed to save model path to preferences", e)
                        }
                        
                        showModelAlreadyPresentNotification()
                        _isDownloading.value = false
                        _downloadProgress.value = 1f
                        
                        return@withContext Result.success(Unit)
                    }
                }
                
                // Delete any existing model files (including duplicates) before downloading fresh
                deleteAllModelFiles()

                // Use persistent storage location (Downloads folder)
                val (modelFile, tempFile) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ - use MediaStore
                    getPersistentStorageFiles()
                } else {
                    // Android 9 and below - use legacy external storage
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir.mkdirs()
                    Pair(
                        File(downloadsDir, MODEL_FILENAME),
                        File(downloadsDir, "$MODEL_FILENAME.tmp")
                    )
                }

                // Check if we can resume
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                android.util.Log.d(TAG, "Existing bytes: $existingBytes")

                // Show initial notification
                if (existingBytes == 0L) {
                    showDownloadNotification(0, "Starting download...")
                } else {
                    showDownloadNotification(
                        ((existingBytes.toFloat() / MIN_MODEL_SIZE) * 100).toInt(),
                        "Resuming download..."
                    )
                }

                val url = URL(modelUrl)
                val connection = url.openConnection() as HttpURLConnection

                // Configure connection for reliability
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
                connection.setRequestProperty("Accept-Encoding", "identity") // Disable compression
                connection.setRequestProperty("Connection", "keep-alive")

                // Resume support
                if (existingBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                    android.util.Log.d(TAG, "Requesting resume from byte: $existingBytes")
                }

                connection.connect()

                val responseCode = connection.responseCode
                android.util.Log.d(TAG, "Response code: $responseCode")

                // Check if resume is supported
                val isResumeSupported = responseCode == HttpURLConnection.HTTP_PARTIAL
                if (existingBytes > 0 && !isResumeSupported) {
                    android.util.Log.w(TAG, "Resume not supported, starting fresh")
                    tempFile.delete()
                }

                if (responseCode != HttpURLConnection.HTTP_OK &&
                    responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("Server returned HTTP $responseCode")
                }

                val contentLength = connection.contentLength.toLong()
                val totalFileSize = if (isResumeSupported) {
                    existingBytes + contentLength
                } else {
                    contentLength
                }

                android.util.Log.d(TAG, "Content length: $contentLength, Total size: $totalFileSize")

                if (totalFileSize <= 0) {
                    throw Exception("Invalid file size: $totalFileSize")
                }

                // Download with resume support
                var downloadedBytes = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, isResumeSupported).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = existingBytes
                        var lastProgressUpdate = 0
                        var lastProgressTime = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            downloadedBytes = totalBytesRead

                            val progress = (totalBytesRead.toFloat() / totalFileSize.toFloat())
                            _downloadProgress.value = progress

                            val progressPercent = (progress * 100).toInt()
                            val currentTime = System.currentTimeMillis()

                            // Update notification every 5% or every 3 seconds
                            if (progressPercent >= lastProgressUpdate + 5 ||
                                currentTime - lastProgressTime > 3000) {
                                lastProgressUpdate = progressPercent
                                lastProgressTime = currentTime
                                val downloadedMB = totalBytesRead / (1024 * 1024)
                                val totalMB = totalFileSize / (1024 * 1024)
                                showDownloadNotification(
                                    progressPercent,
                                    "Downloading: $downloadedMB MB / $totalMB MB"
                                )
                                android.util.Log.d(TAG, "Progress: $progressPercent% ($downloadedMB/$totalMB MB)")
                            }
                        }

                        // Ensure all data is written to disk BEFORE closing
                        output.flush()
                        output.fd.sync()
                        android.util.Log.d(TAG, "Stream flushed and synced. Downloaded: $downloadedBytes bytes")
                    }
                }

                connection.disconnect()

                // CRITICAL: Give filesystem time to update file metadata
                Thread.sleep(100)
                
                android.util.Log.d(TAG, "Download stream closed. Checking temp file...")

                // Check temp file size
                val tempFileSize = tempFile.length()
                android.util.Log.d(TAG, "Download complete. Temp file size: $tempFileSize bytes (expected: $downloadedBytes)")

                // Verify file was downloaded correctly
                if (!tempFile.exists()) {
                    throw Exception("Temp file disappeared after download")
                }

                if (tempFileSize == 0L) {
                    // Try one more time after a longer wait
                    Thread.sleep(500)
                    val retrySize = tempFile.length()
                    if (retrySize == 0L) {
                        throw Exception("Temp file is empty (0 bytes) - filesystem sync issue")
                    }
                    android.util.Log.d(TAG, "File size updated after retry: $retrySize bytes")
                }

                val finalTempSize = tempFile.length()
                if (finalTempSize < MIN_MODEL_SIZE) {
                    throw Exception("Downloaded file is too small: $finalTempSize bytes (min: $MIN_MODEL_SIZE)")
                }

                android.util.Log.i(TAG, "Download verified. Finalizing...")

                // Move temp file to final location
                var finalized = false
                var finalPath: String? = null
                
                try {
                    // Delete existing model file if present
                    if (modelFile.exists()) {
                        android.util.Log.d(TAG, "Deleting existing model file")
                        val deleted = modelFile.delete()
                        android.util.Log.d(TAG, "Existing file deleted: $deleted")
                    }

                    // Try rename first (atomic operation)
                    android.util.Log.d(TAG, "Attempting rename: ${tempFile.absolutePath} -> ${modelFile.absolutePath}")
                    val renamed = tempFile.renameTo(modelFile)

                    if (renamed && modelFile.exists()) {
                        android.util.Log.i(TAG, "Rename successful")
                        
                        // For Android 10+, move to MediaStore for persistence
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            finalPath = moveToMediaStore(modelFile)
                            if (finalPath != null) {
                                android.util.Log.i(TAG, "Moved to persistent storage: $finalPath")
                                finalized = true
                            } else {
                                // Fallback: keep in cache but warn user
                                android.util.Log.w(TAG, "Failed to move to MediaStore, using cache location")
                                finalPath = modelFile.absolutePath
                                finalized = true
                            }
                        } else {
                            // Android 9 and below - already in Downloads folder
                            finalPath = modelFile.absolutePath
                            finalized = true
                        }
                    } else {
                        // Rename failed, try copy instead
                        android.util.Log.w(TAG, "Rename failed (returned: $renamed, exists: ${modelFile.exists()}), trying copy")

                        tempFile.inputStream().use { input ->
                            modelFile.outputStream().use { output ->
                                input.copyTo(output, bufferSize = 8192)
                                output.flush()
                                output.fd.sync()
                            }
                        }

                        if (modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE) {
                            android.util.Log.i(TAG, "Copy successful, deleting temp file")
                            tempFile.delete()
                            
                            // For Android 10+, move to MediaStore
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                finalPath = moveToMediaStore(modelFile)
                                if (finalPath != null) {
                                    android.util.Log.i(TAG, "Moved to persistent storage: $finalPath")
                                    finalized = true
                                } else {
                                    finalPath = modelFile.absolutePath
                                    finalized = true
                                }
                            } else {
                                finalPath = modelFile.absolutePath
                                finalized = true
                            }
                        } else {
                            throw Exception("Copy failed: model file doesn't exist or is too small")
                        }
                    }

                    if (!finalized) {
                        throw Exception("Failed to finalize: unknown error")
                    }

                    android.util.Log.i(TAG, "Model file created successfully: $finalPath")

                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to finalize model file", e)

                    // If finalization failed but temp file is valid, keep it for recovery
                    if (tempFile.exists() && tempFile.length() >= MIN_MODEL_SIZE) {
                        android.util.Log.w(TAG, "Temp file is valid, keeping for recovery")
                    }

                    throw Exception("Failed to finalize model file: ${e.message}")
                }

                // CRITICAL: Save model path to preferences and WAIT for completion
                // This ensures the path is persisted before we mark download as complete
                if (finalPath != null) {
                    val prefs = com.confidant.ai.data.PreferencesManager.getInstance(context)
                    try {
                        prefs.setModelPath(finalPath)
                        android.util.Log.i(TAG, "✓ Model path saved to preferences: $finalPath")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to save model path to preferences", e)
                        // Continue anyway - file is downloaded, path can be recovered
                    }
                }

                // Validate GGUF file format (basic check)
                val finalFile = File(finalPath ?: modelFile.absolutePath)
                val isValidGGUF = validateGGUFFormat(finalFile)
                if (!isValidGGUF) {
                    android.util.Log.w(TAG, "Warning: File may not be valid GGUF format")
                    showErrorNotification("Downloaded file may be corrupted. Try re-downloading.")
                    _isDownloading.value = false
                    _downloadProgress.value = 0f
                    return@withContext Result.failure(Exception("Invalid GGUF format"))
                }
                android.util.Log.i(TAG, "GGUF format validated")

                // Show completion notification
                showCompletionNotification()

                _isDownloading.value = false
                _downloadProgress.value = 1f

                android.util.Log.i(TAG, "=== Model download successful ===")
                android.util.Log.i(TAG, "Path: $finalPath")
                android.util.Log.i(TAG, "Size: ${finalFile.length() / (1024 * 1024)} MB")
                
                return@withContext Result.success(Unit)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Download failed: ${e.message}", e)

                // Check if temp file is valid - if so, try to recover it
                val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")
                if (tempFile.exists() && tempFile.length() >= MIN_MODEL_SIZE) {
                    android.util.Log.w(TAG, "Temp file is valid (${tempFile.length()} bytes), attempting recovery...")
                    try {
                        val modelFile = File(context.filesDir, MODEL_FILENAME)
                        if (modelFile.exists()) modelFile.delete()

                        val recovered = tempFile.renameTo(modelFile)
                        if (recovered && modelFile.exists()) {
                            android.util.Log.i(TAG, "Successfully recovered model from temp file!")

                            // CRITICAL: Save model path and WAIT for completion
                            val prefs = com.confidant.ai.data.PreferencesManager.getInstance(context)
                            try {
                                prefs.setModelPath(modelFile.absolutePath)
                                android.util.Log.i(TAG, "✓ Model path saved to preferences: ${modelFile.absolutePath}")
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "Failed to save model path to preferences", e)
                            }

                            showCompletionNotification()
                            _isDownloading.value = false
                            _downloadProgress.value = 1f

                            return@withContext Result.success(Unit)
                        }
                    } catch (recoveryError: Exception) {
                        android.util.Log.e(TAG, "Recovery failed", recoveryError)
                    }
                }

                // Download failed - user must manually retry
                _isDownloading.value = false
                _downloadProgress.value = 0f
                val errorMsg = "Download failed: ${e.message}. Click to retry."
                android.util.Log.e(TAG, errorMsg, e)
                showErrorNotification(errorMsg)
                Result.failure(e)
            }
        }
    }

    private fun showDownloadNotification(progress: Int, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading AI Model")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Model Download Complete")
            .setContentText("AI model is ready to use!")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Model Download Failed")
            .setContentText(error)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Show notification that model is already present
     */
    fun showModelAlreadyPresentNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("AI Model Ready")
            .setContentText("Model is already downloaded. No download required.")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Validate GGUF file format by checking magic bytes
     * GGUF files start with "GGUF" magic bytes (0x47475546)
     */
    private fun validateGGUFFormat(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                val bytesRead = input.read(magic)
                
                if (bytesRead != 4) {
                    android.util.Log.e(TAG, "Could not read magic bytes")
                    return false
                }
                
                // Check for "GGUF" magic bytes
                val isGGUF = magic[0] == 0x47.toByte() && // 'G'
                             magic[1] == 0x47.toByte() && // 'G'
                             magic[2] == 0x55.toByte() && // 'U'
                             magic[3] == 0x46.toByte()    // 'F'
                
                if (isGGUF) {
                    android.util.Log.d(TAG, "Valid GGUF magic bytes detected")
                } else {
                    android.util.Log.e(TAG, "Invalid magic bytes: ${magic.joinToString { "%02X".format(it) }}")
                }
                
                isGGUF
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error validating GGUF format", e)
            false
        }
    }

    companion object {
        private const val TAG = "ModelDownloadManager"
        const val MODEL_FILENAME = "lfm2.5-1.2b-instruct-q4_k_m.gguf" // Made public for UI access
        private const val MIN_MODEL_SIZE = 600L * 1024 * 1024 // 600MB minimum

        // Official LFM2.5-1.2B-Instruct GGUF from Unsloth (trusted quantization)
        private const val DEFAULT_MODEL_URL = "https://huggingface.co/unsloth/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
        
        // Alternative: Q4_0 for lower memory (slightly faster, slightly lower quality)
        // private const val ALTERNATIVE_MODEL_URL = "https://huggingface.co/unsloth/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_0.gguf"
        
        // Common filename variations users might have
        private val FILENAME_VARIATIONS = listOf(
            "lfm2.5-1.2b-instruct-q4_k_m.gguf",           // Expected (lowercase)
            "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",           // Original HuggingFace name
            "LFM2.5-1.2B-Instruct-Q4_K_M (1).gguf",       // Browser duplicate
            "lfm2.5-1.2b-instruct-q4_k_m (1).gguf",       // Lowercase duplicate
        )

        @Volatile
        private var instance: ModelDownloadManager? = null

        fun getInstance(context: Context): ModelDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: ModelDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
