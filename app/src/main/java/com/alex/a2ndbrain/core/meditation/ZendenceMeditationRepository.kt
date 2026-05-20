package com.alex.a2ndbrain.core.meditation

import android.content.Context
import android.net.Uri

class ZendenceMeditationRepository(
    private val context: Context
) {
    private val contentUri: Uri =
        Uri.parse("content://com.alex.zendence.meditationprovider/meditations")

    fun loadSessions(): List<MeditationSession> {
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
}
