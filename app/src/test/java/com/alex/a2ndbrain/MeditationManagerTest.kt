package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.meditation.MeditationManager
import com.alex.a2ndbrain.core.memory.MemoryEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class MeditationManagerTest {

    @Test
    fun testParseMeditationSession_progressNotification() {
        val entity = MemoryEntity(
            id = "1",
            content = "Meditation in progress - 28:21 remaining",
            timestamp = System.currentTimeMillis(),
            packageName = "com.alex.zendence",
            tags = "meditation"
        )
        val session = MeditationManager.parseMeditationSession(entity)
        assertNull("Progress notification should be filtered out", session)
    }

    @Test
    fun testParseMeditationSession_completedSessionWithInsight() {
        val content = """
            Ready for your session? You've meditated for 15 mins this week. 
            Last insight: "samadhi at 15 mins. felt good all 30 mins"
        """.trimIndent()
        
        val entity = MemoryEntity(
            id = "2",
            content = content,
            timestamp = 1716120000000L, // Specific timestamp
            packageName = "com.alex.zendence",
            tags = "meditation"
        )
        val session = MeditationManager.parseMeditationSession(entity)
        assertNotNull(session)
        assertEquals(15, session!!.durationMinutes)
        assertEquals("samadhi at 15 mins. felt good all 30 mins", session.insight)
        assertEquals("2", session.id)
    }

    @Test
    fun testParseMeditationSession_completedSessionWithoutInsight() {
        val content = "Ready for your session? You've meditated for 25 mins this week."
        val entity = MemoryEntity(
            id = "3",
            content = content,
            timestamp = 1716120000000L,
            packageName = "com.alex.zendence",
            tags = "meditation"
        )
        val session = MeditationManager.parseMeditationSession(entity)
        assertNotNull(session)
        assertEquals(25, session!!.durationMinutes)
        assertEquals("", session.insight)
    }

    @Test
    fun testCalculateStreaks_empty() {
        val streaks = MeditationManager.calculateStreaks(emptyList())
        assertEquals(0, streaks.currentWeekStreak)
        assertEquals(0, streaks.maxOverallStreak)
        assertEquals(0, streaks.totalSessions)
    }

    @Test
    fun testCalculateStreaks_complexStreaks() {
        // We will create a list of sessions on contiguous days
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        
        // Let's set the calendar to a specific known date (e.g. Wednesday of some week)
        calendar.set(2026, Calendar.MAY, 20, 10, 0, 0) // Wednesday
        val sessionWednesday = createSession(calendar.timeInMillis)
        
        calendar.add(Calendar.DAY_OF_YEAR, -1) // Tuesday
        val sessionTuesday = createSession(calendar.timeInMillis)

        calendar.add(Calendar.DAY_OF_YEAR, -1) // Monday
        val sessionMonday = createSession(calendar.timeInMillis)

        calendar.add(Calendar.DAY_OF_YEAR, -2) // Saturday (Skipped Sunday)
        val sessionSaturday = createSession(calendar.timeInMillis)

        calendar.add(Calendar.DAY_OF_YEAR, -1) // Friday
        val sessionFriday = createSession(calendar.timeInMillis)

        val sessions = listOf(sessionWednesday, sessionTuesday, sessionMonday, sessionSaturday, sessionFriday)
        
        // Since we are mocking Calendar.getInstance inside the manager, let's verify that the max overall streak and current week streak work.
        // For testing, calculateStreaks uses the current time in Calendar.getInstance().
        // To make the test robust regardless of the execution date, we can test the general streak calculation logic.
        val streaks = MeditationManager.calculateStreaks(sessions)
        
        // Total sessions should be 5
        assertEquals(5, streaks.totalSessions)
        
        // The overall max streak contains two segments:
        // Segment 1: Friday, Saturday (2 days)
        // Segment 2: Monday, Tuesday, Wednesday (3 days)
        // Max overall should be 3
        assertEquals(3, streaks.maxOverallStreak)
    }

    private fun createSession(timestamp: Long): com.alex.a2ndbrain.core.meditation.MeditationSession {
        return com.alex.a2ndbrain.core.meditation.MeditationSession(
            id = UUID.randomUUID().toString(),
            durationMinutes = 20,
            insight = "Good focus",
            timestamp = timestamp
        )
    }
}
