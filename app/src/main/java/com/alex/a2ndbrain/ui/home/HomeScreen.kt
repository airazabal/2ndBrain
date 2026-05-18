package com.alex.a2ndbrain.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.ui.theme.*
import com.alex.a2ndbrain.ui.usage.ConsolidatedUsage
import com.alex.a2ndbrain.ui.usage.UsageBarChart

@Composable
fun HomeScreen(
    memories: List<MemoryEntity>,
    latestReflection: DailySummaryEntity?,
    notes: List<DocumentFile>,
    usageStats: List<UsageStatEntity>,
    onNavigateToTab: (Int) -> Unit,
    healthMetrics: com.alex.a2ndbrain.core.health.HealthMetrics = com.alex.a2ndbrain.core.health.HealthMetrics(),
    healthPermissionGranted: Boolean = false,
    healthConnectAvailable: Boolean = false,
    onConnectHealth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val unreadCount = remember(memories) { memories.count { !it.isRead } }
    val appCount = remember(memories) {
        memories.filter { it.source == "notification" }
            .mapNotNull { it.packageName }
            .distinct().size
    }

    val consolidatedUsage = remember(usageStats) {
        usageStats.groupBy { it.packageName }
            .map { (packageName, stats) ->
                val totalTime = stats.sumOf { it.totalTimeVisibleMs }
                ConsolidatedUsage(
                    packageName = packageName,
                    totalTimeMs = totalTime,
                    deviceBreakdown = emptyMap(), // Not needed for home chart
                    lastTimestamp = stats.maxOf { it.lastTimestamp }
                )
            }.sortedByDescending { it.totalTimeMs }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            SummaryGrid(
                totalFeed = memories.size,
                unreadFeed = unreadCount,
                appsCount = appCount,
                onCardClick = { onNavigateToTab(1) } // Feed is now index 1
            )
        }

        // Smartwatch Health Connect Card (Recommendation 6)
        if (healthConnectAvailable) {
            item {
                HomeSectionCard(
                    title = "Smartwatch Wellness (Health Connect)",
                    icon = Icons.Default.Favorite,
                    iconColor = PastelGreen,
                    onClick = { if (!healthPermissionGranted) onConnectHealth() }
                ) {
                    if (healthPermissionGranted) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Steps Row
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Steps Today",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${healthMetrics.steps} / 10,000",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val progress = (healthMetrics.steps.toFloat() / 10000f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PastelGreen,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }

                            // Sleep & Heart Rate Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Sleep Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Last Sleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val h = healthMetrics.sleepMinutes / 60
                                        val m = healthMetrics.sleepMinutes % 60
                                        Text(
                                            text = if (healthMetrics.sleepMinutes > 0) "${h}h ${m}m" else "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Heart Rate Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Heart Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (healthMetrics.avgHeartRate > 0) "${healthMetrics.avgHeartRate} BPM" else "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Synchronize physical health logs (Steps, Sleep, and Heart Rate) generated by your Zepp watch and other devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = onConnectHealth,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Health Connect", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        if (latestReflection != null) {
            item {
                HomeSectionCard(
                    title = "Latest Intelligence",
                    icon = Icons.Default.AutoAwesome,
                    iconColor = PastelBlue,
                    onClick = { onNavigateToTab(2) } // Reflection is now index 2
                ) {
                    Text(
                        text = latestReflection.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            HomeSectionCard(
                title = "Recent Notes",
                icon = Icons.Default.Description,
                iconColor = PastelGreen,
                onClick = { onNavigateToTab(3) } // Notes is now index 3
            ) {
                if (notes.isEmpty()) {
                    Text("No notes found.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notes.take(3).forEach { note ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = note.name?.removeSuffix(".md") ?: "Untitled",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (notes.size > 3) {
                            Text(
                                text = "+ ${notes.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        if (consolidatedUsage.isNotEmpty()) {
            item {
                HomeSectionCard(
                    title = "Screen Time",
                    icon = Icons.Default.Schedule,
                    iconColor = PastelPurple,
                    onClick = { onNavigateToTab(4) } // Digital Time is now index 4
                ) {
                    UsageBarChart(consolidatedUsage.take(3))
                }
            }
        }
    }
}

@Composable
fun SummaryGrid(
    totalFeed: Int,
    unreadFeed: Int,
    appsCount: Int,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem(label = "Total", value = totalFeed.toString(), color = PastelBlue)
            SummaryItem(label = "Unread", value = unreadFeed.toString(), color = PastelRed)
            SummaryItem(label = "Apps", value = appsCount.toString(), color = PastelGreen)
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun HomeSectionCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
