package com.alex.a2ndbrain

import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import java.util.UUID
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.core.memory.HabitCompletionEntity
import com.alex.a2ndbrain.core.memory.HabitsDao
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.health.HabitAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TimelineEvent(
    val time: String,
    val title: String,
    val description: String,
    val appName: String
)

class MainViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager,
    private val reflectionManager: ReflectionManager,
    val healthConnectManager: HealthConnectManager,
    private val habitsDao: HabitsDao,
    private val applicationContext: android.content.Context
) : ViewModel() {

    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMemories: Flow<PagingData<MemoryEntity>> = _searchQuery
        .flatMapLatest { rawQuery ->
            val expandedQuery = when (rawQuery.lowercase().trim()) {
                "workout", "fitness", "exercise", "sleep", "heart", "steps", "health" -> "#Health"
                "task", "todoist", "calendar", "meeting", "schedule", "work" -> "#Work"
                "pay", "spent", "money", "bank", "card", "transaction", "finance" -> "#Finance"
                "clipboard", "copied", "url", "copy", "reference" -> "#Reference"
                "gmail", "email", "whatsapp", "message", "chat", "social" -> "#Social"
                else -> rawQuery
            }
            memoryRepository.getPagedMemories(expandedQuery)
        }.cachedIn(viewModelScope)

    val summaries: StateFlow<List<DailySummaryEntity>> = memoryRepository.getAllSummariesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val usageStats: StateFlow<List<UsageStatEntity>> = usageRepository.getUsageStatsForToday()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allMemoriesForHome: StateFlow<List<MemoryEntity>> = memoryRepository.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Proactive Timeline & Schedule Extractor (Recommendation B)
    val todayTimelineEvents: StateFlow<List<TimelineEvent>> = allMemoriesForHome
        .map { memories ->
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            memories.filter { mem ->
                mem.timestamp >= startOfToday && (
                    mem.packageName?.contains("calendar") == true ||
                    mem.packageName?.contains("outlook") == true ||
                    mem.content.lowercase().contains("meeting") ||
                    mem.content.lowercase().contains("appointment") ||
                    mem.content.lowercase().contains("schedule") ||
                    mem.content.lowercase().contains("scheduled") ||
                    mem.content.lowercase().contains("meet") ||
                    mem.content.lowercase().contains("reminder")
                )
            }.map { mem ->
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val timeStr = timeFormat.format(Date(mem.timestamp))
                
                val cleanTitle = mem.title ?: if (mem.content.length > 35) mem.content.take(35) + "..." else mem.content
                
                val appName = when {
                    mem.packageName?.contains("calendar") == true -> "Calendar"
                    mem.packageName?.contains("outlook") == true -> "Outlook"
                    else -> "System"
                }

                TimelineEvent(
                    time = timeStr,
                    title = cleanTitle,
                    description = mem.content,
                    appName = appName
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _vaultNotes = MutableStateFlow<List<DocumentFile>>(emptyList())
    val vaultNotes = _vaultNotes.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    val isGeneratingReflection: StateFlow<Boolean> = WorkManager.getInstance(applicationContext)
        .getWorkInfosForUniqueWorkFlow("manual_reflection")
        .map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Room Database Habits Engine
    val activeHabitsToday: StateFlow<List<HabitEntity>> = habitsDao.getActiveHabits()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val completedHabitIdsToday: StateFlow<Set<String>> = habitsDao.getCompletionsForDate(getTodayDateString())
        .map { completions -> completions.map { it.habitId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    // Backwards compatible individual flows for existing HomeScreen layouts
    val medsAmTaken: StateFlow<Boolean> = completedHabitIdsToday
        .map { it.contains("default_meds") }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val walkCompleted: StateFlow<Boolean> = completedHabitIdsToday
        .map { it.contains("default_walk") }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val reflectionCompleted: StateFlow<Boolean> = completedHabitIdsToday
        .map { it.contains("default_reflection") }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _senseOfDayScore = MutableStateFlow(75)
    val senseOfDayScore = _senseOfDayScore.asStateFlow()

    private val _senseOfDayContext = MutableStateFlow("🎯 Calibrating your day. Complete a daily routine or log a few steps to update your Sense of Day index.")
    val senseOfDayContext = _senseOfDayContext.asStateFlow()

    // Health Connect Synchronization (Recommendation 6)
    private val _healthMetricsToday = MutableStateFlow(HealthMetrics())
    val healthMetricsToday = _healthMetricsToday.asStateFlow()

    private val _healthPermissionsGranted = MutableStateFlow(false)
    val healthPermissionsGranted = _healthPermissionsGranted.asStateFlow()

    // Past 7 Days Habit Completion Rates for historical rings
    val pastWeekHabitCompletions: StateFlow<List<Pair<String, Float>>> = activeHabitsToday.combine(
        flow {
            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -6)
            val startDate = dateFormat.format(cal.time)
            emitAll(habitsDao.getCompletionsInRange(startDate, today))
        }
    ) { activeHabits, completions ->
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayLabelFormat = SimpleDateFormat("E", Locale.getDefault()) // "Mon", "Tue"
        
        val activeCount = activeHabits.size.coerceAtLeast(1)
        val completionsByDate = completions.groupBy { it.date }
        
        val last7Days = mutableListOf<Pair<String, Float>>()
        for (i in 6 downTo 0) {
            val loopCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dateStr = dateFormat.format(loopCal.time)
            val label = dayLabelFormat.format(loopCal.time).take(1).uppercase() // E.g., "M", "T"
            
            val completedForDate = completionsByDate[dateStr]?.size ?: 0
            val pct = completedForDate.toFloat() / activeCount.toFloat()
            last7Days.add(label to pct.coerceIn(0f, 1f))
        }
        last7Days
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // CRUD and Toggles
    fun toggleHabitCompletion(habitId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = getTodayDateString()
            val completions = habitsDao.getCompletionsForDateSync(today)
            val existing = completions.find { it.habitId == habitId }
            if (existing != null) {
                habitsDao.deleteCompletion(existing)
            } else {
                habitsDao.insertCompletion(HabitCompletionEntity(habitId = habitId, date = today))
            }
        }
    }

    // Backwards compatible toggle methods for existing Home widgets
    fun toggleMedsAm() = toggleHabitCompletion("default_meds")
    fun toggleWalk() = toggleHabitCompletion("default_walk")
    fun toggleReflection() = toggleHabitCompletion("default_reflection")

    fun addCustomHabit(name: String, timeString: String, isMedication: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val habit = HabitEntity(id = id, name = name, timeString = timeString, isMedication = isMedication, isActive = true)
            habitsDao.insertHabit(habit)
            
            // Schedule Alarm Manager reminder
            val scheduler = HabitAlarmScheduler(applicationContext)
            scheduler.scheduleHabitAlarm(habit)
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitsDao.getHabitById(habitId)
            if (habit != null) {
                val scheduler = HabitAlarmScheduler(applicationContext)
                scheduler.cancelHabitAlarm(habit)
                habitsDao.deleteHabit(habit)
            }
        }
    }

    fun toggleHabitActive(habitId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitsDao.getHabitById(habitId)
            if (habit != null) {
                val updated = habit.copy(isActive = !habit.isActive)
                habitsDao.insertHabit(updated)
                
                val scheduler = HabitAlarmScheduler(applicationContext)
                if (updated.isActive) {
                    scheduler.scheduleHabitAlarm(updated)
                } else {
                    scheduler.cancelHabitAlarm(updated)
                }
            }
        }
    }

    private fun updateSenseOfDayScore() {
        viewModelScope.launch(Dispatchers.Default) {
            // 1. Habits (30%)
            val active = activeHabitsToday.value.size.coerceAtLeast(1)
            val completed = completedHabitIdsToday.value.size
            val habitsRatio = completed.toFloat() / active.toFloat()
            
            // 2. Physical (30%)
            val steps = _healthMetricsToday.value.steps
            val stepsRatio = (steps.toFloat() / 10000f).coerceIn(0f, 1f)
            
            // 3. Digital Focus Ratio (40%)
            var totalFocusMs = 0L
            var totalSocialMs = 0L
            usageStats.value.forEach { stat ->
                val pkg = stat.packageName.lowercase()
                if (pkg.contains("todoist") || pkg.contains("calendar") || pkg.contains("chrome") || pkg.contains("keep") || pkg.contains("brain")) {
                    totalFocusMs += stat.totalTimeVisibleMs
                } else if (pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("tiktok") || pkg.contains("youtube") || pkg.contains("twitter") || pkg.contains("x.android")) {
                    totalSocialMs += stat.totalTimeVisibleMs
                }
            }
            val totalTime = totalFocusMs + totalSocialMs
            val focusRatio = if (totalTime == 0L) {
                1f
            } else {
                totalFocusMs.toFloat() / totalTime.toFloat()
            }
            
            val calculated = (habitsRatio * 30f) + (stepsRatio * 30f) + (focusRatio * 40f)
            _senseOfDayScore.value = calculated.toInt().coerceIn(10, 100)
            
            val contextText = when {
                stepsRatio >= 0.7f && habitsRatio >= 0.7f && focusRatio >= 0.7f -> {
                    "🌟 A masterclass in balance today! High routine compliance, excellent focus, and physical energy."
                }
                stepsRatio < 0.4f && focusRatio >= 0.7f -> {
                    "🏃 Checked off focus work, but you've been stationary. Step away for a brisk 10-minute stretch!"
                }
                focusRatio < 0.4f -> {
                    "📱 Digital distractions are high today. Set a quick focus timer and reconnect with your space."
                }
                stepsRatio >= 0.6f && habitsRatio < 0.4f -> {
                    "💊 Physically active, but falling behind on daily routines and medications. Let's get structured!"
                }
                completed == 0 && steps == 0L -> {
                    "🎯 Calibrating your day. Complete a daily routine or log a few steps to update your Sense of Day index."
                }
                else -> {
                    "⚡ Balanced progress! Continue checking off routines and keeping a healthy physical-digital ratio."
                }
            }
            _senseOfDayContext.value = contextText
        }
    }

    init {
        loadVaultNotes()
        observeWorkManagerErrors()
        checkHealthPermissionsAndSync()

        // Prepopulate core default habits if empty
        viewModelScope.launch(Dispatchers.IO) {
            val existing = habitsDao.getAllHabitsSync()
            if (existing.isEmpty()) {
                val defaultMeds = HabitEntity(id = "default_meds", name = "Take Morning Medication", timeString = "08:00", isMedication = true)
                val defaultWalk = HabitEntity(id = "default_walk", name = "Active Walk / Hydration", timeString = "12:00", isMedication = false)
                val defaultRefl = HabitEntity(id = "default_reflection", name = "Evening Reflection", timeString = "21:00", isMedication = false)
                
                habitsDao.insertHabit(defaultMeds)
                habitsDao.insertHabit(defaultWalk)
                habitsDao.insertHabit(defaultRefl)

                // Schedule alarms
                val scheduler = HabitAlarmScheduler(applicationContext)
                scheduler.scheduleHabitAlarm(defaultMeds)
                scheduler.scheduleHabitAlarm(defaultWalk)
                scheduler.scheduleHabitAlarm(defaultRefl)
            }
        }
        
        // Recalculate score whenever habits, metrics, or usages shift
        viewModelScope.launch {
            combine(completedHabitIdsToday, activeHabitsToday, _healthMetricsToday, usageStats) { _, _, _, _ -> }
                .collect {
                    updateSenseOfDayScore()
                }
        }
    }

    private fun observeWorkManagerErrors() {
        viewModelScope.launch {
            WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWorkFlow("manual_reflection")
                .collect { infos ->
                    val failedInfo = infos.find { it.state == WorkInfo.State.FAILED }
                    if (failedInfo != null) {
                        val error = failedInfo.outputData.getString("error")
                        if (error != null) {
                            _errorFlow.value = error
                        }
                    }
                }
        }
    }

    fun setTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadVaultNotes() {
        viewModelScope.launch {
            val vaultUri = settingsManager.getObsidianVaultUri()
            if (vaultUri.isNotBlank()) {
                try {
                    val root = DocumentFile.fromTreeUri(applicationContext, android.net.Uri.parse(vaultUri))
                    val notes = root?.listFiles()
                        ?.filter { it.isFile && it.name?.endsWith(".md") == true }
                        ?.sortedByDescending { it.lastModified() } ?: emptyList()
                    _vaultNotes.value = notes
                } catch (e: Exception) {
                    // Handle invalid URI or permissions
                }
            }
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            memoryRepository.markAsRead(id)
        }
    }

    fun clearAllMemories() {
        // We'll let MemoryScreen handle clear all for now or move it here.
        // Actually this requires clearing the whole database, which might be dangerous.
        // I will let it be for now or implement in repo.
    }

    fun generateReflection() {
        val request = OneTimeWorkRequestBuilder<com.alex.a2ndbrain.core.reflection.ReflectionWorker>().build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "manual_reflection",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReflection() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork("manual_reflection")
    }

    fun clearError() {
        _errorFlow.value = null
    }

    fun clearAllSummaries() {
        viewModelScope.launch {
            memoryRepository.clearAllSummaries()
        }
    }

    fun deleteSummary(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteSummary(id)
        }
    }

    fun checkHealthPermissionsAndSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val granted = healthConnectManager.hasPermissions()
            _healthPermissionsGranted.value = granted
            if (granted) {
                val metrics = healthConnectManager.fetchHealthMetricsToday()
                _healthMetricsToday.value = metrics
            }
        }
    }

    // Interactive Q&A Co-Pilot State (Recommendation 1)
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
                    append("You are the user's personal 2ndBrain assistant. You have access to their captured memories below.\n")
                    append("Answer the user's question accurately, concisely, and friendly based on these memories. If the memories do not contain relevant details, politely state that you can't find it in their recent logs.\n\n")
                    
                    if (_healthPermissionsGranted.value) {
                        val metrics = _healthMetricsToday.value
                        append("USER'S CURRENT HEALTH CONNECT METRICS:\n")
                        append("- Active Steps Today: ${metrics.steps} steps\n")
                        append("- Sleep Last Night: ${metrics.sleepMinutes / 60}h ${metrics.sleepMinutes % 60}m\n")
                        append("- Heart Rate Range: ${metrics.minHeartRate} - ${metrics.maxHeartRate} BPM (Avg: ${metrics.avgHeartRate} BPM)\n\n")
                    }

                    append("USER'S MEMORIES:\n")
                    if (contextMemories.isEmpty()) {
                        append("- (No memories logged matching these keywords yet)\n")
                    } else {
                        contextMemories.forEach { mem ->
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

    fun saveVoiceNote(transcript: String, audioPath: String, vaultUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalAudioLink = audioPath

            // 1. If vault is connected, write both the audio and the markdown note into the Obsidian Vault!
            if (vaultUri.isNotEmpty()) {
                try {
                    val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, android.net.Uri.parse(vaultUri))
                    if (root != null && root.exists() && root.canWrite()) {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                        val audioFileName = "VoiceNote-$timestamp.m4a"
                        val newNoteName = "VoiceNote-$timestamp"
                        
                        // Copy the local temporary recording over to the Obsidian Vault
                        val audioDocFile = root.createFile("audio/m4a", audioFileName)
                        if (audioDocFile != null) {
                            try {
                                val tempFile = java.io.File(audioPath)
                                if (tempFile.exists()) {
                                    applicationContext.contentResolver.openOutputStream(audioDocFile.uri)?.use { outputStream ->
                                        tempFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                    tempFile.delete() // Clean up local temporary sandbox file
                                    finalAudioLink = audioDocFile.uri.toString() // Save the public content:// Uri
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("2ndBrain", "Failed to copy audio file to Obsidian Vault", e)
                            }
                        }

                        // Create the Markdown note inside the Obsidian Vault
                        val markdownDocFile = root.createFile("text/markdown", newNoteName)
                        if (markdownDocFile != null) {
                            applicationContext.contentResolver.openOutputStream(markdownDocFile.uri)?.use { stream ->
                                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                
                                // Standard relative wiki attachment notation ensures Obsidian renders an inline audio player natively!
                                val fileContent = """
                                # Voice Note
                                - **Captured**: $dateStr
                                - **Tags**: #audio #voice-capture
                                
                                ---
                                
                                ![[VoiceNote-$timestamp.m4a]]
                                
                                $transcript
                                """.trimIndent()
                                stream.write(fileContent.toByteArray())
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("2ndBrain", "Failed to write voice note directly to Obsidian Vault", e)
                }
            }
            
            // 2. Insert into the local database repository so it is shown on the Feed tab
            // We store the copied audio Uri (or temporary file path) in the deepLink field of the MemoryEntity!
            val entity = MemoryEntity.create(
                source = "voice",
                packageName = null,
                title = "Voice Memo",
                content = transcript,
                deepLink = finalAudioLink
            )
            memoryRepository.insertMemory(entity)
        }
    }
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null
)
