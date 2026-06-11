package com.alex.a2ndbrain.core.mood

import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class MoodRepositoryImpl(
    private val dao: MoodDao,
    private val memoryRepository: MemoryRepository
) : MoodRepository {

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
        val content = buildString {
            append("Mood $mood/5, Energy $energy/5")
            if (note.isNotBlank()) append(": $note")
        }
        try { memoryRepository.insertEpisodicEvent(content, "mood") }
        catch (e: Exception) { /* non-fatal */ }
    }

    override suspend fun getLogsForDate(date: String) = dao.getLogsForDate(date)

    override fun getLogsSinceFlow(sinceDate: String): Flow<List<MoodLogEntity>> =
        dao.getLogsSinceFlow(sinceDate)

    override suspend fun getLogsSince(sinceDate: String) = dao.getLogsSince(sinceDate)

    override suspend fun getLatest() = dao.getLatest()
}
