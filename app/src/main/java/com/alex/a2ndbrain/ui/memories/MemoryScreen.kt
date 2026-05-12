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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.capture.CaptureDebugStore
import com.alex.a2ndbrain.core.memory.MemoryEntity
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sortOption by remember { mutableStateOf(MemorySortOption.USAGE) }
    var unreadOnly by remember { mutableStateOf(false) }

    val filteredByRead = remember(memories, unreadOnly) {
        if (unreadOnly) memories.filter { !it.isRead } else memories
    }

    Column(
        modifier = modifier
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Memories",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
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
                        if (unreadOnly) "No unread memories!" else "No memories captured yet."
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
private fun MemoryItem(memory: MemoryEntity, onMarkAsRead: (Long) -> Unit) {
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
            }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(
                        if (!memory.isRead) MaterialTheme.colorScheme.primary else Color.Transparent,
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
