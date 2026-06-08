package com.alex.a2ndbrain.core.mood

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class MoodRepositoryImpl(private val dao: MoodDao) : MoodRepository {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override suspend fun logMood(mood: Int, energy: Int, note: String) {
        val now = System.currentTimeMillis()
        dao.insert(
            MoodLogEntity(
                date = dateFmt.format(Date(now)),
                timestamp = now,
                mood = mood,
                energy = energy,
                note = note
            )
        )
    }

    override suspend fun getLogsForDate(date: String) = dao.getLogsForDate(date)

    override fun getLogsSinceFlow(sinceDate: String): Flow<List<MoodLogEntity>> =
        dao.getLogsSinceFlow(sinceDate)

    override suspend fun getLogsSince(sinceDate: String) = dao.getLogsSince(sinceDate)

    override suspend fun getLatest() = dao.getLatest()
}
