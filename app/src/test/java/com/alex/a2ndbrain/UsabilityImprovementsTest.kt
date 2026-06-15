package com.alex.a2ndbrain

import com.alex.a2ndbrain.ui.chat.ModelChipData
import com.alex.a2ndbrain.ui.chat.parseModelChip
import com.alex.a2ndbrain.ui.wellness.WellnessLeaf
import com.alex.a2ndbrain.ui.wellness.resolveInitialLeaf
import org.junit.Assert.*
import org.junit.Test

// ── parseModelChip ────────────────────────────────────────────────────────────

class ParseModelChipTest {

    @Test
    fun `null raw returns null`() {
        assertNull(parseModelChip(null))
    }

    @Test
    fun `blank raw returns null`() {
        assertNull(parseModelChip("  "))
    }

    @Test
    fun `Empty sentinel returns null`() {
        assertNull(parseModelChip("Empty"))
    }

    @Test
    fun `LiteRT string returns On-device local chip`() {
        val chip = parseModelChip("LiteRT (Qwen) — 1.2s")
        assertNotNull(chip)
        assertEquals("On-device", chip!!.label)
        assertEquals("1.2s", chip.elapsed)
        assertTrue(chip.isLocal)
    }

    @Test
    fun `LiteRT offline fallback string is still local`() {
        val chip = parseModelChip("LiteRT (Qwen) · offline fallback · 2.3s")
        assertNotNull(chip)
        assertTrue(chip!!.isLocal)
        assertEquals("2.3s", chip.elapsed)
    }

    @Test
    fun `gemini-2_5-flash returns Gemini cloud chip`() {
        val chip = parseModelChip("gemini-2.5-flash (3.1s)")
        assertNotNull(chip)
        assertEquals("Gemini", chip!!.label)
        assertEquals("3.1s", chip.elapsed)
        assertFalse(chip.isLocal)
    }

    @Test
    fun `gemini-flash-lite returns Gemini Lite label`() {
        val chip = parseModelChip("gemini-2.5-flash-lite (1.8s)")
        assertNotNull(chip)
        assertEquals("Gemini Lite", chip!!.label)
        assertFalse(chip!!.isLocal)
    }

    @Test
    fun `Template returns No AI key chip`() {
        val chip = parseModelChip("Template")
        assertNotNull(chip)
        assertEquals("No AI key", chip!!.label)
        assertEquals("", chip.elapsed)
        assertFalse(chip.isLocal)
    }

    @Test
    fun `unknown string returns null`() {
        assertNull(parseModelChip("some unknown model string"))
    }

    @Test
    fun `elapsed is empty when no time pattern in string`() {
        val chip = parseModelChip("LiteRT (Qwen)")
        assertNotNull(chip)
        assertEquals("", chip!!.elapsed)
    }
}

// ── resolveInitialLeaf ────────────────────────────────────────────────────────

class ResolveInitialLeafTest {

    @Test
    fun `HEALTH maps to HEALTH leaf in BODY group`() {
        val leaf = resolveInitialLeaf("HEALTH")
        assertEquals(WellnessLeaf.HEALTH, leaf)
    }

    @Test
    fun `EXERCISE maps to EXERCISE leaf in BODY group`() {
        val leaf = resolveInitialLeaf("EXERCISE")
        assertEquals(WellnessLeaf.EXERCISE, leaf)
    }

    @Test
    fun `MOOD maps to MOOD leaf in MIND group`() {
        val leaf = resolveInitialLeaf("MOOD")
        assertEquals(WellnessLeaf.MOOD, leaf)
    }

    @Test
    fun `MEDITATION maps to MEDITATION leaf in MIND group`() {
        val leaf = resolveInitialLeaf("MEDITATION")
        assertEquals(WellnessLeaf.MEDITATION, leaf)
    }

    @Test
    fun `HABITS maps to HABIT_LIST leaf in HABITS group`() {
        val leaf = resolveInitialLeaf("HABITS")
        assertEquals(WellnessLeaf.HABIT_LIST, leaf)
    }

    @Test
    fun `GOALS maps to GOALS leaf in HABITS group`() {
        val leaf = resolveInitialLeaf("GOALS")
        assertEquals(WellnessLeaf.GOALS, leaf)
    }

    @Test
    fun `ONLINE maps to ONLINE leaf in TIME group`() {
        val leaf = resolveInitialLeaf("ONLINE")
        assertEquals(WellnessLeaf.ONLINE, leaf)
    }

    @Test
    fun `TASKS maps to TASKS leaf in TIME group`() {
        val leaf = resolveInitialLeaf("TASKS")
        assertEquals(WellnessLeaf.TASKS, leaf)
    }

    @Test
    fun `TRENDS maps to TRENDS leaf in INSIGHTS group`() {
        val leaf = resolveInitialLeaf("TRENDS")
        assertEquals(WellnessLeaf.TRENDS, leaf)
    }

    @Test
    fun `REFLECT maps to REFLECT leaf in INSIGHTS group`() {
        val leaf = resolveInitialLeaf("REFLECT")
        assertEquals(WellnessLeaf.REFLECT, leaf)
    }

    @Test
    fun `unknown string defaults to HEALTH`() {
        assertEquals(WellnessLeaf.HEALTH, resolveInitialLeaf("UNKNOWN"))
        assertEquals(WellnessLeaf.HEALTH, resolveInitialLeaf(""))
    }

    @Test
    fun `each deep-link maps to the correct group`() {
        assertEquals(WellnessLeaf.HEALTH.group,    resolveInitialLeaf("HEALTH").group)
        assertEquals(WellnessLeaf.EXERCISE.group,  resolveInitialLeaf("EXERCISE").group)
        assertEquals(WellnessLeaf.MOOD.group,      resolveInitialLeaf("MOOD").group)
        assertEquals(WellnessLeaf.ONLINE.group,    resolveInitialLeaf("ONLINE").group)
    }
}

// ── MoodQuickLogReceiver intent validation ────────────────────────────────────

class MoodQuickLogValidationTest {

    // Mirror the validation predicate from MoodQuickLogReceiver.onReceive
    private fun isValidMood(mood: Int) = mood in 1..5

    @Test
    fun `values 1 to 5 are valid`() {
        (1..5).forEach { assertTrue("mood=$it should be valid", isValidMood(it)) }
    }

    @Test
    fun `0 is invalid`() {
        assertFalse(isValidMood(0))
    }

    @Test
    fun `6 is invalid`() {
        assertFalse(isValidMood(6))
    }

    @Test
    fun `negative values are invalid`() {
        assertFalse(isValidMood(-1))
        assertFalse(isValidMood(Int.MIN_VALUE))
    }

    @Test
    fun `missing extra sentinel -1 is invalid`() {
        // getIntExtra returns -1 when key is absent
        assertFalse(isValidMood(-1))
    }
}
