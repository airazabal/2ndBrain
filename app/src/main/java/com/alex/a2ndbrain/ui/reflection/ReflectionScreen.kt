package com.alex.a2ndbrain.ui.reflection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.ui.theme.PastelBlue
import com.alex.a2ndbrain.ui.theme.PastelGreen
import com.alex.a2ndbrain.ui.theme.PastelGreenText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReflectionScreen(
    summaries: List<DailySummaryEntity>,
    settingsManager: CaptureSettingsManager,
    onGenerateReflection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    var selectedModel by remember { mutableStateOf(settingsManager.getGeminiModel()) }
    var isGenerating by remember { mutableStateOf(false) }
    
    LaunchedEffect(summaries) {
        isGenerating = false
    }

    val models = remember {
        val baseModels = mutableListOf(
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-pro-preview",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash"
        )
        val buildConfigModel = BuildConfig.GEMINI_MODEL
        if (buildConfigModel.isNotEmpty() && !baseModels.contains(buildConfigModel)) {
            baseModels.add(0, buildConfigModel)
        }
        baseModels
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val reflectButtonWidth = (configuration.screenWidthDp * 0.25).coerceIn(90.0, 140.0).dp
                
                Button(
                    onClick = {
                        isGenerating = true
                        onGenerateReflection()
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.width(reflectButtonWidth),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Reflect")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("AI:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(models) { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = {
                                selectedModel = model
                                settingsManager.saveGeminiModel(model)
                            },
                            label = { Text(model.removePrefix("gemini-")) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        if (summaries.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Your daily synthesis will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(summaries) { summary ->
                SummaryCard(summary)
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryCard(summary: DailySummaryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (summary.type == "briefing") PastelGreen else PastelBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (summary.type == "briefing") PastelGreenText else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (summary.type == "briefing") "Morning Briefing" else "Evening Reflection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(summary.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    summary.modelName?.let { name ->
                        Text(
                            text = name.removePrefix("gemini-"),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = summary.summary,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
