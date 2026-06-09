package com.alex.a2ndbrain.ui.goals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.core.goals.GoalProgress
import com.alex.a2ndbrain.core.goals.GoalTrend
import com.alex.a2ndbrain.core.goals.GoalType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (state.progresses.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.TrackChanges, contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))
                Text("No goals yet", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                Spacer(Modifier.height(6.dp))
                Text("Tap + to set a multi-week target", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
            ) {
                item {
                    Text(
                        "${state.progresses.count { it.trend == GoalTrend.AHEAD || it.trend == GoalTrend.ON_TRACK }} of ${state.progresses.size} goals on track",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                items(state.progresses, key = { it.goal.id }) { progress ->
                    GoalCard(
                        progress = progress,
                        onEdit = { viewModel.showEditSheet(progress.goal) },
                        onDelete = { viewModel.deleteGoal(progress.goal.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = viewModel::showAddSheet,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add goal")
        }
    }

    if (state.showSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::hideSheet) {
            GoalAddSheet(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun GoalCard(progress: GoalProgress, onEdit: () -> Unit, onDelete: () -> Unit) {
    val trendColor = when (progress.trend) {
        GoalTrend.AHEAD    -> Color(0xFF388E3C)
        GoalTrend.ON_TRACK -> Color(0xFF1E88E5)
        GoalTrend.BEHIND   -> Color(0xFFF57C00)
        GoalTrend.CRITICAL -> Color(0xFFC62828)
    }
    val trendLabel = when (progress.trend) {
        GoalTrend.AHEAD    -> "Ahead"
        GoalTrend.ON_TRACK -> "On Track"
        GoalTrend.BEHIND   -> "Behind"
        GoalTrend.CRITICAL -> "Critical"
    }
    val typeEmoji = when (progress.goal.goalType) {
        GoalType.EXERCISE_SESSIONS -> "🏋️"
        GoalType.HABIT_COMPLETION  -> "✅"
        GoalType.STEPS_DAILY       -> "👟"
        GoalType.SLEEP_DAILY       -> "😴"
    }
    val animatedFraction by animateFloatAsState(
        targetValue = progress.progressFraction,
        animationSpec = tween(800),
        label = "goal_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(typeEmoji, fontSize = 20.sp)
                    Text(progress.goal.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = trendColor.copy(alpha = 0.15f)) {
                        Text(trendLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = trendColor)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(animatedFraction).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(trendColor)
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(progress.displayCurrent, style = MaterialTheme.typography.labelMedium,
                    color = trendColor, fontWeight = FontWeight.SemiBold)
                Text(
                    "goal: ${progress.displayTarget}" +
                    if (progress.goal.periodDays == 7) " · weekly" else " · ${progress.goal.periodDays}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalAddSheet(state: GoalsUiState, viewModel: GoalsViewModel) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
        Text(if (state.editingGoalId != null) "Edit Goal" else "New Goal",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // Title
        OutlinedTextField(
            value = state.sheetTitle,
            onValueChange = viewModel::setTitle,
            label = { Text("Goal title") },
            placeholder = { Text("e.g. Run 3x/week") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Type selector
        Text("TYPE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), letterSpacing = 0.8.sp)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalTypeChip("🏋️ Exercise", state.sheetType == GoalType.EXERCISE_SESSIONS) {
                viewModel.setType(GoalType.EXERCISE_SESSIONS)
            }
            GoalTypeChip("✅ Habit", state.sheetType == GoalType.HABIT_COMPLETION) {
                viewModel.setType(GoalType.HABIT_COMPLETION)
            }
            GoalTypeChip("👟 Steps", state.sheetType == GoalType.STEPS_DAILY) {
                viewModel.setType(GoalType.STEPS_DAILY)
            }
            GoalTypeChip("😴 Sleep", state.sheetType == GoalType.SLEEP_DAILY) {
                viewModel.setType(GoalType.SLEEP_DAILY)
            }
        }

        // Habit picker (only for HABIT_COMPLETION)
        if (state.sheetType == GoalType.HABIT_COMPLETION && state.habits.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("HABIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            val selectedHabit = state.habits.firstOrNull { it.id == state.sheetLinkedHabitId }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedHabit?.let { "${it.emoji} ${it.name}" } ?: "Any habit",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Any habit") },
                        onClick = { viewModel.setLinkedHabit(null); expanded = false })
                    state.habits.forEach { habit ->
                        DropdownMenuItem(
                            text = { Text("${habit.emoji} ${habit.name}") },
                            onClick = { viewModel.setLinkedHabit(habit.id); expanded = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Target value
        val targetLabel = when (state.sheetType) {
            GoalType.EXERCISE_SESSIONS -> "Sessions per period"
            GoalType.HABIT_COMPLETION  -> "Completions per period"
            GoalType.STEPS_DAILY       -> "Daily steps target"
            GoalType.SLEEP_DAILY       -> "Target sleep hours/night"
        }
        OutlinedTextField(
            value = state.sheetTarget,
            onValueChange = viewModel::setTarget,
            label = { Text(targetLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Period (only for count-based types)
        if (state.sheetType == GoalType.EXERCISE_SESSIONS || state.sheetType == GoalType.HABIT_COMPLETION) {
            Spacer(Modifier.height(16.dp))
            Text("WINDOW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalTypeChip("Weekly (7d)", state.sheetPeriod == 7) { viewModel.setPeriod(7) }
                GoalTypeChip("Monthly (30d)", state.sheetPeriod == 30) { viewModel.setPeriod(30) }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::saveGoal,
            enabled = state.sheetTitle.isNotBlank() && state.sheetTarget.isNotBlank() && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("Save Goal")
        }
    }
}

@Composable
private fun GoalTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
