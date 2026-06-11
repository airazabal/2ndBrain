package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.agents.MemoryAgent
import com.alex.a2ndbrain.core.agents.ModelRouter
import com.alex.a2ndbrain.core.domain.RetrieveMemoriesUseCase
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.fakes.FakeMemoryRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetrieveMemoriesUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeMemoryRepository
    private lateinit var useCase: RetrieveMemoriesUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeMemoryRepository()
        useCase = RetrieveMemoriesUseCase(MemoryAgent(repo, mockk(relaxed = true)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank query returns all recent memories`() = runTest {
        val memories = listOf(
            memory(1, "Meeting at 3pm"),
            memory(2, "Grocery list")
        )
        repo.memories.value = memories

        val result = useCase()

        assertEquals(memories.size, result.size)
    }

    @Test
    fun `keyword query ranks matching memories above non-matching`() = runTest {
        val now = System.currentTimeMillis()
        repo.memories.value = listOf(
            memory(1, "Meeting with John about budget review", timestamp = now),
            memory(2, "Buy groceries", timestamp = now - 1000),
            memory(3, "Budget planning for Q3", timestamp = now - 2000)
        )

        val result = useCase(query = "budget", limit = 10)

        // Both budget-related items should appear before the unrelated one
        val budgetIds = result.filter { it.content.contains("budget", ignoreCase = true) }.map { it.id }
        val unrelatedIdx = result.indexOfFirst { !it.content.contains("budget", ignoreCase = true) }
        val lastBudgetIdx = result.indexOfLast { it.content.contains("budget", ignoreCase = true) }
        assertTrue("Budget items should rank before unrelated item", lastBudgetIdx < unrelatedIdx || unrelatedIdx == -1)
        assertEquals(2, budgetIds.size)
    }

    @Test
    fun `limit caps the number of returned memories`() = runTest {
        repo.memories.value = (1..20).map { memory(it.toLong(), "Memory $it") }

        val result = useCase(limit = 5)

        assertEquals(5, result.size)
    }

    @Test
    fun `empty repository returns empty list`() = runTest {
        repo.memories.value = emptyList()

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `clipboard source scores higher than notification for same content`() = runTest {
        val now = System.currentTimeMillis()
        repo.memories.value = listOf(
            memory(1, "important note", source = "notification", timestamp = now),
            memory(2, "important note", source = "clipboard", timestamp = now)
        )

        val result = useCase(query = "important note", limit = 10)

        // Clipboard should rank first due to higher source weight
        assertEquals(2L, result.first().id)
    }

    @Test
    fun `invoke with defaults uses empty query and limit 50`() = runTest {
        repo.memories.value = (1..60).map { memory(it.toLong(), "Memory $it") }

        val result = useCase()

        assertEquals(50, result.size)
    }

    private fun memory(
        id: Long,
        content: String,
        source: String = "notification",
        timestamp: Long = System.currentTimeMillis()
    ) = MemoryEntity(
        id = id,
        source = source,
        packageName = "com.test.app",
        title = null,
        content = content,
        timestamp = timestamp,
        isRead = false,
        tags = null,
        duplicateCount = 0
    )
}
