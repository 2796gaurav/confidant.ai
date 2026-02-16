package com.confidant.ai.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confidant.ai.ui.components.ConfidantLogo
import com.confidant.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * CONFIDANT AI - COMPREHENSIVE ONBOARDING FLOW 2026
 * 
 * 7-Screen Progressive Setup:
 * 1. Welcome & Privacy Introduction
 * 2. User Interests Collection (minimum 2 required)
 * 3. Telegram Bot Setup (with validation & connection test)
 * 4. Sleep Mode Configuration (with time picker)
 * 5. Permissions Request (Notifications, Battery, Notification Access)
 * 6. Setup Complete
 * 
 * NO name/gender collection - privacy-first design
 */

@Composable
fun OnboardingScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = com.confidant.ai.ConfidantApplication.instance
    
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 6
    
    // State management
    var interests by remember { mutableStateOf("") }
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var telegramValidated by remember { mutableStateOf(false) }
    var sleepModeEnabled by remember { mutableStateOf(true) }
    var sleepStartHour by remember { mutableIntStateOf(23) }
    var sleepStartMinute by remember { mutableIntStateOf(0) }
    var sleepEndHour by remember { mutableIntStateOf(7) }
    var sleepEndMinute by remember { mutableIntStateOf(0) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightMain)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Progress bar
            LinearProgressIndicator(
                progress = { (currentStep + 1) / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = DeepIndigoMain,
                trackColor = MidnightLight,
            )
            
            // Step content with animation
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    0 -> WelcomeScreen(
                        onNext = { currentStep++ }
                    )
                    1 -> InterestsScreen(
                        interests = interests,
                        onInterestsChange = { interests = it },
                        onNext = { currentStep++ },
                        onBack = { currentStep-- }
                    )
                    2 -> TelegramSetupScreen(
                        botToken = botToken,
                        onBotTokenChange = { botToken = it },
                        chatId = chatId,
                        onChatIdChange = { chatId = it },
                        telegramValidated = telegramValidated,
                        onValidationChange = { telegramValidated = it },
                        onNext = {
                            scope.launch(Dispatchers.IO) {
                                // Save Telegram config
                                app.preferencesManager.setTelegramBotToken(botToken)
                                app.preferencesManager.setTelegramChatId(chatId.toLongOrNull() ?: 0L)
                                
                                // Save interests
                                val interestsList = interests.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                val personalizationManager = com.confidant.ai.personalization.PersonalizationManager(context, app.database)
                                personalizationManager.updateProfile(interests = interestsList)
                                
                                // Save interests to core_memory
                                interestsList.forEach { interest ->
                                    app.database.coreMemoryDao().insertOrUpdate(
                                        com.confidant.ai.database.entity.CoreMemoryEntity(
                                            key = "interests.$interest",
                                            value = interest,
                                            category = "interests"
                                        )
                                    )
                                }
                                
                                // Trigger first-time proactive messaging
                                app.simplifiedProactiveSystem.handleFirstTimeProactive(interestsList)
                                
                                // Send onboarding greeting
                                val onboardingManager = com.confidant.ai.onboarding.OnboardingMessageManager(context, app.database)
                                onboardingManager.sendOnboardingGreeting()
                                
                                withContext(Dispatchers.Main) {
                                    currentStep++
                                }
                            }
                        },
                        onBack = { currentStep-- }
                    )
                    3 -> SleepModeScreen(
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
                            scope.launch(Dispatchers.IO) {
                                app.preferencesManager.setSleepModeEnabled(sleepModeEnabled)
                                app.preferencesManager.setSleepStartTime(sleepStartHour, sleepStartMinute)
                                app.preferencesManager.setSleepEndTime(sleepEndHour, sleepEndMinute)
                                app.preferencesManager.setSleepAutoDetect(false)
                                
                                // Save sleep mode to core_memory
                                app.database.coreMemoryDao().insertOrUpdate(
                                    com.confidant.ai.database.entity.CoreMemoryEntity(
                                        key = "sleep_mode.start_hour",
                                        value = sleepStartHour.toString(),
                                        category = "sleep_mode"
                                    )
                                )
                                app.database.coreMemoryDao().insertOrUpdate(
                                    com.confidant.ai.database.entity.CoreMemoryEntity(
                                        key = "sleep_mode.end_hour",
                                        value = sleepEndHour.toString(),
                                        category = "sleep_mode"
                                    )
                                )
                            }
                            currentStep++
                        },
                        onBack = { currentStep-- }
                    )
                    4 -> PermissionsScreen(
                        onNext = { currentStep++ },
                        onBack = { currentStep-- }
                    )
                    5 -> CompletionScreen(
                        onComplete = {
                            scope.launch(Dispatchers.IO) {
                                app.preferencesManager.setSetupComplete(true)
                                withContext(Dispatchers.Main) {
                                    onSetupComplete()
                                }
                            }
                        }
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
