package com.alex.a2ndbrain.ui.reflection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.reflection.ModelDownloader
import com.alex.a2ndbrain.core.reflection.ModelPicker
import com.alex.a2ndbrain.ui.theme.PastelBlue
import com.alex.a2ndbrain.ui.theme.PastelGreen
import com.alex.a2ndbrain.ui.theme.PastelGreenText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReflectionScreen(
    summaries: List<DailySummaryEntity>,
    settingsManager: CaptureSettingsManager,
    onGenerateReflection: () -> Unit,
    onClearAll: () -> Unit,
    onDeleteSummary: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelPicker = remember { ModelPicker(context) }
    
    var selectedModel by remember { mutableStateOf(settingsManager.getPreferredModelType()) }
    var isGenerating by remember { mutableStateOf(false) }

    var availableModels by remember { mutableStateOf<List<ModelDownloader.LiteRTModel>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var selectedLiteRTModel by remember { mutableStateOf(settingsManager.getSelectedLiteRTModel()) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadingModelName by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    
    var geminiApiKey by remember { mutableStateOf(settingsManager.getGeminiApiKey()) }
    var geminiModel by remember { mutableStateOf(settingsManager.getGeminiModel()) }
    
    LaunchedEffect(Unit) {
        isLoadingModels = true
        availableModels = ModelDownloader(context).fetchAvailableModels()
        isLoadingModels = false
    }
    
    LaunchedEffect(summaries) {
        isGenerating = false
    }

    val models = remember {
        listOf("AUTO", "GEMINI_CLOUD", "LITERT_LOCAL")
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (summaries.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

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
                Text("Mode:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(models) { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = {
                                selectedModel = model
                                settingsManager.savePreferredModelType(model)
                            },
                            label = { Text(model.replace("_", " ")) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        if (selectedModel == "LITERT_LOCAL" || (selectedModel == "AUTO" && modelPicker.getBestModel() == ModelPicker.ModelType.LITERT_LOCAL)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Local AI Models", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                        } else {
                            availableModels.forEach { model ->
                                val isDownloaded = remember(model.name, downloadProgress) {
                                    java.io.File(context.filesDir, "models/${model.name}.litertlm").run { exists() && length() > 0 }
                                }
                                val isSelected = selectedLiteRTModel == model.name

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .let { 
                                            if (isSelected) it.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) 
                                            else it 
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                                       else if (isDownloaded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                       else MaterialTheme.colorScheme.surface
                                    ),
                                    onClick = {
                                        if (isDownloaded) {
                                            selectedLiteRTModel = model.name
                                            settingsManager.saveSelectedLiteRTModel(model.name)
                                        }
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = model.name, 
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(model.sizeLabel, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        
                                        if (isDownloaded) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("✓ Ready", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            "ACTIVE", 
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            fontSize = 8.sp
                                                        )
                                                    }
                                                }
                                            }
                                        } else if (downloadingModelName == model.name) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { downloadProgress ?: 0f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text("Downloading: ${(downloadProgress!! * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        downloadingModelName = model.name
                                                        ModelDownloader(context).downloadModel(model).collect { status ->
                                                            when (status) {
                                                                is ModelDownloader.DownloadStatus.Starting -> downloadProgress = 0f
                                                                is ModelDownloader.DownloadStatus.Progress -> downloadProgress = status.progress
                                                                is ModelDownloader.DownloadStatus.Success -> {
                                                                    downloadProgress = null
                                                                    downloadingModelName = null
                                                                    if (selectedLiteRTModel == "") {
                                                                        selectedLiteRTModel = model.name
                                                                        settingsManager.saveSelectedLiteRTModel(model.name)
                                                                    }
                                                                }
                                                                is ModelDownloader.DownloadStatus.Error -> {
                                                                    downloadProgress = null
                                                                    downloadingModelName = null
                                                                    downloadError = status.message
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                                            ) {
                                                Text("Download", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        downloadError?.let {
                            Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (selectedModel == "GEMINI_CLOUD") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gemini Cloud Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = { 
                                geminiApiKey = it
                                settingsManager.saveGeminiApiKey(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            placeholder = { Text("Paste your Gemini API key here") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { 
                                geminiModel = it
                                settingsManager.saveGeminiModel(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Model") },
                            placeholder = { Text("e.g. gemini-1.5-flash") },
                            singleLine = true
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
                SummaryCard(summary, onDelete = { onDeleteSummary(summary.id) })
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryCard(summary: DailySummaryEntity, onDelete: () -> Unit) {
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
                
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
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
