package com.alex.a2ndbrain.fakes

import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.exercise.ExerciseSessionEntity
import com.alex.a2ndbrain.core.exercise.ExerciseType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FakeExerciseRepository : ExerciseRepository {

    val sessions = MutableStateFlow<List<ExerciseSessionEntity>>(emptyList())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override suspend fun logSession(type: ExerciseType, durationMinutes: Int, startedAt: Long, notes: String) {
        sessions.value = sessions.value + ExerciseSessionEntity(
            id = "fake-${System.currentTimeMillis()}",
            deviceId = "test-device",
            type = type.name,
            durationMinutes = durationMinutes,
            startedAt = startedAt,
            notes = notes,
            date = dateFormat.format(Date(if (startedAt > 0L) startedAt else System.currentTimeMillis())),
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )
    }

    override suspend fun deleteSession(id: String) {
        sessions.value = sessions.value.map { if (it.id == id) it.copy(isDeleted = 1) else it }
    }

    override suspend fun updateSession(id: String, type: ExerciseType, durationMinutes: Int, notes: String) {
        sessions.value = sessions.value.map {
            if (it.id == id) it.copy(type = type.name, durationMinutes = durationMinutes, notes = notes) else it
        }
    }

    override fun getAllSessionsFlow(): Flow<List<ExerciseSessionEntity>> = sessions

    override fun getWeeklyConsistency(): Flow<List<Pair<String, Float>>> =
        sessions.map { list ->
            (0..6).map { offset ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -(6 - offset))
                val dateStr = dateFormat.format(cal.time)
                val label = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"; Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"; Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"; Calendar.SATURDAY -> "Sat"
                    else -> "Sun"
                }
                val mins = list.filter { it.isDeleted == 0 && it.date == dateStr }.sumOf { it.durationMinutes }
                label to (mins / 60f).coerceAtMost(1.0f)
            }
        }

    override suspend fun getWeeklySummary(): Pair<Int, Int> {
        val cutoff = run {
            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -6)
            dateFormat.format(cal.time)
        }
        val recent = sessions.value.filter { it.isDeleted == 0 && it.date >= cutoff }
        return recent.size to recent.sumOf { it.durationMinutes }
    }

    override suspend fun getTodaySummary(): Pair<Int, Int> {
        val today = dateFormat.format(Date())
        val todaySessions = sessions.value.filter { it.isDeleted == 0 && it.date == today }
        return todaySessions.size to todaySessions.sumOf { it.durationMinutes }
    }

    override suspend fun getTotalSessionCount(): Int = sessions.value.count { it.isDeleted == 0 }

    override suspend fun getRecentSessions(daysBack: Int): List<ExerciseSessionEntity> {
        val cutoff = run {
            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -daysBack)
            dateFormat.format(cal.time)
        }
        return sessions.value.filter { it.isDeleted == 0 && it.date >= cutoff }
    }

    override suspend fun getModifiedSince(since: Long): List<ExerciseSessionEntity> =
        sessions.value.filter { it.lastModifiedAt >= since }

    override suspend fun getById(id: String): ExerciseSessionEntity? =
        sessions.value.find { it.id == id }

    override suspend fun upsert(session: ExerciseSessionEntity) {
        val existing = sessions.value.indexOfFirst { it.id == session.id }
        sessions.value = if (existing >= 0) {
            sessions.value.toMutableList().also { it[existing] = session }
        } else {
            sessions.value + session
        }
    }
}
