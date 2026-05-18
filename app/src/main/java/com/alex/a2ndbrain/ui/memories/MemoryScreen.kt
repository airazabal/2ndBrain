package com.alex.a2ndbrain.ui.memories

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(
    pagedMemories: LazyPagingItems<MemoryEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCaptureClipboard: () -> Unit,
    onMarkAsRead: (Long) -> Unit,
    onClearAll: () -> Unit,
    monitoredApps: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    var isScanning by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf("All") }

    // Group loaded non-null memories dynamically by category key: Pair(source, packageName)
    val itemsList = (0 until pagedMemories.itemCount).mapNotNull { index ->
        pagedMemories[index]
    }

    val groupedMemories = remember(itemsList, monitoredApps, selectedTag) {
        itemsList
            .filter { memory ->
                val matchesApp = if (monitoredApps.isEmpty() || memory.source != "notification") true
                                 else monitoredApps.contains(memory.packageName)
                val matchesTag = if (selectedTag == "All") true
                                 else memory.tags?.contains(selectedTag) == true
                matchesApp && matchesTag
            }
            .groupBy { Pair(it.source, it.packageName) }
            .toList()
            // Sort groups by the most recent timestamp in each group (descending)
            .sortedByDescending { (_, list) -> list.maxOfOrNull { it.timestamp } ?: 0L }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Adaptive Header Item
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Feed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        softWrap = false
                    )
                    if (isScanning) {
                        Text("Updating...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (configuration.screenWidthDp < 400) {
                        IconButton(onClick = onCaptureClipboard) {
                            Text("📋", fontSize = 16.sp)
                        }
                    } else {
                        TextButton(onClick = onCaptureClipboard) {
                            Text("CAPTURE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = {
                            isScanning = true
                            val scanIntent = Intent(context, com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java).apply {
                                action = "CHECK_ACTIVE"
                            }
                            context.startService(scanIntent)
                            // Reset state quickly for UI
                            isScanning = false
                        },
                        enabled = !isScanning,
                        contentPadding = PaddingValues(horizontal = if (configuration.screenWidthDp < 360) 2.dp else 4.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        } else {
                            Text("SCAN", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Search Field Item
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search captures...") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Text("✕")
                        }
                    }
                }
            )
        }

        // Horizontal Smart Folders Tag Chip row (Recommendation 3)
        item {
            val tags = listOf("All", "#Work", "#Health", "#Social", "#Reference", "#Finance")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(tags.size) { idx ->
                    val tag = tags[idx]
                    val isSelected = tag == selectedTag
                    
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                    val chipBgColor = if (isSelected) {
                        when (tag) {
                            "#Work" -> PastelRed.copy(alpha = 0.85f)
                            "#Health" -> PastelGreen.copy(alpha = 0.85f)
                            "#Social" -> PastelPurple.copy(alpha = 0.85f)
                            "#Reference" -> PastelYellow.copy(alpha = 0.85f)
                            "#Finance" -> PastelBlue.copy(alpha = 0.85f)
                            else -> primaryColor
                        }
                    } else {
                        surfaceVariantColor.copy(alpha = 0.35f)
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedTag = tag },
                        color = chipBgColor,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Main List Content
        if (pagedMemories.loadState.refresh is LoadState.Loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (pagedMemories.itemCount == 0) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isEmpty()) "No notifications captured yet." else "No matches found.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(groupedMemories.size) { index ->
                val (key, list) = groupedMemories[index]
                GroupedMemoryItem(
                    source = key.first,
                    packageName = key.second,
                    memories = list,
                    onMarkAsRead = onMarkAsRead
                )
            }
            
            if (pagedMemories.loadState.append is LoadState.Loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GroupedMemoryItem(
    source: String,
    packageName: String?,
    memories: List<MemoryEntity>,
    onMarkAsRead: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val displayName = remember(source, packageName) {
        val pm = context.packageManager
        val key = packageName ?: source
        try {
            val appInfo = pm.getApplicationInfo(key, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            if (key == "clipboard") "Clipboard" else key
        }
    }
    
    val cardColor = remember(displayName) {
        when {
            displayName.contains("mail", ignoreCase = true) || displayName.contains("outlook", ignoreCase = true) -> PastelBlue
            displayName.contains("calendar", ignoreCase = true) -> PastelGreen
            displayName.contains("todoist", ignoreCase = true) -> PastelRed
            displayName.contains("clipboard", ignoreCase = true) -> PastelYellow
            else -> PastelPurple
        }
    }

    val unreadCount = memories.count { !it.isRead }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                
                if (unreadCount > 0) {
                    Surface(
                        color = PastelRedText.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "$unreadCount new",
                            color = PastelRedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${memories.size} total",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // List of items inside this group (newest first), dynamically deduplicated for display
            val sortedMemories = remember(memories) {
                val list = memories.sortedByDescending { it.timestamp }
                val mergedList = mutableListOf<MemoryEntity>()
                for (item in list) {
                    val isChatApp = (item.packageName ?: "").contains("whatsapp") || 
                                    (item.packageName ?: "").contains("telegram") || 
                                    (item.packageName ?: "").contains("signal") || 
                                    (item.packageName ?: "").contains("discord") || 
                                    (item.packageName ?: "").contains("messenger") || 
                                    (item.packageName ?: "").contains("slack")
                    
                    val existingIdx = mergedList.indexOfFirst { existing ->
                        val similarity = calculateSimilarity(existing.content, item.content)
                        val titleSimilarity = calculateSimilarity(existing.title ?: "", item.title ?: "")
                        
                        when {
                            // Exact content match
                            existing.content == item.content -> true
                            // Chat app substring match
                            isChatApp && (existing.content.contains(item.content) || item.content.contains(existing.content)) -> true
                            // Fuzzy content similarity > 0.8
                            similarity > 0.8 && (existing.title == item.title || titleSimilarity > 0.8) -> true
                            // Prefix match
                            existing.content.take(15) == item.content.take(15) -> true
                            else -> false
                        }
                    }
                    if (existingIdx != -1) {
                        val existing = mergedList[existingIdx]
                        // Keep the newer one (mergedList holds the newer one since list is sorted newest first)
                        // Sum up duplicate counts
                        mergedList[existingIdx] = existing.copy(
                            duplicateCount = existing.duplicateCount + item.duplicateCount
                        )
                    } else {
                        mergedList.add(item)
                    }
                }
                mergedList
            }

            // Aggregated Heuristic Highlight (Recommendation 4)
            val groupHighlight = remember(sortedMemories) {
                val textContent = sortedMemories.joinToString(" ").lowercase()
                when {
                    textContent.contains("step") -> {
                        val stepsList = sortedMemories.mapNotNull { 
                            Regex("\\b\\d{1,3}(,\\d{3})*\\b").find(it.content)?.value?.replace(",", "")?.toIntOrNull()
                        }
                        if (stepsList.isNotEmpty()) {
                            "⚡ Active steps logged ${stepsList.size} times, peaking at ${stepsList.maxOrNull()} steps today."
                        } else {
                            "⚡ Tracked active step logs."
                        }
                    }
                    textContent.contains("heart") -> "⚡ Captured heart rate tracking records today."
                    textContent.contains("sleep") -> "⚡ Sleep cycles and rest logs successfully parsed."
                    textContent.contains("spent") || textContent.contains("transaction") || textContent.contains("amount") -> {
                        val amounts = sortedMemories.mapNotNull {
                            Regex("\\$\\d+(\\.\\d{2})?").find(it.content)?.value?.replace("$", "")?.toDoubleOrNull()
                        }
                        if (amounts.isNotEmpty()) {
                            "⚡ Logged ${amounts.size} payments totaling $${String.format(Locale.getDefault(), "%.2f", amounts.sum())}."
                        } else {
                            "⚡ Transaction card movements logged."
                        }
                    }
                    sortedMemories.size > 2 -> "⚡ Summarized ${sortedMemories.size} logs starting with \"${sortedMemories.last().content.take(30)}...\""
                    else -> null
                }
            }

            if (expanded && groupHighlight != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = groupHighlight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // By default, we show only the most recent entry if not expanded.
            val visibleMemories = if (expanded) sortedMemories else sortedMemories.take(1)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleMemories.forEachIndexed { idx, memory ->
                    if (idx > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    SingleMemoryRow(
                        memory = memory,
                        onMarkAsRead = onMarkAsRead,
                        context = context
                    )
                }
            }

            // Expand/Collapse Button if there is more than 1 entry
            if (sortedMemories.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Show all (${sortedMemories.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleMemoryRow(
    memory: MemoryEntity,
    onMarkAsRead: (Long) -> Unit,
    context: android.content.Context
) {
    var expanded by remember { mutableStateOf(false) }
    val isLong = memory.content.length > 200 || memory.content.count { it == '\n' } > 5

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
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(memory.packageName!!)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                    }
                } else if (memory.source == "notification" && !memory.packageName.isNullOrEmpty()) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(memory.packageName!!)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                if (!memory.title.isNullOrEmpty()) {
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (memory.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                SelectionContainer {
                    Text(
                        text = memory.content,
                        fontSize = 14.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (memory.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(horizontalAlignment = Alignment.End) {
                val timeStr = remember(memory.timestamp) {
                    val date = Date(memory.timestamp)
                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                    sdf.format(date)
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                
                if (memory.duplicateCount > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "x${memory.duplicateCount}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        
        if (isLong) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    text = if (expanded) "Show less" else "Read more",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun calculateSimilarity(s1: String, s2: String): Double {
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0
    val maxLen = maxOf(s1.length, s2.length)
    val distance = levenshteinDistance(s1, s2)
    return (maxLen - distance).toDouble() / maxLen.toDouble()
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    val len1 = s1.length
    val len2 = s2.length
    val dp = IntArray(len2 + 1) { it }
    for (i in 1..len1) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..len2) {
            val temp = dp[j]
            if (s1[i - 1] == s2[j - 1]) {
                dp[j] = prev
            } else {
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
            }
            prev = temp
        }
    }
    return dp[len2]
}
