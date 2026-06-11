package com.alex.a2ndbrain.core.meditation

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ZendenceMeditationRepository(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val appScope: CoroutineScope
) : MeditationRepository {
    private val contentUri: Uri =
        Uri.parse("content://com.alex.zendence.meditationprovider/meditations")

    override fun loadSessions(): List<MeditationSession> {
        val cr = context.contentResolver
        val sessions = mutableListOf<MeditationSession>()

        val cursor = cr.query(contentUri, null, null, null, "timestamp DESC")
            ?: return emptyList()

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow("id")
            val tsCol = c.getColumnIndexOrThrow("timestamp")
            val durCol = c.getColumnIndexOrThrow("durationMinutes")
            val insightCol = c.getColumnIndexOrThrow("insight")

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val ts = c.getLong(tsCol)
                val dur = c.getInt(durCol)
                val insight = c.getString(insightCol) ?: ""

                sessions += MeditationSession(
                    id = id,
                    durationMinutes = dur,
                    insight = insight,
                    timestamp = ts
                )
            }
        }

        return sessions
    }

    override fun insertSession(session: MeditationSession) {
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put("id", session.id)
            put("durationMinutes", session.durationMinutes)
            put("insight", session.insight)
            put("timestamp", session.timestamp)
        }
        try {
            cr.insert(contentUri, values)
        } catch (e: Exception) {
            android.util.Log.e("ZendenceMeditationRepo", "Failed to insert session", e)
        }
        val content = buildString {
            append("${session.durationMinutes}m meditation session")
            if (!session.insight.isNullOrBlank()) append(": ${session.insight}")
        }
        appScope.launch {
            try { memoryRepository.insertEpisodicEvent(content, "meditation") }
            catch (e: Exception) { /* non-fatal */ }
        }
    }
}
