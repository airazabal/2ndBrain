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
                // Keyword match search for memories
                val words = message.split(" ").filter { it.length > 3 }
                val contextMemories = if (words.isEmpty()) {
                    memoryRepository.getRecentMemoriesSync()
                } else {
                    memoryRepository.searchMemoriesSync(words.first())
                }

                // Dynamic Context Routing to prevent Qwen-0.6B context window overflow
                val lowerMessage = message.lowercase(Locale.getDefault())
                
                val includeHealth = listOf("step", "sleep", "heart", "bpm", "walk", "physical", "active", "health", "run", "fit", "calories").any { lowerMessage.contains(it) }
                val includeHabits = listOf("habit", "routine", "alarm", "med", "pill", "checklist", "todo", "task").any { lowerMessage.contains(it) }
                val includeUsage = listOf("screen", "time", "app", "usage", "min", "hour", "setting", "youtube", "chrome", "spend", "social", "distract", "phone", "tablet", "device").any { lowerMessage.contains(it) }
                val includeMeditation = listOf("meditat", "zendence", "streak", "session", "mindful", "calm", "insight").any { lowerMessage.contains(it) }
                val includeMemories = listOf("notification", "clipboard", "log", "memory", "captured", "tag", "remember", "text", "copy", "copied", "message", "email", "chat").any { lowerMessage.contains(it) }
                
                // If it's a general question (no specific category matched), include a compact version of everything
                val isGeneral = !includeHealth && !includeHabits && !includeUsage && !includeMeditation && !includeMemories
                
                val promptContext = buildString {
                    append("You are the user's personal 2ndBrain assistant. You have access to their captured daily routines, smartwatch physical states, mobile screen time metrics, and memory logs below.\n")
                    append("Answer the user's question accurately, concisely, and friendly based on this deep unified context. If the metrics do not contain relevant details for their question, politely state that you can't find it in their current logs.\n\n")
                    
                    // 1. Physical Health Connect Metrics
                    if (includeHealth || isGeneral) {
                        val healthPermissionsGranted = healthConnectManager.hasPermissions()
                        if (healthPermissionsGranted) {
                            val metrics = healthConnectManager.fetchHealthMetricsToday()
                            append("USER'S CURRENT PHYSICAL HEALTH STATS TODAY:\n")
                            append("- Active Steps Today: ${metrics.steps} steps (Goal: 10,000 steps)\n")
                            append("- Sleep Last Night: ${metrics.sleepMinutes / 60}h ${metrics.sleepMinutes % 60}m\n")
                            append("- Heart Rate Range: ${metrics.minHeartRate} - ${metrics.maxHeartRate} BPM (Avg: ${metrics.avgHeartRate} BPM)\n\n")
                        }
                    }

                    // 2. Room Habits Compliance Checklist
                    if (includeHabits || isGeneral) {
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
                    }

                    // 3. Screen-Time Visible Usage Metrics
                    if (includeUsage || isGeneral) {
                        val stats = usageRepository.getUsageStatsForTodaySync()
                        append("USER'S ACTIVE APP SCREEN TIME TODAY:\n")
                        val activeStats = stats.filter { (it.totalTimeVisibleMs / 60000L) > 0 }
                        if (activeStats.isEmpty()) {
                            append("- (No screen time registered today yet)\n\n")
                        } else {
                            // For general queries, just show top 5 active apps to keep context clean
                            val displayStats = if (isGeneral) activeStats.take(5) else activeStats
                            displayStats.forEach { stat ->
                                val mins = stat.totalTimeVisibleMs / 60000L
                                val label = stat.packageName.split(".").lastOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: stat.packageName
                                append("- ${label}: ${mins} mins\n")
                            }
                            if (isGeneral && activeStats.size > 5) {
                                append("- ...and ${activeStats.size - 5} other apps\n")
                            }
                            append("\n")
                        }
                    }

                    // 4. Zendence Meditation Sessions
                    if (includeMeditation || isGeneral) {
                        try {
                            val zendenceMemories = memoryRepository.getMemoriesByPackageSync("com.alex.zendence")
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
                                val limit = if (isGeneral) 2 else 5
                                parsedMeditation.take(limit).forEach { session ->
                                    val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                                    append("  * $dateStr: ${session.durationMinutes} mins | Insight: \"${session.insight}\"\n")
                                }
                                append("\n")
                            }
                        } catch (e: Exception) {
                            // Safe fallback
                        }
                    }

                    // 5. Memory Logs Keyword Matches
                    if (includeMemories || isGeneral) {
                        append("USER'S CAPTURED MEMORY LOGS:\n")
                        if (contextMemories.isEmpty()) {
                            append("- (No recent memories logged matching keyword matches)\n")
                        } else {
                            val limit = if (isGeneral) 5 else 10
                            contextMemories.take(limit).forEach { mem ->
                                val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(mem.timestamp))
                                append("- [$dateStr][${mem.tags ?: ""}] ${mem.content}\n")
                            }
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
