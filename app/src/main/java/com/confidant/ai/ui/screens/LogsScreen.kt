package com.confidant.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.system.LogLevel
import com.confidant.ai.system.LogEntry
import com.confidant.ai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen developer logs screen with terminal-style UI
 * Shows ALL system logs with filtering and search capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit
) {
    val app = ConfidantApplication.instance
    val logs by app.serverManager.logs.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var isPaused by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new logs arrive (if not paused)
    LaunchedEffect(logs.size, isPaused) {
        if (!isPaused && logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    // Filter logs
    val filteredLogs = remember(logs, searchQuery, selectedLevel, selectedCategory) {
        logs.filter { log ->
            val matchesSearch = searchQuery.isEmpty() || 
                log.message.contains(searchQuery, ignoreCase = true) ||
                log.category.contains(searchQuery, ignoreCase = true)
            
            val matchesLevel = selectedLevel == null || log.level == selectedLevel
            val matchesCategory = selectedCategory == null || log.category == selectedCategory
            
            matchesSearch && matchesLevel && matchesCategory
        }
    }
    
    // Get unique categories
    val categories = remember(logs) {
        logs.map { it.category }.distinct().sorted()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = EmeraldSuccessMain,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Logs",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${filteredLogs.size}",
                            fontSize = 12.sp,
                            color = FrostWhiteSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = FrostWhiteMain
                        )
                    }
                },
                actions = {
                    // Toggle filters
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = if (showFilters || selectedLevel != null || selectedCategory != null) 
                                EmeraldSuccessMain else FrostWhiteMain
                        )
                    }
                    
                    // Pause/Resume
                    IconButton(onClick = { isPaused = !isPaused }) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) AmberCautionMain else FrostWhiteMain
                        )
                    }
                    
                    // Clear logs
                    IconButton(onClick = { app.serverManager.clearLogs() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear",
                            tint = CrimsonAlertMain
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightDark,
                    titleContentColor = FrostWhiteMain
                )
            )
        },
        containerColor = MidnightDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = {
                    Text(
                        "Search...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = FrostWhiteTertiary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = FrostWhiteSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = FrostWhiteSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FrostWhiteMain,
                    unfocusedTextColor = FrostWhiteMain,
                    focusedBorderColor = EmeraldSuccessMain,
                    unfocusedBorderColor = FrostWhiteTertiary,
                    cursorColor = EmeraldSuccessMain,
                    focusedContainerColor = MidnightLight,
                    unfocusedContainerColor = MidnightLight
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            
            // Collapsible filter section
            if (showFilters) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // Level filters - compact single row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LogLevel.entries.forEach { level ->
                            FilterChip(
                                selected = selectedLevel == level,
                                onClick = {
                                    selectedLevel = if (selectedLevel == level) null else level
                                },
                                label = {
                                    Text(
                                        level.name[0].toString(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Text(
                                        getLogLevelEmoji(level),
                                        fontSize = 10.sp
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MidnightLight,
                                    selectedContainerColor = getLogLevelColor(level).copy(alpha = 0.3f),
                                    labelColor = FrostWhiteMain,
                                    selectedLabelColor = getLogLevelColor(level)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedLevel == level,
                                    borderColor = if (selectedLevel == level) getLogLevelColor(level) else FrostWhiteTertiary,
                                    selectedBorderColor = getLogLevelColor(level),
                                    borderWidth = 1.dp
                                )
                            )
                        }
                    }
                    
                    // Category filters - compact scrollable row
                    if (categories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = {
                                    Text(
                                        "All",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MidnightLight,
                                    selectedContainerColor = EmeraldSuccessMain.copy(alpha = 0.3f),
                                    labelColor = FrostWhiteMain,
                                    selectedLabelColor = EmeraldSuccessMain
                                )
                            )
                            
                            categories.take(4).forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = {
                                        selectedCategory = if (selectedCategory == category) null else category
                                    },
                                    label = {
                                        Text(
                                            category,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    },
                                    modifier = Modifier.height(28.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MidnightLight,
                                        selectedContainerColor = SystemInfo.copy(alpha = 0.3f),
                                        labelColor = FrostWhiteMain,
                                        selectedLabelColor = SystemInfo
                                    )
                                )
                            }
                        }
                    }
                }
                
                Divider(
                    color = FrostWhiteTertiary.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            // Logs list - takes remaining space
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = FrostWhiteTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            if (searchQuery.isNotEmpty() || selectedLevel != null || selectedCategory != null) {
                                "No logs match filters"
                            } else {
                                "No logs yet"
                            },
                            color = FrostWhiteSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    reverseLayout = true // Newest at top
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        CompactLogEntry(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactLogEntry(log: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timestamp = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MidnightLight.copy(alpha = 0.4f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            text = timestamp,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = FrostWhiteTertiary,
            modifier = Modifier.width(55.dp)
        )
        
        // Level indicator
        Text(
            text = getLogLevelEmoji(log.level),
            fontSize = 10.sp,
            modifier = Modifier.width(14.dp)
        )
        
        // Category
        Text(
            text = log.category,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = SystemInfo,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(70.dp)
        )
        
        // Message - takes remaining space
        Text(
            text = log.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = FrostWhiteMain,
            modifier = Modifier.weight(1f),
            lineHeight = 14.sp
        )
    }
}

private fun getLogLevelColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.ERROR -> CrimsonAlertMain
        LogLevel.WARN -> AmberCautionMain
        LogLevel.SUCCESS -> EmeraldSuccessMain
        LogLevel.INFO -> SystemInfo
        LogLevel.DEBUG -> FrostWhiteSecondary
    }
}

private fun getLogLevelEmoji(level: LogLevel): String {
    return when (level) {
        LogLevel.ERROR -> "🔴"
        LogLevel.WARN -> "🟡"
        LogLevel.SUCCESS -> "🟢"
        LogLevel.INFO -> "🔵"
        LogLevel.DEBUG -> "⚪"
    }
}
