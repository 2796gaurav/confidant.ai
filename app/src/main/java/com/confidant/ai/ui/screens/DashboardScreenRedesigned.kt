package com.confidant.ai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.service.NotificationCaptureService
import com.confidant.ai.system.ServerManager
import com.confidant.ai.thermal.ThermalManager
import com.confidant.ai.ui.components.ConfidantLogo
import com.confidant.ai.ui.theme.*
import com.confidant.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * CONFIDANT AI - REDESIGNED DASHBOARD
 * Complete system control and monitoring
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenRedesigned(
    onNavigateToSettings: () -> Unit,
    onNavigateToThermal: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onNavigateToIntegrations: () -> Unit = {}
) {
    val app = ConfidantApplication.instance
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Collect state
    val thermalState by app.thermalManager.thermalState.collectAsStateWithLifecycle()
    val cpuTemp by app.thermalManager.cpuTemperature.collectAsStateWithLifecycle()
    val telegramRunning by app.telegramBotManager.isRunning.collectAsStateWithLifecycle()
    val serverState by app.serverManager.serverState.collectAsStateWithLifecycle()
    val batteryInfo by app.systemMonitor.batteryInfo.collectAsStateWithLifecycle()
    val ramInfo by app.systemMonitor.ramUsage.collectAsStateWithLifecycle()
    val storageInfo by app.systemMonitor.storageInfo.collectAsStateWithLifecycle()
    val cpuUsage by app.systemMonitor.cpuUsage.collectAsStateWithLifecycle()
    
    // Check model download status when screen opens and server is NOT running
    LaunchedEffect(serverState) {
        if (serverState == ServerManager.ServerState.STOPPED || 
            serverState == ServerManager.ServerState.ERROR) {
            // Check if model is downloaded
            val modelDownloaded = app.modelDownloadManager.isModelDownloaded()
            if (!modelDownloaded) {
                app.serverManager.addLog(
                    "Model not downloaded - user must download before starting server",
                    com.confidant.ai.system.LogLevel.WARN,
                    "📦 Model"
                )
            } else {
                app.serverManager.addLog(
                    "Model verified - ready to start server",
                    com.confidant.ai.system.LogLevel.INFO,
                    "✓ Model"
                )
            }
        }
    }
    
    // Update system metrics periodically
    LaunchedEffect(Unit) {
        while (true) {
            app.systemMonitor.updateMetrics()
            app.thermalManager.updateReadings()
            kotlinx.coroutines.delay(5000)
        }
    }
    
    // Show issue dialog if trying to start with blocking issues
    var showIssueDialog by remember { mutableStateOf(false) }
    var issueDialogMessage by remember { mutableStateOf("") }
    
    if (showIssueDialog) {
        AlertDialog(
            onDismissRequest = { showIssueDialog = false },
            title = { Text("Cannot Start Server") },
            text = { Text(issueDialogMessage) },
            confirmButton = {
                TextButton(onClick = { showIssueDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Confidant Logo
                        ConfidantLogo(
                            size = 32.dp,
                            animate = false
                        )
                        Text(
                            text = "Confidant AI",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DeepIndigoLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = FrostWhiteSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightMain
                )
            )
        },
        containerColor = MidnightMain
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Server Control Section
            item {
                ServerControlCard(
                    serverState = serverState,
                    onStart = {
                        scope.launch {
                            val result = app.serverManager.startServer()
                            if (result.isFailure) {
                                issueDialogMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                                showIssueDialog = true
                            }
                        }
                    },
                    onStop = {
                        scope.launch {
                            app.serverManager.stopServer()
                        }
                    },
                    onRestart = {
                        scope.launch {
                            app.serverManager.restartServer()
                        }
                    },
                    onViewLogs = onNavigateToLogs
                )
            }
            
            // System Health Section
            item {
                Text(
                    text = "SYSTEM HEALTH",
                    style = MaterialTheme.typography.titleSmall,
                    color = DeepIndigoLight,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                SystemHealthCompact(
                    notificationActive = NotificationCaptureService.isEnabled(context),
                    thermalState = thermalState,
                    cpuTemp = cpuTemp,
                    batteryInfo = batteryInfo,
                    cpuUsage = cpuUsage,
                    ramInfo = ramInfo,
                    storageInfo = storageInfo,
                    onThermalClick = onNavigateToThermal
                )
            }
            
            // Telegram Integration
            item {
                Text(
                    text = "TELEGRAM BOT",
                    style = MaterialTheme.typography.titleSmall,
                    color = DeepIndigoLight,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                IntegrationCard(
                    icon = "💬",
                    name = "Telegram Bot",
                    status = if (telegramRunning) "Connected" else "Disconnected",
                    detail = getTelegramDetail(app),
                    isConnected = telegramRunning,
                    onClick = onNavigateToSettings
                )
            }
        }
    }
}

@Composable
private fun getTelegramDetail(app: ConfidantApplication): String {
    val chatId by remember {
        mutableStateOf(
            kotlinx.coroutines.runBlocking {
                app.preferencesManager.getTelegramChatId()
            }
        )
    }
    return chatId?.let { "Chat ID: $it" } ?: "Not configured"
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SERVER CONTROL CARD
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun ServerControlCard(
    serverState: ServerManager.ServerState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onViewLogs: () -> Unit
) {
    val app = com.confidant.ai.ConfidantApplication.instance
    val scope = rememberCoroutineScope()
    val isModelDownloaded = remember { mutableStateOf(app.modelDownloadManager.isModelDownloaded()) }
    val isDownloading by app.modelDownloadManager.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by app.modelDownloadManager.downloadProgress.collectAsStateWithLifecycle()
    
    // Only check model status when download completes
    LaunchedEffect(isDownloading) {
        if (!isDownloading) {
            isModelDownloaded.value = app.modelDownloadManager.isModelDownloaded()
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MidnightLight
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI SERVER",
                        style = MaterialTheme.typography.titleMedium,
                        color = FrostWhitePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = getServerStatusText(serverState),
                        style = MaterialTheme.typography.bodyMedium,
                        color = getServerStatusColor(serverState)
                    )
                }
                
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(getServerStatusColor(serverState).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getServerStatusIcon(serverState),
                        contentDescription = null,
                        tint = getServerStatusColor(serverState),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Divider(color = FrostWhiteTertiary.copy(alpha = 0.2f))
            
            // Model download section - ALWAYS show for manual download option
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDownloading) {
                            "Downloading Model..."
                        } else if (!isModelDownloaded.value) {
                            "Model Required"
                        } else {
                            "Model Management"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDownloading) {
                            SystemInfo
                        } else if (!isModelDownloaded.value) {
                            CrimsonAlertMain
                        } else {
                            FrostWhiteSecondary
                        },
                        fontWeight = FontWeight.Medium
                    )
                    if (isDownloading) {
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = SystemInfo
                        )
                    }
                }
                
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = DeepIndigoMain,
                        trackColor = MidnightDark
                    )
                    Text(
                        text = "Check notification for detailed progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostWhiteTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    // Download button
                    Button(
                        onClick = {
                            android.util.Log.d("Dashboard", "Download button clicked")
                            // Check if model already exists before deleting
                            val modelExists = app.modelDownloadManager.isModelDownloaded()
                            android.util.Log.d("Dashboard", "Model exists before download: $modelExists")
                            
                            // MEMORY LEAK FIX: Use application scope instead of GlobalScope
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // Only delete if we're explicitly re-downloading
                                    if (modelExists) {
                                        android.util.Log.w("Dashboard", "Model exists, user requested re-download")
                                    }
                                    app.modelDownloadManager.deleteModel()
                                    kotlinx.coroutines.delay(500)
                                    val result = app.modelDownloadManager.downloadModel()
                                    if (result.isSuccess) {
                                        android.util.Log.i("Dashboard", "Download completed successfully")
                                    } else {
                                        android.util.Log.e("Dashboard", "Download failed: ${result.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("Dashboard", "Download failed", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isModelDownloaded.value) CrimsonAlertMain else AmberCautionMain,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (!isModelDownloaded.value) "DOWNLOAD MODEL NOW" else "RE-DOWNLOAD MODEL",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Scan button - for when user manually places file in Downloads
                    OutlinedButton(
                        onClick = {
                            android.util.Log.d("Dashboard", "Scan button clicked")
                            // MEMORY LEAK FIX: Use scope instead of GlobalScope
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val foundPath = app.modelDownloadManager.scanForModel()
                                    if (foundPath != null) {
                                        android.util.Log.i("Dashboard", "Model found at: $foundPath")
                                        // Trigger UI refresh
                                        app.serverManager.addLog(
                                            "Model detected in Downloads folder!",
                                            com.confidant.ai.system.LogLevel.INFO,
                                            "✓ Scan"
                                        )
                                    } else {
                                        android.util.Log.w("Dashboard", "Model not found during scan")
                                        app.serverManager.addLog(
                                            "Model not found. Place ${com.confidant.ai.model.ModelDownloadManager.MODEL_FILENAME} in Downloads folder",
                                            com.confidant.ai.system.LogLevel.WARN,
                                            "✗ Scan"
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("Dashboard", "Scan failed", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DeepIndigoMain
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SCAN FOR MODEL IN DOWNLOADS",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Text(
                        text = if (!isModelDownloaded.value) {
                            "⚠️ Model will be saved to Downloads folder (survives uninstall)\n💡 Already downloaded? Click 'Scan' to detect it"
                        } else {
                            "✓ Model found in Downloads folder. Re-download if corrupted."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!isModelDownloaded.value) CrimsonAlertMain else FrostWhiteTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            
            Divider(color = FrostWhiteTertiary.copy(alpha = 0.2f))
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start button
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    enabled = serverState == ServerManager.ServerState.STOPPED || 
                             serverState == ServerManager.ServerState.ERROR,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLimeMain,
                        contentColor = MidnightMain
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("START", fontWeight = FontWeight.Bold)
                }
                
                // Stop button
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    enabled = serverState == ServerManager.ServerState.RUNNING,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CrimsonAlertMain
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP", fontWeight = FontWeight.Bold)
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Restart button
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    enabled = serverState == ServerManager.ServerState.RUNNING,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AmberCautionMain
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESTART", fontWeight = FontWeight.Bold)
                }
                
                // View logs button
                OutlinedButton(
                    onClick = onViewLogs,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DeepIndigoLight
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Article,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LOGS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun getServerStatusText(state: ServerManager.ServerState): String {
    return when (state) {
        ServerManager.ServerState.STOPPED -> "Stopped"
        ServerManager.ServerState.STARTING -> "Starting..."
        ServerManager.ServerState.RUNNING -> "Running"
        ServerManager.ServerState.STOPPING -> "Stopping..."
        ServerManager.ServerState.ERROR -> "Error"
    }
}

private fun getServerStatusColor(state: ServerManager.ServerState): Color {
    return when (state) {
        ServerManager.ServerState.STOPPED -> FrostWhiteTertiary
        ServerManager.ServerState.STARTING -> AmberCautionMain
        ServerManager.ServerState.RUNNING -> ElectricLimeMain
        ServerManager.ServerState.STOPPING -> AmberCautionMain
        ServerManager.ServerState.ERROR -> CrimsonAlertMain
    }
}

private fun getServerStatusIcon(state: ServerManager.ServerState): ImageVector {
    return when (state) {
        ServerManager.ServerState.STOPPED -> Icons.Filled.PowerSettingsNew
        ServerManager.ServerState.STARTING -> Icons.Filled.HourglassEmpty
        ServerManager.ServerState.RUNNING -> Icons.Filled.CheckCircle
        ServerManager.ServerState.STOPPING -> Icons.Filled.HourglassEmpty
        ServerManager.ServerState.ERROR -> Icons.Filled.Error
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SYSTEM HEALTH COMPACT (Side by Side)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun SystemHealthCompact(
    notificationActive: Boolean,
    thermalState: ThermalManager.ThermalState,
    cpuTemp: Float,
    batteryInfo: com.confidant.ai.system.SystemMonitor.BatteryInfo,
    cpuUsage: Float,
    ramInfo: com.confidant.ai.system.SystemMonitor.RamInfo,
    storageInfo: com.confidant.ai.system.SystemMonitor.StorageInfo,
    onThermalClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MidnightLight
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Thermal, Notifications, Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthMetricCompact(
                    icon = Icons.Filled.Thermostat,
                    label = "Thermal",
                    value = "${cpuTemp.toInt()}°C",
                    color = getThermalColor(thermalState),
                    modifier = Modifier.weight(1f),
                    onClick = onThermalClick
                )
                
                HealthMetricCompact(
                    icon = Icons.Filled.Notifications,
                    label = "Notifs",
                    value = if (notificationActive) "OK" else "OFF",
                    color = if (notificationActive) ElectricLimeMain else CrimsonAlertMain,
                    modifier = Modifier.weight(1f)
                )
                
                HealthMetricCompact(
                    icon = if (batteryInfo.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                    label = "Battery",
                    value = "${batteryInfo.percent}%",
                    color = getBatteryColor(batteryInfo.percent, batteryInfo.isCharging),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Row 2: CPU, RAM, Storage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthMetricCompact(
                    icon = Icons.Filled.Memory,
                    label = "CPU",
                    value = "${cpuUsage.toInt()}%",
                    color = getUsageColor(cpuUsage),
                    modifier = Modifier.weight(1f)
                )
                
                HealthMetricCompact(
                    icon = Icons.Filled.Storage,
                    label = "RAM",
                    value = "${ramInfo.usedMB / 1024}GB",
                    color = getUsageColor(ramInfo.usagePercent),
                    modifier = Modifier.weight(1f)
                )
                
                HealthMetricCompact(
                    icon = Icons.Filled.SdCard,
                    label = "Storage",
                    value = "${storageInfo.usagePercent.toInt()}%",
                    color = getUsageColor(storageInfo.usagePercent),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HealthMetricCompact(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MidnightMain
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = FrostWhiteTertiary
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun getThermalColor(state: ThermalManager.ThermalState): Color {
    return when (state) {
        ThermalManager.ThermalState.NOMINAL -> ElectricLimeMain
        ThermalManager.ThermalState.LIGHT -> SystemInfo
        ThermalManager.ThermalState.MODERATE -> AmberCautionMain
        ThermalManager.ThermalState.SEVERE -> ThermalSevere
        ThermalManager.ThermalState.CRITICAL -> CrimsonAlertMain
    }
}

private fun getBatteryColor(percent: Int, isCharging: Boolean): Color {
    return when {
        isCharging -> ElectricLimeMain
        percent < 15 -> CrimsonAlertMain
        percent < 30 -> AmberCautionMain
        else -> ElectricLimeMain
    }
}

private fun getUsageColor(percent: Float): Color {
    return when {
        percent < 60 -> ElectricLimeMain
        percent < 80 -> AmberCautionMain
        else -> CrimsonAlertMain
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// INTEGRATION CARD
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun IntegrationCard(
    icon: String,
    name: String,
    status: String,
    detail: String,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MidnightLight
        ),
        border = if (isConnected) {
            androidx.compose.foundation.BorderStroke(1.dp, ElectricLimeDark)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp)
                )
                
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = FrostWhitePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostWhiteTertiary
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) ElectricLimeMain else FrostWhiteTertiary)
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) ElectricLimeMain else FrostWhiteTertiary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Data classes
data class SystemStatus(
    val isOperational: Boolean = false,
    val llmInitialized: Boolean = false,
    val notificationListenerActive: Boolean = false,
    val telegramConnected: Boolean = false
)

data class ActivityItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val timestamp: java.time.LocalDateTime,
    val color: Color
)


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LOGS DIALOG
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun LogsDialog(
    logs: List<com.confidant.ai.system.LogEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MidnightMain
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Server Logs",
                        style = MaterialTheme.typography.titleLarge,
                        color = FrostWhitePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Clear logs",
                                tint = CrimsonAlertMain
                            )
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = FrostWhiteSecondary
                            )
                        }
                    }
                }
                
                Divider(color = FrostWhiteTertiary.copy(alpha = 0.1f))
                
                // Logs list
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Article,
                                contentDescription = null,
                                tint = FrostWhiteTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No logs yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = FrostWhiteTertiary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { log ->
                            LogEntryItem(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(log: com.confidant.ai.system.LogEntry) {
    val (icon, color) = when (log.level) {
        com.confidant.ai.system.LogLevel.SUCCESS -> Icons.Filled.CheckCircle to ElectricLimeMain
        com.confidant.ai.system.LogLevel.INFO -> Icons.Filled.Info to SystemInfo
        com.confidant.ai.system.LogLevel.WARN -> Icons.Filled.Warning to AmberCautionMain
        com.confidant.ai.system.LogLevel.ERROR -> Icons.Filled.Error to CrimsonAlertMain
        com.confidant.ai.system.LogLevel.DEBUG -> Icons.Filled.Info to FrostWhiteSecondary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = log.getFormattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = FrostWhiteTertiary
                )
                
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FrostWhitePrimary
                )
            }
        }
    }
}
