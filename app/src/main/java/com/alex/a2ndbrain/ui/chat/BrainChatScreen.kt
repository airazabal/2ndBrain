package com.alex.a2ndbrain.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.ChatMessage
import com.alex.a2ndbrain.core.reflection.TtsManager
import com.alex.a2ndbrain.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun BrainChatScreen(
    messages: List<ChatMessage>,
    isThinking: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    // 1. TextToSpeech Engine Integration
    val ttsManager = remember { TtsManager(context) }
    val isTtsSpeaking by ttsManager.isSpeaking.collectAsState()
    val activeUtteranceId by ttsManager.activeUtteranceId.collectAsState()

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    // 2. Inline Speech-to-Text with live partial results
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e("BrainChatScreen", "SpeechRecognizer error: $error")
                isListening = false
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) inputText = text
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) inputText = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { speechRecognizer.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            inputText = ""
            isListening = true
            speechRecognizer.startListening(recognizerIntent)
        }
    }

    val toggleListening = {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                inputText = ""
                isListening = true
                speechRecognizer.startListening(recognizerIntent)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Pulsing animation for the mic button while listening
    val micPulse = rememberInfiniteTransition(label = "mic_pulse")
    val micPulseAlpha by micPulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mic_alpha"
    )

    // Auto scroll to latest message when new messages arrive or thinking state changes
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title block
        Text(
            text = "Co-Pilot Chat",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Ask questions directly about your active notifications, clipboard highlights, or habits. All computations run completely privately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Chat Log List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages.size) { idx ->
                    val msg = messages[idx]
                    val utteranceId = "msg_$idx"
                    val isThisMessageSpeaking = isTtsSpeaking && activeUtteranceId == utteranceId
                    
                    ChatBubble(
                        message = msg,
                        isThisMessageSpeaking = isThisMessageSpeaking,
                        onSpeakClick = {
                            if (isThisMessageSpeaking) {
                                ttsManager.stop()
                            } else {
                                ttsManager.speak(msg.text, utteranceId)
                            }
                        }
                    )
                }

                if (isThinking) {
                    item {
                        ThinkingBubble()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input Field Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text(if (isListening) "Listening..." else "Ask your 2ndBrain...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = {
                    IconButton(onClick = { toggleListening() }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Voice input",
                            tint = if (isListening) Color.Red else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alpha(if (isListening) micPulseAlpha else 1f)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isThisMessageSpeaking: Boolean,
    onSpeakClick: () -> Unit
) {
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val shape = if (message.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                
                if (!message.isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chip = remember(message.modelUsed) {
                            parseModelChip(message.modelUsed)
                        }
                        if (chip != null) {
                            ModelChip(chip)
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isThisMessageSpeaking) {
                                SoundwavePulse(color = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = onSpeakClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isThisMessageSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isThisMessageSpeaking) "Stop Speaking" else "Speak Response",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (message.wasFallback) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Cloud unreachable · Answered via offline model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioVisualizerBar(
    heightScale: Float,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(2.dp)
            .height((12 * heightScale).dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

@Composable
private fun SoundwavePulse(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundwave")
    
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        AudioVisualizerBar(heightScale = bar1, color = color)
        AudioVisualizerBar(heightScale = bar2, color = color)
        AudioVisualizerBar(heightScale = bar3, color = color)
    }
}

@Composable
private fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
            modifier = Modifier
                .widthIn(max = 200.dp)
                .alpha(alpha)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Brain is thinking offline...",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private data class ModelChipData(
    val label: String,
    val elapsed: String,
    val isLocal: Boolean
)

private fun parseModelChip(raw: String?): ModelChipData? {
    if (raw.isNullOrBlank() || raw == "Empty") return null
    val elapsed = Regex("(\\d+\\.\\d+s)").find(raw)?.value ?: ""
    return when {
        raw.startsWith("LiteRT") ->
            ModelChipData("On-device", elapsed, isLocal = true)
        raw.lowercase().contains("gemini") -> {
            val label = if (raw.contains("lite", ignoreCase = true)) "Gemini Lite" else "Gemini"
            ModelChipData(label, elapsed, isLocal = false)
        }
        raw == "Template" ->
            ModelChipData("No AI key", "", isLocal = false)
        else -> null
    }
}

@Composable
private fun ModelChip(chip: ModelChipData) {
    val color = if (chip.isLocal) androidx.compose.ui.graphics.Color(0xFF7C4DFF)
                else androidx.compose.ui.graphics.Color(0xFF1E88E5)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(if (chip.isLocal) "⚡" else "☁", fontSize = 9.sp)
            Text(
                text = chip.label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            if (chip.elapsed.isNotBlank()) {
                Text(
                    text = "· ${chip.elapsed}",
                    fontSize = 9.sp,
                    color = color.copy(alpha = 0.65f)
                )
            }
        }
    }
}
