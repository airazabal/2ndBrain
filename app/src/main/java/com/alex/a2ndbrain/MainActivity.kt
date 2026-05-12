package com.alex.a2ndbrain

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.core.capture.CaptureDebugStore
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.capture.ClipboardCaptureManager
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var clipboardCaptureManager: ClipboardCaptureManager
    private lateinit var settingsManager: CaptureSettingsManager
    private lateinit var reflectionManager: ReflectionManager

    override fun onResume() {
        super.onResume()
        clipboardCaptureManager.captureCurrentClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = CaptureSettingsManager(this)
        clipboardCaptureManager = ClipboardCaptureManager(this)
        reflectionManager = ReflectionManager(this)
        
        reflectionManager.schedulePeriodicReflection()
        
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var currentTab by remember { mutableIntStateOf(0) }
                    val database = AppDatabase.getDatabase(this)
                    val memories by database.memoryDao().getAllMemories().collectAsState(initial = emptyList())
                    val summaries by database.memoryDao().getAllSummaries().collectAsState(initial = emptyList())
                    
                    var searchQuery by remember { mutableStateOf("") }
                    val filteredMemories = if (searchQuery.isEmpty()) {
                        memories
                    } else {
                        memories.filter { 
                            it.content.contains(searchQuery, ignoreCase = true) || 
                            (it.title ?: "").contains(searchQuery, ignoreCase = true) 
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Search, contentDescription = "Memories") },
                                    label = { Text("Memories") },
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Reflection") },
                                    label = { Text("Reflection") },
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentTab) {
                                0 -> MemoryScreen(
                                    memories = filteredMemories,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    onOpenSettings = {
                                        startActivity(Intent(this@MainActivity, AppCaptureSettingsActivity::class.java))
                                    },
                                    onCaptureClipboard = {
                                        clipboardCaptureManager.captureCurrentClipboard()
                                    },
                                    onMarkAsRead = { id ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            database.memoryDao().markAsRead(id)
                                        }
                                    },
                                    onClearAll = {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            database.clearAllTables()
                                        }
                                    }
                                )
                                1 -> ReflectionScreen(
                                    summaries = summaries,
                                    settingsManager = settingsManager,
                                    onGenerateReflection = {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            reflectionManager.generateDailyReflection()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class MemorySortOption {
    USAGE, RECENCY
}

@Composable
fun MemoryScreen(
    memories: List<MemoryEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCaptureClipboard: () -> Unit,
    onMarkAsRead: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var sortOption by remember { mutableStateOf(MemorySortOption.USAGE) }
    var unreadOnly by remember { mutableStateOf(false) }
    
    val filteredByRead = remember(memories, unreadOnly) {
        if (unreadOnly) memories.filter { !it.isRead } else memories
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val lastDebugEvent by CaptureDebugStore.lastEvent.collectAsState()
        
        val totalCount = memories.size
        val clipboardCount = remember(memories) { memories.count { it.source == "clipboard" } }
        val appCount = remember(memories) { 
            memories.filter { it.source == "notification" }
                .mapNotNull { it.packageName }
                .distinct().size 
        }
        val unreadCount = remember(memories) { memories.count { !it.isRead } }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Memories",
                    style = MaterialTheme.typography.headlineMedium
                )
                val statusText = lastDebugEvent.substringAfter("] ").ifEmpty { "Monitoring for captures..." }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val scanIntent = Intent(context, com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java).apply {
                        action = "CHECK_ACTIVE"
                    }
                    context.startService(scanIntent)
                }) {
                    Text("SCAN", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onCaptureClipboard,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("📋 CAPTURE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClearAll) {
                    Text("CLEAR", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(onClick = onOpenSettings) {
                    Text("Setup")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(label = "Total", count = totalCount, color = MaterialTheme.colorScheme.primaryContainer)
            }
            item {
                CategoryChip(label = "Unread", count = unreadCount, color = if (unreadCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            }
            item {
                CategoryChip(label = "Apps", count = appCount, color = MaterialTheme.colorScheme.secondaryContainer)
            }
            item {
                CategoryChip(label = "Clipboard", count = clipboardCount, color = MaterialTheme.colorScheme.tertiaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your brain...") },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Text("✕") // Clear button
                    }
                }
            }
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sort:", style = MaterialTheme.typography.labelMedium)
            FilterChip(
                selected = sortOption == MemorySortOption.USAGE,
                onClick = { sortOption = MemorySortOption.USAGE },
                label = { Text("Used") }
            )
            FilterChip(
                selected = sortOption == MemorySortOption.RECENCY,
                onClick = { sortOption = MemorySortOption.RECENCY },
                label = { Text("Recent") }
            )
            Spacer(modifier = Modifier.weight(1f))
            FilterChip(
                selected = unreadOnly,
                onClick = { unreadOnly = !unreadOnly },
                label = { Text("Unread Only") },
                leadingIcon = {
                    if (unreadOnly) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (filteredByRead.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (searchQuery.isEmpty()) {
                    if (unreadOnly) "No unread memories!" else "No memories captured yet."
                } else "No matches found.")
            }
        } else {
            val packageManager = context.packageManager
            val sortedGroups by remember(filteredByRead, sortOption) {
                derivedStateOf {
                    filteredByRead.groupBy { memory ->
                        val key = memory.packageName ?: memory.source
                        try {
                            val appInfo = packageManager.getApplicationInfo(key, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            if (key == "clipboard") "Clipboard" else key
                        }
                    }.toList().let { grouped ->
                        when (sortOption) {
                            MemorySortOption.USAGE -> grouped.sortedByDescending { it.second.size }
                            MemorySortOption.RECENCY -> grouped.sortedByDescending { it.second.maxOf { m -> m.timestamp } }
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedGroups) { (displayName, groupMemories) ->
                    GroupedMemoryCard(
                        displayName = displayName,
                        memories = groupMemories,
                        onMarkAsRead = onMarkAsRead
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(label: String, count: Int, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun GroupedMemoryCard(displayName: String, memories: List<MemoryEntity>, onMarkAsRead: (Long) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var showAllItems by remember { mutableStateOf(false) }
    
    // Automatically mark all as read when category is expanded or new items arrive while expanded
    LaunchedEffect(isExpanded, memories) {
        if (isExpanded) {
            memories.filter { !it.isRead }.forEach { onMarkAsRead(it.id) }
        }
    }
    
    val displayMemories = if (showAllItems) memories else memories.take(5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = memories.size.toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    val hasUnread = memories.any { !it.isRead }
                    if (hasUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.size(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(5.dp)
                        ) {}
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    displayMemories.forEachIndexed { index, memory ->
                        MemoryItem(memory, onMarkAsRead = onMarkAsRead)
                        if (index < displayMemories.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                    
                    if (memories.size > 5) {
                        TextButton(
                            onClick = { showAllItems = !showAllItems },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showAllItems) "Show less" else "Show ${memories.size - 5} more...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryItem(memory: MemoryEntity, onMarkAsRead: (Long) -> Unit) {
    var itemExpanded by remember { mutableStateOf(false) }
    val isLong = memory.content.length > 200 || memory.content.count { it == '\n' } > 5
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onMarkAsRead(memory.id)
                if (!memory.deepLink.isNullOrEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(memory.deepLink))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback to launching the app if deep link fails
                        if (memory.source == "notification" && !memory.packageName.isNullOrEmpty()) {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(memory.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                    }
                } else if (memory.source == "notification" && !memory.packageName.isNullOrEmpty()) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(memory.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    } catch (_: Exception) {
                        // Handle case where app might have been uninstalled
                    }
                }
            }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // New vertical unread indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(
                        if (!memory.isRead) MaterialTheme.colorScheme.primary 
                        else Color.Transparent, 
                        RoundedCornerShape(2.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                if (!memory.title.isNullOrEmpty()) {
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (memory.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                SelectionContainer {
                    Text(
                        text = memory.content,
                        fontSize = 14.sp,
                        maxLines = if (itemExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (memory.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            if (memory.duplicateCount > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "x${memory.duplicateCount}",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        
        if (isLong) {
            TextButton(
                onClick = { 
                    itemExpanded = !itemExpanded
                    onMarkAsRead(memory.id)
                },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    if (itemExpanded) "Read less" else "Read more...",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!memory.deepLink.isNullOrEmpty()) {
                Text(
                    text = "🔗 View original",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            Text(
                text = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(memory.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ReflectionScreen(
    summaries: List<DailySummaryEntity>, 
    settingsManager: CaptureSettingsManager,
    onGenerateReflection: () -> Unit
) {
    var selectedModel by remember { mutableStateOf(settingsManager.getGeminiModel()) }
    val models = remember {
        val baseModels = mutableListOf(
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-pro-preview",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash"
        )
        val buildConfigModel = BuildConfig.GEMINI_MODEL
        if (buildConfigModel.isNotEmpty() && !baseModels.contains(buildConfigModel)) {
            baseModels.add(0, buildConfigModel)
        }
        baseModels
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daily Reflections",
                style = MaterialTheme.typography.headlineMedium
            )
            Button(onClick = onGenerateReflection) {
                Text("Reflect")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Model Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Model:", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models) { model ->
                    FilterChip(
                        selected = selectedModel == model,
                        onClick = { 
                            selectedModel = model
                            settingsManager.saveGeminiModel(model)
                        },
                        label = { Text(model.removePrefix("gemini-")) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (summaries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Summaries will appear here at the end of the day.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(summaries) { summary ->
                    SummaryCard(summary)
                }
            }
        }
    }
}

@Composable
fun SummaryCard(summary: DailySummaryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(summary.timestamp)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    summary.modelName?.let { name ->
                        Text(
                            text = "AI: ${name.removePrefix("gemini-")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = summary.summary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }
    }
}

// MemoryItem, GroupedMemoryCard, and CategoryChip composables remain here
