package com.confidant.ai.oem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * OEMOptimizationHelper - Detects device manufacturer and provides OEM-specific guidance
 * Helps users whitelist app for 24/7 background operation
 */
class OEMOptimizationHelper(private val context: Context) {
    
    data class OEMInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: Int,
        val isAggressive: Boolean,
        val requiredSteps: List<String>
    )
    
    /**
     * Detect device OEM and return configuration info
     */
    fun detectOEM(): OEMInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL
        val androidVersion = Build.VERSION.SDK_INT
        
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                OEMInfo(
                    manufacturer = "Xiaomi (MIUI)",
                    model = model,
                    androidVersion = androidVersion,
                    isAggressive = true,
                    requiredSteps = listOf(
                        "1. Enable Autostart: Security > Permissions > Autostart > Enable",
                        "2. Disable Battery Saver: Settings > Apps > Confidant AI > Battery Saver > No Restrictions",
                        "3. Lock App in Recents: Open recents, drag app down to lock",
                        "4. Disable MIUI Optimizations: Developer Options > MIUI Optimizations > Off",
                        "5. Power Management: Security > Battery > Power > No restrictions"
                    )
                )
            }
            manufacturer.contains("samsung") -> {
                OEMInfo(
                    manufacturer = "Samsung (One UI)",
                    model = model,
                    androidVersion = androidVersion,
                    isAggressive = true,
                    requiredSteps = listOf(
                        "1. Disable Battery Optimization: Settings > Apps > Confidant AI > Battery > Unrestricted",
                        "2. Disable Adaptive Battery: Settings > Battery > More > Adaptive Battery > Off",
                        "3. Remove from Sleeping Apps: Settings > Battery > Background usage limits > Never sleeping apps > Add Confidant AI",
                        "4. Disable Put App to Sleep: Settings > Apps > Confidant AI > Battery > Put app to sleep > Off"
                    )
                )
            }
            manufacturer.contains("oneplus") -> {
                OEMInfo(
                    manufacturer = "OnePlus (OxygenOS)",
                    model = model,
                    androidVersion = androidVersion,
                    isAggressive = false,
                    requiredSteps = listOf(
                        "1. Disable Battery Optimization: Settings > Apps > Confidant AI > Battery > Don't optimize",
                        "2. Advanced Optimization: Settings > Battery > Advanced Optimization > Off"
                    )
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                OEMInfo(
                    manufacturer = "Oppo/Realme (ColorOS)",
                    model = model,
                    androidVersion = androidVersion,
                    isAggressive = true,
                    requiredSteps = listOf(
                        "1. Disable Battery Optimization: Settings > Apps > Confidant AI > Battery > Don't optimize",
                        "2. Enable Autostart: Settings > Apps > Confidant AI > Autostart > Enable",
                        "3. Lock App in Recents: Open recents, lock app"
                    )
                )
            }
            manufacturer.contains("vivo") -> {
                OEMInfo(
                    manufacturer = "Vivo (Funtouch OS)",
                    model = model,
                    androidVersion = androidVersion,
                    isAggressive = true,
                    requiredSteps = listOf(
                        "1. Disable Battery Optimization: Settings > Apps > Confidant AI > Battery > High background power consumption > Allow",
                        "2. Enable Autostart: Settings > Apps > Confidant AI > Autostart > Enable"
                    )
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                OEMInfo(
                    manufacturer = "Huawei/Honor (EMUI)",
                    model = model,
                    androidVersion = androidVersion,
                    isAggressive = true,
                    requiredSteps = listOf(
                        "1. Disable Battery Optimization: Settings > Apps > Confidant AI > Battery > Don't optimize",
                        "2. Enable Autostart: Settings > Apps > Confidant AI > Autostart > Enable",
                        "3. Lock App: Phone Manager > App Launch > Confidant AI > Manage manually"
                    )
                )
            }
            else -> {
                OEMInfo(
                    manufacturer = "Stock Android",
                    model = model,
                    androidVersion = androidVersion,
                    isAggressive = false,
                    requiredSteps = listOf(
                        "1. Disable Battery Optimization: Settings > Apps > Confidant AI > Battery > Unrestricted"
                    )
                )
            }
        }
    }
    
    /**
     * Check if battery optimization is disabled for this app
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Not applicable on older Android versions
        }
    }
    
    /**
     * Open battery optimization settings for this app
     */
    fun openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings", e)
            // Fallback to general battery settings
            try {
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open battery settings", e2)
            }
        }
    }
    
    /**
     * Get user-friendly message about OEM optimization
     */
    fun getOptimizationMessage(): String {
        val oemInfo = detectOEM()
        
        return buildString {
            appendLine("Device: ${oemInfo.manufacturer} ${oemInfo.model}")
            appendLine()
            if (oemInfo.isAggressive) {
                appendLine("⚠️ Your device has aggressive battery optimization.")
                appendLine("Follow these steps to ensure 24/7 operation:")
            } else {
                appendLine("✅ Your device has standard battery optimization.")
                appendLine("Follow this step:")
            }
            appendLine()
            oemInfo.requiredSteps.forEach { step ->
                appendLine(step)
            }
        }
    }
    
    /**
     * Check if autostart permission is granted (Xiaomi/Oppo/Vivo)
     * Note: This is difficult to detect programmatically, so we return false to show instructions
     */
    fun hasAutostartPermission(): Boolean {
        // Cannot reliably detect autostart permission programmatically
        // Return false to always show the instruction to users
        return false
    }
    
    /**
     * Check if MIUI battery saver is enabled
     */
    fun isMIUIBatterySaverEnabled(): Boolean {
        // Cannot reliably detect MIUI-specific settings programmatically
        return false
    }
    
    /**
     * Check if Samsung sleeping apps feature is enabled
     */
    fun isSamsungSleepingAppsEnabled(): Boolean {
        // Cannot reliably detect Samsung-specific settings programmatically
        return false
    }
    
    /**
     * Check if Samsung auto-optimization is enabled
     */
    fun isSamsungAutoOptimizeEnabled(): Boolean {
        // Cannot reliably detect Samsung-specific settings programmatically
        return false
    }
    
    /**
     * Open OEM-specific settings based on manufacturer
     */
    fun openOEMSpecificSettings() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                openXiaomiAutostartSettings()
            }
            manufacturer.contains("samsung") -> {
                openSamsungSleepingAppsSettings()
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                openHuaweiProtectedAppsSettings()
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                openOppoStartupManager()
            }
            manufacturer.contains("vivo") -> {
                openVivoBackgroundSettings()
            }
            else -> {
                // Fallback to battery optimization settings
                openBatteryOptimizationSettings()
            }
        }
    }
    
    /**
     * Open Xiaomi autostart settings
     */
    private fun openXiaomiAutostartSettings() {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Xiaomi autostart settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Xiaomi autostart settings", e)
            openBatteryOptimizationSettings()
        }
    }
    
    /**
     * Open Samsung sleeping apps settings
     */
    private fun openSamsungSleepingAppsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Samsung app settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Samsung settings", e)
            openBatteryOptimizationSettings()
        }
    }
    
    /**
     * Open Huawei protected apps settings
     */
    private fun openHuaweiProtectedAppsSettings() {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Huawei protected apps settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Huawei settings", e)
            openBatteryOptimizationSettings()
        }
    }
    
    /**
     * Open Oppo startup manager
     */
    private fun openOppoStartupManager() {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Oppo startup manager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Oppo settings", e)
            openBatteryOptimizationSettings()
        }
    }
    
    /**
     * Open Vivo background settings
     */
    private fun openVivoBackgroundSettings() {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Vivo background settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Vivo settings", e)
            openBatteryOptimizationSettings()
        }
    }
    
    /**
     * Get detailed optimization status
     */
    fun getOptimizationStatus(): OptimizationStatus {
        val oemInfo = detectOEM()
        val batteryOptDisabled = isBatteryOptimizationDisabled()
        
        return OptimizationStatus(
            oemInfo = oemInfo,
            isBatteryOptimizationDisabled = batteryOptDisabled,
            hasAutostartPermission = hasAutostartPermission(),
            overallStatus = if (batteryOptDisabled) {
                if (oemInfo.isAggressive) OptimizationLevel.PARTIAL else OptimizationLevel.GOOD
            } else {
                OptimizationLevel.POOR
            }
        )
    }
    
    data class OptimizationStatus(
        val oemInfo: OEMInfo,
        val isBatteryOptimizationDisabled: Boolean,
        val hasAutostartPermission: Boolean,
        val overallStatus: OptimizationLevel
    )
    
    enum class OptimizationLevel {
        GOOD,      // All optimizations disabled
        PARTIAL,   // Battery optimization disabled, but OEM restrictions may apply
        POOR       // Battery optimization enabled
    }
    
    companion object {
        private const val TAG = "OEMOptimizationHelper"
    }
}
