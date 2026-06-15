package com.alex.a2ndbrain

import app.cash.turbine.test
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.fakes.FakeMemoryRepository
import com.alex.a2ndbrain.ui.search.SearchResults
import com.alex.a2ndbrain.ui.search.SearchViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeMemoryRepository
    private lateinit var vm: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeMemoryRepository()
        vm = SearchViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── SearchResults data class ──────────────────────────────────────────────

    @Test fun `default SearchResults has no error`() {
        val r = SearchResults()
        assertNull(r.error)
        assertFalse(r.isLoading)
        assertTrue(r.isEmpty)
    }

    @Test fun `isEmpty is false when error is set`() {
        val r = SearchResults(error = "Something went wrong")
        assertFalse(r.isEmpty)
    }

    @Test fun `isEmpty is false when memories present`() {
        val mem = MemoryEntity(
            id = 1, source = "notification", packageName = "com.example",
            title = "Hello", content = "World", timestamp = System.currentTimeMillis()
        )
        val r = SearchResults(memories = listOf(mem))
        assertFalse(r.isEmpty)
    }

    @Test fun `total counts memories and summaries`() {
        val mem = MemoryEntity(
            id = 1, source = "notification", packageName = "com.example",
            title = "T", content = "C", timestamp = System.currentTimeMillis()
        )
        val r = SearchResults(memories = listOf(mem, mem))
        assertEquals(2, r.total)
    }

    // ── Query below minimum length ────────────────────────────────────────────

    @Test fun `query shorter than 2 chars keeps results empty`() = runTest {
        // StateFlow deduplicates SearchResults() == SearchResults() so no new emission;
        // verify the current value directly rather than waiting for a turbine event.
        vm.setQuery("a")
        advanceUntilIdle()
        val r = vm.results.value
        assertFalse(r.isLoading)
        assertNull(r.error)
        assertTrue(r.isEmpty)
    }

    // ── Successful search ─────────────────────────────────────────────────────

    @Test fun `valid query emits loading then results`() = runTest {
        val mem = MemoryEntity(
            id = 1, source = "notification", packageName = "com.example",
            title = "Meeting notes", content = "meeting recap for today",
            timestamp = System.currentTimeMillis()
        )
        repo.memories.value = listOf(mem)

        vm.results.test {
            awaitItem() // initial

            vm.setQuery("meeting")
            advanceUntilIdle()

            // loading state
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            assertNull(loading.error)

            // results
            val results = awaitItem()
            assertFalse(results.isLoading)
            assertNull(results.error)
            assertEquals(1, results.memories.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `empty query keeps results empty`() = runTest {
        vm.setQuery("")
        advanceUntilIdle()
        val r = vm.results.value
        assertTrue(r.isEmpty)
        assertNull(r.error)
    }

    // ── Error recovery ────────────────────────────────────────────────────────

    private fun throwingRepo(exception: Exception = RuntimeException("DB locked")): MemoryRepository {
        val mock = mockk<MemoryRepository>()
        coEvery { mock.searchMemoriesSync(any()) } throws exception
        coEvery { mock.searchSummaries(any()) } returns emptyList()
        coEvery { mock.getAllMemoriesFlow() } returns MutableStateFlow(emptyList())
        coEvery { mock.getAllSummariesFlow() } returns MutableStateFlow(emptyList())
        return mock
    }

    @Test fun `database exception emits error state, not stuck loading`() = runTest {
        val errorVm = SearchViewModel(throwingRepo())

        errorVm.results.test {
            awaitItem() // initial

            errorVm.setQuery("crash")
            advanceUntilIdle()

            val loading = awaitItem()
            assertTrue(loading.isLoading)

            // must NOT stay stuck — should emit error state
            val error = awaitItem()
            assertFalse(error.isLoading)
            assertNotNull(error.error)
            assertTrue(error.memories.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `error state message is non-blank`() = runTest {
        val errorVm = SearchViewModel(throwingRepo(IllegalStateException("lock contention")))

        errorVm.results.test {
            awaitItem()
            errorVm.setQuery("test")
            advanceUntilIdle()
            awaitItem() // loading
            val error = awaitItem()
            assertTrue("error message should be non-blank", error.error!!.isNotBlank())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `subsequent valid query after error recovers successfully`() = runTest {
        // First: a throwing repo
        val errorVm = SearchViewModel(throwingRepo())

        errorVm.results.test {
            awaitItem() // initial

            errorVm.setQuery("fail")
            advanceUntilIdle()
            awaitItem() // loading
            val errorState = awaitItem()
            assertNotNull(errorState.error)

            cancelAndIgnoreRemainingEvents()
        }

        // Then: a fresh VM with a working repo — simulates retry
        val recoveredVm = SearchViewModel(repo)
        recoveredVm.results.test {
            awaitItem() // initial
            recoveredVm.setQuery("ok")
            advanceUntilIdle()
            awaitItem() // loading
            val recovered = awaitItem()
            assertNull(recovered.error)
            assertFalse(recovered.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
