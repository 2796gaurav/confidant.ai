package com.confidant.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.ui.theme.*
import kotlinx.coroutines.launch

/**
 * IntegrationsScreen - Configure external integrations
 * - Web Search (DuckDuckGo) - Always enabled, no config needed
 * - Notes System - Always enabled, no config needed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    onNavigateBack: () -> Unit
) {
    val app = ConfidantApplication.instance
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Integrations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightMain,
                    titleContentColor = FrostWhitePrimary,
                    navigationIconContentColor = FrostWhiteSecondary
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
            // Web Search - Always enabled
            item {
                IntegrationCard(
                    icon = "🔍",
                    title = "Web Search",
                    description = "DuckDuckGo search - no configuration needed",
                    enabled = true,
                    alwaysEnabled = true,
                    onToggle = {}
                ) {
                    Text(
                        text = "✓ Always available for searching current information",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricLimeMain
                    )
                }
            }
        }
    }
}

@Composable
fun IntegrationCard(
    icon: String,
    title: String,
    description: String,
    enabled: Boolean,
    alwaysEnabled: Boolean = false,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = FrostWhitePrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = FrostWhiteSecondary
                        )
                    }
                }
                
                if (!alwaysEnabled) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElectricLimeMain,
                            checkedTrackColor = ElectricLimeDark,
                            uncheckedThumbColor = FrostWhiteTertiary,
                            uncheckedTrackColor = MidnightDark
                        )
                    )
                }
            }
            
            // Expandable content
            if (enabled || alwaysEnabled) {
                Divider(color = FrostWhiteTertiary.copy(alpha = 0.2f))
                content()
            }
        }
    }
}
