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
    onOpenSettings: () -> Unit,
    onCaptureClipboard: () -> Unit,
    onMarkAsRead: (Long) -> Unit,
    onClearAll: () -> Unit,
    monitoredApps: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    var isScanning by remember { mutableStateOf(false) }

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

                    Spacer(modifier = Modifier.width(if (configuration.screenWidthDp < 360) 2.dp else 4.dp))

                    val setupWidth = (configuration.screenWidthDp * 0.18).coerceIn(60.0, 110.0).dp
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.width(setupWidth),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Setup", style = if (configuration.screenWidthDp < 360) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
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
            items(pagedMemories.itemCount) { index ->
                val memory = pagedMemories[index]
                if (memory != null) {
                    // Respect monitored apps if it's a notification
                    val shouldShow = if (monitoredApps.isEmpty() || memory.source != "notification") true
                                     else monitoredApps.contains(memory.packageName)
                                     
                    if (shouldShow) {
                        MemoryItem(memory = memory, onMarkAsRead = onMarkAsRead)
                    }
                }
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
private fun MemoryItem(memory: MemoryEntity, onMarkAsRead: (Long) -> Unit) {
    var itemExpanded by remember { mutableStateOf(false) }
    val isLong = memory.content.length > 200 || memory.content.count { it == '\n' } > 5
    val context = LocalContext.current
    
    val displayName = remember(memory) {
        val pm = context.packageManager
        val key = memory.packageName ?: memory.source
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
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                if (!memory.isRead) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        color = PastelRedText,
                        shape = RoundedCornerShape(4.dp)
                    ) {}
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
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
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(memory.timestamp)),
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
