package com.alex.a2ndbrain.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.agents.ModelRouter
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.deduplicateMemories
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(FlowPreview::class)
class GrandCentralViewModel(
    private val memoryRepository: MemoryRepository,
    private val modelRouter: ModelRouter,
    private val settingsManager: CaptureSettingsManager,
    private val applicationContext: Context
) : ViewModel() {

    private val _monitoredAppsState = MutableStateFlow(settingsManager.getMonitoredApps())
    val monitoredAppsState: StateFlow<Set<String>> = _monitoredAppsState.asStateFlow()

    fun refreshMonitoredApps() {
        _monitoredAppsState.value = settingsManager.getMonitoredApps()
    }

    private val allMemories = memoryRepository.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val unreadEmailCount: StateFlow<Int> = combine(allMemories, _monitoredAppsState) { memories, monitored ->
        val startOfToday = startOfToday()
        val emailMemories = memories.filter { m ->
            m.source == "notification" && m.timestamp >= startOfToday &&
            emailPackages.any { pkg -> m.packageName == pkg || m.packageName?.startsWith("$pkg.") == true } &&
            (monitored.isEmpty() || monitored.contains(m.packageName))
        }
        deduplicateMemories(emailMemories).count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val unreadMessageCount: StateFlow<Int> = combine(allMemories, _monitoredAppsState) { memories, monitored ->
        val startOfToday = startOfToday()
        val todayMessaging = memories.filter { m ->
            m.source == "notification" && m.timestamp >= startOfToday &&
            messagingPackages.any { pkg -> m.packageName?.contains(pkg) == true } &&
            (monitored.isEmpty() || monitored.contains(m.packageName))
        }
        deduplicateMemories(todayMessaging).count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _grandCentralResult = MutableStateFlow(GrandCentralResult())
    val grandCentralResult: StateFlow<GrandCentralResult> = _grandCentralResult.asStateFlow()

    init {
        viewModelScope.launch {
            combine(allMemories, _monitoredAppsState) { memories, monitored ->
                val startOfToday = startOfToday()
                memories.filter { m ->
                    m.source == "notification" && m.timestamp >= startOfToday && !m.isRead &&
                    (monitored.isEmpty() || monitored.contains(m.packageName)) &&
                    m.packageName?.contains("calendar") != true &&
                    m.packageName?.contains("agenda") != true
                }
            }
            .debounce(3_000L)
            .distinctUntilChangedBy { items -> items.map { it.id }.toSet() }
            .collect { notifications -> triageAllNotifications(notifications) }
        }
    }

    private suspend fun triageAllNotifications(notifications: List<MemoryEntity>) {
        if (notifications.isEmpty()) {
            _grandCentralResult.value = GrandCentralResult()
            return
        }
        _grandCentralResult.value = GrandCentralResult(isLoading = true)

        val pm = applicationContext.packageManager
        fun appLabel(pkg: String?): String {
            if (pkg.isNullOrEmpty()) return "App"
            return try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            catch (e: Exception) {
                val lp = pkg.lowercase()
                when {
                    lp.contains("gmail") -> "Gmail"; lp.contains("whatsapp") -> "WhatsApp"
                    lp.contains("messaging") -> "Messages"; lp.contains("instagram") -> "Instagram"
                    lp.contains("slack") -> "Slack"; lp.contains("telegram") -> "Telegram"
                    lp.contains("twitter") || lp.contains("x.android") -> "X"
                    lp.contains("facebook") -> "Facebook"; lp.contains("discord") -> "Discord"
                    else -> pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                }
            }
        }

        val items = deduplicateMemories(notifications).map { it.primary }.take(40)
        val snippets = items.joinToString("\n") { m ->
            val app = appLabel(m.packageName)
            val sender = (m.title ?: "Unknown").take(50)
            val body = m.content.take(80).replace("\n", " ")
            "#${m.id} | $app | $sender | $body"
        }

        val prompt = """
You are a personal intelligence assistant. Analyze today's notifications and organize them clearly.

Notifications (id | source app | sender | content):
$snippets

Output EXACTLY in this format — no extra text, no markdown bold, no preamble:

ACTIONS:
#<id>: <one-line action summary>

CATEGORIES:
[<Category Name>] <single emoji>
#<id>: <Source App> — <one-line summary>

Rules:
- ACTIONS: list only items that genuinely need a response or decision (1–5 items max). If none, write "none".
- CATEGORIES: group ALL notifications into 3–7 contextual topic categories derived from content. Every notification must appear in exactly one category.
- Category names should be specific and descriptive (e.g. "Financial Management", "Purchases & Deliveries", "Family & Personal", "Home & Property", "Social & Messaging", "Health & Wellness", "Work & School").
- Each category line: [Category Name] single-emoji, then items below it as #id: App — summary.
- No item should appear more than once.
        """.trimIndent()

        try {
            val (response, _) = modelRouter.run(prompt, ModelRouter.Complexity.MEDIUM, timeoutMs = 25_000L)

            val suggestedActions = mutableListOf<CategorizedNotification>()
            val categories = mutableListOf<NotificationCategory>()
            val idToCategory = mutableMapOf<Long, String>()
            val categoryEmojiMap = mutableMapOf<String, String>()
            val seenIds = mutableSetOf<Long>()
            val idToEntity = items.associateBy { it.id }

            var section = ""
            var currentCategoryName = ""
            var currentEmoji = ""
            var currentItems = mutableListOf<CategorizedNotification>()

            val categoryHeaderRegex = Regex("""^\[(.+)]\s*(\S+)\s*$""")
            val itemRegex = Regex("""^#(\d+):\s*(.+)$""")

            fun flushCategory() {
                if (currentCategoryName.isNotEmpty() && currentItems.isNotEmpty()) {
                    categories.add(NotificationCategory(currentCategoryName, currentEmoji, currentItems.toList()))
                    categoryEmojiMap[currentCategoryName] = currentEmoji
                }
                currentItems = mutableListOf()
            }

            response.lines().forEach { raw ->
                val line = raw.trim()
                when {
                    line.equals("ACTIONS:", ignoreCase = true) -> { flushCategory(); section = "actions" }
                    line.equals("CATEGORIES:", ignoreCase = true) -> { flushCategory(); section = "categories"; currentCategoryName = "" }
                    line.equals("none", ignoreCase = true) -> { /* skip */ }
                    section == "categories" && categoryHeaderRegex.matches(line) -> {
                        flushCategory()
                        val match = categoryHeaderRegex.find(line)!!
                        currentCategoryName = match.groupValues[1].trim()
                        currentEmoji = match.groupValues[2].trim()
                        categoryEmojiMap[currentCategoryName] = currentEmoji
                    }
                    itemRegex.matches(line) -> {
                        val match = itemRegex.find(line)!!
                        val id = match.groupValues[1].toLongOrNull() ?: return@forEach
                        if (!seenIds.add(id)) return@forEach
                        val summary = match.groupValues[2].trim()
                        val sourceApp = appLabel(idToEntity[id]?.packageName)
                        val item = CategorizedNotification(id, summary, sourceApp)
                        if (section == "actions") {
                            suggestedActions.add(item)
                        } else if (section == "categories" && currentCategoryName.isNotEmpty()) {
                            currentItems.add(item)
                            idToCategory[id] = currentCategoryName
                        }
                    }
                }
            }
            flushCategory()

            _grandCentralResult.value = GrandCentralResult(
                isLoading = false,
                suggestedActions = suggestedActions,
                categories = categories,
                idToCategoryMap = idToCategory,
                categoryEmojiMap = categoryEmojiMap
            )
        } catch (e: Exception) {
            android.util.Log.w("GrandCentralViewModel", "triage failed", e)
            _grandCentralResult.value = GrandCentralResult(isLoading = false)
        }
    }

    private fun startOfToday() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private val emailPackages = setOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "me.proton.mail", "ch.protonmail.android",
            "net.thunderbird.android", "org.mozilla.thunderbird"
        )
        private val messagingPackages = setOf(
            "whatsapp", "messaging", "messages", "mms", "sms",
            "messenger", "telegram", "signal", "viber"
        )
    }
}
