package com.confidant.ai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.confidant.ai.ui.theme.*

/**
 * CONFIDANT AI - EMPTY STATES & ERROR STATES
 * Based on UI/UX Design Document Section VIII
 */

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// EMPTY STATE: NO ACTIVITY TODAY
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun EmptyActivityState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Empty mailbox icon
        Text(
            text = "📭",
            style = MaterialTheme.typography.displayMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No activity yet today",
            style = MaterialTheme.typography.titleLarge,
            color = FrostWhitePrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "I'm learning in the background. Check back later for insights!",
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteTertiary,
            textAlign = TextAlign.Center
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// CRITICAL ERROR: NOTIFICATION ACCESS REVOKED
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun NotificationAccessRevokedError(
    onGrantPermission: () -> Unit,
    onShowExplanation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon
        Icon(
            imageVector = Icons.Filled.NotificationsOff,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = CrimsonAlertMain
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Notification Access Lost",
            style = MaterialTheme.typography.displayLarge,
            color = FrostWhitePrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Confidant needs notification access to learn about your life. Without it, the app cannot function.",
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhiteSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onGrantPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonAlertMain,
                contentColor = FrostWhitePrimary
            )
        ) {
            Text("GRANT PERMISSION")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onShowExplanation) {
            Text(
                "WHY IS THIS NEEDED?",
                color = DeepIndigoLight
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// NETWORK ERROR SNACKBAR
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun NetworkErrorSnackbar(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            TextButton(onClick = onRetry) {
                Text("RETRY", color = ElectricLimeMain)
            }
        },
        dismissAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = FrostWhiteSecondary
                )
            }
        },
        containerColor = MidnightLight,
        contentColor = FrostWhiteSecondary,
        actionContentColor = ElectricLimeMain
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠️")
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FrostWhitePrimary
                )
                Text(
                    text = "Check your internet connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostWhiteTertiary
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// EMPTY MEMORIES STATE
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun EmptyMemoriesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Psychology,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = DeepIndigoLight.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No Memories Yet",
            style = MaterialTheme.typography.headlineMedium,
            color = FrostWhitePrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "As you use the app and chat with your AI companion, memories will be stored here.",
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteTertiary,
            textAlign = TextAlign.Center
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LOADING STATE
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun LoadingState(
    message: String = "Loading..."
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = DeepIndigoMain,
            trackColor = MidnightLight
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhiteSecondary
        )
    }
}
