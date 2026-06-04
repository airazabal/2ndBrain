package com.alex.a2ndbrain.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardHeader(
    lastRefreshedAt: Long,
    refreshIntervalMinutes: Int,
    onRefreshIntervalChange: (Int) -> Unit,
    overdueCount: Int,
    unreadEmailCount: Int,
    meetingsCount: Int,
    unreadMessageCount: Int,
    steps: Long = 0L,
    sleepMinutes: Int = 0,
    avgHeartRate: Int = 0,
    onOverdueClick: () -> Unit,
    onEmailClick: () -> Unit,
    onMeetingsClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onHealthClick: () -> Unit = {},
    exerciseSessionsThisWeek: Int = 0,
    exerciseTotalMinutesThisWeek: Int = 0,
    onExerciseClick: () -> Unit = {},
    themePreference: String = "SYSTEM",
    onThemeToggle: () -> Unit = {},
    senseOfDayScore: Int = 0,
    senseOfDayContext: String = "",
    senseOfDayPillars: List<SenseOfDayPillar> = emptyList(),
    onPillarClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Top row: greeting (left) + refresh info (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = greeting(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Text(
                    text = "Your 2nd Brain · Tap any card to open",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Last refreshed ${formatRefreshTime(lastRefreshedAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    IconButton(onClick = onThemeToggle, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (themePreference == "DARK") Icons.Outlined.WbSunny else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle theme",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                RefreshChip(intervalMinutes = refreshIntervalMinutes, onIntervalChange = onRefreshIntervalChange)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "v${com.alex.a2ndbrain.BuildConfig.VERSION_NAME}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Sense of Day widget
        SenseOfDayWidget(
            score = senseOfDayScore,
            context = senseOfDayContext,
            pillars = senseOfDayPillars,
            onPillarClick = onPillarClick
        )

        Spacer(Modifier.height(12.dp))

        // Cards row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashCard(
                label = "OVERDUE\nACTIONS",
                value = overdueCount.toString(),
                subtitle = if (overdueCount == 0) "All caught up" else "$overdueCount past due",
                accentColor = Color(0xFFEF5350),
                icon = Icons.Default.Warning,
                onClick = onOverdueClick
            )
            DashCard(
                label = "UNREAD\nEMAIL",
                value = if (unreadEmailCount > 99) "99+" else unreadEmailCount.toString(),
                subtitle = "tap to review",
                accentColor = Color(0xFFFF9800),
                icon = Icons.Default.Email,
                onClick = onEmailClick
            )
            DashCard(
                label = "TASKS\nTODAY",
                value = meetingsCount.toString(),
                subtitle = if (meetingsCount == 0) "Nothing on schedule" else "$meetingsCount on schedule",
                accentColor = Color(0xFF1E88E5),
                icon = Icons.Default.CalendarMonth,
                onClick = onMeetingsClick
            )
            DashCard(
                label = "UNREAD\nMESSAGES",
                value = if (unreadMessageCount > 99) "99+" else unreadMessageCount.toString(),
                subtitle = "WhatsApp · SMS",
                accentColor = Color(0xFF26A69A),
                icon = Icons.Default.Chat,
                onClick = onMessagesClick
            )
            val sleepH = sleepMinutes / 60
            val sleepM = sleepMinutes % 60
            DashCard(
                label = "HEALTH\nTODAY",
                value = if (steps > 0L) steps.toString() else "--",
                subtitle = buildString {
                    if (sleepMinutes > 0) append("${sleepH}h ${sleepM}m sleep")
                    if (sleepMinutes > 0 && avgHeartRate > 0) append(" · ")
                    if (avgHeartRate > 0) append("${avgHeartRate} BPM")
                    if (sleepMinutes == 0 && avgHeartRate == 0) append("steps today")
                },
                accentColor = Color(0xFF7E57C2),
                icon = Icons.Default.Favorite,
                onClick = onHealthClick
            )
            DashCard(
                label = "EXERCISE\nTHIS WEEK",
                value = exerciseSessionsThisWeek.toString(),
                subtitle = if (exerciseSessionsThisWeek == 0) "No sessions logged"
                           else "${exerciseTotalMinutesThisWeek} min total",
                accentColor = Color(0xFF43A047),
                icon = Icons.Default.FitnessCenter,
                onClick = onExerciseClick
            )
        }
    }
}

@Composable
private fun DashCard(
    label: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 12.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = value,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun RefreshChip(intervalMinutes: Int, onIntervalChange: (Int) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val options = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours")
    val label = options.firstOrNull { it.first == intervalMinutes }?.second ?: "30 min"

    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { showMenu = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Refreshes every $label",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            options.forEach { (min, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = { onIntervalChange(min); showMenu = false },
                    leadingIcon = if (intervalMinutes == min) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

private fun formatRefreshTime(timestampMs: Long): String {
    val diffMin = (System.currentTimeMillis() - timestampMs) / 60_000
    return when {
        diffMin < 1 -> "just now"
        diffMin < 60 -> "$diffMin min ago"
        else -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
    }
}
