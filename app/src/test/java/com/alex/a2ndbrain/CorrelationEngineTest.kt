package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshot
import com.alex.a2ndbrain.core.senseofday.CorrelationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrelationEngineTest {

    // ── pearson ───────────────────────────────────────────────────────────────

    @Test
    fun `pearson returns 0 for fewer than 3 data points`() {
        assertEquals(0f, CorrelationEngine.pearson(listOf(0.1f, 0.9f), listOf(0.1f, 0.9f)), 0.001f)
    }

    @Test
    fun `pearson returns 1 for perfect positive correlation`() {
        val xs = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        val ys = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
        assertEquals(1f, CorrelationEngine.pearson(xs, ys), 0.001f)
    }

    @Test
    fun `pearson returns -1 for perfect negative correlation`() {
        val xs = listOf(0.9f, 0.7f, 0.5f, 0.3f, 0.1f)
        val ys = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
        assertEquals(-1f, CorrelationEngine.pearson(xs, ys), 0.001f)
    }

    @Test
    fun `pearson returns 0 when all x values are identical`() {
        val xs = listOf(0.5f, 0.5f, 0.5f, 0.5f)
        val ys = listOf(0.1f, 0.4f, 0.7f, 0.9f)
        assertEquals(0f, CorrelationEngine.pearson(xs, ys), 0.001f)
    }

    @Test
    fun `pearson returns 0 when all y values are identical`() {
        val xs = listOf(0.2f, 0.5f, 0.8f, 0.95f)
        val ys = listOf(0.6f, 0.6f, 0.6f, 0.6f)
        assertEquals(0f, CorrelationEngine.pearson(xs, ys), 0.001f)
    }

    @Test
    fun `pearson result is clamped to -1 to 1`() {
        val xs = listOf(1f, 2f, 3f, 4f, 5f)
        val ys = listOf(2f, 4f, 6f, 8f, 10f)
        val r = CorrelationEngine.pearson(xs, ys)
        assertTrue(r >= -1f && r <= 1f)
    }

    // ── insight ───────────────────────────────────────────────────────────────

    @Test
    fun `insight strong positive`() {
        val text = CorrelationEngine.insight(0.8f, "sleep", "score")
        assertTrue("Expected 'Strong' in: $text", text.contains("Strong"))
    }

    @Test
    fun `insight moderate positive`() {
        val text = CorrelationEngine.insight(0.5f, "steps", "focus")
        assertTrue("Expected 'Moderate' in: $text", text.contains("Moderate"))
    }

    @Test
    fun `insight strong negative`() {
        val text = CorrelationEngine.insight(-0.7f, "social", "sleep")
        assertTrue("Expected 'Inverse' in: $text", text.contains("Inverse"))
    }

    @Test
    fun `insight weak returns no-pattern message`() {
        val text = CorrelationEngine.insight(0.1f, "sleep", "score")
        assertTrue("Expected 'No clear' in: $text", text.contains("No clear"))
    }

    // ── buildCorrelations ─────────────────────────────────────────────────────

    @Test
    fun `buildCorrelations returns empty for fewer than 3 snapshots`() {
        val snaps = listOf(snap("2026-06-01", 60), snap("2026-06-02", 70))
        assertTrue(CorrelationEngine.buildCorrelations(snaps).isEmpty())
    }

    @Test
    fun `buildCorrelations always includes Sleep-Score pair`() {
        val snaps = (1..7).map { snap("2026-06-0$it", 50 + it * 3) }
        val pairs = CorrelationEngine.buildCorrelations(snaps)
        assertTrue(pairs.any { it.chipLabel == "Sleep / Score" })
    }

    @Test
    fun `buildCorrelations Sleep-Score uses 1-day lag for r`() {
        // Perfectly aligned: sleep[i] == score[i+1]/100 → r should be 1
        val snaps = (0..5).map { i ->
            snap("2026-06-0${i + 1}", score = (i + 1) * 15, sleepProgress = (i + 1) * 0.15f)
        }
        val pair = CorrelationEngine.buildCorrelations(snaps).first { it.chipLabel == "Sleep / Score" }
        assertEquals(1f, pair.r, 0.05f)
    }

    @Test
    fun `buildCorrelations omits Steps-Focus when no step data`() {
        val snaps = (1..5).map { snap("2026-06-0$it", 60, stepsProgress = 0f, focusProgress = 0f) }
        val pairs = CorrelationEngine.buildCorrelations(snaps)
        assertTrue(pairs.none { it.chipLabel == "Steps / Focus" })
    }

    @Test
    fun `buildCorrelations includes Steps-Focus when sufficient data`() {
        val snaps = (1..5).map { i ->
            snap("2026-06-0$i", 60, stepsProgress = i * 0.2f, focusProgress = i * 0.15f)
        }
        val pairs = CorrelationEngine.buildCorrelations(snaps)
        assertTrue(pairs.any { it.chipLabel == "Steps / Focus" })
    }

    @Test
    fun `buildCorrelations omits Exercise-Mood when mood never logged`() {
        val snaps = (1..5).map { i ->
            snap("2026-06-0$i", 60, exerciseProgress = 0.8f, moodProgress = -1f)
        }
        val pairs = CorrelationEngine.buildCorrelations(snaps)
        assertTrue(pairs.none { it.chipLabel == "Exercise / Mood" })
    }

    @Test
    fun `buildCorrelations includes Exercise-Mood when enough mood-logged days`() {
        val snaps = (1..5).map { i ->
            snap("2026-06-0$i", 60, exerciseProgress = i * 0.2f, moodProgress = i * 0.2f)
        }
        val pairs = CorrelationEngine.buildCorrelations(snaps)
        assertTrue(pairs.any { it.chipLabel == "Exercise / Mood" })
    }

    @Test
    fun `buildCorrelations Exercise-Mood excludes days without mood from r calculation`() {
        // 3 days with mood, 2 without — should still produce the pair
        val snaps = listOf(
            snap("2026-06-01", 60, exerciseProgress = 0.8f, moodProgress = 0.9f),
            snap("2026-06-02", 65, exerciseProgress = 0.6f, moodProgress = 0.7f),
            snap("2026-06-03", 55, exerciseProgress = 0.4f, moodProgress = 0.5f),
            snap("2026-06-04", 70, exerciseProgress = 0.7f, moodProgress = -1f),
            snap("2026-06-05", 75, exerciseProgress = 0.5f, moodProgress = -1f),
        )
        val pairs = CorrelationEngine.buildCorrelations(snaps)
        assertTrue(pairs.any { it.chipLabel == "Exercise / Mood" })
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private fun snap(
        date: String,
        score: Int,
        sleepProgress: Float = 0.7f,
        stepsProgress: Float = 0.6f,
        focusProgress: Float = 0.5f,
        exerciseProgress: Float = 0.4f,
        moodProgress: Float = -1f
    ) = SenseOfDaySnapshot(
        date = date,
        score = score,
        stepsProgress = stepsProgress,
        sleepProgress = sleepProgress,
        exerciseProgress = exerciseProgress,
        focusProgress = focusProgress,
        savedAt = 0L,
        moodProgress = moodProgress
    )
}
