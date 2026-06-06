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
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.memory.deduplicateMemories
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.ConsolidatedUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.alex.a2ndbrain.core.sync.NearbySyncManager

data class SenseOfDayPillar(
    val label: String,
    val value: String,
    val goalText: String,
    val progress: Float
)

// Legacy alias kept so callers not yet migrated still compile
typealias EmailTriageResult = GrandCentralResult

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager,
    private val reflectionManager: ReflectionManager,
    private val applicationContext: Context,
    private val nearbySyncManager: NearbySyncManager
) : ViewModel() {

    private val _lastRefreshedAt = MutableStateFlow(System.currentTimeMillis())
    val lastRefreshedAt: StateFlow<Long> = _lastRefreshedAt.asStateFlow()

    private val _refreshIntervalMinutes = MutableStateFlow(settingsManager.getRefreshIntervalMinutes())
    val refreshIntervalMinutes: StateFlow<Int> = _refreshIntervalMinutes.asStateFlow()

    fun setRefreshInterval(minutes: Int) {
        settingsManager.setRefreshIntervalMinutes(minutes)
        _refreshIntervalMinutes.value = minutes
    }

    fun markRefreshed() {
        _lastRefreshedAt.value = System.currentTimeMillis()
    }

    val summaries = memoryRepository.getAllSummariesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Ticks every minute so time-sensitive UI (conflict expiry, date rollover) stays current
    private val _minuteTicker: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); kotlinx.coroutines.delay(60_000L) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    // Re-queries the DB each time the calendar date changes (handles ViewModel surviving midnight)
    val usageStats: StateFlow<List<UsageStatEntity>> = _minuteTicker
        .map { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        .distinctUntilChanged()
        .flatMapLatest { today -> usageRepository.getUsageStatsForDate(today) }
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
                        deviceBreakdown = emptyMap(),
                        lastTimestamp = stats.maxOfOrNull { it.lastTimestamp } ?: 0L
                    )
                }.sortedByDescending { it.totalTimeMs }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allMemoriesForHome: StateFlow<List<MemoryEntity>> = memoryRepository.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _monitoredAppsState = MutableStateFlow(settingsManager.getMonitoredApps())
    val monitoredAppsState = _monitoredAppsState.asStateFlow()

    fun refreshMonitoredApps() {
        _monitoredAppsState.value = settingsManager.getMonitoredApps()
    }

    private val _vaultNotes = MutableStateFlow<List<DocumentFile>>(emptyList())
    val vaultNotes = _vaultNotes.asStateFlow()

    private val _inlineCopilotResponses = MutableStateFlow<Map<String, String>>(emptyMap())
    val inlineCopilotResponses = _inlineCopilotResponses.asStateFlow()

    private val _inlineCopilotLoading = MutableStateFlow<Set<String>>(emptySet())
    val inlineCopilotLoading = _inlineCopilotLoading.asStateFlow()

    // ── Timeline & Schedule ───────────────────────────────────────────────────
    val todayTimelineEvents: StateFlow<List<TimelineEvent>> = combine(
        allMemoriesForHome,
        vaultNotes,
        monitoredAppsState
    ) { memories, notes, monitoredApps ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timelineList = mutableListOf<TimelineEvent>()

        val calendarPackageKeywords = setOf("calendar", "agenda")
        val databaseEvents = memories.filter { mem ->
            val isCalendarApp = mem.packageName != null &&
                calendarPackageKeywords.any { mem.packageName!!.contains(it) }
            if (isCalendarApp) return@filter false

            val isMonitored = if (mem.source == "notification" && mem.packageName != null) {
                monitoredApps.isEmpty() || monitoredApps.contains(mem.packageName)
            } else true

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
                val ts = timeFormat.format(Date(mem.timestamp))
                val cal = Calendar.getInstance().apply { timeInMillis = mem.timestamp }
                val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                Pair(ts, mins)
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

        val isObsidianMonitored = monitoredApps.isEmpty() || monitoredApps.contains("md.obsidian")
        if (isObsidianMonitored) {
            notes.take(5).forEach { note ->
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
                    android.util.Log.e("2ndBrain", "Failed to parse Obsidian note: ${note.name}", e)
                }
            }
        }

        timelineList.addAll(queryGoogleCalendarEvents(startOfToday, startOfToday + DAY_MS))

        val cleanRegex = Regex("[^a-z0-9 ]")
        val normalizedTitles = timelineList.map { it.title.trim().lowercase(Locale.getDefault()).replace(cleanRegex, "") }
        val acceptedMinutesByTitle = HashMap<String, MutableList<Int>>()
        val acceptedTitlesByMinute = HashMap<Int, MutableList<String>>()
        val uniqueEvents = mutableListOf<TimelineEvent>()

        for (i in timelineList.indices) {
            val event = timelineList[i]
            val norm = normalizedTitles[i]
            val minute = event.minutesFromMidnight

            val isDuplicateA = acceptedMinutesByTitle[norm]?.any { Math.abs(it - minute) < 15 } == true
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
        .map { memories ->
            val startOfTomorrow = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val events = mutableListOf<TimelineEvent>()
            queryGoogleCalendarEvents(startOfTomorrow, startOfTomorrow + DAY_MS).forEach { events.add(it) }
            events.sortedBy { it.minutesFromMidnight }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun queryGoogleCalendarEvents(startMs: Long, endMs: Long): List<TimelineEvent> {
        return try {
            val uri = android.net.Uri.parse("content://com.android.calendar/instances/when/$startMs/$endMs")
            val projection = arrayOf("title", "begin", "allDay", "eventId")
            val cursor = applicationContext.contentResolver.query(uri, projection, null, null, "begin ASC")
                ?: return emptyList()
            val results = mutableListOf<TimelineEvent>()
            cursor.use { c ->
                val titleIdx = c.getColumnIndex("title")
                val beginIdx = c.getColumnIndex("begin")
                val allDayIdx = c.getColumnIndex("allDay")
                val eventIdIdx = c.getColumnIndex("eventId")
                while (c.moveToNext()) {
                    val title = c.getString(titleIdx) ?: continue
                    val begin = c.getLong(beginIdx)
                    val allDay = c.getInt(allDayIdx) == 1
                    if (allDay) continue
                    val eventId = if (eventIdIdx >= 0) c.getLong(eventIdIdx).toString() else begin.toString()
                    val cal = Calendar.getInstance().apply { timeInMillis = begin }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val min = cal.get(Calendar.MINUTE)
                    val totalMinutes = hour * 60 + min
                    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val timeStr = "$displayHour:${min.toString().padStart(2, '0')} $ampm"
                    results.add(
                        TimelineEvent(
                            id = "cal_${eventId}_$begin",
                            time = timeStr,
                            title = title,
                            description = title,
                            appName = "Calendar",
                            sourcePackage = "calendar",
                            minutesFromMidnight = totalMinutes
                        )
                    )
                }
            }
            results
        } catch (e: Exception) {
            android.util.Log.e("2ndBrain", "Calendar provider query failed", e)
            emptyList()
        }
    }

    // ── Conflict Detection ────────────────────────────────────────────────────
    private val _conflictPrefs = applicationContext.getSharedPreferences("dismissed_conflicts", Context.MODE_PRIVATE)
    private fun persistedDismissedIds(): Set<String> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return _conflictPrefs.getStringSet("dismissed_$today", emptySet()) ?: emptySet()
    }
    private fun persistDismiss(id: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val key = "dismissed_$today"
        val current = _conflictPrefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        _conflictPrefs.edit().putStringSet(key, current).apply()
    }

    private val _dismissedConflictIds = MutableStateFlow<Set<String>>(persistedDismissedIds())
    val dismissedConflictIds = _dismissedConflictIds.asStateFlow()

    private val _localComputedConflicts: StateFlow<List<TimelineConflict>> = combine(
        todayTimelineEvents,
        usageStats,
        _minuteTicker
    ) { events, usage, _ ->
        val conflicts = mutableListOf<TimelineConflict>()
        val nowCal = Calendar.getInstance()
        val currentMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        // Overlap Detection — surface only the tightest upcoming pair
        val sortedEvents = events.filter { it.sourcePackage == "calendar" || it.sourcePackage == "manual" }.sortedBy { it.minutesFromMidnight }
        var worstDiff = Int.MAX_VALUE
        var worstEv1: TimelineEvent? = null
        var worstEv2: TimelineEvent? = null
        for (i in 0 until sortedEvents.size - 1) {
            val ev1 = sortedEvents[i]
            val ev2 = sortedEvents[i + 1]
            val diff = ev2.minutesFromMidnight - ev1.minutesFromMidnight
            if (diff in 0..45 && ev2.minutesFromMidnight >= currentMinutes && diff < worstDiff) {
                worstDiff = diff; worstEv1 = ev1; worstEv2 = ev2
            }
        }
        if (worstEv1 != null && worstEv2 != null) {
            conflicts.add(
                TimelineConflict(
                    id = "overlap_${worstEv1.id}_${worstEv2.id}",
                    type = ConflictType.OVERLAP,
                    severity = if (worstDiff < 15) ConflictSeverity.ALERT else ConflictSeverity.WARNING,
                    title = "Schedule Crunch",
                    description = "You have '${worstEv1.title}' and '${worstEv2.title}' scheduled within ${worstDiff} minutes.",
                    deepDivePrompt = "I have a schedule overlap today between '${worstEv1.title}' at ${worstEv1.time} and '${worstEv2.title}' at ${worstEv2.time}. Please suggest a strategy to manage this crunch and prioritize my time.",
                    relatedEventIds = listOf(worstEv1.id, worstEv2.id)
                )
            )
        }

        // Distraction Gap Detection
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

    val timelineConflicts: StateFlow<List<TimelineConflict>> = combine(
        _localComputedConflicts,
        nearbySyncManager.remoteConflicts,
        dismissedConflictIds
    ) { local, remote, dismissed ->
        val localIds = local.map { it.id }.toSet()
        (local + remote.filter { it.id !in localIds }).filter { it.id !in dismissed }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun dismissConflict(id: String) {
        _dismissedConflictIds.value = _dismissedConflictIds.value + id
        persistDismiss(id)
        nearbySyncManager.addDismissedConflict(id)
    }

    init {
        loadVaultNotes()
        viewModelScope.launch {
            _localComputedConflicts.collect { nearbySyncManager.updateLocalConflicts(it) }
        }
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
                } catch (e: Exception) { /* invalid URI or permissions */ }
            }
        }
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
            idString.toLongOrNull()?.let { memoryRepository.deleteMemoryById(it) }
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

    private fun findTimePattern(line: String): Pair<String, Int>? {
        val regex1 = Regex("(?:\\b|@|at\\s+)(\\d{1,2}):(\\d{2})\\s*(AM|PM|am|pm)?\\b")
        val match1 = regex1.find(line)
        if (match1 != null) {
            val hourStr = match1.groupValues[1]; val minStr = match1.groupValues[2]; val ampm = match1.groupValues[3]
            var hour = hourStr.toIntOrNull() ?: return null
            val min = minStr.toIntOrNull() ?: return null
            var parsedTimeStr = "$hour:${min.toString().padStart(2, '0')}"
            if (ampm.isNotBlank()) {
                val suffix = ampm.uppercase(); parsedTimeStr += " $suffix"
                if (suffix == "PM" && hour < 12) hour += 12
                if (suffix == "AM" && hour == 12) hour = 0
            } else { if (hour >= 24) return null }
            val totalMinutes = hour * 60 + min
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayAmpm = if (hour >= 12) "PM" else "AM"
            return Pair("$displayHour:${min.toString().padStart(2, '0')} $displayAmpm", totalMinutes)
        }
        val regex2 = Regex("(?:\\b|@|at\\s+)(\\d{1,2})\\s*(AM|PM|am|pm)\\b")
        val match2 = regex2.find(line)
        if (match2 != null) {
            val hourStr = match2.groupValues[1]; val ampm = match2.groupValues[2]
            var hour = hourStr.toIntOrNull() ?: return null
            if (hour > 12) return null
            val suffix = ampm.uppercase()
            val totalMinutes = when { suffix == "PM" && hour < 12 -> (hour + 12) * 60; suffix == "AM" && hour == 12 -> 0; else -> hour * 60 }
            return Pair("$hour:00 $suffix", totalMinutes)
        }
        val regex3 = Regex("(?:@|at\\s+)([01]\\d|2[0-3])([0-5]\\d)\\b")
        val match3 = regex3.find(line)
        if (match3 != null) {
            val hour = match3.groupValues[1].toIntOrNull() ?: return null
            val min = match3.groupValues[2].toIntOrNull() ?: return null
            val totalMinutes = hour * 60 + min
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayAmpm = if (hour >= 12) "PM" else "AM"
            return Pair("$displayHour:${min.toString().padStart(2, '0')} $displayAmpm", totalMinutes)
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
            cleaned = if (rawSnippet.isNotEmpty() && rawSnippet != timeStr)
                rawSnippet.take(25) + if (rawSnippet.length > 25) "..." else ""
            else "Scheduled Time Block"
        }
        return cleaned
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
