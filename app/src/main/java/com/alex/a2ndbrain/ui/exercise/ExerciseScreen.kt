package com.alex.a2ndbrain.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import com.alex.a2ndbrain.core.exercise.ExerciseSessionEntity
import com.alex.a2ndbrain.core.exercise.ExerciseType
import java.text.SimpleDateFormat
import java.util.*

private val accentGreen = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    sessions: List<ExerciseSessionEntity>,
    weeklyConsistency: List<Pair<String, Float>>,
    todaySessionCount: Int,
    todayTotalMinutes: Int,
    weeklySessionCount: Int,
    weeklyTotalMinutes: Int,
    totalSessionCount: Int,
    showLogSheet: Boolean,
    selectedType: ExerciseType,
    durationMinutes: Int,
    notes: String,
    isLoading: Boolean,
    editingSession: ExerciseSessionEntity?,
    editSelectedType: ExerciseType,
    editDurationMinutes: Int,
    editNotes: String,
    onShowLogSheet: () -> Unit,
    onHideLogSheet: () -> Unit,
    onSelectType: (ExerciseType) -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetNotes: (String) -> Unit,
    onLogSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onEditSession: (ExerciseSessionEntity) -> Unit,
    onHideEditSheet: () -> Unit,
    onSetEditType: (ExerciseType) -> Unit,
    onSetEditDuration: (Int) -> Unit,
    onSetEditNotes: (String) -> Unit,
    onSaveEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sessionsExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (sessionsExpanded) 180f else 0f,
        label = "chevron"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ExerciseStatsRow(
                todaySessionCount = todaySessionCount,
                todayTotalMinutes = todayTotalMinutes,
                weeklySessionCount = weeklySessionCount,
                weeklyTotalMinutes = weeklyTotalMinutes,
                totalSessionCount = totalSessionCount
            )
        }
        item {
            Button(
                onClick = onShowLogSheet,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Log Session", fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            ExerciseConsistencyBars(consistency = weeklyConsistency)
        }
        // Collapsible sessions header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { sessionsExpanded = !sessionsExpanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (sessions.isEmpty()) "Sessions" else "Sessions (${sessions.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (sessionsExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp).rotate(chevronRotation)
                )
            }
        }
        if (sessionsExpanded) {
            if (sessions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No sessions logged yet",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    ExerciseSessionItem(
                        session = session,
                        onDelete = { onDeleteSession(session.id) },
                        onEdit = { onEditSession(session) }
                    )
                }
            }
        }
    }

    if (showLogSheet) {
        LogSessionBottomSheet(
            selectedType = selectedType,
            durationMinutes = durationMinutes,
            notes = notes,
            isLoading = isLoading,
            onDismiss = onHideLogSheet,
            onSelectType = onSelectType,
            onSetDuration = onSetDuration,
            onSetNotes = onSetNotes,
            onLogSession = onLogSession
        )
    }

    if (editingSession != null) {
        EditSessionBottomSheet(
            selectedType = editSelectedType,
            durationMinutes = editDurationMinutes,
            notes = editNotes,
            isLoading = isLoading,
            onDismiss = onHideEditSheet,
            onSelectType = onSetEditType,
            onSetDuration = onSetEditDuration,
            onSetNotes = onSetEditNotes,
            onSave = onSaveEdit
        )
    }
}

@Composable
private fun ExerciseStatsRow(
    todaySessionCount: Int,
    todayTotalMinutes: Int,
    weeklySessionCount: Int,
    weeklyTotalMinutes: Int,
    totalSessionCount: Int
) {
    fun minutesToLabel(mins: Int): String = when {
        mins == 0 -> "0m"
        mins >= 60 -> "${mins / 60}h ${mins % 60}m"
        else -> "${mins}m"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExerciseStatCard(
            title = "Today",
            value = if (todaySessionCount == 0) "Rest day"
                    else "${todaySessionCount} · ${minutesToLabel(todayTotalMinutes)}",
            icon = Icons.Default.Today,
            tint = accentGreen,
            modifier = Modifier.weight(1f)
        )
        ExerciseStatCard(
            title = "This Week",
            value = if (weeklySessionCount == 0) "No sessions"
                    else "${weeklySessionCount} · ${minutesToLabel(weeklyTotalMinutes)}",
            icon = Icons.Default.CalendarViewWeek,
            tint = accentGreen,
            modifier = Modifier.weight(1f)
        )
        ExerciseStatCard(
            title = "Total",
            value = "$totalSessionCount sessions",
            icon = Icons.Default.EmojiEvents,
            tint = accentGreen,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExerciseStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
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
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ExerciseConsistencyBars(consistency: List<Pair<String, Float>>) {
    if (consistency.isEmpty()) return

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
                consistency.forEach { (label, ratio) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(56.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Background track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(
                                        accentGreen.copy(alpha = 0.12f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                            // Fill bar
                            if (ratio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(ratio)
                                        .background(accentGreen, RoundedCornerShape(4.dp))
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
            Spacer(Modifier.height(4.dp))
            Text(
                "Full bar = 60+ min",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ExerciseSessionItem(
    session: ExerciseSessionEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val type = runCatching { ExerciseType.valueOf(session.type) }.getOrDefault(ExerciseType.OTHER)
    val dateLabel = runCatching {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val display = SimpleDateFormat("MMM d", Locale.getDefault())
        display.format(sdf.parse(session.date)!!)
    }.getOrDefault(session.date)

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
                imageVector = exerciseIcon(type),
                contentDescription = null,
                tint = accentGreen,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    type.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val hours = session.durationMinutes / 60
                val mins = session.durationMinutes % 60
                val durationStr = when {
                    hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                    hours > 0 -> "${hours}h"
                    else -> "${mins} min"
                }
                Text(
                    "$durationStr · $dateLabel",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (session.notes.isNotBlank()) {
                    Text(
                        session.notes,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit session",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete session",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSessionBottomSheet(
    selectedType: ExerciseType,
    durationMinutes: Int,
    notes: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectType: (ExerciseType) -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetNotes: (String) -> Unit,
    onLogSession: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Log Exercise Session",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Exercise type chips — 2 rows of 4
            ExerciseTypeChips(selected = selectedType, onSelect = onSelectType)

            // Duration slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Duration",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val h = durationMinutes / 60
                    val m = durationMinutes % 60
                    val label = when {
                        h > 0 && m > 0 -> "${h}h ${m}m"
                        h > 0 -> "${h}h"
                        else -> "${m} min"
                    }
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentGreen
                    )
                }
                Slider(
                    value = (durationMinutes / 5f),
                    onValueChange = { onSetDuration((it.toInt() * 5).coerceAtLeast(5)) },
                    valueRange = 1f..24f,  // steps: 5–120 min in 5-min increments (1*5=5 .. 24*5=120)
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = accentGreen,
                        activeTrackColor = accentGreen
                    )
                )
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = onSetNotes,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onLogSession()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Log Session", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSessionBottomSheet(
    selectedType: ExerciseType,
    durationMinutes: Int,
    notes: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectType: (ExerciseType) -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetNotes: (String) -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Edit Session",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            ExerciseTypeChips(selected = selectedType, onSelect = onSelectType)

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Duration",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val h = durationMinutes / 60
                    val m = durationMinutes % 60
                    val label = when {
                        h > 0 && m > 0 -> "${h}h ${m}m"
                        h > 0 -> "${h}h"
                        else -> "${m} min"
                    }
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentGreen
                    )
                }
                Slider(
                    value = (durationMinutes / 5f),
                    onValueChange = { onSetDuration((it.toInt() * 5).coerceAtLeast(5)) },
                    valueRange = 1f..24f,
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = accentGreen,
                        activeTrackColor = accentGreen
                    )
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = onSetNotes,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ExerciseTypeChips(
    selected: ExerciseType,
    onSelect: (ExerciseType) -> Unit
) {
    val types = ExerciseType.entries
    val firstRow = types.take(4)
    val secondRow = types.drop(4)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            firstRow.forEach { type ->
                ExerciseChip(
                    type = type,
                    isSelected = type == selected,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            secondRow.forEach { type ->
                ExerciseChip(
                    type = type,
                    isSelected = type == selected,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExerciseChip(
    type: ExerciseType,
    isSelected: Boolean,
    onSelect: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(type) },
        label = {
            Text(
                type.displayName,
                fontSize = 11.sp,
                maxLines = 1
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accentGreen.copy(alpha = 0.15f),
            selectedLabelColor = accentGreen,
            selectedLeadingIconColor = accentGreen
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            selectedBorderColor = accentGreen,
            selectedBorderWidth = 1.dp
        ),
        modifier = modifier
    )
}

private fun exerciseIcon(type: ExerciseType): ImageVector = when (type) {
    ExerciseType.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
    ExerciseType.RUNNING -> Icons.AutoMirrored.Filled.DirectionsRun
    ExerciseType.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    ExerciseType.SWIMMING -> Icons.Default.Pool
    ExerciseType.STRENGTH -> Icons.Default.FitnessCenter
    ExerciseType.STRETCHING -> Icons.Default.AccessibilityNew
    ExerciseType.HIIT -> Icons.Default.Whatshot
    ExerciseType.OTHER -> Icons.Default.SportsHandball
}
