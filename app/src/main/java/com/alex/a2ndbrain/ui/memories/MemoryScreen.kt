package com.alex.a2ndbrain.ui.memories

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.capture.CaptureDebugStore
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onClearAll: () -> Unit,
    monitoredApps: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val scope = rememberCoroutineScope()
    var sortOption by remember { mutableStateOf(MemorySortOption.USAGE) }
    var unreadOnly by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    // Reset scan state after a short delay or when memories change
    LaunchedEffect(memories) {
        isScanning = false
    }

    val filteredByMonitoring = remember(memories, monitoredApps) {
        if (monitoredApps.isEmpty()) memories
        else memories.filter { it.source != "notification" || monitoredApps.contains(it.packageName) }
    }

    val filteredByRead = remember(filteredByMonitoring, unreadOnly) {
        if (unreadOnly) filteredByMonitoring.filter { !it.isRead } else filteredByMonitoring
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val totalCount = memories.size
        val clipboardCount = remember(memories) { memories.count { it.source == "clipboard" } }
        val appCount = remember(memories) {
            memories.filter { it.source == "notification" }
                .mapNotNull { it.packageName }
                .distinct().size
        }
        val unreadCount = remember(memories) { memories.count { !it.isRead } }

        // Adaptive Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Feed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isScanning) {
                    Text("Updating...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Action Buttons with proportional spacing
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (configuration.screenWidthDp < 360) 4.dp else 8.dp)
            ) {
                // Clipboard Capture (Icon only on small screens)
                if (configuration.screenWidthDp < 400) {
                    IconButton(onClick = onCaptureClipboard) {
                        Text("📋", fontSize = 16.sp)
                    }
                } else {
                    TextButton(onClick = onCaptureClipboard) {
                        Text("CAPTURE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // SCAN Button
                TextButton(
                    onClick = {
                        isScanning = true
                        val scanIntent = Intent(context, com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java).apply {
                            action = "CHECK_ACTIVE"
                        }
                        context.startService(scanIntent)
                    },
                    enabled = !isScanning,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    } else {
                        Text("SCAN", fontSize = 10.sp)
                    }
                }

                // Proportional Setup Button
                val setupWidth = (configuration.screenWidthDp * 0.2).coerceIn(70.0, 110.0).dp
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.width(setupWidth),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Setup", style = MaterialTheme.typography.labelMedium)
                }
                
                // Clear (Icon only)
                IconButton(onClick = onClearAll, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search, 
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
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
                CategoryChip(
                    label = "Unread",
                    count = unreadCount,
                    color = if (unreadCount > 0) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
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
                        Text("✕")
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
                Text(
                    if (searchQuery.isEmpty()) {
                        if (unreadOnly) "No unread notifications!" else "No notifications captured yet."
                    } else {
                        "No matches found."
                    }
                )
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

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun CategoryChip(label: String, count: Int, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp)
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
private fun GroupedMemoryCard(
    displayName: String,
    memories: List<MemoryEntity>,
    onMarkAsRead: (Long) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showAllItems by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded, memories) {
        if (isExpanded) {
            memories.filter { !it.isRead }.forEach { onMarkAsRead(it.id) }
        }
    }

    val displayMemories = if (showAllItems) memories else memories.take(5)
    
    // Choose a pastel color based on the app name
    val cardColor = remember(displayName) {
        when {
            displayName.contains("mail", ignoreCase = true) || displayName.contains("outlook", ignoreCase = true) -> PastelBlue
            displayName.contains("calendar", ignoreCase = true) -> PastelGreen
            displayName.contains("todoist", ignoreCase = true) -> PastelRed
            displayName.contains("clipboard", ignoreCase = true) -> PastelYellow
            else -> PastelPurple
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${memories.size} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasUnread = memories.any { !it.isRead }
                    if (hasUnread) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            color = PastelRedText,
                            shape = RoundedCornerShape(4.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    displayMemories.forEachIndexed { index, memory ->
                        MemoryItem(memory, onMarkAsRead = onMarkAsRead)
                        if (index < displayMemories.size - 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    if (memories.size > 5) {
                        TextButton(
                            onClick = { showAllItems = !showAllItems },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(
                                text = if (showAllItems) "Show less" else "Show ${memories.size - 5} more...",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryItem(memory: MemoryEntity, onMarkAsRead: (Long) -> Unit) {
    var itemExpanded by remember { mutableStateOf(false) }
    val isLong = memory.content.length > 200 || memory.content.count { it == '\n' } > 5
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                onMarkAsRead(memory.id)
                if (!memory.deepLink.isNullOrEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(memory.deepLink))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
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
                    }
                }
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!memory.title.isNullOrEmpty()) {
                        Text(
                            text = memory.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (memory.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = memory.content,
                            fontSize = 14.sp,
                            maxLines = if (itemExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (memory.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (memory.duplicateCount > 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "x${memory.duplicateCount}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(memory.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                
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
                            if (itemExpanded) "Read less" else "Read more",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
