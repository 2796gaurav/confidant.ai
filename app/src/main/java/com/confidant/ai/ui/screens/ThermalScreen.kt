package com.confidant.ai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.thermal.ThermalManager
import com.confidant.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalScreen(
    onNavigateBack: () -> Unit
) {
    val app = ConfidantApplication.instance
    val thermalState by app.thermalManager.thermalState.collectAsStateWithLifecycle()
    val cpuTemp by app.thermalManager.cpuTemperature.collectAsStateWithLifecycle()
    val headroom by app.thermalManager.thermalHeadroom.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thermal Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Large temperature display
            TemperatureDisplay(
                temperature = cpuTemp,
                thermalState = thermalState
            )
            
            // Status cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard(
                    title = "Status",
                    value = thermalState.name,
                    color = getThermalColorLegacy(thermalState),
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "Headroom",
                    value = "${(headroom * 100).toInt()}%",
                    color = if (headroom < 0.5f) SuccessGreen else WarningOrange,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Thread count info
            ThreadCountCard(thermalState = thermalState)
            
            // Thermal guidelines
            ThermalGuidelinesCard()
        }
    }
}

@Composable
fun TemperatureDisplay(
    temperature: Float,
    thermalState: ThermalManager.ThermalState
) {
    val color by animateColorAsState(
        targetValue = getThermalColorLegacy(thermalState),
        label = "temperature_color"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(56.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "${temperature.toInt()}°C",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = getThermalDescription(thermalState),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ThreadCountCard(thermalState: ThermalManager.ThermalState) {
    val threadCount = when (thermalState) {
        ThermalManager.ThermalState.NOMINAL, ThermalManager.ThermalState.LIGHT -> 4
        ThermalManager.ThermalState.MODERATE -> 2
        ThermalManager.ThermalState.SEVERE, ThermalManager.ThermalState.CRITICAL -> 1
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Inference Threads",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Automatically adjusted based on temperature",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Text(
                text = threadCount.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ThermalGuidelinesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Temperature Guidelines",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            ThermalGuidelineItem(
                color = ThermalCool,
                range = "< 50°C",
                description = "Cool - Full performance"
            )
            ThermalGuidelineItem(
                color = InfoBlue,
                range = "50-60°C",
                description = "Warm - Normal operation"
            )
            ThermalGuidelineItem(
                color = WarningOrange,
                range = "60-70°C",
                description = "Hot - Reduced threads"
            )
            ThermalGuidelineItem(
                color = ThermalHot,
                range = "> 70°C",
                description = "Very Hot - Minimal inference"
            )
        }
    }
}

@Composable
fun ThermalGuidelineItem(
    color: Color,
    range: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = range,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.width(60.dp)
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

private fun getThermalColorLegacy(state: ThermalManager.ThermalState): Color {
    return when (state) {
        ThermalManager.ThermalState.NOMINAL -> ThermalNominal
        ThermalManager.ThermalState.LIGHT -> ThermalLight
        ThermalManager.ThermalState.MODERATE -> ThermalModerate
        ThermalManager.ThermalState.SEVERE -> ThermalSevere
        ThermalManager.ThermalState.CRITICAL -> ThermalCritical
    }
}

private fun getThermalDescription(state: ThermalManager.ThermalState): String {
    return when (state) {
        ThermalManager.ThermalState.NOMINAL -> "Optimal Temperature"
        ThermalManager.ThermalState.LIGHT -> "Slightly Warm"
        ThermalManager.ThermalState.MODERATE -> "Running Hot"
        ThermalManager.ThermalState.SEVERE -> "Very Hot - Throttling"
        ThermalManager.ThermalState.CRITICAL -> "Critical - Minimal Performance"
    }
}