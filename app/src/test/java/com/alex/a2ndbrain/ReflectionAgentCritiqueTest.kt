package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.agents.BrainContext
import com.alex.a2ndbrain.core.agents.HealthContext
import com.alex.a2ndbrain.core.agents.ReflectionAgent
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.memory.MemoryEntity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionAgentCritiqueTest {

    private val agent = ReflectionAgent()

    private fun words(n: Int) = (1..n).joinToString(" ") { "word$it" }

    private fun memoryWith(content: String) = MemoryEntity(
        source = "notification",
        packageName = null,
        title = null,
        content = content
    )

    // ── Word count ────────────────────────────────────────────────────────────

    @Test
    fun `daily briefing below 80 words fails`() {
        val critique = agent.critique(
            output = words(60),
            type = ReflectionAgent.ReflectionType.MORNING_BRIEFING,
            ctx = BrainContext()
        )
        assertNotNull(critique)
        assertTrue(critique!!.contains("too short", ignoreCase = true))
    }

    @Test
    fun `daily briefing at 80 words passes word count`() {
        val critique = agent.critique(
            output = words(80),
            type = ReflectionAgent.ReflectionType.MORNING_BRIEFING,
            ctx = BrainContext()
        )
        assertNull(critique)
    }

    @Test
    fun `weekly correlation below 150 words fails`() {
        val critique = agent.critique(
            output = words(100),
            type = ReflectionAgent.ReflectionType.WEEKLY_CORRELATION,
            ctx = BrainContext()
        )
        assertNotNull(critique)
        assertTrue(critique!!.contains("too short", ignoreCase = true))
    }

    @Test
    fun `weekly correlation at 150 words passes word count`() {
        val output = words(150)
        val critique = agent.critique(
            output = output,
            type = ReflectionAgent.ReflectionType.WEEKLY_CORRELATION,
            ctx = BrainContext()
        )
        assertNull(critique)
    }

    // ── Weekly bullet detection ───────────────────────────────────────────────

    @Test
    fun `weekly reflection with more than 3 bullet lines fails`() {
        val bullets = buildString {
            repeat(150) { appendLine("word$it") }
            appendLine("- bullet one")
            appendLine("- bullet two")
            appendLine("- bullet three")
            appendLine("- bullet four")
        }
        val critique = agent.critique(
            output = bullets,
            type = ReflectionAgent.ReflectionType.WEEKLY_CORRELATION,
            ctx = BrainContext()
        )
        assertNotNull(critique)
        assertTrue(critique!!.contains("bullet", ignoreCase = true))
    }

    @Test
    fun `weekly reflection with 3 or fewer bullets passes`() {
        val output = buildString {
            repeat(150) { appendLine("word$it") }
            appendLine("- only three bullets max")
            appendLine("- second bullet")
            appendLine("- third bullet")
        }
        val critique = agent.critique(
            output = output,
            type = ReflectionAgent.ReflectionType.WEEKLY_CORRELATION,
            ctx = BrainContext()
        )
        assertNull(critique)
    }

    @Test
    fun `daily reflection with bullet points does not fail on bullet check`() {
        val output = buildString {
            repeat(80) { appendLine("word$it") }
            repeat(10) { appendLine("- bullet $it") }
        }
        val critique = agent.critique(
            output = output,
            type = ReflectionAgent.ReflectionType.EVENING_REFLECTION,
            ctx = BrainContext()
        )
        // Bullet check only applies to weekly — daily should pass
        assertNull(critique)
    }

    // ── Data denial ───────────────────────────────────────────────────────────

    @Test
    fun `denial phrase with non-empty ctx fails`() {
        val output = words(80) + " I don't have any data to analyze."
        val ctx = BrainContext(memories = listOf(memoryWith("some captured text")))
        val critique = agent.critique(
            output = output,
            type = ReflectionAgent.ReflectionType.EVENING_REFLECTION,
            ctx = ctx
        )
        assertNotNull(critique)
        assertTrue(critique!!.contains("data was provided", ignoreCase = true))
    }

    @Test
    fun `denial phrase with empty ctx passes`() {
        val output = words(80) + " No data is available."
        val critique = agent.critique(
            output = output,
            type = ReflectionAgent.ReflectionType.EVENING_REFLECTION,
            ctx = BrainContext()
        )
        // Empty ctx — denial is accurate, should pass
        assertNull(critique)
    }

    @Test
    fun `denial phrase with available health ctx fails`() {
        val output = words(80) + " No records found."
        val ctx = BrainContext(
            health = HealthContext(
                metrics = HealthMetrics(steps = 8000),
                isAvailable = true
            )
        )
        val critique = agent.critique(
            output = output,
            type = ReflectionAgent.ReflectionType.MORNING_BRIEFING,
            ctx = ctx
        )
        assertNotNull(critique)
    }
}
