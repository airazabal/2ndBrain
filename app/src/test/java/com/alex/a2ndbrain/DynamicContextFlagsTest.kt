package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.agents.DynamicContextFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicContextFlagsTest {

    @Test
    fun `meditation query only triggers meditation`() {
        val flags = DynamicContextFlags.fromMessage("How many meditation sessions did I have this week?")
        assertTrue(flags.includeMeditation)
        assertFalse("usage should NOT fire for meditation query", flags.includeUsage)
        assertFalse("health should NOT fire for meditation query", flags.includeHealth)
        assertFalse(flags.isGeneral)
    }

    @Test
    fun `meditate keyword triggers meditation`() {
        val flags = DynamicContextFlags.fromMessage("Did I meditate today?")
        assertTrue(flags.includeMeditation)
    }

    @Test
    fun `natural meditation phrasing is routed correctly`() {
        listOf(
            "how is my mindfulness practice going?",
            "did I sit today?",
            "what's my meditation streak?",
            "how calm have I been?",
            "show me my zendence sessions"
        ).forEach { query ->
            val flags = DynamicContextFlags.fromMessage(query)
            assertTrue("'$query' should trigger meditation", flags.includeMeditation)
        }
    }

    @Test
    fun `screen time query triggers usage not meditation`() {
        val flags = DynamicContextFlags.fromMessage("How much screen time did I have today?")
        assertTrue(flags.includeUsage)
        assertFalse(flags.includeMeditation)
    }

    @Test
    fun `time alone does not trigger usage section`() {
        val flags = DynamicContextFlags.fromMessage("What time did I start meditating?")
        assertTrue(flags.includeMeditation)
        assertFalse("bare 'time' should not trigger usage", flags.includeUsage)
    }

    @Test
    fun `general query includes all sections`() {
        val flags = DynamicContextFlags.fromMessage("How am I doing?")
        assertTrue(flags.isGeneral)
        assertTrue(flags.includeHealth)
        assertTrue(flags.includeUsage)
        assertTrue(flags.includeMeditation)
        assertTrue(flags.includeMemories)
    }

    @Test
    fun `exercise query triggers exercise not health`() {
        val flags = DynamicContextFlags.fromMessage("How many workouts did I do this week?")
        assertTrue(flags.includeExercise)
        assertFalse(flags.isGeneral)
    }

    @Test
    fun `finance query triggers memories`() {
        val flags = DynamicContextFlags.fromMessage("What did I spend money on this week?")
        assertTrue(flags.includeMemories)
    }
}
