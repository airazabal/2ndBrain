package com.alex.a2ndbrain.ui.todoist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.core.todoist.TodoistCompletion
import java.text.SimpleDateFormat
import java.util.*

private val accentRed = Color(0xFFE44332)
private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoistScreen(
    completions: List<TodoistCompletion>,
    weeklyActivity: List<Pair<String, Int>>,
    todayCount: Int,
    weeklyCount: Int,
    totalCount: Int,
    todayMissedCount: Int = 0,
    weeklyMissedCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val today = remember { dateFmt.format(Date()) }
    val weekStart = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        dateFmt.format(cal.time)
    }

    val todayMissed  = remember(completions) {
        completions.filter { it.isMissed && it.date == today }.distinctBy { it.taskId }
    }
    val weeklyMissed = remember(completions) {
        completions.filter { it.isMissed && it.date >= weekStart }.distinctBy { it.taskId }
    }

    var skippedSheet by remember { mutableStateOf<List<TodoistCompletion>?>(null) }

    var listExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (listExpanded) 180f else 0f,
        label = "chevron"
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TodoistStatsRow(
                    todayCount = todayCount,
                    weeklyCount = weeklyCount,
                    totalCount = totalCount,
                    todayMissedCount = todayMissedCount,
                    weeklyMissedCount = weeklyMissedCount,
                    onTodaySkippedClick = { if (todayMissed.isNotEmpty()) skippedSheet = todayMissed },
                    onWeeklySkippedClick = { if (weeklyMissed.isNotEmpty()) skippedSheet = weeklyMissed }
                )
            }
            item {
                TodoistActivityBars(weeklyActivity = weeklyActivity)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { listExpanded = !listExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (completions.isEmpty()) "Completion History"
                               else "Completion History (${completions.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (listExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp).rotate(chevronRotation)
                    )
                }
            }
            if (listExpanded) {
                if (completions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No completed tasks yet",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(completions, key = { it.id }) { completion ->
                        TodoistCompletionItem(completion = completion)
                    }
                }
            }
        }

        skippedSheet?.let { missed ->
            ModalBottomSheet(
                onDismissRequest = { skippedSheet = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Skipped Tasks",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    missed.forEach { task ->
                        SkippedTaskItem(task = task)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoistStatsRow(
    todayCount: Int,
    weeklyCount: Int,
    totalCount: Int,
    todayMissedCount: Int = 0,
    weeklyMissedCount: Int = 0,
    onTodaySkippedClick: () -> Unit = {},
    onWeeklySkippedClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TodoistStatCard(
            title = "Today",
            value = if (todayCount == 0 && todayMissedCount == 0) "Nothing yet" else "$todayCount done",
            subtitle = if (todayMissedCount > 0) "$todayMissedCount skipped" else null,
            onSubtitleClick = onTodaySkippedClick,
            icon = Icons.Default.Today,
            modifier = Modifier.weight(1f)
        )
        TodoistStatCard(
            title = "This Week",
            value = if (weeklyCount == 0 && weeklyMissedCount == 0) "No tasks" else "$weeklyCount done",
            subtitle = if (weeklyMissedCount > 0) "$weeklyMissedCount skipped" else null,
            onSubtitleClick = onWeeklySkippedClick,
            icon = Icons.Default.CalendarViewWeek,
            modifier = Modifier.weight(1f)
        )
        TodoistStatCard(
            title = "All Time",
            value = "$totalCount tasks",
            icon = Icons.Default.EmojiEvents,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TodoistStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    subtitle: String? = null,
    onSubtitleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentRed,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 12.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    modifier = Modifier.clickable(onClick = onSubtitleClick)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun TodoistActivityBars(weeklyActivity: List<Pair<String, Int>>) {
    if (weeklyActivity.isEmpty()) return

    val maxCount = weeklyActivity.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "LAST 7 DAYS",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyActivity.forEach { (label, count) ->
                    val ratio = count.toFloat() / maxCount
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                fontSize = 9.sp,
                                color = accentRed,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(56.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(accentRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            )
                            if (ratio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(ratio)
                                        .background(accentRed, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoistCompletionItem(completion: TodoistCompletion) {
    val timeLabel = remember(completion.completedAt) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(completion.completedAt))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accentRed,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    completion.taskContent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    timeLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SkippedTaskItem(task: TodoistCompletion) {
    val dateLabel = remember(task.date) {
        runCatching {
            val parsed = dateFmt.parse(task.date)
            SimpleDateFormat("MMM d", Locale.getDefault()).format(parsed!!)
        }.getOrDefault(task.date)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.taskContent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    dateLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
