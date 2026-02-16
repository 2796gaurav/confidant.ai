package com.confidant.ai.engine

import android.content.Context
import android.util.Log
import com.confidant.ai.thermal.ThermalManager
import com.confidant.ai.thermal.ThermalThrottlingException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * LLMEngine - Native LLM inference via JNI
 * Wraps llama.cpp for on-device inference with LFM2.5-1.2B-Instruct
 * 
 * 2026 OPTIMIZATIONS:
 * - Dynamic temperature based on query type (prevents hallucinations)
 * - Improved prompt caching
 * - Thermal-aware thread management
 */
class LLMEngine(
    private val context: Context,
    private val thermalManager: ThermalManager
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    
    private val _generationProgress = MutableStateFlow(0f)
    val generationProgress: StateFlow<Float> = _generationProgress.asStateFlow()
    
    private var modelPath: String? = null
    private var currentThreads = 4
    
    /**
     * Query types for dynamic temperature selection
     */
    enum class QueryType {
        FACTUAL,        // Facts, data, specific information (temp=0.3)
        CONVERSATIONAL, // Normal chat, casual responses (temp=0.5)
        CREATIVE,       // Stories, ideas, brainstorming (temp=0.7)
        TOOL_CALLING    // Function/tool detection (temp=0.2)
    }
    
    /**
     * Get optimal temperature for query type
     * CRITICAL FIX: Prevents hallucinations in factual queries
     */
    fun getOptimalTemperature(queryType: QueryType): Float {
        return when (queryType) {
            QueryType.FACTUAL -> 0.3f        // Low temp = more deterministic, less hallucination
            QueryType.CONVERSATIONAL -> 0.5f // Medium temp = natural but controlled
            QueryType.CREATIVE -> 0.7f       // High temp = more creative/varied
            QueryType.TOOL_CALLING -> 0.2f   // Very low temp = precise format matching
        }
    }
    
    /**
     * Detect query type from prompt content
     */
    fun detectQueryType(prompt: String): QueryType {
        val lower = prompt.lowercase()
        
        return when {
            // Tool calling patterns
            lower.contains("<|tool_call") || lower.contains("function_call") || 
            lower.contains("available tools") -> QueryType.TOOL_CALLING
            
            // Factual query patterns
            lower.contains("what is") || lower.contains("who is") || 
            lower.contains("when did") || lower.contains("how many") ||
            lower.contains("define") || lower.contains("explain") -> QueryType.FACTUAL
            
            // Creative patterns
            lower.contains("imagine") || lower.contains("create") ||
            lower.contains("write a story") || lower.contains("brainstorm") -> QueryType.CREATIVE
            
            // Default to conversational
            else -> QueryType.CONVERSATIONAL
        }
    }
    
    // Native methods
    external fun nativeLoadModel(
        path: String,
        nThreads: Int,
        ctxSize: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float
    ): Boolean
    
    external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    
    external fun nativeGenerateWithCache(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        temperature: Float
    ): String
    
    external fun nativeFreeModel()
    
    external fun nativeGetTokenCount(text: String): Int
    
    // NEW: Real streaming generation
    external fun nativeGenerateStreaming(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: StreamingCallback
    )
    
    /**
     * Initialize the LLM engine with a model
     */
    suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== LLM Initialization Started ===")
            Log.d(TAG, "Model path: $modelPath")
            Log.d(TAG, "Model: LFM2.5-1.2B-Instruct Q4_K_M, optimized for mobile edge inference")
            
            // Check 1: Native library availability
            if (!isNativeLibraryAvailable()) {
                val error = "Native library (libllama-jni.so) not loaded. Check ABI compatibility."
                Log.e(TAG, error)
                return@withContext Result.failure(
                    UnsatisfiedLinkError(error)
                )
            }
            Log.d(TAG, "✓ Native library loaded")
            
            // Check 2: File existence
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                val error = "Model file not found: $modelPath"
                Log.e(TAG, error)
                
                // USER NOTIFICATION: Inform user that model is missing
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "AI Model missing! Please download LFM2.5 model.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                
                return@withContext Result.failure(IllegalArgumentException(error))
            }
            Log.d(TAG, "✓ Model file exists")
            
            // Check 3: File size
            val fileSize = modelFile.length()
            Log.d(TAG, "Model file size: ${fileSize / (1024 * 1024)} MB")
            if (fileSize < 50 * 1024 * 1024) { // Less than 50MB is suspicious
                val error = "Model file too small ($fileSize bytes). File may be corrupted."
                Log.e(TAG, error)
                return@withContext Result.failure(IllegalArgumentException(error))
            }
            Log.d(TAG, "✓ File size valid")
            
            // Check 4: File readability
            if (!modelFile.canRead()) {
                val error = "Model file exists but cannot be read. Check permissions."
                Log.e(TAG, error)
                return@withContext Result.failure(IllegalArgumentException(error))
            }
            Log.d(TAG, "✓ File readable")
            
            this@LLMEngine.modelPath = modelPath
            
            // Get thermal-aware thread count
            currentThreads = thermalManager.getThermalAwareThreadCount()
            Log.d(TAG, "Thread count: $currentThreads (thermal-aware)")
            
            // Attempt native model loading
            Log.d(TAG, "Calling nativeLoadModel()...")
            val startTime = System.currentTimeMillis()
            
            // FIXED: Use consistent 2048 context size (optimal for LFM2.5-1.2B on mobile)
            // LFM2.5-1.2B works best with 2048 context for memory/performance balance
            val ctxSize = 2048
            
            val success = try {
                nativeLoadModel(
                    path = modelPath,
                    nThreads = currentThreads,
                    ctxSize = ctxSize,
                    temperature = 0.7f,  // Optimal for LFM2.5 (0.7 recommended)
                    topK = 50,           // Optimal for LFM2.5 (50 recommended)
                    topP = 0.8f,         // Optimal for LFM2.5 (0.8 recommended)
                    minP = 0.0f
                )
            } catch (e: Exception) {
                Log.e(TAG, "Native call threw exception", e)
                throw e
            }
            
            val loadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "nativeLoadModel() returned: $success (took ${loadTime}ms)")
            
            if (success) {
                _isInitialized.value = true
                Log.i(TAG, "=== LLM Engine initialized successfully ===")
                Log.i(TAG, "Model: LFM2.5-1.2B-Instruct Q4_K_M")
                Log.i(TAG, "Config: threads=$currentThreads, ctx=$ctxSize, temp=0.7, topK=50, topP=0.8")
                Log.i(TAG, "Optimizations: KV-Q8, flash_attn, hybrid architecture, cache enabled")
                Log.i(TAG, "Load time: ${loadTime}ms")
                Result.success(Unit)
            } else {
                val error = """
                    Failed to load model via native call. Possible causes:
                    1. Corrupted GGUF file (try re-downloading)
                    2. Incompatible model format (expected GGUF)
                    3. Insufficient memory (need ~800MB free RAM)
                    4. Wrong model architecture (expected LFM/Llama-compatible)
                    
                    File: $modelPath
                    Size: ${fileSize / (1024 * 1024)} MB
                    Threads: $currentThreads
                """.trimIndent()
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== LLM Initialization Failed ===", e)
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Generate text with prompt caching (MUCH faster for repeated system prompts)
     * OPTIMIZED for 2026: Ultra-fast mobile inference
     */
    suspend fun generateWithCache(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 128,        // INCREASED from 48 for better responses (2026: balanced speed/quality)
        temperature: Float = 0.7f,   // Optimal for LFM2.5 (0.7 recommended)
        timeoutSeconds: Int = 20     // INCREASED from 12s for longer responses
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isNativeLibraryAvailable()) {
                return@withContext Result.failure(
                    UnsatisfiedLinkError("Native library not available - LLM features disabled")
                )
            }
            
            // SAFETY CHECK: Empty messages cause native tokenization failures
            if (userMessage.isBlank() && systemPrompt.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot generate with empty prompts")
                )
            }
            
            // Pre-inference thermal check
            if (!thermalManager.canStartInference()) {
                return@withContext Result.failure(
                    ThermalThrottlingException("Device too hot to start inference")
                )
            }
            
            _isGenerating.value = true
            _generationProgress.value = 0f
            
            // Update thread count based on current thermal state
            updateThreadCount()
            
            // DIAGNOSTIC: Log thermal and configuration state
            Log.d(TAG, "=== GENERATION START DIAGNOSTICS ===")
            Log.d(TAG, "Thermal state: ${thermalManager.getThermalStatus()}")
            Log.d(TAG, "Thread count: $currentThreads (thermal-aware)")
            Log.d(TAG, "Can start inference: ${thermalManager.canStartInference()}")
            Log.d(TAG, "Timeout: ${timeoutSeconds}s")
            Log.d(TAG, "Max tokens: $maxTokens")
            Log.d(TAG, "Temperature: $temperature")
            Log.d(TAG, "System prompt: ${systemPrompt.length} chars (~${estimateTokens(systemPrompt)} tokens)")
            Log.d(TAG, "User message: ${userMessage.length} chars (~${estimateTokens(userMessage)} tokens)")
            Log.d(TAG, "=== END DIAGNOSTICS ===")
            
            Log.d(TAG, "Starting cached generation with ${timeoutSeconds}s timeout...")
            
            // Run with timeout
            val result = withTimeout(timeoutSeconds * 1000L) {
                nativeGenerateWithCache(systemPrompt, userMessage, maxTokens, temperature)
            }
            
            _generationProgress.value = 1f
            Log.d(TAG, "Cached generation completed successfully")
            Result.success(result)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Generation timed out after ${timeoutSeconds}s")
            // PRODUCTION FIX: Provide helpful error message
            Result.failure(Exception("Response generation timed out. This may happen if:\n1. Device is under heavy load\n2. Model is processing a complex query\n3. Thermal throttling is active\n\nPlease try again in a moment."))
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library error", e)
            Result.failure(Exception("LLM engine not available. Please restart the app."))
        } catch (e: ThermalThrottlingException) {
            Log.e(TAG, "Thermal throttling", e)
            Result.failure(e) // Already has helpful message
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            // PRODUCTION FIX: Generic error with recovery suggestion
            Result.failure(Exception("Generation failed: ${e.message}. Please try again or restart the app if the issue persists."))
        } finally {
            _isGenerating.value = false
        }
    }
    
    /**
     * Generate text from prompt (blocking) - legacy method
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        timeoutSeconds: Int = 30
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isNativeLibraryAvailable()) {
                return@withContext Result.failure(
                    UnsatisfiedLinkError("Native library not available - LLM features disabled")
                )
            }
            
            // Pre-inference thermal check
            if (!thermalManager.canStartInference()) {
                return@withContext Result.failure(
                    ThermalThrottlingException("Device too hot to start inference")
                )
            }
            
            _isGenerating.value = true
            _generationProgress.value = 0f
            
            // Update thread count based on current thermal state
            updateThreadCount()
            
            Log.d(TAG, "Starting generation with ${timeoutSeconds}s timeout...")
            
            // Run with timeout
            val result = withTimeout(timeoutSeconds * 1000L) {
                nativeGenerate(prompt, maxTokens, temperature)
            }
            
            _generationProgress.value = 1f
            Log.d(TAG, "Generation completed successfully")
            Result.success(result)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Generation timed out after ${timeoutSeconds}s")
            Result.failure(Exception("Generation timed out - model may be too slow or stuck"))
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            Result.failure(e)
        } finally {
            _isGenerating.value = false
        }
    }
    
    /**
     * Estimate token count for text
     */
    fun estimateTokens(text: String): Int {
        return if (isNativeLibraryAvailable() && _isInitialized.value) {
            try {
                nativeGetTokenCount(text)
            } catch (e: Exception) {
                // Fallback to simple estimation
                (text.length / 4.0).toInt()
            }
        } else {
            // Simple estimation: ~4 chars per token
            (text.length / 4.0).toInt()
        }
    }
    
    /**
     * Generate text with REAL token-by-token streaming
     * Returns a Flow that emits each token as it's generated
     * 
     * This is TRUE streaming - tokens are emitted as llama.cpp generates them,
     * not chunked from a complete response.
     * 
     * @param prompt The input prompt
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature (0.0-1.0)
     * @return Flow of tokens as they're generated
     */
    fun generateStreaming(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.callbackFlow {
        if (!isNativeLibraryAvailable()) {
            close(UnsatisfiedLinkError("Native library not available"))
            return@callbackFlow
        }
        
        if (!_isInitialized.value) {
            close(IllegalStateException("Model not initialized"))
            return@callbackFlow
        }
        
        // Pre-inference thermal check
        if (!thermalManager.canStartInference()) {
            close(ThermalThrottlingException("Device too hot to start inference"))
            return@callbackFlow
        }
        
        _isGenerating.value = true
        _generationProgress.value = 0f
        
        // Update thread count based on current thermal state
        updateThreadCount()
        
        // LOG GENERATION PARAMETERS FOR DEBUGGING
        Log.d(TAG, "=== STREAMING GENERATION START ===")
        Log.d(TAG, "Prompt length: ${prompt.length} chars")
        Log.d(TAG, "Estimated tokens: ${estimateTokens(prompt)}")
        Log.d(TAG, "Max tokens: $maxTokens")
        Log.d(TAG, "Temperature: $temperature")
        Log.d(TAG, "=== PROMPT PREVIEW (first 500 chars) ===")
        Log.d(TAG, prompt.take(500))
        if (prompt.length > 500) {
            Log.d(TAG, "... (${prompt.length - 500} more chars)")
        }
        Log.d(TAG, "=== END PREVIEW ===")
        
        Log.d(TAG, "Starting REAL streaming generation...")
        
        val callback = object : StreamingCallback {
            override fun onToken(token: String) {
                // Emit token immediately as it's generated
                trySend(token).isSuccess
            }
            
            override fun onComplete() {
                _isGenerating.value = false
                _generationProgress.value = 1f
                Log.d(TAG, "Streaming generation completed")
                close()
            }
            
            override fun onError(error: String) {
                _isGenerating.value = false
                Log.e(TAG, "Streaming generation error: $error")
                close(Exception(error))
            }
        }
        
        // Call native streaming method
        withContext(Dispatchers.IO) {
            try {
                nativeGenerateStreaming(prompt, maxTokens, temperature, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Streaming generation failed", e)
                close(e)
            }
        }
        
        awaitClose {
            _isGenerating.value = false
        }
    }
    
    /**
     * Release model resources
     */
    fun release() {
        nativeFreeModel()
        _isInitialized.value = false
        Log.i(TAG, "LLM Engine released")
    }
    
    private fun updateThreadCount() {
        val newThreadCount = thermalManager.getThermalAwareThreadCount()
        if (newThreadCount != currentThreads) {
            currentThreads = newThreadCount
            Log.i(TAG, "Adjusted thread count to $currentThreads due to thermal state")
            
            // Re-initialize with new thread count (maintain consistent context size)
            modelPath?.let { path ->
                nativeLoadModel(
                    path = path,
                    nThreads = currentThreads,
                    ctxSize = 2048,      // Consistent 2048 context size for LFM2.5
                    temperature = 0.7f,  // Optimal for LFM2.5
                    topK = 50,
                    topP = 0.8f,
                    minP = 0.0f
                )
            }
        }
    }
    
    companion object {
        private const val TAG = "LLMEngine"
        
        private var nativeLibraryLoaded = false
        
        init {
            try {
                System.loadLibrary("llama-jni")
                nativeLibraryLoaded = true
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available - LLM features will be disabled", e)
                nativeLibraryLoaded = false
            }
        }
        
        fun isNativeLibraryAvailable(): Boolean = nativeLibraryLoaded
        
        // Default model configuration - LFM2.5-1.2B-Instruct optimized for mobile
        const val DEFAULT_MODEL_URL = "https://huggingface.co/unsloth/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
        const val DEFAULT_MODEL_FILENAME = "lfm2.5-1.2b-instruct-q4_k_m.gguf"
        const val DEFAULT_MODEL_SIZE = 750L * 1024 * 1024 // 750MB
        
        // Alternative: Q4_0 for lower memory (slightly faster, slightly lower quality)
        const val ALTERNATIVE_MODEL_URL = "https://huggingface.co/unsloth/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_0.gguf"
        const val ALTERNATIVE_MODEL_FILENAME = "lfm2.5-1.2b-instruct-q4_0.gguf"
    }
}