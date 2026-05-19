package com.alex.a2ndbrain.core.meditation

import com.alex.a2ndbrain.core.memory.MemoryEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MeditationSession(
    val id: Long,
    val durationMinutes: Int,
    val insight: String,
    val timestamp: Long
)

data class StreakResult(
    val currentWeekStreak: Int,
    val maxOverallStreak: Int,
    val totalSessions: Int
)

object MeditationManager {

    fun parseMeditationSession(memory: MemoryEntity): MeditationSession? {
        if (memory.packageName != "com.alex.zendence") return null
        val content = memory.content
        
        // Ignore progress updates
        if (content.contains("Meditation in progress", ignoreCase = true) ||
            content.contains("in progress", ignoreCase = true)) {
            return null
        }
        
        var duration = 15 // Fallback
        
        // Find minutes using regex
        val minsRegex = Regex("(\\d+)\\s*(?:mins|minutes|min)", RegexOption.IGNORE_CASE)
        val matches = minsRegex.findAll(content).toList()
        if (matches.isNotEmpty()) {
            // Find a non-zero match first
            val nonZero = matches.mapNotNull { it.groupValues[1].toIntOrNull() }.firstOrNull { it > 0 }
            if (nonZero != null) {
                duration = nonZero
            }
        }
        
        var insight = ""
        // Check for quoted string
        val quoteRegex = Regex("\"([^\"]+)\"")
        val quoteMatch = quoteRegex.find(content)
        if (quoteMatch != null) {
            insight = quoteMatch.groupValues[1]
        } else {
            val insightIdx = content.indexOf("insight:", ignoreCase = true)
            if (insightIdx != -1) {
                insight = content.substring(insightIdx + 8).trim()
            }
        }
        
        if (insight.isBlank()) {
            insight = "Completed session"
        }
        
        return MeditationSession(
            id = memory.id,
            durationMinutes = duration,
            insight = insight,
            timestamp = memory.timestamp
        )
    }

    fun calculateStreaks(sessions: List<MeditationSession>): StreakResult {
        if (sessions.isEmpty()) return StreakResult(0, 0, 0)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = sessions.map { sdf.format(Date(it.timestamp)) }.toSortedSet() // Ascending sorted unique dates
        
        // 1. Current Active Streak
        val todayCal = Calendar.getInstance()
        val todayStr = sdf.format(todayCal.time)
        todayCal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(todayCal.time)
        
        var currentStreak = 0
        
        // If today is in the set, start checking from today. If not, but yesterday is, start from yesterday. Otherwise 0.
        val startCal = when {
            dates.contains(todayStr) -> Calendar.getInstance()
            dates.contains(yesterdayStr) -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            else -> null
        }
        
        if (startCal != null) {
            val checkCal = startCal.clone() as Calendar
            var checkStr = sdf.format(checkCal.time)
            while (dates.contains(checkStr)) {
                currentStreak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
                checkStr = sdf.format(checkCal.time)
            }
        }
        
        // 2. Max Overall Streak
        var maxStreak = 0
        if (dates.isNotEmpty()) {
            val dateList = dates.toList() // Sorted ascending
            var tempStreak = 1
            maxStreak = 1
            
            val loopCal = Calendar.getInstance()
            for (i in 1 until dateList.size) {
                val prevDate = sdf.parse(dateList[i - 1]) ?: continue
                val currDate = sdf.parse(dateList[i]) ?: continue
                
                loopCal.time = prevDate
                loopCal.add(Calendar.DAY_OF_YEAR, 1)
                val nextDayStr = sdf.format(loopCal.time)
                val currDayStr = sdf.format(currDate)
                
                if (currDayStr == nextDayStr) {
                    tempStreak++
                } else if (currDayStr != sdf.format(prevDate)) {
                    tempStreak = 1
                }
                if (tempStreak > maxStreak) {
                    maxStreak = tempStreak
                }
            }
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
            }
        }
        
        // 3. This Week's Streak (contiguous days *within* the current calendar week Monday-Sunday)
        var weekStreak = 0
        if (startCal != null) {
            val weekCheckCal = startCal.clone() as Calendar
            var weekCheckStr = sdf.format(weekCheckCal.time)
            
            val todayWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
            val todayYear = Calendar.getInstance().get(Calendar.YEAR)
            
            while (dates.contains(weekCheckStr) && 
                   weekCheckCal.get(Calendar.WEEK_OF_YEAR) == todayWeek && 
                   weekCheckCal.get(Calendar.YEAR) == todayYear) {
                weekStreak++
                weekCheckCal.add(Calendar.DAY_OF_YEAR, -1)
                weekCheckStr = sdf.format(weekCheckCal.time)
            }
        }
        
        return StreakResult(
            currentWeekStreak = weekStreak,
            maxOverallStreak = maxStreak,
            totalSessions = sessions.size
        )
    }
}
