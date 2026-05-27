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
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.agents.ModelRouter
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.ConsolidatedUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.alex.a2ndbrain.core.meditation.MeditationManager
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.meditation.ZendenceMeditationRepository
import com.alex.a2ndbrain.core.sync.NearbySyncManager
import com.alex.a2ndbrain.core.todoist.TodoistRepository
import com.alex.a2ndbrain.core.todoist.TodoistTask

data class EmailTriageResult(
    val isLoading: Boolean = false,
    val critical: List<String> = emptyList(),
    val overdue: List<String> = emptyList()
)

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager,
    private val reflectionManager: ReflectionManager,
    private val applicationContext: Context,
    private val nearbySyncManager: NearbySyncManager,
    private val zendenceMeditationRepository: ZendenceMeditationRepository,
    private val healthRepository: HealthRepository,
    private val modelRouter: ModelRouter,
    private val todoistRepository: TodoistRepository
) : ViewModel() {
    val healthConnectManager get() = healthRepository.healthConnectManager

    private val _lastRefreshedAt = MutableStateFlow(System.currentTimeMillis())
    val lastRefreshedAt: StateFlow<Long> = _lastRefreshedAt.asStateFlow()

    private val _refreshIntervalMinutes = MutableStateFlow(settingsManager.getRefreshIntervalMinutes())
    val refreshIntervalMinutes: StateFlow<Int> = _refreshIntervalMinutes.asStateFlow()

    fun setRefreshInterval(minutes: Int) {
        settingsManager.setRefreshIntervalMinutes(minutes)
        _refreshIntervalMinutes.value = minutes
    }

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

    // Ticks every minute so time-sensitive UI (conflict expiry, etc.) stays current
    private val _minuteTicker: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); kotlinx.coroutines.delay(60_000L) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    // ── Todoist ──────────────────────────────────────────────────────────────
    private val _todoistTasks = MutableStateFlow<List<TodoistTask>>(emptyList())
    val todoistTasks: StateFlow<List<TodoistTask>> = _todoistTasks.asStateFlow()

    private val _todoistLoading = MutableStateFlow(false)
    val todoistLoading: StateFlow<Boolean> = _todoistLoading.asStateFlow()

    init {
        refreshTodoistTasks()
    }

    fun refreshTodoistTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            _todoistLoading.value = true
            val tasks = todoistRepository.getTodayTasks()
            _todoistTasks.value = tasks
            _todoistLoading.value = false
            if (tasks.isNotEmpty()) maybeFireTodoistReminder(tasks)
        }
    }

    private fun maybeFireTodoistReminder(tasks: List<com.alex.a2ndbrain.core.todoist.TodoistTask>) {
        val prefs = applicationContext.getSharedPreferences("todoist_reminder_prefs", android.content.Context.MODE_PRIVATE)
        val lastMs = prefs.getLong("last_notified_ms", 0L)
        if (System.currentTimeMillis() - lastMs < 60 * 60 * 1000L) return

        val nm = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "task_reminders_v2"
        nm.createNotificationChannel(
            android.app.NotificationChannel(channelId, "Todoist Reminders", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hourly reminders for incomplete tasks due today."
            }
        )
        val openIntent = android.app.PendingIntent.getActivity(
            applicationContext, 9001,
            android.content.Intent(applicationContext, com.alex.a2ndbrain.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (tasks.size == 1) "1 task still pending" else "${tasks.size} tasks still pending"
        val body = tasks.take(5).joinToString(" · ") { it.content }
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        nm.notify(9001, notification)
        prefs.edit().putLong("last_notified_ms", System.currentTimeMillis()).apply()
    }

    fun completeTodoistTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = todoistRepository.closeTask(taskId)
            if (ok) {
                _todoistTasks.value = _todoistTasks.value.filter { it.id != taskId }
            }
        }
    }

    private val _monitoredAppsState = MutableStateFlow(settingsManager.getMonitoredApps())
    val monitoredAppsState = _monitoredAppsState.asStateFlow()

    val unreadEmailCount: StateFlow<Int> = combine(allMemoriesForHome, _monitoredAppsState) { memories, monitored ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val emailMemories = memories.filter { m ->
            m.source == "notification" && m.timestamp >= startOfToday && !m.isRead &&
            emailPackages.any { pkg -> m.packageName?.contains(pkg) == true } &&
            (monitored.isEmpty() || monitored.contains(m.packageName))
        }
        deduplicateEmailCount(emailMemories)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val unreadMessageCount: StateFlow<Int> = combine(allMemoriesForHome, _monitoredAppsState) { memories, monitored ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        memories.filter { m ->
            m.source == "notification" && m.timestamp >= startOfToday &&
            messagingPackages.any { pkg -> m.packageName?.contains(pkg) == true } &&
            (monitored.isEmpty() || monitored.contains(m.packageName))
        }.distinctBy { "${it.packageName}-${it.title?.trim()}" }.size
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _meditationSessions = MutableStateFlow<List<MeditationSession>>(emptyList())
    val meditationSessions: StateFlow<List<MeditationSession>> = _meditationSessions.asStateFlow()

    private val _emailTriageResult = MutableStateFlow(EmailTriageResult())
    val emailTriageResult: StateFlow<EmailTriageResult> = _emailTriageResult.asStateFlow()

    init {
        refreshMeditationSessions()
        // Observe automatic meditation sync trigger
        viewModelScope.launch {
            nearbySyncManager.meditationSyncTrigger.collect {
                android.util.Log.d("HomeViewModel", "Meditation sync triggered, refreshing sessions")
                refreshMeditationSessions()
            }
        }
        // Auto-triage unread emails whenever the set changes
        viewModelScope.launch {
            combine(allMemoriesForHome, _monitoredAppsState) { memories, monitored ->
                val startOfToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                memories.filter { m ->
                    m.source == "notification" && m.timestamp >= startOfToday && !m.isRead &&
                    emailPackages.any { pkg -> m.packageName?.contains(pkg) == true } &&
                    (monitored.isEmpty() || monitored.contains(m.packageName))
                }
            }
            .debounce(3_000L)
            .distinctUntilChangedBy { emails -> emails.map { it.id }.toSet() }
            .collect { emails -> triageEmails(emails) }
        }
    }

    private suspend fun triageEmails(emails: List<com.alex.a2ndbrain.core.memory.MemoryEntity>) {
        if (emails.isEmpty()) {
            _emailTriageResult.value = EmailTriageResult()
            return
        }
        _emailTriageResult.value = EmailTriageResult(isLoading = true)
        val snippets = emails.take(20).joinToString("\n") { m ->
            val sender = (m.title ?: "Unknown").take(40)
            val body = (m.content).take(80)
            "- From: $sender | $body"
        }
        val prompt = """
You are reviewing ${emails.size} unread email notification(s). Identify only the ones that are CRITICAL (need immediate action or response) or OVERDUE (past a deadline or time-sensitive). Ignore newsletters, promotions, automated alerts, and FYI emails.

Emails:
$snippets

For each actionable email output exactly ONE line in this format (nothing else):
CRITICAL|Brief description of what needs action
OVERDUE|Brief description of what is overdue

If no emails need attention, output exactly: NONE
        """.trimIndent()
        try {
            val (response, _) = modelRouter.run(prompt, ModelRouter.Complexity.MEDIUM, timeoutMs = 20_000L)
            val critical = mutableListOf<String>()
            val overdue = mutableListOf<String>()
            response.lines().forEach { line ->
                when {
                    line.startsWith("CRITICAL|") -> critical.add(line.removePrefix("CRITICAL|").trim())
                    line.startsWith("OVERDUE|") -> overdue.add(line.removePrefix("OVERDUE|").trim())
                }
            }
            _emailTriageResult.value = EmailTriageResult(isLoading = false, critical = critical, overdue = overdue)
        } catch (e: Exception) {
            android.util.Log.w("HomeViewModel", "Email triage failed", e)
            _emailTriageResult.value = EmailTriageResult(isLoading = false)
        }
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

    private val _senseOfDayContext = MutableStateFlow("Calibrating your day. Log a few steps or meditate to update your Sense of Day index.")
    val senseOfDayContext = _senseOfDayContext.asStateFlow()

    // Proactive Timeline & Schedule Extractor
    val todayTimelineEvents: StateFlow<List<TimelineEvent>> = combine(
        allMemoriesForHome,
        vaultNotes,
        monitoredAppsState
    ) { memories, notes, monitoredApps ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timelineList = mutableListOf<TimelineEvent>()

        // 1. Process Database Notification & Manual Memories
        // Calendar packages are excluded here — CalendarContract (section 3) is the authoritative source.
        val calendarPackageKeywords = setOf("calendar", "agenda")
        val databaseEvents = memories.filter { mem ->
            val isCalendarApp = mem.packageName != null &&
                calendarPackageKeywords.any { mem.packageName!!.contains(it) }
            if (isCalendarApp) return@filter false  // handled by CalendarContract query

            val isMonitored = if (mem.source == "notification" && mem.packageName != null) {
                monitoredApps.isEmpty() || monitoredApps.contains(mem.packageName)
            } else {
                true
            }

            isMonitored && mem.timestamp >= startOfToday && (
                mem.packageName?.contains("outlook") == true ||
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

        // 3. Read today's events directly from Android Calendar Provider (Google accounts only)
        timelineList.addAll(queryGoogleCalendarEvents(startOfToday, startOfToday + DAY_MS))

        val cleanRegex = Regex("[^a-z0-9 ]")

        // Normalize all titles once — avoids recompiling the regex O(n²) times
        val normalizedTitles = timelineList.map { event ->
            event.title.trim().lowercase(Locale.getDefault()).replace(cleanRegex, "")
        }

        // title → accepted minute values (condition A: exact match within 15 min)
        val acceptedMinutesByTitle = HashMap<String, MutableList<Int>>()
        // minute → accepted normalized titles (condition B: partial containment, same minute)
        val acceptedTitlesByMinute = HashMap<Int, MutableList<String>>()

        val uniqueEvents = mutableListOf<TimelineEvent>()

        for (i in timelineList.indices) {
            val event = timelineList[i]
            val norm = normalizedTitles[i]
            val minute = event.minutesFromMidnight

            // Condition A: exact title, within 15 minutes of any already-accepted event
            val isDuplicateA = acceptedMinutesByTitle[norm]
                ?.any { Math.abs(it - minute) < 15 } == true

            // Condition B: one title contains the other, at the exact same minute
            val isDuplicateB = !isDuplicateA && norm.length >= 4 &&
                    acceptedTitlesByMinute[minute]?.any { existing ->
                        existing.length >= 4 && (existing.contains(norm) || norm.contains(existing))
                    } == true

            if (!isDuplicateA && !isDuplicateB) {
                uniqueEvents.add(event)
                acceptedMinutesByTitle.getOrPut(norm) { mutableListOf() }.add(minute)
                acceptedTitlesByMinute.getOrPut(minute) { mutableListOf() }.add(norm)
            }
        }

        uniqueEvents.sortedBy { it.minutesFromMidnight }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tomorrowTimelineEvents: StateFlow<List<TimelineEvent>> = allMemoriesForHome
        .map { _ ->
            val tomorrowStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val events = mutableListOf<TimelineEvent>()
            events.addAll(queryGoogleCalendarEvents(tomorrowStart, tomorrowStart + DAY_MS))
            events.sortedBy { it.minutesFromMidnight }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun queryGoogleCalendarEvents(startMs: Long, endMs: Long): List<TimelineEvent> {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.READ_CALENDAR)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        return try {
            val googleCalIds = mutableListOf<Long>()
            applicationContext.contentResolver.query(
                android.provider.CalendarContract.Calendars.CONTENT_URI,
                arrayOf(android.provider.CalendarContract.Calendars._ID),
                "${android.provider.CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
                arrayOf("com.google"), null
            )?.use { c ->
                val col = c.getColumnIndex(android.provider.CalendarContract.Calendars._ID)
                while (c.moveToNext()) googleCalIds.add(c.getLong(col))
            }
            if (googleCalIds.isEmpty()) return emptyList()
            val calIdIn = googleCalIds.joinToString(",")
            val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(startMs.toString()).appendPath(endMs.toString()).build()
            val projection = arrayOf(
                android.provider.CalendarContract.Instances.EVENT_ID,
                android.provider.CalendarContract.Instances.TITLE,
                android.provider.CalendarContract.Instances.DESCRIPTION,
                android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.ALL_DAY,
                android.provider.CalendarContract.Instances.CALENDAR_DISPLAY_NAME
            )
            val results = mutableListOf<TimelineEvent>()
            applicationContext.contentResolver.query(
                uri, projection,
                "${android.provider.CalendarContract.Instances.ALL_DAY} = 0 AND " +
                    "${android.provider.CalendarContract.Instances.CALENDAR_ID} IN ($calIdIn)",
                null,
                "${android.provider.CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.TITLE)
                val descIdx  = cursor.getColumnIndex(android.provider.CalendarContract.Instances.DESCRIPTION)
                val beginIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                val calIdx   = cursor.getColumnIndex(android.provider.CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val idIdx    = cursor.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIdx)?.ifBlank { null } ?: continue
                    val beginMs = cursor.getLong(beginIdx)
                    val calName = cursor.getString(calIdx) ?: "Google Calendar"
                    val eventId = cursor.getLong(idIdx)
                    val description = cursor.getString(descIdx)?.ifBlank { null } ?: "Calendar event"
                    val cal = Calendar.getInstance().apply { timeInMillis = beginMs }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val min  = cal.get(Calendar.MINUTE)
                    val dispHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                    val ampm = if (hour >= 12) "PM" else "AM"
                    results.add(TimelineEvent(
                        id = "cal_${eventId}_$beginMs",
                        time = "$dispHour:${min.toString().padStart(2, '0')} $ampm",
                        title = title,
                        description = description,
                        appName = calName,
                        sourcePackage = "calendar",
                        minutesFromMidnight = hour * 60 + min
                    ))
                }
            }
            results
        } catch (e: Exception) {
            android.util.Log.e("2ndBrain", "Calendar provider query failed", e)
            emptyList()
        }
    }

    val meetingsTodayCount: StateFlow<Int> = combine(todayTimelineEvents, _todoistTasks) { events, tasks ->
        events.count { it.sourcePackage == "calendar" } + tasks.size
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        // Gmail package is com.google.android.gm — NOT "gmail"
        private val emailPackages = setOf("google.android.gm", "outlook", "yahoo.mail", "protonmail", "hotmail", "thunderbird")
        private val messagingPackages = setOf("whatsapp", "messaging", "messages", "mms", "sms", "messenger", "telegram", "signal", "viber")

        // Mirrors MemoryScreen.deduplicateMemories — keeps card count in sync with feed's group count.
        fun deduplicateEmailCount(memories: List<com.alex.a2ndbrain.core.memory.MemoryEntity>): Int {
            val sorted = memories.sortedByDescending { it.timestamp }
            val groupPrimaries = mutableListOf<com.alex.a2ndbrain.core.memory.MemoryEntity>()
            val groupRead = mutableListOf<Boolean>()

            for (item in sorted) {
                val existingIdx = groupPrimaries.indexOfFirst { existing ->
                    if (existing.packageName != item.packageName) return@indexOfFirst false
                    val sim = emailSimilarity(
                        existing.content.take(200), item.content.take(200)
                    )
                    val titleSim = emailSimilarity(
                        (existing.title ?: "").take(100), (item.title ?: "").take(100)
                    )
                    val existingLines = existing.content.split("\n").filter { it.isNotBlank() }
                    val itemLines = item.content.split("\n").filter { it.isNotBlank() }
                    val lineOverlap = existingLines.isNotEmpty() && itemLines.isNotEmpty() &&
                        (existingLines.intersect(itemLines.toSet()).size.toFloat() / itemLines.size) > 0.5f
                    val isGmailSummary = (existing.packageName ?: "").contains("gm") &&
                        (existing.title ?: "").contains("messages") &&
                        (item.title ?: "").contains("messages")
                    when {
                        existing.content == item.content -> true
                        sim > 0.8 && (existing.title == item.title || titleSim > 0.8) -> true
                        existing.content.take(15) == item.content.take(15) -> true
                        lineOverlap || isGmailSummary -> true
                        else -> false
                    }
                }
                if (existingIdx != -1) {
                    groupRead[existingIdx] = groupRead[existingIdx] && item.isRead
                } else {
                    groupPrimaries.add(item)
                    groupRead.add(item.isRead)
                }
            }
            return groupRead.count { !it }
        }

        private fun emailSimilarity(s1: String, s2: String): Double {
            if (s1 == s2) return 1.0
            if (s1.isEmpty() || s2.isEmpty()) return 0.0
            val maxLen = maxOf(s1.length, s2.length)
            val dist = emailLevenshtein(s1, s2)
            return (maxLen - dist).toDouble() / maxLen
        }

        private fun emailLevenshtein(s1: String, s2: String): Int {
            val dp = IntArray(s2.length + 1) { it }
            for (i in 1..s1.length) {
                var prev = dp[0]; dp[0] = i
                for (j in 1..s2.length) {
                    val temp = dp[j]
                    dp[j] = if (s1[i - 1] == s2[j - 1]) prev
                            else minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
                    prev = temp
                }
            }
            return dp[s2.length]
        }
    }

    private val _conflictPrefs = applicationContext.getSharedPreferences("dismissed_conflicts", android.content.Context.MODE_PRIVATE)
    // Persist dismissed IDs keyed by today's date so they auto-clear at midnight
    private fun persistedDismissedIds(): Set<String> {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return _conflictPrefs.getStringSet("dismissed_$today", emptySet()) ?: emptySet()
    }
    private fun persistDismiss(id: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val key = "dismissed_$today"
        val current = _conflictPrefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        _conflictPrefs.edit().putStringSet(key, current).apply()
    }

    private val _dismissedConflictIds = MutableStateFlow<Set<String>>(persistedDismissedIds())
    val dismissedConflictIds = _dismissedConflictIds.asStateFlow()

    // Raw local conflicts (no dismiss filter) — pushed to NearbySyncManager for peer sync
    private val _localComputedConflicts: StateFlow<List<TimelineConflict>> = combine(
        todayTimelineEvents,
        usageStats,
        _minuteTicker
    ) { events, usage, _ ->
        val conflicts = mutableListOf<TimelineConflict>()
        val nowCal = Calendar.getInstance()
        val currentMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        // 1. Overlap Detection (only explicit calendar and manual events within 45 mins)
        val sortedEvents = events.filter { it.sourcePackage == "calendar" || it.sourcePackage == "manual" }.sortedBy { it.minutesFromMidnight }
        for (i in 0 until sortedEvents.size - 1) {
            val ev1 = sortedEvents[i]
            val ev2 = sortedEvents[i+1]
            val diff = ev2.minutesFromMidnight - ev1.minutesFromMidnight
            // Skip if both events are already in the past — conflict is no longer actionable
            if (diff in 0..45 && ev2.minutesFromMidnight >= currentMinutes) {
                conflicts.add(
                    TimelineConflict(
                        id = "overlap_${ev1.id}_${ev2.id}",
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

        // 2. Distraction Gap Detection
        val hasWorkEvent = events.any {
            val t = it.title.lowercase()
            t.contains("meeting") || t.contains("work") || t.contains("review") || t.contains("sync") || t.contains("call")
        }
        if (hasWorkEvent) {
            var totalSocialTimeMs = 0L
            usage.forEach { stat ->
                val pkg = stat.packageName.lowercase()
                if (pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("tiktok") ||
                    pkg.contains("youtube") || pkg.contains("twitter") || pkg.contains("x.android")) {
                    totalSocialTimeMs += stat.totalTimeVisibleMs
                }
            }
            val socialMinutes = totalSocialTimeMs / (1000 * 60)
            if (socialMinutes > 15) {
                conflicts.add(
                    TimelineConflict(
                        id = "distraction_gap_today",
                        type = ConflictType.DISTRACTION_GAP,
                        severity = ConflictSeverity.WARNING,
                        title = "Focus Strain",
                        description = "You've spent ${socialMinutes}m on social media on a deep-work day.",
                        deepDivePrompt = "I have deep work and meetings scheduled today, but I've already spent ${socialMinutes} minutes on social media apps. Suggest a 3-step action plan to reset my focus and get back on track."
                    )
                )
            }
        }

        conflicts
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Merge local + remote (dedup by id) then apply dismiss filter
    val timelineConflicts: StateFlow<List<TimelineConflict>> = combine(
        _localComputedConflicts,
        nearbySyncManager.remoteConflicts,
        dismissedConflictIds
    ) { local, remote, dismissed ->
        val localIds = local.map { it.id }.toSet()
        (local + remote.filter { it.id !in localIds })
            .filter { it.id !in dismissed }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun dismissConflict(id: String) {
        _dismissedConflictIds.value = _dismissedConflictIds.value + id
        persistDismiss(id)
        nearbySyncManager.addDismissedConflict(id)
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

        // regex3: 4-digit military time without colon, requires explicit "at" or "@" prefix
        val regex3 = Regex("(?:@|at\\s+)([01]\\d|2[0-3])([0-5]\\d)\\b")
        val match3 = regex3.find(line)
        if (match3 != null) {
            val hour = match3.groupValues[1].toIntOrNull() ?: return null
            val min = match3.groupValues[2].toIntOrNull() ?: return null
            val totalMinutes = hour * 60 + min
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayAmpm = if (hour >= 12) "PM" else "AM"
            val cleanDisplayStr = "$displayHour:${min.toString().padStart(2, '0')} $displayAmpm"
            return Pair(cleanDisplayStr, totalMinutes)
        }

        return null
    }

    private fun cleanAgendaLine(line: String, timeStr: String): String {
        var cleaned = line
            .replace(Regex("^\\s*[-*+]\\s*(\\[[ xX]])?\\s*"), "")
            .replace(Regex("(?:@|at\\s+)?\\b\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?\\b"), "")
            .replace(Regex("(?:@|at\\s+)?\\b\\d{1,2}\\s*(?:AM|PM|am|pm)\\b"), "")
            .replace(Regex("(?:@|at\\s+)(?:[01]\\d|2[0-3])[0-5]\\d\\b"), "")
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
            val calculated = (stepsRatio * 50f) + (focusRatio * 30f) + (if (meditated) 20f else 0f)
            _senseOfDayScore.value = calculated.toInt().coerceIn(10, 100)

            val contextText = when {
                meditated && stepsRatio >= 0.7f && focusRatio >= 0.7f -> {
                    "A masterclass in balance! Meditated, physically active, and excellent focus."
                }
                stepsRatio >= 0.7f && focusRatio >= 0.7f -> {
                    "A great day so far! High physical activity and excellent focus."
                }
                stepsRatio < 0.4f && focusRatio >= 0.7f -> {
                    "Checked off focus work, but you've been stationary. Step away for a brisk 10-minute stretch!"
                }
                focusRatio < 0.4f -> {
                    "Digital distractions are high today. Set a quick focus timer and reconnect with your space."
                }
                steps == 0L -> {
                    "Calibrating your day. Log a few steps or meditate to update your Sense of Day index."
                }
                else -> {
                    "Balanced progress! Keep a healthy physical-digital ratio."
                }
            }
            _senseOfDayContext.value = contextText
        }
    }

    init {
        loadVaultNotes()
        checkHealthPermissionsAndSync()

        // Poll Health Connect every 15 minutes so Zepp syncs are reflected
        // without requiring the user to relaunch the app.
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(15 * 60 * 1000L)
                checkHealthPermissionsAndSync()
            }
        }

        // Recalculate score whenever metrics, usages, or meditation shifts
        viewModelScope.launch {
            combine(_healthMetricsToday, usageStats, meditatedToday) { _, _, _ -> }
                .collect {
                    updateSenseOfDayScore()
                }
        }

        // Keep NearbySyncManager up-to-date with latest local conflicts for peer sync
        viewModelScope.launch {
            _localComputedConflicts.collect { nearbySyncManager.updateLocalConflicts(it) }
        }

        // Apply dismissed IDs received from peer so both devices stay in sync
        viewModelScope.launch {
            nearbySyncManager.remoteDismissedIds.collect { remoteIds ->
                _dismissedConflictIds.value = _dismissedConflictIds.value + remoteIds
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
        _lastRefreshedAt.value = System.currentTimeMillis()
        refreshMonitoredApps()
        viewModelScope.launch(Dispatchers.IO) {
            val hcMetrics = healthRepository.getHCMetricsIfWearable()
            if (hcMetrics != null) {
                _healthPermissionsGranted.value = true
                _healthMetricsToday.value = hcMetrics
                nearbySyncManager.ensureScanning()
                return@launch
            }
            _healthPermissionsGranted.value = false
            nearbySyncManager.requestImmediateSync()
            loadSyncedHealthSnapshot()
        }
    }

    private suspend fun loadSyncedHealthSnapshot() {
        val metrics = healthRepository.getTodayMetrics()
        if (metrics.steps > 0 || metrics.sleepMinutes > 0 || metrics.avgHeartRate > 0) {
            _healthMetricsToday.value = metrics
            _healthPermissionsGranted.value = true
        }
    }

    init {
        viewModelScope.launch {
            nearbySyncManager.healthSyncTrigger.collect {
                loadSyncedHealthSnapshot()
            }
        }
    }
}
