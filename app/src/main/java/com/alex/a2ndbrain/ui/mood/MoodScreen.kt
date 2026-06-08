package com.alex.a2ndbrain.ui.mood

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
import com.alex.a2ndbrain.core.mood.MoodLogEntity
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

private val moodLabels = listOf("Low", "Fair", "Okay", "Good", "Great")
private val moodEmojis = listOf("😞", "😕", "😐", "🙂", "😄")
private val energyLabels = listOf("Drained", "Low", "Moderate", "High", "Peak")
private val energyEmojis = listOf("🪫", "😴", "⚡", "🔋", "🚀")

private fun moodColor(value: Int): Color {
    val hue = ((value - 1) / 4f) * 120f
    return Color.hsv(hue, 0.65f, 0.82f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodScreen(
    viewModel: MoodViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showCheckInSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::hideCheckIn) {
            CheckInSheetContent(
                selectedMood = state.selectedMood,
                selectedEnergy = state.selectedEnergy,
                note = state.note,
                isSaving = state.isSaving,
                onMoodSelect = viewModel::setMood,
                onEnergySelect = viewModel::setEnergy,
                onNoteChange = viewModel::setNote,
                onSave = viewModel::save,
                onDismiss = viewModel::hideCheckIn
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { TodayCard(state.todayLog, onCheckIn = viewModel::showCheckIn) }
        if (state.recentLogs.isNotEmpty()) {
            item { TrendCard(state.recentLogs) }
            item { SectionLabel("RECENT CHECK-INS") }
            items(state.recentLogs.take(14)) { log ->
                LogRow(log)
            }
        }
    }
}

// ─── Today card ───────────────────────────────────────────────────────────────

@Composable
private fun TodayCard(todayLog: MoodLogEntity?, onCheckIn: () -> Unit) {
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
                Text(
                    "Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalIconButton(onClick = onCheckIn, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (todayLog != null) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = if (todayLog != null) "Update check-in" else "Log mood",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (todayLog != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MoodRing(label = "Mood", value = todayLog.mood, emojis = moodEmojis, labels = moodLabels)
                    MoodRing(label = "Energy", value = todayLog.energy, emojis = energyEmojis, labels = energyLabels)
                }
                if (todayLog.note.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "\"${todayLog.note}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No check-in yet", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onCheckIn) { Text("How are you feeling?") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodRing(label: String, value: Int, emojis: List<String>, labels: List<String>) {
    val color = moodColor(value)
    val progress by animateFloatAsState((value / 5f), tween(600), label = "ring_$label")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(72.dp)) {
                val strokePx = 7.dp.toPx()
                val inset = strokePx / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokePx,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                if (progress > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f, sweepAngle = progress * 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokePx,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
            Text(emojis.getOrElse(value - 1) { "?" }, fontSize = 24.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            labels.getOrElse(value - 1) { value.toString() },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

// ─── 14-day trend chart ───────────────────────────────────────────────────────

@Composable
private fun TrendCard(logs: List<MoodLogEntity>) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val dayLabelFmt = remember { SimpleDateFormat("d", Locale.getDefault()) }

    val logsByDate = logs.groupBy { it.date }
    val slots = (13 downTo 0).map { offset ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        val dateStr = dateFmt.format(cal.time)
        val dayLabel = dayLabelFmt.format(cal.time)
        val entry = logsByDate[dateStr]?.firstOrNull()
        Triple(dayLabel, entry?.mood ?: -1, entry?.energy ?: -1)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SectionLabel("14-DAY MOOD & ENERGY")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                slots.forEach { (day, mood, energy) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Mood bar
                        DualBar(mood, energy)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            day,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(color = moodColor(4), label = "Mood")
                LegendDot(color = moodColor(2), label = "Energy")
            }
        }
    }
}

@Composable
private fun DualBar(mood: Int, energy: Int) {
    val moodRatio by animateFloatAsState(if (mood > 0) mood / 5f else 0f, tween(600), label = "mood_bar")
    val energyRatio by animateFloatAsState(if (energy > 0) energy / 5f else 0f, tween(600), label = "energy_bar")
    val moodColor = if (mood > 0) moodColor(mood) else Color.Gray.copy(alpha = 0.2f)
    val energyColor = if (energy > 0) moodColor(energy).copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.2f)

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        SingleBar(ratio = moodRatio, color = moodColor, width = 7.dp)
        SingleBar(ratio = energyRatio, color = energyColor, width = 7.dp)
    }
}

@Composable
private fun SingleBar(ratio: Float, color: Color, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.width(width).height(48.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
        )
        if (ratio > 0f) {
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(ratio)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

// ─── Log row ─────────────────────────────────────────────────────────────────

@Composable
private fun LogRow(log: MoodLogEntity) {
    val timeFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            moodEmojis.getOrElse(log.mood - 1) { "?" },
            fontSize = 22.sp,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(label = "Mood ${log.mood}/5", color = moodColor(log.mood))
                Chip(label = "Energy ${log.energy}/5", color = moodColor(log.energy))
            }
            if (log.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(log.note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1)
            }
        }
        Text(
            timeFmt.format(Date(log.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun Chip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

// ─── Check-in bottom sheet ───────────────────────────────────────────────────

@Composable
private fun CheckInSheetContent(
    selectedMood: Int,
    selectedEnergy: Int,
    note: String,
    isSaving: Boolean,
    onMoodSelect: (Int) -> Unit,
    onEnergySelect: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "How are you feeling?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        SelectorRow(
            title = "Mood",
            emojis = moodEmojis,
            labels = moodLabels,
            selected = selectedMood,
            onSelect = onMoodSelect
        )

        SelectorRow(
            title = "Energy",
            emojis = energyEmojis,
            labels = energyLabels,
            selected = selectedEnergy,
            onSelect = onEnergySelect
        )

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("Note (optional)") },
            placeholder = { Text("What's on your mind?") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Cancel") }
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun SelectorRow(
    title: String,
    emojis: List<String>,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..5).forEach { value ->
                val isSelected = selected == value
                val color = moodColor(value)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isSelected) Modifier.background(color.copy(alpha = 0.15f))
                                .border(1.dp, color, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable { onSelect(value) }
                        .padding(8.dp)
                ) {
                    Text(emojis.getOrElse(value - 1) { "?" }, fontSize = 24.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        labels.getOrElse(value - 1) { value.toString() },
                        fontSize = 10.sp,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}
