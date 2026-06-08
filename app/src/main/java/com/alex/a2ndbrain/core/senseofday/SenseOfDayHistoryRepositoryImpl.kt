package com.alex.a2ndbrain.core.senseofday

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class SenseOfDayHistoryRepositoryImpl(private val dao: SenseOfDaySnapshotDao) : SenseOfDayHistoryRepository {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun getLast14DaysFlow(): Flow<List<SenseOfDaySnapshotEntity>> = dao.getRecentFlow(14)

    override suspend fun saveSnapshot(
        score: Int,
        stepsProgress: Float,
        sleepProgress: Float,
        exerciseProgress: Float,
        focusProgress: Float
    ) {
        dao.upsert(
            SenseOfDaySnapshotEntity(
                date = sdf.format(Date()),
                score = score,
                stepsProgress = stepsProgress,
                sleepProgress = sleepProgress,
                exerciseProgress = exerciseProgress,
                focusProgress = focusProgress,
                savedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getStats(): Triple<Int, Int, Int> {
        val today = sdf.format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val weekAgo = sdf.format(cal.time)
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -29)
        val monthAgo = sdf.format(cal.time)

        val month = dao.getSince(monthAgo)
        val todayScore = month.lastOrNull { it.date == today }?.score ?: 0
        val week = month.filter { it.date >= weekAgo }
        val weekAvg = if (week.isNotEmpty()) week.map { it.score }.average().toInt() else 0
        val monthAvg = if (month.isNotEmpty()) month.map { it.score }.average().toInt() else 0
        return Triple(todayScore, weekAvg, monthAvg)
    }

    override suspend fun getWeeklyAverages(weeks: Int): List<Pair<String, Float>> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.WEEK_OF_YEAR, -(weeks - 1))
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val sinceDate = sdf.format(cal.time)

        val snapshots = dao.getSince(sinceDate)
        val weekSdf = SimpleDateFormat("yyyy-ww", Locale.getDefault())

        val grouped: Map<String, List<SenseOfDaySnapshotEntity>> = snapshots.groupBy { snap ->
            weekSdf.format(sdf.parse(snap.date)!!)
        }

        val result = mutableListOf<Pair<String, Float>>()
        for (i in (weeks - 1) downTo 0) {
            val wCal = Calendar.getInstance()
            wCal.add(Calendar.WEEK_OF_YEAR, -i)
            val key = weekSdf.format(wCal.time)
            val avg = grouped[key]?.map { it.score.toFloat() }?.average()?.toFloat() ?: 0f
            val label = if (i == 0) "This wk" else "W-$i"
            result.add(label to avg)
        }
        return result
    }

    override suspend fun getRecentSnapshots(days: Int): List<SenseOfDaySnapshotEntity> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return dao.getSince(sdf.format(cal.time))
    }
}
