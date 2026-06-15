package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.memory.MAX_LEV_LEN
import com.alex.a2ndbrain.core.memory.MIN_LENGTH_RATIO
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.calculateSimilarity
import com.alex.a2ndbrain.core.memory.deduplicateMemories
import org.junit.Assert.*
import org.junit.Test

class MemoryDeduplicatorTest {

    // ── calculateSimilarity guards ────────────────────────────────────────────

    @Test fun `identical strings return 1_0`() {
        assertEquals(1.0, calculateSimilarity("hello world", "hello world"), 0.001)
    }

    @Test fun `empty s1 returns 0_0`() {
        assertEquals(0.0, calculateSimilarity("", "hello"), 0.001)
    }

    @Test fun `empty s2 returns 0_0`() {
        assertEquals(0.0, calculateSimilarity("hello", ""), 0.001)
    }

    @Test fun `length ratio guard fires when shorter is under 40pct of longer`() {
        // "ab" is 20% of "aaaaaaaaaa" — guard should return 0.0 without matrix
        val short = "ab"
        val long  = "a".repeat(20)
        val ratio = short.length.toFloat() / long.length.toFloat()
        assertTrue("test precondition: ratio should be below MIN_LENGTH_RATIO", ratio < MIN_LENGTH_RATIO)
        assertEquals(0.0, calculateSimilarity(short, long), 0.001)
    }

    @Test fun `length ratio guard does NOT fire when strings are similar length`() {
        // Two strings of similar length that differ — should still compute similarity
        val s1 = "Your meeting starts in 5 minutes"
        val s2 = "Your meeting starts in 6 minutes"
        val result = calculateSimilarity(s1, s2)
        assertTrue("similar-length near-duplicate should have high similarity", result > 0.8)
    }

    @Test fun `very long strings are truncated to MAX_LEV_LEN`() {
        // Two strings far exceeding MAX_LEV_LEN that share the same prefix
        // should still be recognised as similar after truncation
        val base = "A".repeat(MAX_LEV_LEN) + "X".repeat(500)
        val near = "A".repeat(MAX_LEV_LEN) + "Y".repeat(500)
        // After truncation both become "A" * MAX_LEV_LEN — identical, similarity = 1.0
        assertEquals(1.0, calculateSimilarity(base, near), 0.001)
    }

    @Test fun `completely different strings of equal length have low similarity`() {
        val result = calculateSimilarity("abcdefghijklmno", "pqrstuvwxyz12345")
        assertTrue("completely different strings should have similarity below 0.3", result < 0.3)
    }

    @Test fun `constants have sensible values`() {
        assertTrue(MAX_LEV_LEN in 100..500)
        assertTrue(MIN_LENGTH_RATIO in 0.1f..0.6f)
    }

    // ── deduplicateMemories behaviour ─────────────────────────────────────────

    private fun mem(id: Long, content: String, pkg: String = "com.example.app", title: String = "") =
        MemoryEntity(
            id = id, source = "notification", packageName = pkg,
            title = title, content = content,
            timestamp = System.currentTimeMillis() - id * 1000
        )

    @Test fun `exact duplicate is merged`() {
        val memories = listOf(
            mem(1, "Payment received: \$10.00"),
            mem(2, "Payment received: \$10.00")
        )
        val result = deduplicateMemories(memories)
        assertEquals(1, result.size)
        assertEquals(2, result[0].allIds.size)
    }

    @Test fun `near-duplicate above threshold is merged`() {
        val memories = listOf(
            mem(1, "Your order has been shipped and will arrive tomorrow"),
            mem(2, "Your order has been shipped and will arrive today")
        )
        val result = deduplicateMemories(memories)
        assertEquals(1, result.size)
    }

    @Test fun `different content from same app is NOT merged`() {
        val memories = listOf(
            mem(1, "Alex sent you a message"),
            mem(2, "Payment received for your order")
        )
        val result = deduplicateMemories(memories)
        assertEquals(2, result.size)
    }

    @Test fun `items from different packages are NOT merged even if content matches`() {
        val memories = listOf(
            mem(1, "You have a new message", pkg = "com.app.one"),
            mem(2, "You have a new message", pkg = "com.app.two")
        )
        val result = deduplicateMemories(memories)
        assertEquals(2, result.size)
    }

    @Test fun `voice notes are never merged`() {
        val voice = MemoryEntity(
            id = 1, source = "voice", packageName = null,
            title = "Note", content = "Same content here",
            timestamp = System.currentTimeMillis()
        )
        val voice2 = voice.copy(id = 2, timestamp = System.currentTimeMillis() - 1000)
        val result = deduplicateMemories(listOf(voice, voice2))
        assertEquals(2, result.size)
    }

    @Test fun `strings vastly different in length are NOT merged`() {
        // Short title notification vs long detailed body — should not be treated as duplicates
        val memories = listOf(
            mem(1, "Hi"),
            mem(2, "Hi, I just wanted to reach out and let you know about the upcoming team meeting scheduled for next Monday at 10 AM in the main conference room.")
        )
        val result = deduplicateMemories(memories)
        assertEquals(2, result.size)
    }

    @Test fun `isRead is false if any duplicate is unread`() {
        val memories = listOf(
            mem(1, "Same text").copy(isRead = true),
            mem(2, "Same text").copy(isRead = false)
        )
        val result = deduplicateMemories(memories)
        assertEquals(1, result.size)
        assertFalse(result[0].isRead)
    }

    @Test fun `line overlap triggers merge`() {
        val sharedLine = "Alex: Let's meet at 3 PM"
        val memories = listOf(
            mem(1, "$sharedLine\nBob: Sounds good"),
            mem(2, "$sharedLine\nCarol: I'll be there too")
        )
        val result = deduplicateMemories(memories)
        assertEquals(1, result.size)
    }
}
