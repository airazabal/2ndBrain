package com.alex.a2ndbrain.ui.memories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemoryViewModel(
    private val memoryRepository: MemoryRepository,
    private val settingsManager: CaptureSettingsManager,
    private val applicationContext: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val memories: StateFlow<List<MemoryEntity>> = combine(
        memoryRepository.getAllMemoriesFlow(),
        _searchQuery
    ) { allMemories, query ->
        val expandedQuery = when (query.lowercase().trim()) {
            "workout", "fitness", "exercise", "sleep", "heart", "steps", "health" -> "#Health"
            "task", "todoist", "calendar", "meeting", "schedule", "work" -> "#Work"
            "pay", "spent", "money", "bank", "card", "transaction", "finance" -> "#Finance"
            "clipboard", "copied", "url", "copy", "reference" -> "#Reference"
            "gmail", "email", "whatsapp", "message", "chat", "social" -> "#Social"
            else -> query
        }.trim()

        if (expandedQuery.isEmpty()) {
            allMemories
        } else {
            allMemories.filter {
                it.content.contains(expandedQuery, ignoreCase = true) ||
                (it.title?.contains(expandedQuery, ignoreCase = true) == true) ||
                (it.tags?.contains(expandedQuery, ignoreCase = true) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        pruneOldMemories()
    }

    private fun pruneOldMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            val threshold = getRetentionCutoff()
            memoryRepository.pruneOldMemories(threshold)
        }
    }

    private fun getRetentionCutoff(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
        return cal.timeInMillis
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun markAsRead(id: Long) {
        markMultipleAsRead(listOf(id))
    }

    fun markMultipleAsRead(ids: List<Long>) {
        viewModelScope.launch {
            memoryRepository.markMultipleAsRead(ids)
        }
    }

    fun markMultipleAsUnread(ids: List<Long>) {
        viewModelScope.launch {
            memoryRepository.markMultipleAsUnread(ids)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.deleteAllMemories()
        }
    }

    fun saveVoiceNote(transcript: String, audioPath: String, vaultUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("VoiceNote", "saveVoiceNote called. vaultUri empty=${vaultUri.isEmpty()}")
            if (vaultUri.isNotEmpty()) {
                try {
                    val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, android.net.Uri.parse(vaultUri))
                    android.util.Log.d("VoiceNote", "root=$root exists=${root?.exists()} canWrite=${root?.canWrite()}")
                    if (root != null && root.exists() && root.canWrite()) {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                        val newNoteName = "VoiceNote-$timestamp.md"
                        android.util.Log.d("VoiceNote", "Creating file: $newNoteName")

                        val markdownDocFile = root.createFile("text/markdown", newNoteName)
                        android.util.Log.d("VoiceNote", "markdownDocFile=$markdownDocFile uri=${markdownDocFile?.uri} name=${markdownDocFile?.name}")
                        if (markdownDocFile != null) {
                            val stream = applicationContext.contentResolver.openOutputStream(markdownDocFile.uri)
                            android.util.Log.d("VoiceNote", "outputStream=$stream transcript length=${transcript.length} preview='${transcript.take(80)}'")
                            stream?.use {
                                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                val dateIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                val fileContent = buildString {
                                    appendLine("---")
                                    appendLine("created: $dateIso")
                                    appendLine("tags:")
                                    appendLine("  - voice-capture")
                                    appendLine("---")
                                    appendLine("# Voice Note")
                                    appendLine("- **Captured**: $dateStr")
                                    appendLine()
                                    appendLine("---")
                                    appendLine()
                                    appendLine(transcript)
                                }
                                it.write(fileContent.toByteArray())
                                android.util.Log.d("VoiceNote", "Written ${fileContent.length} bytes, starts: '${fileContent.take(40)}'")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VoiceNote", "Failed to write voice note to vault", e)
                }
            }
            
            val entity = MemoryEntity.create(
                source = "voice",
                packageName = null,
                title = "Voice Memo",
                content = transcript,
                deepLink = null
            )
            memoryRepository.insertMemory(entity)
        }
    }
}
