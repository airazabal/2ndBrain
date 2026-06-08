package com.alex.a2ndbrain

import app.cash.turbine.test
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.fakes.FakeMemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeMemoryRepositoryTest {

    private lateinit var repo: FakeMemoryRepository

    @Before
    fun setUp() {
        repo = FakeMemoryRepository()
    }

    @Test
    fun `getAllMemoriesFlow emits updated list after insert`() = runTest {
        repo.getAllMemoriesFlow().test {
            assertEquals(emptyList<MemoryEntity>(), awaitItem())

            repo.insertMemory(memory(1, "Hello"))
            assertEquals(1, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markAsRead updates isRead flag`() = runTest {
        repo.memories.value = listOf(memory(1, "Test", isRead = false))

        repo.markAsRead(1L)

        assertTrue(repo.memories.value.first().isRead)
    }

    @Test
    fun `deleteMemoryById removes the item`() = runTest {
        repo.memories.value = listOf(memory(1, "To delete"), memory(2, "Keep"))

        repo.deleteMemoryById(1L)

        assertEquals(1, repo.memories.value.size)
        assertEquals(2L, repo.memories.value.first().id)
        assertTrue(repo.deletedIds.contains(1L))
    }

    @Test
    fun `pruneOldMemories removes memories before timestamp`() = runTest {
        val now = System.currentTimeMillis()
        repo.memories.value = listOf(
            memory(1, "Old", timestamp = now - 10_000),
            memory(2, "New", timestamp = now)
        )

        repo.pruneOldMemories(now - 5_000)

        assertEquals(1, repo.memories.value.size)
        assertEquals(2L, repo.memories.value.first().id)
    }

    @Test
    fun `findExisting returns null when no match`() = runTest {
        repo.memories.value = listOf(memory(1, "Something else"))

        val result = repo.findExisting("notification", "com.app", "Title", "Content")

        assertNull(result)
    }

    @Test
    fun `searchMemoriesSync filters by content`() = runTest {
        repo.memories.value = listOf(
            memory(1, "Meeting at 3pm"),
            memory(2, "Buy groceries")
        )

        val result = repo.searchMemoriesSync("meeting")

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    private fun memory(
        id: Long,
        content: String,
        isRead: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ) = MemoryEntity(
        id = id,
        source = "notification",
        packageName = "com.test",
        title = null,
        content = content,
        timestamp = timestamp,
        isRead = isRead,
        tags = null,
        duplicateCount = 0
    )
}
