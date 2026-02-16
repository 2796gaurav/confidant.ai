package com.confidant.ai.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.confidant.ai.service.NotificationCaptureService
import com.confidant.ai.ui.components.ConfidantLogo
import com.confidant.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════
// STEP 1: WELCOME & PRIVACY INTRODUCTION
// ═══════════════════════════════════════════════════════

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    OnboardingStepContainer(
        title = "Welcome to Confidant AI",
        subtitle = "Your Privacy-First AI Companion",
        onNext = onNext,
        nextButtonText = "Get Started"
    ) {
        ConfidantLogo(size = 120.dp)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        FeatureCard(
            icon = Icons.Default.Lock,
            title = "100% On-Device AI",
            description = "All AI processing happens locally on your device. No cloud servers, no data collection, complete privacy."
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FeatureCard(
            icon = Icons.Default.Security,
            title = "Privacy First Design",
            description = "We don't collect names, gender, or personal identifiers. Your data stays on your device. Open source code available at github.com/2796gaurav/confidant.ai"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FeatureCard(
            icon = Icons.Default.Psychology,
            title = "Intelligent & Proactive",
            description = "Learns from your notifications and conversations to provide contextual assistance and proactive insights."
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FeatureCard(
            icon = Icons.Default.Telegram,
            title = "Telegram Integration",
            description = "Chat with your AI companion through Telegram. Get instant responses and proactive notifications."
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FeatureCard(
            icon = Icons.Default.Search,
            title = "Smart Web Search",
            description = "Integrated DuckDuckGo search with intelligent query handling for accurate, up-to-date information."
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Let's set up your AI companion in just a few steps.",
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════
// STEP 2: INTERESTS COLLECTION (MINIMUM 2 REQUIRED)
// ═══════════════════════════════════════════════════════

@Composable
fun InterestsScreen(
    interests: String,
    onInterestsChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val interestsList = remember(interests) {
        interests.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val isValid = interestsList.size >= 2
    
    OnboardingStepContainer(
        title = "Your Interests",
        subtitle = "Help me understand what matters to you",
        onNext = if (isValid) onNext else null,
        onBack = onBack,
        nextButtonText = "Continue"
    ) {
        Text(
            text = "Enter at least 2 interests (comma-separated)",
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = interests,
            onValueChange = onInterestsChange,
            label = { Text("Your Interests") },
            placeholder = { Text("technology, finance, health, fitness, cooking") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigoMain,
                unfocusedBorderColor = MidnightLight,
                focusedTextColor = FrostWhitePrimary,
                unfocusedTextColor = FrostWhiteSecondary,
                focusedLabelColor = DeepIndigoMain,
                unfocusedLabelColor = FrostWhiteSecondary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Interest count indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${interestsList.size} interest${if (interestsList.size != 1) "s" else ""} added",
                style = MaterialTheme.typography.bodySmall,
                color = if (isValid) Color(0xFF4CAF50) else FrostWhiteSecondary
            )
            
            if (!isValid) {
                Text(
                    text = "Minimum 2 required",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        InfoCard(
            text = "💡 These interests help me provide relevant proactive insights and personalized responses. You can update them anytime in settings."
        )
    }
}

// ═══════════════════════════════════════════════════════
// STEP 3: TELEGRAM BOT SETUP WITH VALIDATION
// ═══════════════════════════════════════════════════════

@Composable
fun TelegramSetupScreen(
    botToken: String,
    onBotTokenChange: (String) -> Unit,
    chatId: String,
    onChatIdChange: (String) -> Unit,
    telegramValidated: Boolean,
    onValidationChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isValidating by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showInstructions by remember { mutableStateOf(false) }
    
    OnboardingStepContainer(
        title = "Connect Telegram",
        subtitle = "Your AI will communicate via Telegram",
        onNext = if (telegramValidated) onNext else null,
        onBack = onBack,
        nextButtonText = "Continue"
    ) {
        // Instructions toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showInstructions = !showInstructions },
            colors = CardDefaults.cardColors(containerColor = MidnightLight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = null,
                        tint = DeepIndigoMain
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "How to get Bot Token & Chat ID",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FrostWhitePrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = if (showInstructions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = FrostWhiteSecondary
                )
            }
        }
        
        AnimatedVisibility(visible = showInstructions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InstructionStep(
                        number = 1,
                        title = "Create Bot",
                        description = "Open Telegram and search for @BotFather"
                    )
                    InstructionStep(
                        number = 2,
                        title = "Get Token",
                        description = "Send /newbot command, follow prompts, and copy the API token"
                    )
                    InstructionStep(
                        number = 3,
                        title = "Get Chat ID",
                        description = "Search for @userinfobot in Telegram, start chat, and copy your ID"
                    )
                    InstructionStep(
                        number = 4,
                        title = "Start Your Bot",
                        description = "Search for your bot in Telegram and send /start"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Bot Token Input
        OutlinedTextField(
            value = botToken,
            onValueChange = {
                onBotTokenChange(it)
                onValidationChange(false)
                validationError = null
            },
            label = { Text("Bot Token") },
            placeholder = { Text("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigoMain,
                unfocusedBorderColor = MidnightLight,
                focusedTextColor = FrostWhitePrimary,
                unfocusedTextColor = FrostWhiteSecondary,
                focusedLabelColor = DeepIndigoMain,
                unfocusedLabelColor = FrostWhiteSecondary
            ),
            trailingIcon = {
                if (telegramValidated) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Validated",
                        tint = Color(0xFF4CAF50)
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Chat ID Input
        OutlinedTextField(
            value = chatId,
            onValueChange = {
                onChatIdChange(it)
                onValidationChange(false)
                validationError = null
            },
            label = { Text("Your Chat ID") },
            placeholder = { Text("123456789") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigoMain,
                unfocusedBorderColor = MidnightLight,
                focusedTextColor = FrostWhitePrimary,
                unfocusedTextColor = FrostWhiteSecondary,
                focusedLabelColor = DeepIndigoMain,
                unfocusedLabelColor = FrostWhiteSecondary
            ),
            trailingIcon = {
                if (telegramValidated) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Validated",
                        tint = Color(0xFF4CAF50)
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Test Connection Button
        Button(
            onClick = {
                scope.launch {
                    isValidating = true
                    validationError = null
                    
                    val result = validateTelegramConnection(botToken, chatId)
                    
                    isValidating = false
                    if (result.isSuccess) {
                        onValidationChange(true)
                    } else {
                        validationError = result.exceptionOrNull()?.message ?: "Connection failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = botToken.isNotBlank() && chatId.isNotBlank() && !isValidating && !telegramValidated,
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepIndigoMain,
                disabledContainerColor = MidnightLight
            )
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = FrostWhitePrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing Connection...")
            } else if (telegramValidated) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connection Verified!")
            } else {
                Icon(imageVector = Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Connection")
            }
        }
        
        // Validation Error
        if (validationError != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x33F44336))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFFF5252)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFF5252)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InfoCard(
            text = "⚠️ Make sure you've started your bot in Telegram by sending /start before testing the connection."
        )
    }
}

/**
 * Validate Telegram bot token and chat ID by sending a test message
 */
private suspend fun validateTelegramConnection(botToken: String, chatId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        // Validate format
        if (!botToken.matches(Regex("\\d+:[A-Za-z0-9_-]+"))) {
            return@withContext Result.failure(Exception("Invalid bot token format"))
        }
        
        val chatIdLong = chatId.toLongOrNull() ?: return@withContext Result.failure(Exception("Invalid chat ID"))
        
        // Test connection by sending a message
        val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val jsonPayload = """
            {
                "chat_id": $chatIdLong,
                "text": "✅ Connection successful! Your Confidant AI is ready to chat.",
                "parse_mode": "Markdown"
            }
        """.trimIndent()
        
        connection.outputStream.use { it.write(jsonPayload.toByteArray()) }
        
        val responseCode = connection.responseCode
        
        if (responseCode == 200) {
            Log.i("TelegramSetup", "✅ Telegram connection validated successfully")
            Result.success(Unit)
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            Log.e("TelegramSetup", "❌ Telegram validation failed: $errorBody")
            
            val errorMessage = when {
                errorBody.contains("chat not found") -> "Chat not found. Make sure you've started your bot in Telegram."
                errorBody.contains("Unauthorized") -> "Invalid bot token. Please check your token."
                else -> "Connection failed. Please verify your credentials."
            }
            
            Result.failure(Exception(errorMessage))
        }
    } catch (e: Exception) {
        Log.e("TelegramSetup", "❌ Telegram validation error", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }
}

// ═══════════════════════════════════════════════════════
// STEP 4: SLEEP MODE CONFIGURATION
// ═══════════════════════════════════════════════════════

@Composable
fun SleepModeScreen(
    sleepModeEnabled: Boolean,
    onSleepModeEnabledChange: (Boolean) -> Unit,
    sleepStartHour: Int,
    sleepStartMinute: Int,
    onSleepStartTimeChange: (Int, Int) -> Unit,
    sleepEndHour: Int,
    sleepEndMinute: Int,
    onSleepEndTimeChange: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    
    OnboardingStepContainer(
        title = "Sleep Mode",
        subtitle = "Pause notifications during your sleep hours",
        onNext = onNext,
        onBack = onBack,
        nextButtonText = "Continue"
    ) {
        Text(
            text = "Sleep mode helps conserve battery and prevents disturbances during your rest.",
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sleep Mode Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MidnightLight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Sleep Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FrostWhitePrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Recommended for battery savings",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostWhiteSecondary
                    )
                }
                Switch(
                    checked = sleepModeEnabled,
                    onCheckedChange = onSleepModeEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FrostWhitePrimary,
                        checkedTrackColor = DeepIndigoMain,
                        uncheckedThumbColor = FrostWhiteSecondary,
                        uncheckedTrackColor = MidnightLight
                    )
                )
            }
        }
        
        AnimatedVisibility(visible = sleepModeEnabled) {
            Column {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Sleep Start Time
                TimePickerCard(
                    label = "Sleep Time",
                    icon = Icons.Default.Bedtime,
                    hour = sleepStartHour,
                    minute = sleepStartMinute,
                    onClick = { showStartTimePicker = true }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Wake Time
                TimePickerCard(
                    label = "Wake Time",
                    icon = Icons.Default.WbSunny,
                    hour = sleepEndHour,
                    minute = sleepEndMinute,
                    onClick = { showEndTimePicker = true }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Sleep Duration Display
                val duration = calculateSleepDuration(sleepStartHour, sleepStartMinute, sleepEndHour, sleepEndMinute)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x33673AB7))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = DeepIndigoMain
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sleep duration: $duration hours",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FrostWhitePrimary
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InfoCard(
            text = "💡 During sleep mode, only critical notifications will be processed. You can adjust these times anytime in settings."
        )
    }
    
    // Time Pickers
    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = sleepStartHour,
            initialMinute = sleepStartMinute,
            onTimeSelected = { hour, minute ->
                onSleepStartTimeChange(hour, minute)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    
    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = sleepEndHour,
            initialMinute = sleepEndMinute,
            onTimeSelected = { hour, minute ->
                onSleepEndTimeChange(hour, minute)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

private fun calculateSleepDuration(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): String {
    val startMinutes = startHour * 60 + startMinute
    val endMinutes = endHour * 60 + endMinute
    
    val durationMinutes = if (endMinutes > startMinutes) {
        endMinutes - startMinutes
    } else {
        (24 * 60) - startMinutes + endMinutes
    }
    
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    
    return if (minutes == 0) {
        "$hours"
    } else {
        "$hours.${(minutes * 10) / 60}"
    }
}

// ═══════════════════════════════════════════════════════
// STEP 5: PERMISSIONS REQUEST
// ═══════════════════════════════════════════════════════

@Composable
fun PermissionsScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true // Not required on older versions
            }
        )
    }
    
    var storagePermissionGranted by remember {
        mutableStateOf(
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    // Android 13+ - Check if we have file access
                    // For GGUF files in Downloads, we need MANAGE_EXTERNAL_STORAGE or app-specific directory
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        android.os.Environment.isExternalStorageManager()
                    } else {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == 
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                else -> true // Not required on older versions
            }
        )
    }
    
    var batteryOptimizationDisabled by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }
    
    var notificationAccessGranted by remember {
        mutableStateOf(NotificationCaptureService.isEnabled(context))
    }
    
    // Refresh permission states when screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Refresh all permission states
                notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                
                storagePermissionGranted = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            android.os.Environment.isExternalStorageManager()
                        } else {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == 
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == 
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    else -> true
                }
                
                batteryOptimizationDisabled = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
                
                notificationAccessGranted = NotificationCaptureService.isEnabled(context)
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val allPermissionsGranted = notificationPermissionGranted && 
                                storagePermissionGranted &&
                                batteryOptimizationDisabled && 
                                notificationAccessGranted
    
    OnboardingStepContainer(
        title = "Grant Permissions",
        subtitle = "Required for optimal performance",
        onNext = if (allPermissionsGranted) onNext else null,
        onBack = onBack,
        nextButtonText = "Continue"
    ) {
        Text(
            text = "Confidant AI needs these permissions to function properly:",
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Storage Permission (CRITICAL for model file access)
        PermissionCard(
            icon = Icons.Default.Folder,
            title = "Storage Access",
            description = "Required to read AI model file from Downloads folder",
            isGranted = storagePermissionGranted,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ - Request MANAGE_EXTERNAL_STORAGE
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to general settings
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        context.startActivity(intent)
                    }
                } else {
                    // Android 10 and below - Open app settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notification Permission",
                description = "Required to send you AI messages and alerts",
                isGranted = notificationPermissionGranted,
                onRequest = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Battery Optimization
        PermissionCard(
            icon = Icons.Default.BatteryChargingFull,
            title = "Battery Optimization",
            description = "Disable battery optimization to keep AI running 24/7",
            isGranted = batteryOptimizationDisabled,
            onRequest = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Notification Access
        PermissionCard(
            icon = Icons.Default.NotificationsActive,
            title = "Notification Access",
            description = "Read notifications to provide contextual assistance",
            isGranted = notificationAccessGranted,
            onRequest = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!allPermissionsGranted) {
            InfoCard(
                text = "⚠️ All permissions are required for Confidant AI to function properly. Please grant them to continue."
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x334CAF50))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "All permissions granted! You're ready to go.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// STEP 6: COMPLETION SCREEN
// ═══════════════════════════════════════════════════════

@Composable
fun CompletionScreen(onComplete: () -> Unit) {
    OnboardingStepContainer(
        title = "You're All Set!",
        subtitle = "Welcome to Confidant AI",
        onNext = onComplete,
        nextButtonText = "Start Using Confidant AI"
    ) {
        ConfidantLogo(size = 100.dp)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Your AI companion is ready to assist you!",
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhitePrimary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        NextStepsCard(
            number = 1,
            title = "Download AI Model",
            description = "Go to Dashboard and download the LFM-2 model (~2GB)"
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        NextStepsCard(
            number = 2,
            title = "Start AI Server",
            description = "Tap 'Start Server' to activate your AI companion"
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        NextStepsCard(
            number = 3,
            title = "Chat on Telegram",
            description = "Open Telegram and start chatting with your bot!"
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        InfoCard(
            text = "🎉 Your data stays on your device. No cloud, no tracking, just intelligent assistance.\n\n📱 Source code: github.com/2796gaurav/confidant.ai\n🌐 Website: 2796gaurav.github.io/confidantai"
        )
    }
}

// ═══════════════════════════════════════════════════════
// REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════

@Composable
private fun OnboardingStepContainer(
    title: String,
    subtitle: String,
    onNext: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
    nextButtonText: String = "Next",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = FrostWhitePrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        content()
        
        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(32.dp))
        
        // Navigation Buttons - moved up with more padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FrostWhitePrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            
            Button(
                onClick = { onNext?.invoke() },
                enabled = onNext != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepIndigoMain,
                    disabledContainerColor = MidnightLight
                )
            ) {
                Text(nextButtonText)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MidnightLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DeepIndigoMain,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FrostWhitePrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostWhiteSecondary
                )
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x33673AB7))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = FrostWhiteSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun InstructionStep(
    number: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DeepIndigoMain),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = FrostWhitePrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = FrostWhitePrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = FrostWhiteSecondary
            )
        }
    }
}

@Composable
private fun TimePickerCard(
    label: String,
    icon: ImageVector,
    hour: Int,
    minute: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MidnightLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DeepIndigoMain
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FrostWhitePrimary
                )
            }
            Text(
                text = String.format("%02d:%02d", hour, minute),
                style = MaterialTheme.typography.headlineSmall,
                color = DeepIndigoMain,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0x334CAF50) else MidnightLight
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF4CAF50) else DeepIndigoMain
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = FrostWhitePrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = FrostWhiteSecondary
                        )
                    }
                }
                
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = Color(0xFF4CAF50)
                    )
                } else {
                    Button(
                        onClick = onRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = DeepIndigoMain)
                    ) {
                        Text("Grant")
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStepsCard(
    number: Int,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MidnightLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepIndigoMain),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = FrostWhitePrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FrostWhitePrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostWhiteSecondary
                )
            }
        }
    }
}

// Time Picker Dialog (reuse existing implementation)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(timePickerState.hour, timePickerState.minute)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}
