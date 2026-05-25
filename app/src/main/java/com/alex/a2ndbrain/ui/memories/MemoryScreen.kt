package com.alex.a2ndbrain.ui.memories

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import com.alex.a2ndbrain.AppCaptureSettingsActivity
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
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import android.app.Activity
import android.widget.Toast
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import java.io.File

@Composable
fun MemoryScreen(
    memories: List<MemoryEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCaptureClipboard: () -> Unit,
    onMarkAsRead: (List<Long>) -> Unit,
    onMarkAsUnread: (List<Long>) -> Unit = {},
    onClearAll: () -> Unit,
    monitoredApps: Set<String> = emptySet(),
    onClearAppFilter: () -> Unit = {},
    initialFilter: String = "All",
    vaultUri: String = "",
    onSaveVoiceNote: ((String, String) -> Unit)? = null,
    onDeepDiveCoPilot: (MemoryEntity) -> Unit = {},
    onNotesSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    var isScanning by remember { mutableStateOf(false) }
    var selectedApp by remember(initialFilter) { mutableStateOf(initialFilter) }
    var showRecordingDialog by remember { mutableStateOf(false) }
    var expandedAppGroups by remember { mutableStateOf(setOf<String>()) }
    var expandedDays by remember { mutableStateOf(setOf<String>("Today")) }
    var localReadOverrides by remember { mutableStateOf(mapOf<Long, Boolean>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showRecordingDialog = true
        } else {
            Toast.makeText(context, "Audio recording permission is required to record voice notes.", Toast.LENGTH_SHORT).show()
        }
    }

    // All distinct app names in the full memory list — chips always show every app, not just monitored ones
    val availableApps = remember(memories) {
        memories
            .map { m -> getAppName(context, m.packageName, m.source) }
            .distinct()
            .sorted()
    }

    // Reset selectedApp only when memories are loaded and the app is genuinely gone
    if (selectedApp != "All" && memories.isNotEmpty() && selectedApp !in availableApps) {
        selectedApp = "All"
    }

    val dayGroups = remember(memories, monitoredApps, selectedApp, localReadOverrides) {
        val filtered = memories.map { memory ->
            val override = localReadOverrides[memory.id]
            if (override != null) memory.copy(isRead = override) else memory
        }.filter { memory ->
            // "All" shows every captured notification; individual chips respect the monitored filter
            val matchesMonitored = selectedApp == "All" ||
                monitoredApps.isEmpty() ||
                memory.source != "notification" ||
                monitoredApps.contains(memory.packageName)
            val matchesAppFilter = selectedApp == "All" || getAppName(context, memory.packageName, memory.source) == selectedApp
            matchesMonitored && matchesAppFilter
        }

        val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

        fun buildDayGroup(label: String, dateStart: Long, rawMemories: List<MemoryEntity>): DayGroup {
            val sorted = rawMemories.sortedByDescending { it.timestamp }
            val merged = deduplicateMemories(sorted)
            val grouped = merged.groupBy { item ->
                getAppName(context, item.primary.packageName, item.primary.source)
            }
            val appGroups = grouped.map { (appName, items) ->
                val rep = items.first().primary
                AppGroup(appName = appName, packageName = rep.packageName, source = rep.source, memories = items)
            }.sortedByDescending { appGroup -> appGroup.memories.maxOf { it.primary.timestamp } }
            return DayGroup(
                label = label, dateStart = dateStart, appGroups = appGroups,
                memories = sorted, unreadCount = merged.count { !it.primary.isRead }
            )
        }

        // Bucket memories into day slots 0..6 (today = 0, yesterday = 1, …)
        val buckets = Array(7) { mutableListOf<MemoryEntity>() }
        val dayBoundaries = Array(7) { getStartOfDay(it) }

        for (m in filtered) {
            val slot = (0..5).firstOrNull { m.timestamp >= dayBoundaries[it] } ?: continue
            buckets[slot].add(m)
        }

        val groups = mutableListOf<DayGroup>()
        buckets.forEachIndexed { i, dayMemories ->
            if (dayMemories.isEmpty()) return@forEachIndexed
            val label = when (i) {
                0 -> "Today"
                1 -> "Yesterday"
                else -> sdf.format(Date(dayBoundaries[i]))
            }
            groups.add(buildDayGroup(label, dayBoundaries[i], dayMemories))
        }
        groups
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasPermission) showRecordingDialog = true
                        else permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Quick Voice Capture",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    TextButton(
                        onClick = {
                            context.startActivity(Intent(context, AppCaptureSettingsActivity::class.java))
                        }
                    ) {
                        Text("SETUP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }

                    var showMenu by remember { mutableStateOf(false) }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Capture Clipboard") },
                                leadingIcon = { Text("📋", fontSize = 16.sp) },
                                onClick = {
                                    showMenu = false
                                    onCaptureClipboard()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isScanning) "Scanning..." else "Scan Active") },
                                leadingIcon = {
                                    if (isScanning) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                enabled = !isScanning,
                                onClick = {
                                    showMenu = false
                                    isScanning = true
                                    val scanIntent = Intent(context, com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java).apply {
                                        action = "CHECK_ACTIVE"
                                    }
                                    context.startService(scanIntent)
                                    isScanning = false
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = "Clear Feed",
                                        color = MaterialTheme.colorScheme.error
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onClearAll()
                                }
                            )
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

        // Dynamic app filter chip row
        item {
            val chips = listOf("All") + availableApps
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(chips.size) { idx ->
                    val chip = chips[idx]
                    val isSelected = chip == selectedApp
                    val primary = MaterialTheme.colorScheme.primary
                    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedApp = chip },
                        color = if (isSelected) primary else surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = chip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                item {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onNotesSelected() },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Notes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // App filter active banner
        if (monitoredApps.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${monitoredApps.size} app${if (monitoredApps.size == 1) "" else "s"} filtered — some notifications hidden.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onClearAppFilter,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Show all", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Main List Content
        if (memories.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isEmpty()) "No notifications captured yet." else "No matches found.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            dayGroups.forEach { dayGroup ->
                val dayKey = dayGroup.label
                val isDayExpanded = expandedDays.contains(dayKey)

                item(key = "header_${dayGroup.label}") {
                    DayHeaderRow(
                        label = dayGroup.label,
                        unreadCount = dayGroup.unreadCount,
                        isExpanded = isDayExpanded,
                        onToggleExpand = {
                            expandedDays = if (isDayExpanded) {
                                expandedDays - dayKey
                            } else {
                                expandedDays + dayKey
                            }
                        },
                        onMarkDayAsRead = {
                            val unreadIds = dayGroup.memories.filter { !it.isRead }.map { it.id }
                            if (unreadIds.isNotEmpty()) {
                                localReadOverrides = localReadOverrides + unreadIds.associateWith { true }
                                onMarkAsRead(unreadIds)
                            }
                        }
                    )
                }

                if (isDayExpanded) {
                    dayGroup.appGroups.forEach { appGroup ->
                        val groupKey = "${dayGroup.label}_${appGroup.appName}"
                        val isExpanded = expandedAppGroups.contains(groupKey)

                        item(key = "app_header_${dayGroup.label}_${appGroup.appName}") {
                            AppGroupHeader(
                                appName = appGroup.appName,
                                source = appGroup.source,
                                packageName = appGroup.packageName,
                                unreadCount = appGroup.memories.count { !it.isRead },
                                totalCount = appGroup.memories.size,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedAppGroups = if (isExpanded) {
                                        expandedAppGroups - groupKey
                                    } else {
                                        expandedAppGroups + groupKey
                                    }
                                },
                                onMarkAppAsRead = {
                                    val unreadIds = appGroup.memories.filter { !it.isRead }.flatMap { it.allIds }
                                    if (unreadIds.isNotEmpty()) {
                                        localReadOverrides = localReadOverrides + unreadIds.associateWith { true }
                                        onMarkAsRead(unreadIds)
                                    }
                                }
                            )
                        }

                        if (isExpanded) {
                            items(
                                count = appGroup.memories.size,
                                key = { idx -> "memory_${appGroup.memories[idx].primary.id}" }
                            ) { idx ->
                                val merged = appGroup.memories[idx]
                                MemoryCard(
                                    merged = merged,
                                    onMarkAsRead = { ids ->
                                        localReadOverrides = localReadOverrides + ids.associateWith { true }
                                        onMarkAsRead(ids)
                                    },
                                    onMarkAsUnread = { ids ->
                                        localReadOverrides = localReadOverrides + ids.associateWith { false }
                                        onMarkAsUnread(ids)
                                    },
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
        
    }

    if (showRecordingDialog) {
        VoiceRecordingDialog(
            onDismiss = { showRecordingDialog = false },
            onFinished = { transcript, audioPath ->
                showRecordingDialog = false
                onSaveVoiceNote?.invoke(transcript, audioPath)
            }
        )
    }
}

data class AppGroup(
    val appName: String,
    val packageName: String?,
    val source: String,
    val memories: List<MergedMemory>
)

data class DayGroup(
    val label: String,
    val dateStart: Long,
    val appGroups: List<AppGroup>,
    val memories: List<MemoryEntity>,
    val unreadCount: Int
)

data class MergedMemory(
    val primary: MemoryEntity,
    val allIds: List<Long>,
    val duplicateCount: Int,
    val isRead: Boolean
)

private fun getStartOfDay(daysAgo: Int): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
    return cal.timeInMillis
}

private fun deduplicateMemories(memories: List<MemoryEntity>): List<MergedMemory> {
    val sorted = memories.sortedByDescending { it.timestamp }
    val mergedList = mutableListOf<MergedMemory>()
    
    for (item in sorted) {
        val isChatApp = (item.packageName ?: "").contains("whatsapp") || 
                        (item.packageName ?: "").contains("telegram") || 
                        (item.packageName ?: "").contains("signal") || 
                        (item.packageName ?: "").contains("discord") || 
                        (item.packageName ?: "").contains("messenger") || 
                        (item.packageName ?: "").contains("slack")
        
        val existingIdx = if (item.source == "voice") -1 else mergedList.indexOfFirst { merged ->
            val existing = merged.primary
            if (existing.source != item.source || existing.packageName != item.packageName) {
                return@indexOfFirst false
            }
            
            val similarity = calculateSimilarity(existing.content, item.content)
            val titleSimilarity = calculateSimilarity(existing.title ?: "", item.title ?: "")
            
            val existingLines = existing.content.split("\n").filter { it.isNotBlank() }
            val itemLines = item.content.split("\n").filter { it.isNotBlank() }
            val hasLineOverlap = if (existingLines.isNotEmpty() && itemLines.isNotEmpty()) {
                val intersection = existingLines.intersect(itemLines.toSet())
                (intersection.size.toFloat() / itemLines.size.toFloat()) > 0.5f
            } else false
            
            val isGmailSummary = (existing.packageName ?: "").contains("gm") && 
                                 (existing.title ?: "").contains("messages") && 
                                 (item.title ?: "").contains("messages")
            
            when {
                // Exact content match
                existing.content == item.content -> true
                // Chat app substring match
                isChatApp && (existing.content.contains(item.content) || item.content.contains(existing.content)) -> true
                // Fuzzy content similarity > 0.8
                similarity > 0.8 && (existing.title == item.title || titleSimilarity > 0.8) -> true
                // Prefix match (only for non-voice)
                existing.source != "voice" && existing.content.take(15) == item.content.take(15) -> true
                // Line overlap for group summaries/conversations
                hasLineOverlap || isGmailSummary -> true
                else -> false
            }
        }
        
        if (existingIdx != -1) {
            val merged = mergedList[existingIdx]
            mergedList[existingIdx] = merged.copy(
                allIds = merged.allIds + item.id,
                duplicateCount = merged.duplicateCount + item.duplicateCount,
                isRead = merged.isRead && item.isRead
            )
        } else {
            mergedList.add(
                MergedMemory(
                    primary = item,
                    allIds = listOf(item.id),
                    duplicateCount = item.duplicateCount,
                    isRead = item.isRead
                )
            )
        }
    }
    return mergedList
}

@Composable
private fun DayHeaderRow(
    label: String,
    unreadCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMarkDayAsRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggleExpand() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse Day" else "Expand Day",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (unreadCount > 0) {
                Surface(
                    color = PastelRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$unreadCount unread",
                        color = PastelRedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "All read",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        if (unreadCount > 0) {
            TextButton(
                onClick = onMarkDayAsRead,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Mark read",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AppGroupHeader(
    appName: String,
    source: String,
    packageName: String?,
    unreadCount: Int,
    totalCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMarkAppAsRead: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val chipColor = when (source) {
        "clipboard" -> PastelPurple
        "voice" -> PastelGreen
        else -> when {
            packageName?.contains("gmail") == true -> PastelRed
            packageName?.contains("whatsapp") == true -> PastelGreen
            packageName?.contains("messaging") == true -> PastelBlue
            packageName?.contains("slack") == true -> PastelOrange
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    }
    val chipTextColor = when (source) {
        "clipboard" -> PastelPurpleText
        "voice" -> PastelGreenText
        else -> when {
            packageName?.contains("gmail") == true -> PastelRedText
            packageName?.contains("whatsapp") == true -> PastelGreenText
            packageName?.contains("messaging") == true -> PastelBlueText
            packageName?.contains("slack") == true -> PastelOrangeText
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggleExpand() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = chipColor,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = appName,
                color = chipTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            text = if (unreadCount > 0) "$unreadCount unread" else "$totalCount messages",
            style = MaterialTheme.typography.labelSmall,
            color = if (unreadCount > 0) PastelRedText else MaterialTheme.colorScheme.outline,
            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
        )
        if (unreadCount > 0 && onMarkAppAsRead != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = {
                    onMarkAppAsRead()
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    text = "Mark read",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun getAppName(context: android.content.Context, packageName: String?, source: String): String {
    if (source == "clipboard") return "Clipboard"
    if (source == "voice") return "Voice Notes"
    if (packageName.isNullOrEmpty()) return "Notification"
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        val lowerPkg = packageName.lowercase()
        when {
            lowerPkg.contains("gmail") -> "Gmail"
            lowerPkg.contains("whatsapp") -> "WhatsApp"
            lowerPkg.contains("messaging") -> "Messages"
            lowerPkg.contains("slack") -> "Slack"
            lowerPkg.contains("telegram") -> "Telegram"
            lowerPkg.contains("discord") -> "Discord"
            lowerPkg.contains("twitter") || lowerPkg.contains("x.android") -> "X"
            lowerPkg.contains("instagram") -> "Instagram"
            lowerPkg.contains("facebook") -> "Facebook"
            else -> packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }
}

private fun getQuickSummary(content: String): String {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return ""
    
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        val cleanUrl = trimmed.substringBefore("?")
        return "Link: $cleanUrl"
    }

    val lines = trimmed.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (lines.isEmpty()) return ""

    val firstLine = lines.first()

    val combinedText = if (firstLine.length < 15 && lines.size > 1) {
        "$firstLine: ${lines[1]}"
    } else {
        firstLine
    }

    return if (combinedText.length > 70) {
        combinedText.take(67) + "..."
    } else {
        combinedText
    }
}

@Composable
private fun MemoryCard(
    merged: MergedMemory,
    onMarkAsRead: (List<Long>) -> Unit,
    onMarkAsUnread: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val memory = merged.primary
    val context = LocalContext.current

    val canLaunch = (memory.source == "notification" && !memory.packageName.isNullOrEmpty()) || !memory.deepLink.isNullOrEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (!merged.isRead) {
                    onMarkAsRead(merged.allIds)
                }
                if (canLaunch) {
                    if (!memory.deepLink.isNullOrEmpty()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(memory.deepLink))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            if (!memory.packageName.isNullOrEmpty()) {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(memory.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            }
                        }
                    } else if (!memory.packageName.isNullOrEmpty()) {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(memory.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        } catch (_: Exception) {}
                    }
                } else if (memory.source == "clipboard") {
                    try {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("2ndBrain Copy", memory.content)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Time, Duplicate Badge, Unread Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                if (merged.duplicateCount > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "x${merged.duplicateCount}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (merged.isRead) {
                                onMarkAsUnread(merged.allIds)
                            } else {
                                onMarkAsRead(merged.allIds)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!merged.isRead) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(4.5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .border(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(4.5.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (!memory.title.isNullOrEmpty()) {
                Text(
                    text = memory.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (merged.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            val summary = remember(memory.content) { getQuickSummary(memory.content) }
            Text(
                text = summary,
                fontSize = 13.sp,
                style = MaterialTheme.typography.bodyMedium,
                color = if (merged.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (memory.source == "voice" && !memory.deepLink.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AudioPlayerControl(
                    audioPath = memory.deepLink,
                    modifier = Modifier.padding(top = 4.dp)
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

@Composable
fun VoiceRecordingDialog(
    onDismiss: () -> Unit,
    onFinished: (String, String) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(true) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var transcribedText by remember { mutableStateOf("") }
    
    // File to save the original high-fidelity audio
    val audioFile = remember {
        val dir = File(context.filesDir, "audio")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "voice-memo-${System.currentTimeMillis()}.m4a")
    }

    // MediaRecorder setup
    val mediaRecorder = remember {
        android.media.MediaRecorder().apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
        }
    }

    // SpeechRecognizer setup
    val speechRecognizer = remember { android.speech.SpeechRecognizer.createSpeechRecognizer(context) }
    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // Timer and active listening lifecycles
    LaunchedEffect(Unit) {
        // Start MediaRecorder
        try {
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (e: Exception) {
            android.util.Log.e("2ndBrain", "Failed to start MediaRecorder", e)
        }

        // Setup and start SpeechRecognizer
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                android.util.Log.e("2ndBrain", "SpeechRecognizer error: $error")
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    transcribedText = text
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    transcribedText = text
                }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        speechRecognizer.startListening(recognizerIntent)

        // Increment timer
        while (isRecording) {
            kotlinx.coroutines.delay(1000)
            secondsElapsed++
        }
    }

    // Release native resources safely
    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer.destroy()
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) {
                isRecording = false
                try {
                    mediaRecorder.stop()
                    mediaRecorder.release()
                } catch (_: Exception) {}
            }
            onDismiss()
        },
        confirmButton = {
            Button(
                onClick = {
                    isRecording = false
                    try {
                        mediaRecorder.stop()
                        mediaRecorder.release()
                    } catch (_: Exception) {}
                    
                    try {
                        speechRecognizer.stopListening()
                    } catch (_: Exception) {}

                    val finalTranscript = transcribedText.ifBlank {
                        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                        "Voice Note captured on ${sdf.format(Date())}"
                    }
                    onFinished(finalTranscript, audioFile.absolutePath)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Stop & Save", color = Color.White)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PastelRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Listening to your thoughts...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val mins = secondsElapsed / 60
                val secs = secondsElapsed % 60
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Show dynamic transcript box as they speak
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = transcribedText.ifBlank { "Start speaking to record a transcript..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (transcribedText.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun AudioPlayerControl(
    audioPath: String,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    val context = LocalContext.current
    val mediaPlayer = remember { android.media.MediaPlayer() }

    // Initialize MediaPlayer safely
    LaunchedEffect(audioPath) {
        try {
            mediaPlayer.reset()
            if (audioPath.startsWith("content://")) {
                mediaPlayer.setDataSource(context, android.net.Uri.parse(audioPath))
            } else {
                mediaPlayer.setDataSource(audioPath)
            }
            mediaPlayer.prepare()
            duration = mediaPlayer.duration
            
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentPosition = 0
                mediaPlayer.seekTo(0)
            }
        } catch (e: Exception) {
            android.util.Log.e("2ndBrain", "Failed to initialize MediaPlayer for $audioPath", e)
        }
    }

    // Periodically update progress slider
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer.currentPosition
            kotlinx.coroutines.delay(200)
        }
    }

    // Safe lifecycle resource release
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    // Set playback speed
    LaunchedEffect(playbackSpeed) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                if (isPlaying) {
                    mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
                }
            } catch (_: Exception) {}
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Play/Pause Button
        IconButton(
            onClick = {
                try {
                    if (isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    } else {
                        // Apply speed before playing
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
                        }
                        mediaPlayer.start()
                        isPlaying = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("2ndBrain", "MediaPlayer play/pause failed", e)
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 2. Progressive Progress Bar / Slider
        val displayPos = currentPosition.coerceIn(0, duration)
        Slider(
            value = if (duration > 0) displayPos.toFloat() / duration.toFloat() else 0f,
            onValueChange = { fraction ->
                if (duration > 0) {
                    val dest = (fraction * duration).toInt()
                    currentPosition = dest
                    mediaPlayer.seekTo(dest)
                }
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 3. Current elapsed time / Duration (e.g. 0:05 / 0:22)
        val posMins = displayPos / 1000 / 60
        val posSecs = (displayPos / 1000) % 60
        val durMins = duration / 1000 / 60
        val durSecs = (duration / 1000) % 60
        
        Text(
            text = String.format(Locale.getDefault(), "%d:%02d / %d:%02d", posMins, posSecs, durMins, durSecs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 4. Playback Speed Selector (Cycles: 1x -> 1.5x -> 2x)
        TextButton(
            onClick = {
                playbackSpeed = when (playbackSpeed) {
                    1.0f -> 1.5f
                    1.5f -> 2.0f
                    else -> 1.0f
                }
            },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.width(44.dp)
        ) {
            Text(
                text = "${playbackSpeed}x",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
