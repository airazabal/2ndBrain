package com.alex.a2ndbrain.ui.home

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.ConflictSeverity
import com.alex.a2ndbrain.ConflictType
import com.alex.a2ndbrain.TimelineConflict
import com.alex.a2ndbrain.TimelineEvent
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.memory.HabitCompletionEntity
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.core.memory.HabitsDao
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.ConsolidatedUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.alex.a2ndbrain.core.meditation.MeditationManager
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.meditation.ZendenceMeditationRepository

class HomeViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager,
    private val reflectionManager: ReflectionManager,
    val healthConnectManager: HealthConnectManager,
    private val habitsDao: HabitsDao,
    private val applicationContext: Context,
    private val zendenceMeditationRepository: ZendenceMeditationRepository
) : ViewModel() {

    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    val summaries = memoryRepository.getAllSummariesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val usageStats: StateFlow<List<UsageStatEntity>> = usageRepository.getUsageStatsForToday()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val consolidatedUsage: StateFlow<List<ConsolidatedUsage>> = usageStats
        .map { statsList ->
            if (statsList.isEmpty()) return@map emptyList()
            statsList.groupBy { it.packageName }
                .map { (packageName, stats) ->
                    val totalTime = stats.sumOf { it.totalTimeVisibleMs }
                    ConsolidatedUsage(
                        packageName = packageName,
                        totalTimeMs = totalTime,
                        deviceBreakdown = emptyMap(), // Not needed for home chart
                        lastTimestamp = stats.maxOfOrNull { it.lastTimestamp } ?: 0L
                    )
                }.sortedByDescending { it.totalTimeMs }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allMemoriesForHome: StateFlow<List<MemoryEntity>> = memoryRepository.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _meditationSessions = MutableStateFlow<List<MeditationSession>>(emptyList())
    val meditationSessions: StateFlow<List<MeditationSession>> = _meditationSessions.asStateFlow()

    init {
        refreshMeditationSessions()
    }

    fun refreshMeditationSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = zendenceMeditationRepository.loadSessions()
            _meditationSessions.value = sessions
        }
    }

    val meditationStreaks: StateFlow<StreakResult> = meditationSessions.map { sessions ->
        MeditationManager.calculateStreaks(sessions)
    }.stateIn(viewModelScope, SharingStarted.Lazily, StreakResult(0, 0, 0))

    val meditatedToday: StateFlow<Boolean> = meditationSessions.map { sessions ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sessions.any { sdf.format(Date(it.timestamp)) == todayStr }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _vaultNotes = MutableStateFlow<List<DocumentFile>>(emptyList())
    val vaultNotes = _vaultNotes.asStateFlow()

    // Room Database Habits Engine
    val activeHabitsToday: StateFlow<List<HabitEntity>> = habitsDao.getActiveHabits()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val completedHabitIdsToday: StateFlow<Set<String>> = habitsDao.getCompletionsForDate(getTodayDateString())
        .map { completions -> completions.map { it.habitId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

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

    private val _monitoredAppsState = MutableStateFlow(settingsManager.getMonitoredApps())
    val monitoredAppsState = _monitoredAppsState.asStateFlow()

    fun refreshMonitoredApps() {
        _monitoredAppsState.value = settingsManager.getMonitoredApps()
    }

    private val _inlineCopilotResponses = MutableStateFlow<Map<String, String>>(emptyMap())
    val inlineCopilotResponses = _inlineCopilotResponses.asStateFlow()

    private val _inlineCopilotLoading = MutableStateFlow<Set<String>>(emptySet())
    val inlineCopilotLoading = _inlineCopilotLoading.asStateFlow()

    // Health Connect Synchronization
    private val _healthMetricsToday = MutableStateFlow(HealthMetrics())
    val healthMetricsToday = _healthMetricsToday.asStateFlow()

    private val _healthPermissionsGranted = MutableStateFlow(false)
    val healthPermissionsGranted = _healthPermissionsGranted.asStateFlow()

    private val _senseOfDayScore = MutableStateFlow(75)
    val senseOfDayScore = _senseOfDayScore.asStateFlow()

    private val _senseOfDayContext = MutableStateFlow("🎯 Calibrating your day. Complete a daily routine or log a few steps to update your Sense of Day index.")
    val senseOfDayContext = _senseOfDayContext.asStateFlow()

    // Proactive Timeline & Schedule Extractor
    val todayTimelineEvents: StateFlow<List<TimelineEvent>> = combine(
        allMemoriesForHome,
        vaultNotes,
        activeHabitsToday,
        completedHabitIdsToday,
        monitoredAppsState
    ) { memories, notes, habits, completedIds, monitoredApps ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val timelineList = mutableListOf<TimelineEvent>()
        
        // 1. Process Database Notification & Manual Memories
        val databaseEvents = memories.filter { mem ->
            val isMonitored = if (mem.source == "notification" && mem.packageName != null) {
                monitoredApps.isEmpty() || monitoredApps.contains(mem.packageName)
            } else {
                true
            }
            
            isMonitored && mem.timestamp >= startOfToday && (
                mem.packageName?.contains("calendar") == true ||
                mem.packageName?.contains("outlook") == true ||
                mem.packageName?.contains("agenda") == true ||
                mem.content.lowercase().contains("meeting") ||
                mem.content.lowercase().contains("appointment") ||
                mem.content.lowercase().contains("schedule") ||
                mem.content.lowercase().contains("scheduled") ||
                mem.content.lowercase().contains("meet") ||
                mem.content.lowercase().contains("reminder")
            )
        }.mapNotNull { mem ->
            val timeMatch = findTimePattern(mem.content) ?: findTimePattern(mem.title ?: "")
            val (timeStr, minutes) = if (timeMatch != null) {
                timeMatch
            } else {
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val timeStr = timeFormat.format(Date(mem.timestamp))
                val cal = Calendar.getInstance().apply { timeInMillis = mem.timestamp }
                val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                Pair(timeStr, minutes)
            }
            
            val cleanTitle = mem.title ?: if (mem.content.length > 35) mem.content.take(35) + "..." else mem.content
            val appName = when {
                mem.packageName?.contains("calendar") == true -> "Calendar"
                mem.packageName?.contains("outlook") == true -> "Outlook"
                mem.packageName?.contains("agenda") == true -> "Cockpit"
                else -> "System"
            }
            val sourcePackage = when {
                mem.packageName?.contains("calendar") == true -> "calendar"
                mem.packageName?.contains("outlook") == true -> "calendar"
                mem.packageName?.contains("agenda") == true -> "manual"
                else -> "system"
            }
            
            TimelineEvent(
                id = mem.id.toString(),
                time = timeStr,
                title = cleanTitle,
                description = mem.content,
                appName = appName,
                sourcePackage = sourcePackage,
                minutesFromMidnight = minutes
            )
        }
        timelineList.addAll(databaseEvents)
        
        // 2. Process Obsidian Vault Notes
        val isObsidianMonitored = monitoredApps.isEmpty() || monitoredApps.contains("md.obsidian")
        if (isObsidianMonitored) {
            val recentNotes = notes.take(5)
            recentNotes.forEach { note ->
                try {
                    applicationContext.contentResolver.openInputStream(note.uri)?.use { inputStream ->
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                        var line = reader.readLine()
                        while (line != null) {
                            val timeMatch = findTimePattern(line)
                            if (timeMatch != null) {
                                val cleanTitle = cleanAgendaLine(line, timeMatch.first)
                                val deterministicId = "obsidian_${note.name.hashCode()}_${timeMatch.first.hashCode()}_${cleanTitle.hashCode()}"
                                timelineList.add(
                                    TimelineEvent(
                                        id = deterministicId,
                                        time = timeMatch.first,
                                        title = cleanTitle,
                                        description = "Captured from Obsidian Note: ${note.name ?: "Unnamed"}\nLine Content: $line",
                                        appName = "Obsidian",
                                        sourcePackage = "obsidian",
                                        minutesFromMidnight = timeMatch.second
                                    )
                                )
                            }
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("2ndBrain", "Failed to parse Obsidian note in timeline: ${note.name}", e)
                }
            }
        }
        
        // 3. Process Scheduled Habits today
        val habitEvents = habits.map { habit ->
            val timeParts = habit.timeString.split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
            val min = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            val totalMinutes = hour * 60 + min
            
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayAmpm = if (hour >= 12) "PM" else "AM"
            val timeStr = "$displayHour:${min.toString().padStart(2, '0')} $displayAmpm"
            
            val isCompleted = completedIds.contains(habit.id)
            val statusText = if (isCompleted) "✓ Completed" else "⏰ Scheduled"
            
            TimelineEvent(
                id = habit.id,
                time = timeStr,
                title = habit.name,
                description = "Daily Routine Habit (${if (habit.isMedication) "Medication" else "Habit"})\nStatus: $statusText",
                appName = "Routines",
                sourcePackage = "habit",
                minutesFromMidnight = totalMinutes
            )
        }
        timelineList.addAll(habitEvents)
        
        timelineList.sortedBy { it.minutesFromMidnight }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _dismissedConflictIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedConflictIds = _dismissedConflictIds.asStateFlow()

    val timelineConflicts: StateFlow<List<TimelineConflict>> = combine(
        todayTimelineEvents,
        activeHabitsToday,
        completedHabitIdsToday,
        usageStats,
        dismissedConflictIds
    ) { events, habits, completedIds, usage, dismissedIds ->
        val conflicts = mutableListOf<TimelineConflict>()
        
        // 1. Overlap Detection (only explicit calendar and manual events within 45 mins)
        val sortedEvents = events.filter { it.sourcePackage == "calendar" || it.sourcePackage == "manual" }.sortedBy { it.minutesFromMidnight }
        for (i in 0 until sortedEvents.size - 1) {
            val ev1 = sortedEvents[i]
            val ev2 = sortedEvents[i+1]
            val diff = ev2.minutesFromMidnight - ev1.minutesFromMidnight
            if (diff in 0..45) { // overlapping or very tight back-to-back
                val id = "overlap_${ev1.id}_${ev2.id}"
                if (!dismissedIds.contains(id)) {
                    conflicts.add(
                        TimelineConflict(
                            id = id,
                            type = ConflictType.OVERLAP,
                            severity = if (diff < 15) ConflictSeverity.ALERT else ConflictSeverity.WARNING,
                            title = "Schedule Crunch",
                            description = "You have '${ev1.title}' and '${ev2.title}' scheduled within ${diff} minutes.",
                            deepDivePrompt = "I have a schedule overlap today between '${ev1.title}' at ${ev1.time} and '${ev2.title}' at ${ev2.time}. Please suggest a strategy to manage this crunch and prioritize my time.",
                            relatedEventIds = listOf(ev1.id, ev2.id)
                        )
                    )
                }
            }
        }
        
        // 2. Overdue Habit/Meds
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        habits.forEach { habit ->
            if (habit.isMedication && !completedIds.contains(habit.id)) {
                val timeParts = habit.timeString.split(":")
                val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
                val min = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                val habitMinutes = hour * 60 + min
                
                // If it's overdue by at least 30 minutes
                if (currentMinutes > habitMinutes + 30) {
                    val id = "overdue_${habit.id}"
                    if (!dismissedIds.contains(id)) {
                        conflicts.add(
                            TimelineConflict(
                                id = id,
                                type = ConflictType.OVERDUE_HABIT,
                                severity = ConflictSeverity.ALERT,
                                title = "Medication Overdue",
                                description = "Your medication '${habit.name}' was scheduled for ${habit.timeString} and is pending.",
                                deepDivePrompt = "I missed my scheduled medication '${habit.name}' which was due at ${habit.timeString}. Can you help me quickly review my schedule so I can take it now and log it?",
                                relatedEventIds = listOf(habit.id)
                            )
                        )
                    }
                }
            }
        }
        
        // 3. Distraction Gap Detection
        val hasWorkEvent = events.any { it.title.lowercase().contains("meeting") || it.title.lowercase().contains("work") || it.title.lowercase().contains("review") || it.title.lowercase().contains("sync") || it.title.lowercase().contains("call") }
        if (hasWorkEvent) {
            var totalSocialTimeMs = 0L
            usage.forEach { stat ->
                val pkg = stat.packageName.lowercase()
                if (pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("tiktok") || pkg.contains("youtube") || pkg.contains("twitter") || pkg.contains("x.android")) {
                    totalSocialTimeMs += stat.totalTimeVisibleMs
                }
            }
            val socialMinutes = totalSocialTimeMs / (1000 * 60)
            if (socialMinutes > 15) {
                val id = "distraction_gap_today"
                if (!dismissedIds.contains(id)) {
                    conflicts.add(
                        TimelineConflict(
                            id = id,
                            type = ConflictType.DISTRACTION_GAP,
                            severity = ConflictSeverity.WARNING,
                            title = "Focus Strain",
                            description = "You've spent ${socialMinutes}m on social media on a deep-work day.",
                            deepDivePrompt = "I have deep work and meetings scheduled today, but I've already spent ${socialMinutes} minutes on social media apps. Suggest a 3-step action plan to reset my focus and get back on track."
                        )
                    )
                }
            }
        }
        
        conflicts
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun dismissConflict(id: String) {
        _dismissedConflictIds.value = _dismissedConflictIds.value + id
    }

    private fun findTimePattern(line: String): Pair<String, Int>? {
        val regex1 = Regex("(?:\\b|@|at\\s+)(\\d{1,2}):(\\d{2})\\s*(AM|PM|am|pm)?\\b")
        val match1 = regex1.find(line)
        if (match1 != null) {
            val hourStr = match1.groupValues[1]
            val minStr = match1.groupValues[2]
            val ampm = match1.groupValues[3]
            
            var hour = hourStr.toIntOrNull() ?: return null
            val min = minStr.toIntOrNull() ?: return null
            
            var parsedTimeStr = "$hour:${min.toString().padStart(2, '0')}"
            
            if (ampm.isNotBlank()) {
                val suffix = ampm.uppercase()
                parsedTimeStr += " $suffix"
                if (suffix == "PM" && hour < 12) hour += 12
                if (suffix == "AM" && hour == 12) hour = 0
            } else {
                if (hour >= 24) return null
            }
            val totalMinutes = hour * 60 + min
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayAmpm = if (hour >= 12) "PM" else "AM"
            val cleanDisplayStr = "$displayHour:${min.toString().padStart(2, '0')} $displayAmpm"
            return Pair(cleanDisplayStr, totalMinutes)
        }
        
        val regex2 = Regex("(?:\\b|@|at\\s+)(\\d{1,2})\\s*(AM|PM|am|pm)\\b")
        val match2 = regex2.find(line)
        if (match2 != null) {
            val hourStr = match2.groupValues[1]
            val ampm = match2.groupValues[2]
            
            var hour = hourStr.toIntOrNull() ?: return null
            if (hour > 12) return null
            
            val suffix = ampm.uppercase()
            val totalMinutes = when {
                suffix == "PM" && hour < 12 -> (hour + 12) * 60
                suffix == "AM" && hour == 12 -> 0
                else -> hour * 60
            }
            val cleanDisplayStr = "$hour:00 $suffix"
            return Pair(cleanDisplayStr, totalMinutes)
        }
        
        return null
    }

    private fun cleanAgendaLine(line: String, timeStr: String): String {
        var cleaned = line
            .replace(Regex("^\\s*[-*+]\\s*(\\[[ xX]])?\\s*"), "")
            .replace(Regex("(?:@|at\\s+)?\\b\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?\\b"), "")
            .replace(Regex("(?:@|at\\s+)?\\b\\d{1,2}\\s*(?:AM|PM|am|pm)\\b"), "")
            .replace(Regex("\\b(?:at|@)\\b"), "")
            .trim()
        if (cleaned.isBlank()) {
            val rawSnippet = line.replace(Regex("^\\s*[-*+]\\s*(\\[[ xX]])?\\s*"), "").trim()
            cleaned = if (rawSnippet.isNotEmpty() && rawSnippet != timeStr) {
                rawSnippet.take(25) + if (rawSnippet.length > 25) "..." else ""
            } else {
                "Scheduled Time Block"
            }
        }
        return cleaned
    }

    fun addManualAgendaEvent(title: String, timeString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = "$title @ $timeString"
            val memory = MemoryEntity.create(
                source = "agenda",
                packageName = "com.alex.a2ndbrain.agenda",
                title = title,
                content = content
            )
            memoryRepository.insertMemory(memory)
        }
    }

    fun deleteManualAgendaEvent(idString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = idString.toLongOrNull()
            if (id != null) {
                memoryRepository.deleteMemoryById(id)
            }
        }
    }

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

    fun resolveInlineCopilot(eventId: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _inlineCopilotLoading.update { it + eventId }
            val (responseText, _) = reflectionManager.runChatInference(prompt)
            _inlineCopilotResponses.update { it + (eventId to responseText) }
            _inlineCopilotLoading.update { it - eventId }
        }
    }

    private fun updateSenseOfDayScore() {
        viewModelScope.launch(Dispatchers.Default) {
            val active = activeHabitsToday.value.size.coerceAtLeast(1)
            val completed = completedHabitIdsToday.value.size
            val habitsRatio = completed.toFloat() / active.toFloat()
            
            val steps = _healthMetricsToday.value.steps
            val stepsRatio = (steps.toFloat() / 10000f).coerceIn(0f, 1f)
            
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
            
            val meditated = meditatedToday.value
            val calculated = (habitsRatio * 25f) + (stepsRatio * 25f) + (focusRatio * 30f) + (if (meditated) 20f else 0f)
            _senseOfDayScore.value = calculated.toInt().coerceIn(10, 100)
            
            val contextText = when {
                meditated && stepsRatio >= 0.7f && habitsRatio >= 0.7f && focusRatio >= 0.7f -> {
                    "🧘 A masterclass in balance! Meditated, active routines completed, excellent focus, and physical energy."
                }
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
        checkHealthPermissionsAndSync()

        // Recalculate score whenever habits, metrics, usages, or meditation shifts
        viewModelScope.launch {
            combine(completedHabitIdsToday, activeHabitsToday, _healthMetricsToday, usageStats, meditatedToday) { _, _, _, _, _ -> }
                .collect {
                    updateSenseOfDayScore()
                }
        }
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

    fun checkHealthPermissionsAndSync() {
        refreshMonitoredApps()
        viewModelScope.launch(Dispatchers.IO) {
            val granted = healthConnectManager.hasPermissions()
            _healthPermissionsGranted.value = granted
            if (granted) {
                val metrics = healthConnectManager.fetchHealthMetricsToday()
                _healthMetricsToday.value = metrics
            }
        }
    }
}
