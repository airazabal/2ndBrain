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
        assertFalse("habits should NOT fire for meditation query", flags.includeHabits)
        assertFalse("usage should NOT fire for meditation query", flags.includeUsage)
        assertFalse("health should NOT fire for meditation query", flags.includeHealth)
        assertFalse(flags.isGeneral)
    }

    @Test
    fun `meditate keyword does not trigger habits`() {
        val flags = DynamicContextFlags.fromMessage("Did I meditate today?")
        assertTrue(flags.includeMeditation)
        assertFalse(flags.includeHabits)
    }

    @Test
    fun `medication query triggers habits not meditation`() {
        val flags = DynamicContextFlags.fromMessage("Did I take my medication?")
        assertTrue(flags.includeHabits)
        assertFalse("medication should not trigger meditation section", flags.includeMeditation)
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
            assertFalse("'$query' should NOT trigger habits", flags.includeHabits)
        }
    }

    @Test
    fun `screen time query triggers usage not meditation`() {
        val flags = DynamicContextFlags.fromMessage("How much screen time did I have today?")
        assertTrue(flags.includeUsage)
        assertFalse(flags.includeMeditation)
        assertFalse(flags.includeHabits)
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
        assertTrue(flags.includeHabits)
        assertTrue(flags.includeUsage)
        assertTrue(flags.includeMeditation)
        assertTrue(flags.includeMemories)
    }
}
