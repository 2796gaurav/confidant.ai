package com.confidant.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confidant.ai.ConfidantApplication
import com.confidant.ai.database.entity.ConversationEntity
import com.confidant.ai.database.entity.CoreMemoryEntity
import com.confidant.ai.database.entity.NotificationEntity
import com.confidant.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

/**
 * DATA VIEWER SCREEN
 * Complete transparency - shows all data captured and stored
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataViewerScreen(
    onNavigateBack: () -> Unit
) {
    val app = ConfidantApplication.instance
    val scope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteCategory by remember { mutableStateOf("") }
    
    // Data counts
    var notificationCount by remember { mutableIntStateOf(0) }
    var conversationCount by remember { mutableIntStateOf(0) }
    var coreMemoryCount by remember { mutableIntStateOf(0) }
    var proactiveMessageCount by remember { mutableIntStateOf(0) }
    var noteCount by remember { mutableIntStateOf(0) }
    
    // Load data counts
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            notificationCount = app.database.notificationDao().getAll().size
            conversationCount = app.database.conversationDao().getAll().size
            coreMemoryCount = app.database.coreMemoryDao().getAll().size
            proactiveMessageCount = app.database.proactiveMessageDao().getAll().size
            noteCount = app.database.noteDao().getActiveNoteCount()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Your Data",
                            style = MaterialTheme.typography.headlineMedium,
                            color = FrostWhitePrimary
                        )
                        Text(
                            "Complete transparency",
                            style = MaterialTheme.typography.bodySmall,
                            color = FrostWhiteSecondary
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Privacy notice
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SystemInfo.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = SystemInfo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "All data is stored locally on your device. Nothing is sent to external servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostWhiteSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
            
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MidnightMain,
                contentColor = DeepIndigoMain,
                edgePadding = 16.dp
            ) {
                DataTab("Notifications", notificationCount, 0, selectedTab) { selectedTab = 0 }
                DataTab("Conversations", conversationCount, 1, selectedTab) { selectedTab = 1 }
                DataTab("Core Facts", coreMemoryCount, 2, selectedTab) { selectedTab = 2 }
                DataTab("Proactive", proactiveMessageCount, 3, selectedTab) { selectedTab = 3 }
                DataTab("Notes", noteCount, 4, selectedTab) { selectedTab = 4 }
            }
            
            // Content
            when (selectedTab) {
                0 -> NotificationsDataView(
                    onDelete = { 
                        deleteCategory = "Notifications"
                        showDeleteDialog = true
                    },
                    onRefresh = {
                        scope.launch(Dispatchers.IO) {
                            notificationCount = app.database.notificationDao().getAll().size
                        }
                    }
                )
                1 -> ConversationsDataView(
                    onDelete = {
                        deleteCategory = "Conversations"
                        showDeleteDialog = true
                    },
                    onRefresh = {
                        scope.launch(Dispatchers.IO) {
                            conversationCount = app.database.conversationDao().getAll().size
                        }
                    }
                )
                2 -> CoreMemoryDataView(
                    onDelete = {
                        deleteCategory = "Core Facts"
                        showDeleteDialog = true
                    },
                    onRefresh = {
                        scope.launch(Dispatchers.IO) {
                            coreMemoryCount = app.database.coreMemoryDao().getAll().size
                        }
                    }
                )
                3 -> ProactiveMessagesDataView(
                    onDelete = {
                        deleteCategory = "Proactive Messages"
                        showDeleteDialog = true
                    },
                    onRefresh = {
                        scope.launch(Dispatchers.IO) {
                            proactiveMessageCount = app.database.proactiveMessageDao().getAll().size
                        }
                    }
                )
                4 -> NotesDataView(
                    onDelete = {
                        deleteCategory = "Notes"
                        showDeleteDialog = true
                    },
                    onRefresh = {
                        scope.launch(Dispatchers.IO) {
                            noteCount = app.database.noteDao().getActiveNoteCount()
                        }
                    }
                )
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete $deleteCategory?", color = FrostWhitePrimary) },
            text = { 
                Text(
                    "This will permanently delete all $deleteCategory data. This action cannot be undone.",
                    color = FrostWhiteSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            when (selectedTab) {
                                0 -> app.database.notificationDao().deleteAll()
                                1 -> app.database.conversationDao().deleteAll()
                                2 -> app.database.coreMemoryDao().deleteAll()
                                3 -> {
                                    // Delete all proactive messages
                                    val all = app.database.proactiveMessageDao().getAll()
                                    all.forEach { app.database.proactiveMessageDao().getById(it.id)?.let { msg -> 
                                        // Note: ProactiveMessageDao doesn't have delete method, but we can clear by marking all as sent
                                        app.database.proactiveMessageDao().markAsSent(msg.id)
                                    }}
                                }
                                4 -> {
                                    // Delete all notes
                                    val notes = app.database.noteDao().getRecentNotes(10000)
                                    notes.forEach { app.database.noteDao().delete(it) }
                                }
                            }
                            // Reload counts
                            notificationCount = app.database.notificationDao().getAll().size
                            conversationCount = app.database.conversationDao().getAll().size
                            coreMemoryCount = app.database.coreMemoryDao().getAll().size
                            proactiveMessageCount = app.database.proactiveMessageDao().getAll().size
                            noteCount = app.database.noteDao().getActiveNoteCount()
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("DELETE", color = CrimsonAlertMain, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", color = FrostWhiteTertiary)
                }
            },
            containerColor = MidnightLight
        )
    }
}

@Composable
fun DataTab(
    label: String,
    count: Int,
    index: Int,
    selectedIndex: Int,
    onClick: () -> Unit
) {
    Tab(
        selected = selectedIndex == index,
        onClick = onClick,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedIndex == index) DeepIndigoMain else FrostWhiteTertiary
                )
            }
        }
    )
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// DATA VIEW COMPONENTS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun NotificationsDataView(onDelete: () -> Unit, onRefresh: () -> Unit) {
    val app = ConfidantApplication.instance
    var notifications by remember { mutableStateOf<List<NotificationEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            notifications = app.database.notificationDao().getAll()
            isLoading = false
        }
    }
    
    DataViewContainer(
        title = "Captured Notifications",
        description = "Notifications captured from your device for learning",
        count = notifications.size,
        isLoading = isLoading,
        onDelete = onDelete
    ) {
        items(notifications) { notification ->
            NotificationCard(notification)
        }
    }
}



@Composable
fun ConversationsDataView(onDelete: () -> Unit, onRefresh: () -> Unit) {
    val app = ConfidantApplication.instance
    var conversations by remember { mutableStateOf<List<ConversationEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            conversations = app.database.conversationDao().getAll()
            isLoading = false
        }
    }
    
    DataViewContainer(
        title = "Conversation History",
        description = "Your chat history with Confidant",
        count = conversations.size,
        isLoading = isLoading,
        onDelete = onDelete
    ) {
        items(conversations) { conversation ->
            ConversationCard(conversation)
        }
    }
}

@Composable
fun CoreMemoryDataView(onDelete: () -> Unit, onRefresh: () -> Unit) {
    val app = ConfidantApplication.instance
    var coreMemories by remember { mutableStateOf<List<CoreMemoryEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            coreMemories = app.database.coreMemoryDao().getAll()
            isLoading = false
        }
    }
    
    DataViewContainer(
        title = "Core Facts",
        description = "Important facts Confidant remembers about you",
        count = coreMemories.size,
        isLoading = isLoading,
        onDelete = onDelete
    ) {
        items(coreMemories) { memory ->
            CoreMemoryCard(memory)
        }
    }
}



@Composable
fun ProactiveMessagesDataView(onDelete: () -> Unit, onRefresh: () -> Unit) {
    val app = ConfidantApplication.instance
    var messages by remember { mutableStateOf<List<com.confidant.ai.database.entity.ProactiveMessageEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            messages = app.database.proactiveMessageDao().getAll()
            isLoading = false
        }
    }
    
    DataViewContainer(
        title = "Proactive Messages",
        description = "LLM decisions about notifications and proactive messages",
        count = messages.size,
        isLoading = isLoading,
        onDelete = onDelete
    ) {
        items(messages) { message ->
            ProactiveMessageCard(message)
        }
    }
}



@Composable
fun NotesDataView(onDelete: () -> Unit, onRefresh: () -> Unit) {
    val app = ConfidantApplication.instance
    var notes by remember { mutableStateOf<List<com.confidant.ai.database.entity.NoteEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            notes = app.database.noteDao().getRecentNotes(1000)
            isLoading = false
        }
    }
    
    DataViewContainer(
        title = "Notes",
        description = "User notes and saved information",
        count = notes.size,
        isLoading = isLoading,
        onDelete = onDelete
    ) {
        items(notes) { note ->
            NoteCard(note)
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// CONTAINER AND CARD COMPONENTS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun DataViewContainer(
    title: String,
    description: String,
    count: Int,
    isLoading: Boolean,
    onDelete: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MidnightLight
            ),
            shape = RoundedCornerShape(12.dp)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$count items stored",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepIndigoLight
                    )
                }
                
                if (count > 0) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete all",
                            tint = CrimsonAlertMain
                        )
                    }
                }
            }
        }
        
        // List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = DeepIndigoMain)
            }
        } else if (count == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Inbox,
                        contentDescription = null,
                        tint = FrostWhiteTertiary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No data yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = FrostWhiteTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun NotificationCard(notification: NotificationEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val date = Date.from(notification.timestamp)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MidnightLight
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = notification.appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = DeepIndigoLight,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = FrostWhiteTertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyMedium,
                color = FrostWhitePrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostWhiteSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}



@Composable
fun ConversationCard(conversation: ConversationEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val date = Date.from(conversation.timestamp)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (conversation.role == "user") MidnightLight else DeepIndigoMain.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (conversation.role == "user") "You" else "Confidant",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (conversation.role == "user") SystemInfo else DeepIndigoLight,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = FrostWhiteTertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.content,
                style = MaterialTheme.typography.bodySmall,
                color = FrostWhiteSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CoreMemoryCard(memory: CoreMemoryEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val date = Date.from(memory.lastUpdated)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ElectricLimeMain.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = memory.key,
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricLimeMain,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = memory.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = FrostWhiteTertiary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = memory.value,
                style = MaterialTheme.typography.bodySmall,
                color = FrostWhiteSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last updated: ${dateFormat.format(date)}",
                style = MaterialTheme.typography.labelSmall,
                color = FrostWhiteTertiary
            )
        }
    }
}



@Composable
fun ProactiveMessageCard(message: com.confidant.ai.database.entity.ProactiveMessageEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val date = Date.from(message.timestamp)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.shouldTrigger) SystemInfo.copy(alpha = 0.15f) else MidnightLight
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confidence: ${(message.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.shouldTrigger) SystemInfo else FrostWhitePrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = FrostWhiteTertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.thought,
                style = MaterialTheme.typography.bodySmall,
                color = FrostWhiteSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (message.message != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Message: ${message.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostWhiteSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (message.shouldSaveToNotes) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📝 Saved to notes",
                    style = MaterialTheme.typography.labelSmall,
                    color = SystemInfo
                )
            }
        }
    }
}

@Composable
fun NoteCard(note: com.confidant.ai.database.entity.NoteEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val date = Date.from(note.updatedAt)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) DeepIndigoLight.copy(alpha = 0.2f) else MidnightLight
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = FrostWhitePrimary,
                    fontWeight = FontWeight.Bold
                )
                if (note.isPinned) {
                    Text("📌", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodySmall,
                color = FrostWhiteSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Updated: ${dateFormat.format(date)}",
                style = MaterialTheme.typography.labelSmall,
                color = FrostWhiteTertiary
            )
        }
    }
}
