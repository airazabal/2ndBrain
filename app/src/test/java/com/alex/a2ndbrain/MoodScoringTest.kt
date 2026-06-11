package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the mood pillar scoring formulas used in WellnessViewModel.
 * The logic is extracted here as pure functions to avoid the ViewModel's
 * heavyweight dependency graph.
 */
class MoodScoringTest {

    // ── Mood progress normalization ───────────────────────────────────────────
    // Formula: ((mood + energy) / 2f - 1f) / 4f  → maps [1,5]×[1,5] → [0,1]

    private fun moodProgress(mood: Int, energy: Int): Float =
        ((mood + energy) / 2f - 1f) / 4f

    @Test
    fun `minimum mood and energy gives progress 0`() {
        assertEquals(0f, moodProgress(1, 1), 0.001f)
    }

    @Test
    fun `maximum mood and energy gives progress 1`() {
        assertEquals(1f, moodProgress(5, 5), 0.001f)
    }

    @Test
    fun `mid mood and energy gives progress 0_5`() {
        assertEquals(0.5f, moodProgress(3, 3), 0.001f)
    }

    @Test
    fun `high mood low energy averages correctly`() {
        // (5+1)/2=3 → (3-1)/4 = 0.5
        assertEquals(0.5f, moodProgress(5, 1), 0.001f)
    }

    @Test
    fun `mood progress always in 0 to 1 range`() {
        for (mood in 1..5) {
            for (energy in 1..5) {
                val p = moodProgress(mood, energy)
                assertTrue("moodProgress($mood,$energy)=$p out of range", p in 0f..1f)
            }
        }
    }

    // ── Dynamic denominator scoring ───────────────────────────────────────────
    // When mood is logged (moodP >= 0): score = sum(5 pillars) / 5 * 100
    // When mood not logged (moodP < 0): score = sum(4 pillars) / 4 * 100

    private fun computeScore(
        stepsP: Float, sleepP: Float, exerciseP: Float, focusP: Float,
        moodP: Float
    ): Int {
        val moodLogged = moodP >= 0f
        val denominator = if (moodLogged) 5f else 4f
        val moodContribution = if (moodLogged) moodP else 0f
        return ((stepsP + sleepP + exerciseP + focusP + moodContribution) / denominator * 100f)
            .toInt().coerceIn(0, 100)
    }

    @Test
    fun `all pillars perfect without mood gives 100`() {
        assertEquals(100, computeScore(1f, 1f, 1f, 1f, -1f))
    }

    @Test
    fun `all pillars perfect with perfect mood gives 100`() {
        assertEquals(100, computeScore(1f, 1f, 1f, 1f, 1f))
    }

    @Test
    fun `no mood logged uses denominator 4`() {
        // 2/4 * 100 = 50
        assertEquals(50, computeScore(0.5f, 0.5f, 0.5f, 0.5f, -1f))
    }

    @Test
    fun `mood logged at 0 uses denominator 5`() {
        // (2 + 0) / 5 * 100 = 40
        assertEquals(40, computeScore(0.5f, 0.5f, 0.5f, 0.5f, 0f))
    }

    @Test
    fun `perfect mood lifts score above no-mood baseline`() {
        val withoutMood = computeScore(0.5f, 0.5f, 0.5f, 0.5f, -1f) // 50
        val withMood    = computeScore(0.5f, 0.5f, 0.5f, 0.5f, 1f)  // 60
        assertTrue(withMood > withoutMood)
        assertEquals(50, withoutMood)
        assertEquals(60, withMood)
    }

    @Test
    fun `low mood score less than no-mood baseline`() {
        val withoutMood  = computeScore(0.5f, 0.5f, 0.5f, 0.5f, -1f) // 50
        val withLowMood  = computeScore(0.5f, 0.5f, 0.5f, 0.5f,  0f) // 40
        assertTrue(withLowMood < withoutMood)
    }

    @Test
    fun `score is clamped to 0 minimum`() {
        assertEquals(0, computeScore(0f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun `score is clamped to 100 maximum`() {
        assertEquals(100, computeScore(2f, 2f, 2f, 2f, 2f))
    }

    // ── SenseOfDaySnapshotEntity defaults ────────────────────────────────────

    @Test
    fun `snapshot moodProgress defaults to -1 when not supplied`() {
        val snapshot = SenseOfDaySnapshotEntity(
            date = "2026-06-11",
            score = 72,
            stepsProgress = 0.8f,
            sleepProgress  = 0.7f,
            exerciseProgress = 0.5f,
            focusProgress  = 0.6f,
            savedAt = 0L
            // moodProgress omitted — should default to -1f
        )
        assertEquals(-1f, snapshot.moodProgress, 0.001f)
    }

    @Test
    fun `snapshot with explicit moodProgress stores it correctly`() {
        val snapshot = SenseOfDaySnapshotEntity(
            date = "2026-06-11", score = 80,
            stepsProgress = 0.9f, sleepProgress = 0.8f,
            exerciseProgress = 0.7f, focusProgress = 0.6f,
            savedAt = 0L, moodProgress = 0.75f
        )
        assertEquals(0.75f, snapshot.moodProgress, 0.001f)
    }
}
