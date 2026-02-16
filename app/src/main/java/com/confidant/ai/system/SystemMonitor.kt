package com.confidant.ai.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

/**
 * SystemMonitor - Monitors system resources (CPU, RAM, Storage, Battery)
 */
class SystemMonitor(private val context: Context) {
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    private val _cpuUsage = MutableStateFlow(0f)
    val cpuUsage: StateFlow<Float> = _cpuUsage.asStateFlow()
    
    private val _ramUsage = MutableStateFlow(RamInfo(0L, 0L, 0f))
    val ramUsage: StateFlow<RamInfo> = _ramUsage.asStateFlow()
    
    private val _storageInfo = MutableStateFlow(StorageInfo(0L, 0L, 0f))
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()
    
    private val _batteryInfo = MutableStateFlow(BatteryInfo(0, false, 0f))
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()
    
    private var lastCpuStats: CpuStats? = null
    
    /**
     * Update all system metrics
     */
    fun updateMetrics() {
        updateCpuUsage()
        updateRamUsage()
        updateStorageInfo()
        updateBatteryInfo()
    }
    
    /**
     * Get CPU usage percentage
     */
    private fun updateCpuUsage() {
        try {
            val currentStats = readCpuStats()
            
            if (lastCpuStats != null) {
                val totalDelta = currentStats.total - lastCpuStats!!.total
                val idleDelta = currentStats.idle - lastCpuStats!!.idle
                
                if (totalDelta > 0) {
                    val usage = ((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f
                    _cpuUsage.value = usage.coerceIn(0f, 100f)
                }
            }
            
            lastCpuStats = currentStats
        } catch (e: Exception) {
            _cpuUsage.value = 0f
        }
    }
    
    /**
     * Get RAM usage
     */
    private fun updateRamUsage() {
        try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            val totalRam = memInfo.totalMem
            val availableRam = memInfo.availMem
            val usedRam = totalRam - availableRam
            val usagePercent = (usedRam.toFloat() / totalRam.toFloat()) * 100f
            
            _ramUsage.value = RamInfo(
                usedMB = usedRam / (1024 * 1024),
                totalMB = totalRam / (1024 * 1024),
                usagePercent = usagePercent
            )
        } catch (e: Exception) {
            _ramUsage.value = RamInfo(0L, 0L, 0f)
        }
    }
    
    /**
     * Get storage info
     */
    private fun updateStorageInfo() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val usedBytes = totalBytes - availableBytes
            val usagePercent = (usedBytes.toFloat() / totalBytes.toFloat()) * 100f
            
            _storageInfo.value = StorageInfo(
                usedGB = usedBytes / (1024 * 1024 * 1024),
                totalGB = totalBytes / (1024 * 1024 * 1024),
                usagePercent = usagePercent
            )
        } catch (e: Exception) {
            _storageInfo.value = StorageInfo(0L, 0L, 0f)
        }
    }
    
    /**
     * Get battery info
     */
    private fun updateBatteryInfo() {
        try {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            
            val batteryPercent = if (level >= 0 && scale > 0) {
                (level.toFloat() / scale.toFloat() * 100f).toInt()
            } else {
                0
            }
            
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL
            
            val tempCelsius = temperature / 10f
            
            _batteryInfo.value = BatteryInfo(
                percent = batteryPercent,
                isCharging = isCharging,
                temperature = tempCelsius
            )
        } catch (e: Exception) {
            _batteryInfo.value = BatteryInfo(0, false, 0f)
        }
    }
    
    /**
     * Read CPU stats from /proc/stat
     */
    private fun readCpuStats(): CpuStats {
        try {
            RandomAccessFile("/proc/stat", "r").use { reader ->
                val line = reader.readLine()
                val tokens = line.split("\\s+".toRegex())
                
                // cpu  user nice system idle iowait irq softirq
                val user = tokens[1].toLong()
                val nice = tokens[2].toLong()
                val system = tokens[3].toLong()
                val idle = tokens[4].toLong()
                val iowait = tokens[5].toLong()
                val irq = tokens[6].toLong()
                val softirq = tokens[7].toLong()
                
                val total = user + nice + system + idle + iowait + irq + softirq
                
                return CpuStats(total, idle)
            }
        } catch (e: Exception) {
            return CpuStats(0, 0)
        }
    }
    
    data class RamInfo(
        val usedMB: Long,
        val totalMB: Long,
        val usagePercent: Float
    )
    
    data class StorageInfo(
        val usedGB: Long,
        val totalGB: Long,
        val usagePercent: Float
    )
    
    data class BatteryInfo(
        val percent: Int,
        val isCharging: Boolean,
        val temperature: Float
    )
    
    private data class CpuStats(
        val total: Long,
        val idle: Long
    )
}
