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
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager,
    private val reflectionManager: ReflectionManager,
    val healthConnectManager: HealthConnectManager,
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

    init {
        loadVaultNotes()
        observeWorkManagerErrors()
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

    // Health Connect Synchronization (Recommendation 6)
    private val _healthMetricsToday = MutableStateFlow(HealthMetrics())
    val healthMetricsToday = _healthMetricsToday.asStateFlow()

    private val _healthPermissionsGranted = MutableStateFlow(false)
    val healthPermissionsGranted = _healthPermissionsGranted.asStateFlow()

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

    init {
        checkHealthPermissionsAndSync()
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
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null
)
