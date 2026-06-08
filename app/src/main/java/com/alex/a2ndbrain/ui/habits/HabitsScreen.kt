package com.alex.a2ndbrain.ui.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alex.a2ndbrain.core.habits.HabitEntity
import org.koin.androidx.compose.koinViewModel

private val EMOJI_PRESETS = listOf(
    "✅", "💪", "🏃", "🧘", "💊", "💧", "📚", "🎯",
    "🌅", "🍎", "😴", "✍️", "🧹", "🎵", "🐕", "🌿"
)

private val REPEAT_OPTIONS = listOf(
    null          to "No repeat",
    "every day"   to "Daily",
    "every weekday" to "Weekdays",
    "every week"  to "Weekly",
    "every 2 weeks" to "Biweekly",
    "every month" to "Monthly"
)

private fun repeatLabel(rule: String?): String =
    REPEAT_OPTIONS.firstOrNull { it.first == rule }?.second ?: rule?.replaceFirstChar { it.uppercase() } ?: "No repeat"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    viewModel: HabitsViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showAddSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::hideSheet) {
            HabitEditSheetContent(
                isEditing = state.editingHabit != null,
                name = state.sheetName,
                emoji = state.sheetEmoji,
                time = state.sheetTime,
                repeatRule = state.sheetRepeatRule,
                isSaving = state.isSaving,
                onNameChange = viewModel::setName,
                onEmojiSelect = viewModel::setEmoji,
                onTimeChange = viewModel::setTime,
                onRepeatRuleChange = viewModel::setRepeatRule,
                onSave = viewModel::save,
                onDismiss = viewModel::hideSheet
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.habitsWithStats.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("No habits yet", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("Build your daily routines and track streaks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center)
                Button(onClick = viewModel::showAddSheet) { Text("Add your first habit") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { SummaryRow(state) }
                items(state.habitsWithStats, key = { it.habit.id }) { entry ->
                    HabitCard(
                        entry = entry,
                        onToggle = { viewModel.toggleCompletion(entry.habit.id) },
                        onEdit = { viewModel.showEditSheet(entry.habit) },
                        onDelete = { viewModel.deleteHabit(entry.habit.id) }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        FloatingActionButton(
            onClick = viewModel::showAddSheet,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add habit")
        }
    }
}

// ─── Summary row ─────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(state: HabitsUiState) {
    val total = state.habitsWithStats.size
    val done = state.habitsWithStats.count { it.isCompletedToday }
    val progress = if (total > 0) done.toFloat() / total else 0f
    val animProg by animateFloatAsState(progress, tween(600), label = "summary_prog")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("$done of $total completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = progressColor(progress)
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animProg },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = progressColor(progress),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

// ─── Habit card ───────────────────────────────────────────────────────────────

@Composable
private fun HabitCard(
    entry: HabitWithStreak,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val habit = entry.habit
    val done = entry.isCompletedToday
    val accentColor = if (done) Color(0xFF43A047) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete habit?") },
            text = { Text("'${habit.name}' and its history will be removed.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done)
                Color(0xFF43A047).copy(alpha = 0.07f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (done) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Check button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (done) Modifier.background(Color(0xFF43A047))
                        else Modifier.border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text(habit.emoji, fontSize = 18.sp)
                }
            }

            // Name + meta
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (habit.timeString.isNotBlank()) {
                        MetaChip(habit.timeString, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                    if (!habit.repeatRule.isNullOrBlank()) {
                        MetaChip("🔄 ${repeatLabel(habit.repeatRule)}", MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f))
                    }
                    if (entry.streak > 0) {
                        MetaChip("🔥 ${entry.streak}d", Color(0xFFFB8C00))
                    }
                    if (entry.weeklyRate > 0f) {
                        MetaChip("${(entry.weeklyRate * 100).toInt()}% this week", MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun MetaChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

// ─── Add / edit bottom sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditSheetContent(
    isEditing: Boolean,
    name: String,
    emoji: String,
    time: String,
    repeatRule: String?,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onEmojiSelect: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onRepeatRuleChange: (String?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (isEditing) "Edit habit" else "New habit",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Emoji picker
        Text("Choose an icon", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        EmojiPicker(selected = emoji, onSelect = onEmojiSelect)

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Habit name") },
            placeholder = { Text("e.g. Morning walk") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Time
        OutlinedTextField(
            value = time,
            onValueChange = onTimeChange,
            label = { Text("Time (optional)") },
            placeholder = { Text("e.g. 08:00") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Repeat
        RepeatPicker(selected = repeatRule, onSelect = onRepeatRuleChange)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = name.isNotBlank() && !isSaving,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (isEditing) "Save" else "Add")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatPicker(selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = repeatLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Repeat") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            REPEAT_OPTIONS.forEach { (rule, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(rule); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    val rows = EMOJI_PRESETS.chunked(8)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { e ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (e == selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            .clickable { onSelect(e) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(e, fontSize = 20.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun progressColor(progress: Float): Color {
    val hue = progress * 120f
    return Color.hsv(hue, 0.65f, 0.78f)
}
