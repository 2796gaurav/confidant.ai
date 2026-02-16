package com.confidant.ai.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.exp

/**
 * ThermalManager - Monitors and manages device thermal state
 * Uses Android PowerManager APIs for thermal status and headroom
 */
class ThermalManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val listeners = mutableListOf<(ThermalState) -> Unit>()
    
    private val _thermalState = MutableStateFlow(ThermalState.NOMINAL)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()
    
    private val _thermalHeadroom = MutableStateFlow(0.0f)
    val thermalHeadroom: StateFlow<Float> = _thermalHeadroom.asStateFlow()
    
    private val _cpuTemperature = MutableStateFlow(40.0f)
    val cpuTemperature: StateFlow<Float> = _cpuTemperature.asStateFlow()
    
    enum class ThermalState {
        NOMINAL,     // < 50% throttling - Full performance
        LIGHT,       // 50-60% throttling - Slight reduction
        MODERATE,    // 60-70% throttling - Significant reduction
        SEVERE,      // 70-80% throttling - Heavy reduction
        CRITICAL     // 80%+ throttling - Minimal performance
    }
    
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Register thermal status listener (API 29+)
            try {
                powerManager.addThermalStatusListener { status ->
                    val state = mapThermalStatus(status)
                    _thermalState.value = state
                    notifyListeners(state)
                    Log.d(TAG, "Thermal status changed: $state")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register thermal listener", e)
            }
        }
    }
    
    /**
     * Get current thermal status
     */
    fun getThermalStatus(): ThermalState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mapThermalStatus(powerManager.currentThermalStatus)
        } else {
            estimateThermalStatusLegacy()
        }
    }
    
    /**
     * Check if device has thermal headroom for inference
     * RELAXED - Only blocks at extreme temperatures to allow normal operation
     */
    fun canStartInference(): Boolean {
        val state = getThermalStatus()
        
        // Only block at CRITICAL state (device is dangerously hot)
        // MODERATE is fine - modern phones can handle it
        if (state == ThermalState.CRITICAL) {
            Log.w(TAG, "Cannot start inference - thermal state: CRITICAL")
            return false
        }
        
        // REMOVED: Predictive headroom check - too aggressive and blocks normal operation
        // The device can handle inference even when warm
        
        return true
    }
    
    /**
     * Get predicted thermal headroom
     * 0.0 = lots of headroom, 1.0 = SEVERE throttling imminent
     */
    fun getThermalHeadroom(forecastSeconds: Int = 10): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                powerManager.getThermalHeadroom(forecastSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get thermal headroom", e)
                estimateHeadroomFromState()
            }
        } else {
            estimateHeadroomFromState()
        }
    }
    
    /**
     * Get optimal thread count for LLM inference
     * RELAXED - Use 4 threads unless device is truly overheating
     * Research shows 4 threads is optimal for mobile due to memory bandwidth limits
     */
    fun getThermalAwareThreadCount(): Int {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val thermalState = getThermalStatus()
        
        return when {
            // Use 4 threads for all normal states (NOMINAL, MODERATE)
            thermalState == ThermalState.NOMINAL || thermalState == ThermalState.MODERATE -> 4
            thermalState == ThermalState.SEVERE -> 2  // Only reduce at SEVERE
            else -> 1  // CRITICAL only
        }
    }
    
    /**
     * Get optimal batch size based on thermal state
     */
    fun getOptimalBatchSize(): Int {
        return when (getThermalStatus()) {
            ThermalState.NOMINAL -> 128
            ThermalState.LIGHT -> 96
            ThermalState.MODERATE -> 64
            ThermalState.SEVERE -> 32
            ThermalState.CRITICAL -> 16
        }
    }
    
    /**
     * Get optimal context size based on conversation length
     */
    fun getOptimalContextSize(conversationTurns: Int): Int {
        return when {
            conversationTurns < 5 -> 512   // Short conversations
            conversationTurns < 15 -> 1024 // Medium conversations
            else -> 2048                    // Long conversations
        }
    }
    
    /**
     * Update thermal readings (call periodically)
     */
    fun updateReadings() {
        _thermalState.value = getThermalStatus()
        _thermalHeadroom.value = getThermalHeadroom(10)
        _cpuTemperature.value = readCpuTemperature()
    }
    
    private fun mapThermalStatus(status: Int): ThermalState {
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.NOMINAL  // RELAXED: Treat MODERATE as NOMINAL
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.MODERATE   // RELAXED: Treat SEVERE as MODERATE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
            else -> ThermalState.NOMINAL
        }
    }
    
    /**
     * For pre-Android 10 devices - estimate from CPU temperature
     * RELAXED thresholds - modern phones can handle higher temps
     */
    private fun estimateThermalStatusLegacy(): ThermalState {
        val temp = readCpuTemperature()
        return when {
            temp < 70 -> ThermalState.NOMINAL   // RELAXED: Up to 70°C is fine
            temp < 80 -> ThermalState.MODERATE  // RELAXED: 70-80°C is moderate
            temp < 90 -> ThermalState.SEVERE    // RELAXED: 80-90°C is severe
            else -> ThermalState.CRITICAL       // 90°C+ is critical
        }
    }
    
    private fun estimateHeadroomFromState(): Float {
        return when (getThermalStatus()) {
            ThermalState.NOMINAL -> 0.2f
            ThermalState.LIGHT -> 0.4f
            ThermalState.MODERATE -> 0.6f
            ThermalState.SEVERE -> 0.8f
            ThermalState.CRITICAL -> 1.0f
        }
    }
    
    private fun readCpuTemperature(): Float {
        return try {
            // Try multiple thermal zones
            val thermalZones = listOf(
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/class/thermal/thermal_zone2/temp"
            )
            
            for (zone in thermalZones) {
                try {
                    val temp = File(zone).readText().trim().toFloat()
                    // Some devices report in millidegrees, others in degrees
                    return if (temp > 1000) temp / 1000f else temp
                } catch (e: Exception) {
                    continue
                }
            }
            
            45f // Safe default
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read CPU temperature", e)
            45f
        }
    }
    
    fun addListener(listener: (ThermalState) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (ThermalState) -> Unit) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners(state: ThermalState) {
        listeners.forEach { it(state) }
    }
    
    companion object {
        private const val TAG = "ThermalManager"
    }
}

/**
 * Exception thrown when thermal throttling prevents inference
 */
class ThermalThrottlingException(message: String) : Exception(message)