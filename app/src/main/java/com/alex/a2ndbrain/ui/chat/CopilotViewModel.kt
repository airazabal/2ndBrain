package com.alex.a2ndbrain.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.ChatMessage
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.memory.HabitsDao
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CopilotViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val habitsDao: HabitsDao,
    private val settingsManager: CaptureSettingsManager,
    private val reflectionManager: ReflectionManager,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            text = "Hello! I am your 2ndBrain Co-Pilot. Ask me anything about your captured notifications, clipboard history, daily digital usages, or smartwatch health stats!",
            isUser = false
        )
    ))
    val chatMessages = _chatMessages.asStateFlow()

    private val _chatIsThinking = MutableStateFlow(false)
    val chatIsThinking = _chatIsThinking.asStateFlow()

    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        val userMsg = ChatMessage(text = message, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg
        _chatIsThinking.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Keyword match search
                val words = message.split(" ").filter { it.length > 3 }
                val contextMemories = if (words.isEmpty()) {
                    memoryRepository.getRecentMemoriesSync()
                } else {
                    memoryRepository.searchMemoriesSync(words.first())
                }
                
                val promptContext = buildString {
                    append("You are the user's personal 2ndBrain assistant. You have access to their captured daily routines, smartwatch physical states, mobile screen time metrics, and memory logs below.\n")
                    append("Answer the user's question accurately, concisely, and friendly based on this deep unified context. If the metrics do not contain relevant details for their question, politely state that you can't find it in their current logs.\n\n")
                    
                    // 1. Physical Health Connect Metrics
                    val healthPermissionsGranted = healthConnectManager.hasPermissions()
                    if (healthPermissionsGranted) {
                        val metrics = healthConnectManager.fetchHealthMetricsToday()
                        append("USER'S CURRENT PHYSICAL HEALTH STATS TODAY:\n")
                        append("- Active Steps Today: ${metrics.steps} steps (Goal: 10,000 steps)\n")
                        append("- Sleep Last Night: ${metrics.sleepMinutes / 60}h ${metrics.sleepMinutes % 60}m\n")
                        append("- Heart Rate Range: ${metrics.minHeartRate} - ${metrics.maxHeartRate} BPM (Avg: ${metrics.avgHeartRate} BPM)\n\n")
                    }

                    // 2. Room Habits Compliance Checklist
                    val habits = habitsDao.getAllHabitsSync()
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val completions = habitsDao.getCompletionsForDateSync(todayStr)
                    val completedIds = completions.map { it.habitId }.toSet()
                    
                    append("USER'S DAILY COMPLETED & PENDING HABITS TODAY:\n")
                    if (habits.isEmpty()) {
                        append("- (No routines configured)\n\n")
                    } else {
                        habits.forEach { habit ->
                            val status = if (completedIds.contains(habit.id)) "DONE (Completed)" else "PENDING (Unfinished)"
                            append("- [${status}] ${habit.name} (${habit.timeString})\n")
                        }
                        append("\n")
                    }

                    // 3. Screen-Time Visible Usage Metrics
                    val stats = usageRepository.getUsageStatsForToday().first()
                    append("USER'S ACTIVE APP SCREEN TIME TODAY:\n")
                    val activeStats = stats.filter { (it.totalTimeVisibleMs / 60000L) > 0 }
                    if (activeStats.isEmpty()) {
                        append("- (No screen time registered today yet)\n\n")
                    } else {
                        activeStats.forEach { stat ->
                            val mins = stat.totalTimeVisibleMs / 60000L
                            val label = stat.packageName.split(".").lastOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: stat.packageName
                            append("- ${label}: ${mins} mins\n")
                        }
                        append("\n")
                    }

                    // 4. Zendence Meditation Sessions
                    try {
                        val allMemories = memoryRepository.getAllMemoriesFlow().first()
                        val zendenceMemories = allMemories.filter { it.packageName == "com.alex.zendence" }
                        val parsedMeditation = zendenceMemories.mapNotNull { com.alex.a2ndbrain.core.meditation.MeditationManager.parseMeditationSession(it) }
                        val streaks = com.alex.a2ndbrain.core.meditation.MeditationManager.calculateStreaks(parsedMeditation)
                        
                        append("USER'S MEDITATION SESSIONS (ZENDENCE):\n")
                        append("- Contiguous Sessions This Week: ${streaks.currentWeekStreak} days\n")
                        append("- Max Overall Streak: ${streaks.maxOverallStreak} days\n")
                        append("- Total Sessions: ${streaks.totalSessions}\n")
                        if (parsedMeditation.isEmpty()) {
                            append("- (No meditation sessions recorded yet)\n\n")
                        } else {
                            append("- Recent Sessions:\n")
                            parsedMeditation.take(5).forEach { session ->
                                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                                append("  * $dateStr: ${session.durationMinutes} mins | Insight: \"${session.insight}\"\n")
                            }
                            append("\n")
                        }
                    } catch (e: Exception) {
                        // Safe fallback
                    }

                    // 5. Memory Logs Keyword Matches
                    append("USER'S CAPTURED MEMORY LOGS:\n")
                    if (contextMemories.isEmpty()) {
                        append("- (No recent memories logged matching keyword matches)\n")
                    } else {
                        contextMemories.take(10).forEach { mem ->
                            val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(mem.timestamp))
                            append("- [$dateStr][${mem.tags ?: ""}] ${mem.content}\n")
                        }
                    }
                    append("\nUSER'S QUESTION: $message\n")
                }
                
                val (replyText, modelUsed) = reflectionManager.runChatInference(promptContext)
                
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = replyText,
                    isUser = false,
                    modelUsed = modelUsed
                )
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = "Sorry, I ran into an error generating a response: ${e.message}",
                    isUser = false,
                    modelUsed = "Error"
                )
            } finally {
                _chatIsThinking.value = false
            }
        }
    }
}
