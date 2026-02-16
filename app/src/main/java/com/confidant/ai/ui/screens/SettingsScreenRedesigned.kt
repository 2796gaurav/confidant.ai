package com.confidant.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.ui.components.ConfidantLogo
import com.confidant.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * CONFIDANT AI - REDESIGNED SETTINGS SCREEN
 * Based on UI/UX Design Document v1.0
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenRedesigned(
    onNavigateBack: () -> Unit,
    onNavigateToIntegrations: () -> Unit = {},
    onNavigateToDataViewer: () -> Unit = {}
) {
    val app = ConfidantApplication.instance
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var telegramToken by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    
    // Load preferences
    LaunchedEffect(Unit) {
        telegramToken = app.preferencesManager.getTelegramBotToken() ?: ""
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConfidantLogo(
                            size = 28.dp,
                            animate = false
                        )
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineLarge,
                            color = FrostWhitePrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
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
            // GENERAL
            item {
                SettingsSectionHeader("GENERAL")
            }
            
            item {
                SettingsCard {
                    SettingsTextField(
                        icon = Icons.Filled.Key,
                        label = "Telegram Bot Token",
                        value = telegramToken,
                        onValueChange = { telegramToken = it },
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    if (tokenVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Toggle visibility",
                                    tint = FrostWhiteTertiary
                                )
                            }
                        },
                        onSave = {
                            scope.launch {
                                app.preferencesManager.setTelegramBotToken(telegramToken)
                                app.telegramBotManager.startBot()
                            }
                        }
                    )
                }
            }
            
            // BEHAVIOR - Disabled (Future Enhancement)
            item {
                SettingsSectionHeader("BEHAVIOR")
            }
            
            item {
                DisabledSettingsCard(
                    title = "Behavior Settings",
                    description = "Proactive messages and thermal management",
                    comingSoonText = "Coming Soon - Future Enhancement"
                )
            }
            
            // INTEGRATIONS - Disabled (Future Enhancement)
            item {
                SettingsSectionHeader("INTEGRATIONS")
            }
            
            item {
                DisabledSettingsCard(
                    title = "External Integrations",
                    description = "Web Search and more",
                    comingSoonText = "Coming Soon - Future Enhancement"
                )
            }
            
            // SLEEP MODE - Disabled (Future Enhancement)
            item {
                SettingsSectionHeader("SLEEP MODE")
            }
            
            item {
                DisabledSettingsCard(
                    title = "Sleep Mode",
                    description = "Reduce activity during sleep hours",
                    comingSoonText = "Coming Soon - Future Enhancement"
                )
            }
            
            // NOTIFICATIONS - Disabled (Future Enhancement)
            item {
                SettingsSectionHeader("NOTIFICATIONS")
            }
            
            item {
                DisabledSettingsCard(
                    title = "Notification Settings",
                    description = "Quiet mode and event notifications",
                    comingSoonText = "Coming Soon - Future Enhancement"
                )
            }
            
            // DATA & PRIVACY
            item {
                SettingsSectionHeader("DATA & PRIVACY")
            }
            
            item {
                SettingsCard {
                    SettingsNavigationItem(
                        icon = Icons.Filled.Storage,
                        label = "View Your Data",
                        value = "See what we store",
                        onClick = {
                            // Navigate to data viewer
                            onNavigateToDataViewer()
                        }
                    )
                    
                    Divider(color = FrostWhiteTertiary.copy(alpha = 0.2f))
                    
                    SettingsNavigationItem(
                        icon = Icons.Filled.Delete,
                        label = "Clear All Data",
                        value = "",
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                app.database.clearAllTables()
                                // Core memory will be reloaded on next app start
                            }
                        },
                        textColor = CrimsonAlertMain
                    )
                }
            }
            
            // ADVANCED - Disabled (Future Enhancement)
            item {
                SettingsSectionHeader("ADVANCED")
            }
            
            item {
                DisabledSettingsCard(
                    title = "Advanced Settings",
                    description = "Debug mode and developer options",
                    comingSoonText = "Coming Soon - Future Enhancement"
                )
            }
            
            // ABOUT
            item {
                SettingsSectionHeader("ABOUT")
            }
            
            item {
                SettingsCard {
                    SettingsInfoItem(
                        icon = Icons.Filled.Info,
                        label = "App Version",
                        value = "1.0.0"
                    )
                    Divider(color = MidnightMain, thickness = 1.dp)
                    SettingsClickableItem(
                        icon = Icons.Filled.Code,
                        label = "Source Code",
                        value = "GitHub",
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://github.com/2796gaurav/confidant.ai")
                            }
                            context.startActivity(intent)
                        }
                    )
                    Divider(color = MidnightMain, thickness = 1.dp)
                    SettingsClickableItem(
                        icon = Icons.Filled.Language,
                        label = "Website",
                        value = "Visit",
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("http://2796gaurav.github.io/confidantai")
                            }
                            context.startActivity(intent)
                        }
                    )
                    Divider(color = MidnightMain, thickness = 1.dp)
                    SettingsInfoItem(
                        icon = Icons.Filled.Security,
                        label = "Privacy",
                        value = "100% On-Device"
                    )
                    Divider(color = MidnightMain, thickness = 1.dp)
                    SettingsInfoItem(
                        icon = Icons.Filled.Lock,
                        label = "Data Storage",
                        value = "Local Only"
                    )
                }
            }
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SETTINGS COMPONENTS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = DeepIndigoLight,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
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
                .padding(vertical = 8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsTextField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(24.dp)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigoMain,
                unfocusedBorderColor = FrostWhiteTertiary,
                focusedLabelColor = DeepIndigoLight,
                unfocusedLabelColor = FrostWhiteTertiary,
                focusedTextColor = FrostWhitePrimary,
                unfocusedTextColor = FrostWhiteSecondary
            )
        )
        
        IconButton(onClick = onSave) {
            Icon(
                Icons.Filled.Save,
                contentDescription = "Save",
                tint = DeepIndigoLight
            )
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color = FrostWhitePrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = FrostWhiteTertiary
            )
        }
        
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhitePrimary,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = FrostWhiteTertiary
        )
    }
}

@Composable
fun SettingsClickableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhitePrimary,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = DeepIndigoLight
        )
        
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = FrostWhitePrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = FrostWhiteTertiary
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ElectricLimeMain,
                checkedTrackColor = ElectricLimeDark,
                uncheckedThumbColor = FrostWhiteTertiary,
                uncheckedTrackColor = MidnightDark
            )
        )
    }
}

@Composable
fun SettingsSliderItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FrostWhiteTertiary,
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = FrostWhitePrimary,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = DeepIndigoLight
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(start = 36.dp),
            colors = SliderDefaults.colors(
                thumbColor = DeepIndigoMain,
                activeTrackColor = DeepIndigoMain,
                inactiveTrackColor = FrostWhiteTertiary.copy(alpha = 0.3f)
            )
        )
    }
}


@Composable
fun SettingsTimeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = FrostWhitePrimary,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = String.format("%02d:%02d", hour, minute),
            style = MaterialTheme.typography.titleMedium,
            color = DeepIndigoLight
        )
        
        Icon(
            Icons.Filled.Edit,
            contentDescription = null,
            tint = FrostWhiteTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
    
    if (showDialog) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showDialog = false },
            onConfirm = { h, m ->
                onTimeChange(h, m)
                showDialog = false
            }
        )
    }
}

@Composable
fun DisabledSettingsCard(
    title: String,
    description: String,
    comingSoonText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MidnightLight.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = FrostWhiteTertiary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostWhiteTertiary.copy(alpha = 0.5f)
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MidnightDark.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Disabled",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = FrostWhiteTertiary.copy(alpha = 0.7f)
                    )
                }
            }
            
            Divider(color = FrostWhiteTertiary.copy(alpha = 0.1f))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = FrostWhiteTertiary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = comingSoonText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FrostWhiteTertiary.copy(alpha = 0.6f)
                )
            }
            
            Text(
                text = "This feature is planned for a future release. Stay tuned for updates!",
                style = MaterialTheme.typography.bodySmall,
                color = FrostWhiteTertiary.copy(alpha = 0.5f)
            )
        }
    }
}


@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Select Time",
                color = FrostWhitePrimary
            ) 
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Increase hour", tint = DeepIndigoMain)
                    }
                    Text(
                        text = String.format("%02d", selectedHour),
                        style = MaterialTheme.typography.headlineMedium,
                        color = FrostWhitePrimary
                    )
                    IconButton(onClick = { selectedHour = if (selectedHour == 0) 23 else selectedHour - 1 }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Decrease hour", tint = DeepIndigoMain)
                    }
                }
                
                Text(
                    ":",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FrostWhitePrimary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                // Minute picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { selectedMinute = (selectedMinute + 15) % 60 }) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Increase minute", tint = DeepIndigoMain)
                    }
                    Text(
                        text = String.format("%02d", selectedMinute),
                        style = MaterialTheme.typography.headlineMedium,
                        color = FrostWhitePrimary
                    )
                    IconButton(onClick = { selectedMinute = if (selectedMinute == 0) 45 else selectedMinute - 15 }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Decrease minute", tint = DeepIndigoMain)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedHour, selectedMinute) }) {
                Text("OK", color = DeepIndigoMain)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FrostWhiteTertiary)
            }
        },
        containerColor = MidnightLight
    )
}
