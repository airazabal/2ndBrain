package com.alex.a2ndbrain.core.exercise

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val context: Context
) {
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun sinceDate(daysBack: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysBack)
        return dateFormat.format(cal.time)
    }

    suspend fun logSession(
        type: ExerciseType,
        durationMinutes: Int,
        startedAt: Long = 0L,
        notes: String = ""
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val date = dateFormat.format(Date(if (startedAt > 0L) startedAt else now))
        exerciseDao.insert(
            ExerciseSessionEntity(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                type = type.name,
                durationMinutes = durationMinutes,
                startedAt = startedAt,
                notes = notes,
                date = date,
                createdAt = now,
                lastModifiedAt = now
            )
        )
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        exerciseDao.softDelete(id)
    }

    fun getAllSessionsFlow(): Flow<List<ExerciseSessionEntity>> =
        exerciseDao.getAllSessionsFlow()

    /**
     * 7-day consistency bars: returns exactly 7 entries from 6 days ago through today.
     * Each entry is (dayLabel, durationRatio) where ratio = totalMins/60f clamped 0–1.0.
     * Days with no sessions return 0.0f.
     */
    fun getWeeklyConsistency(): Flow<List<Pair<String, Float>>> {
        val since = sinceDate(6)
        return exerciseDao.getSessionsSince(since).map { sessions ->
            (0..6).map { offset ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -(6 - offset))
                val dateStr = dateFormat.format(cal.time)
                val label = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    else -> "Sun"
                }
                val totalMinutes = sessions
                    .filter { it.date == dateStr }
                    .sumOf { it.durationMinutes }
                label to (totalMinutes / 60f).coerceAtMost(1.0f)
            }
        }
    }

    /** One-shot: (sessionCount, totalMinutes) for the last 7 days. */
    suspend fun getWeeklySummary(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val sessions = exerciseDao.getSessionsSinceSync(sinceDate(6))
        sessions.size to sessions.sumOf { it.durationMinutes }
    }

    suspend fun getModifiedSince(since: Long) = withContext(Dispatchers.IO) {
        exerciseDao.getModifiedSince(since)
    }
}
