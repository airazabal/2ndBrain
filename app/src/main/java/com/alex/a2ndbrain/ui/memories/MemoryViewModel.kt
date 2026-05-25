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
            var finalAudioLink = audioPath

            if (vaultUri.isNotEmpty()) {
                try {
                    val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, android.net.Uri.parse(vaultUri))
                    if (root != null && root.exists() && root.canWrite()) {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                        val audioFileName = "VoiceNote-$timestamp.m4a"
                        val newNoteName = "VoiceNote-$timestamp"
                        
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
                                    tempFile.delete()
                                    finalAudioLink = audioDocFile.uri.toString()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("2ndBrain", "Failed to copy audio file to Obsidian Vault", e)
                            }
                        }

                        val markdownDocFile = root.createFile("text/markdown", newNoteName)
                        if (markdownDocFile != null) {
                            applicationContext.contentResolver.openOutputStream(markdownDocFile.uri)?.use { stream ->
                                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                val dateIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                
                                val fileContent = """
                                ---
                                created: $dateIso
                                tags:
                                  - audio
                                  - voice-capture
                                ---
                                # Voice Note
                                - **Captured**: $dateStr
                                
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
