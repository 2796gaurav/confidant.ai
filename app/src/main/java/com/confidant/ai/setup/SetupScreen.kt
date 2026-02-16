package com.confidant.ai.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confidant.ai.ui.components.ConfidantLogo
import com.confidant.ai.ui.theme.*
import com.confidant.ai.ui.screens.TimePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * CONFIDANT AI - REDESIGNED SETUP FLOW (NO NAME/GENDER PERSONALIZATION)
 * 6-Screen Progressive Onboarding with Validation
 * Collects only interests for proactive messaging
 */

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = com.confidant.ai.ConfidantApplication.instance
    
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 6  // Reduced from 8 (removed name/gender step)
    
    // Shared state across screens - REMOVED: userName, botName, userGender, botGender
    var interests by remember { mutableStateOf("") }
    var botToken by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    
    // Sleep mode configuration
    var sleepModeEnabled by remember { mutableStateOf(true) } // DEFAULT: enabled
    var sleepStartHour by remember { mutableIntStateOf(23) }
    var sleepStartMinute by remember { mutableIntStateOf(0) }
    var sleepEndHour by remember { mutableIntStateOf(7) }
    var sleepEndMinute by remember { mutableIntStateOf(0) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightMain)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1) / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = DeepIndigoMain,
                trackColor = MidnightLight,
            )
            
            // Step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label = "setup_step"
            ) { step ->
                when (step) {
                    0 -> WelcomeStep1(
                        onNext = { currentStep++ }
                    )
                    1 -> WelcomeStep2(
                        onNext = { currentStep++ },
                        onBack = { currentStep-- }
                    )
                    // REMOVED: PersonalizationStep1 (names and gender)
                    2 -> InterestsStep(  // Renamed from PersonalizationStep2
                        interests = interests,
                        onInterestsChange = { interests = it },
                        onNext = { currentStep++ },
                        onBack = { currentStep-- }
                    )
                    3 -> TelegramSetupStep(
                        botToken = botToken,
                        onBotTokenChange = { botToken = it },
                        userId = userId,
                        onUserIdChange = { userId = it },
                        onNext = { 
                            // Save telegram config, interests, and send onboarding greeting
                            scope.launch(Dispatchers.IO) {
                                app.preferencesManager.setTelegramBotToken(botToken)
                                app.preferencesManager.setTelegramChatId(userId.toLongOrNull() ?: 0L)
                                
                                // Parse and save interests (NO NAMES/GENDER)
                                val interestsList = interests.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                val personalizationManager = com.confidant.ai.personalization.PersonalizationManager(context, app.database)
                                personalizationManager.updateProfile(
                                    interests = interestsList
                                )
                                
                                // Send onboarding greeting via Telegram
                                val onboardingManager = com.confidant.ai.onboarding.OnboardingMessageManager(context, app.database)
                                onboardingManager.sendOnboardingGreeting()
                                
                                withContext(Dispatchers.Main) {
                                    currentStep++
                                }
                            }
                        },
                        onBack = { currentStep-- }
                    )
                    4 -> SleepModeSetupStep(
                        sleepModeEnabled = sleepModeEnabled,
                        onSleepModeEnabledChange = { sleepModeEnabled = it },
                        sleepStartHour = sleepStartHour,
                        sleepStartMinute = sleepStartMinute,
                        onSleepStartTimeChange = { hour, minute ->
                            sleepStartHour = hour
                            sleepStartMinute = minute
                        },
                        sleepEndHour = sleepEndHour,
                        sleepEndMinute = sleepEndMinute,
                        onSleepEndTimeChange = { hour, minute ->
                            sleepEndHour = hour
                            sleepEndMinute = minute
                        },
                        onNext = {
                            // Save sleep mode configuration
                            scope.launch(Dispatchers.IO) {
                                app.preferencesManager.setSleepModeEnabled(sleepModeEnabled)
                                app.preferencesManager.setSleepStartTime(sleepStartHour, sleepStartMinute)
                                app.preferencesManager.setSleepEndTime(sleepEndHour, sleepEndMinute)
                                app.preferencesManager.setSleepAutoDetect(false) // Manual mode by default
                            }
                            currentStep++
                        },
                        onBack = { currentStep-- }
                    )
                    5 -> PermissionsStep(
                        onComplete = {
                            // Complete setup (NO NAMES TO SAVE)
                            scope.launch(Dispatchers.IO) {
                                app.preferencesManager.setSetupComplete(true)
                                withContext(Dispatchers.Main) {
                                    onSetupComplete()
                                }
                            }
                        },
                        onBack = { currentStep-- },
                        botToken = botToken
                    )
                }
            }
        }
        
        // Step indicator dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStep) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= currentStep) DeepIndigoMain else MidnightLight
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// SETUP STEP COMPOSABLES
// ═══════════════════════════════════════════════════════

@Composable
private fun WelcomeStep1(onNext: () -> Unit) {
    SetupStepContainer(
        title = "Welcome to Confidant AI",
        subtitle = "Your privacy-first AI companion",
        onNext = onNext
    ) {
        ConfidantLogo(size = 120.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Confidant AI runs entirely on your device with no cloud dependencies.",
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WelcomeStep2(onNext: () -> Unit, onBack: () -> Unit) {
    SetupStepContainer(
        title = "Privacy First",
        subtitle = "We don't collect personal data",
        onNext = onNext,
        onBack = onBack
    ) {
        Text(
            text = "• No names or gender data collected\n• Only interests for personalization\n• All data stays on your device",
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhiteSecondary
        )
    }
}

@Composable
private fun InterestsStep(
    interests: String,
    onInterestsChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    SetupStepContainer(
        title = "Your Interests",
        subtitle = "Help us personalize your experience",
        onNext = onNext,
        onBack = onBack
    ) {
        OutlinedTextField(
            value = interests,
            onValueChange = onInterestsChange,
            label = { Text("Interests (comma-separated)") },
            placeholder = { Text("technology, finance, health") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigoMain,
                unfocusedBorderColor = MidnightLight,
                focusedTextColor = FrostWhitePrimary,
                unfocusedTextColor = FrostWhiteSecondary
            )
        )
    }
}

@Composable
private fun TelegramSetupStep(
    botToken: String,
    onBotTokenChange: (String) -> Unit,
    userId: String,
    onUserIdChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    SetupStepContainer(
        title = "Telegram Setup",
        subtitle = "Connect your Telegram bot",
        onNext = onNext,
        onBack = onBack
    ) {
        OutlinedTextField(
            value = botToken,
            onValueChange = onBotTokenChange,
            label = { Text("Bot Token") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigoMain,
                unfocusedBorderColor = MidnightLight,
                focusedTextColor = FrostWhitePrimary,
                unfocusedTextColor = FrostWhiteSecondary
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = userId,
            onValueChange = onUserIdChange,
            label = { Text("Chat ID") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigoMain,
                unfocusedBorderColor = MidnightLight,
                focusedTextColor = FrostWhitePrimary,
                unfocusedTextColor = FrostWhiteSecondary
            )
        )
    }
}

@Composable
private fun SleepModeSetupStep(
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
    SetupStepContainer(
        title = "Sleep Mode",
        subtitle = "Pause notifications during sleep",
        onNext = onNext,
        onBack = onBack
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Sleep Mode", color = FrostWhitePrimary)
            Switch(
                checked = sleepModeEnabled,
                onCheckedChange = onSleepModeEnabledChange
            )
        }
        if (sleepModeEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Sleep: ${sleepStartHour}:${sleepStartMinute.toString().padStart(2, '0')} - ${sleepEndHour}:${sleepEndMinute.toString().padStart(2, '0')}", color = FrostWhiteSecondary)
        }
    }
}

@Composable
private fun PermissionsStep(
    onComplete: () -> Unit,
    onBack: () -> Unit,
    botToken: String
) {
    SetupStepContainer(
        title = "Permissions",
        subtitle = "Grant required permissions",
        onNext = onComplete,
        onBack = onBack
    ) {
        Text(
            text = "Please grant notification access and battery optimization exemption.",
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SetupStepContainer(
    title: String,
    subtitle: String,
    onNext: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = FrostWhitePrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteSecondary
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        content()
        
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (onBack != null) {
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = DeepIndigoMain)
            ) {
                Text("Next")
            }
        }
    }
}
