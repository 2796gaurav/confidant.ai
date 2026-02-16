package com.confidant.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.confidant.ai.ui.theme.*

/**
 * CONFIDANT AI - EXPLAINER DIALOGS
 * Based on UI/UX Design Document Section II.4
 */

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// "WHY NOTIFICATIONS?" EXPLAINER DIALOG
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun WhyNotificationsDialog(
    onDismiss: () -> Unit,
    onWhatsAppGuide: () -> Unit = {},
    onGmailGuide: () -> Unit = {},
    onOthersGuide: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightLight,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Why Notification Access?",
                    style = MaterialTheme.typography.titleLarge,
                    color = FrostWhitePrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = FrostWhiteSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Confidant learns about your life by observing your notifications. Here's what we capture:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FrostWhiteSecondary
                )
                
                // WhatsApp
                NotificationExampleItem(
                    icon = "📱",
                    title = "WhatsApp messages",
                    description = "Learns who you talk to and when"
                )
                
                // Financial notifications
                NotificationExampleItem(
                    icon = "💰",
                    title = "Banking notifications",
                    description = "Tracks spending patterns and transactions"
                )
                
                // Calendar
                NotificationExampleItem(
                    icon = "📅",
                    title = "Calendar events",
                    description = "Understands your schedule and commitments"
                )
                
                // Fitness
                NotificationExampleItem(
                    icon = "🏋️",
                    title = "Fitness apps",
                    description = "Monitors your health routines"
                )
                
                // Privacy Guarantee Callout
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = DeepIndigoDark.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DeepIndigoMain)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = ElectricLimeMain,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Privacy Guarantee:",
                                style = MaterialTheme.typography.titleMedium,
                                color = FrostWhitePrimary
                            )
                        }
                        
                        Text(
                            text = "• All data stays on THIS device\n• No cloud uploads, no internet required\n• You can review what we've learned anytime",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FrostWhiteSecondary
                        )
                    }
                }
                
                Text(
                    text = "Each app has different notification settings. Here's how to enable them:",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostWhiteTertiary
                )
                
                // Guide buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onWhatsAppGuide,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("WHATSAPP", color = DeepIndigoLight)
                    }
                    TextButton(
                        onClick = onGmailGuide,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GMAIL", color = DeepIndigoLight)
                    }
                    TextButton(
                        onClick = onOthersGuide,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OTHERS", color = DeepIndigoLight)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepIndigoMain,
                    contentColor = FrostWhitePrimary
                )
            ) {
                Text("GOT IT")
            }
        }
    )
}

@Composable
private fun NotificationExampleItem(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = FrostWhitePrimary
            )
            Text(
                text = "→ $description",
                style = MaterialTheme.typography.bodyMedium,
                color = FrostWhiteTertiary
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// "HOW DOES IT WORK?" BOTTOM SHEET
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorksBottomSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightLight,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "How Does It Work?",
                style = MaterialTheme.typography.headlineMedium,
                color = FrostWhitePrimary
            )
            
            HowItWorksStep(
                number = 1,
                title = "We capture your notifications",
                description = "With your permission, we observe notifications from your apps"
            )
            
            HowItWorksStep(
                number = 2,
                title = "AI analyzes patterns locally",
                description = "All processing happens on your device - no internet needed"
            )
            
            HowItWorksStep(
                number = 3,
                title = "Learns about your life",
                description = "Understands your relationships, routines, and preferences"
            )
            
            HowItWorksStep(
                number = 4,
                title = "Sends insights via Telegram",
                description = "Proactive messages when you need them most"
            )
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepIndigoMain,
                    contentColor = FrostWhitePrimary
                )
            ) {
                Text("GOT IT")
            }
        }
    }
}

@Composable
private fun HowItWorksStep(
    number: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(20.dp),
            color = DeepIndigoMain
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = FrostWhitePrimary
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = FrostWhitePrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = FrostWhiteSecondary
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// CONFIRMATION DIALOG (for destructive actions)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "CONFIRM",
    cancelText: String = "CANCEL",
    isDangerous: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightLight,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                if (isDangerous) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = if (isDangerous) CrimsonAlertMain else DeepIndigoMain,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = FrostWhitePrimary
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = FrostWhiteSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDangerous) CrimsonAlertMain else DeepIndigoMain,
                    contentColor = FrostWhitePrimary
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText, color = FrostWhiteSecondary)
            }
        }
    )
}
