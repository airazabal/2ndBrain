package com.alex.a2ndbrain.core.capture

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClipboardCaptureManager(private val context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val database = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun captureCurrentClipboard() {
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank()) {
                saveToMemory(text)
            }
        }
    }

    private fun saveToMemory(text: String) {
        scope.launch {
            val existing = database.memoryDao().findExisting("clipboard", null, "Copied Text", text)
            if (existing != null) {
                // If it's identical, just update the timestamp and count
                val updated = existing.copy(
                    timestamp = System.currentTimeMillis(),
                    duplicateCount = existing.duplicateCount + 1,
                    isRead = false // Mark as unread if re-copied
                )
                database.memoryDao().insert(updated)
                Log.d("ClipboardCapture", "Updated duplicate: count=${updated.duplicateCount}")
            } else {
                val entity = MemoryEntity.create(
                    source = "clipboard",
                    packageName = null,
                    title = "Copied Text",
                    content = text
                )
                database.memoryDao().insert(entity)
                Log.d("ClipboardCapture", "Captured: ${text.take(20)}...")
            }
        }
    }
}
