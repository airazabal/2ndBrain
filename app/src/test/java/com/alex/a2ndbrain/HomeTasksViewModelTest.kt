package com.alex.a2ndbrain

import app.cash.turbine.test
import com.alex.a2ndbrain.fakes.FakeTodoistRepository
import com.alex.a2ndbrain.fakes.FakeTodoistStatsRepository
import com.alex.a2ndbrain.core.todoist.TodoistTask
import com.alex.a2ndbrain.ui.home.HomeTasksViewModel
import io.mockk.every
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
class HomeTasksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var todoistRepo: FakeTodoistRepository
    private lateinit var statsRepo: FakeTodoistStatsRepository
    private lateinit var viewModel: HomeTasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        todoistRepo = FakeTodoistRepository()
        statsRepo = FakeTodoistStatsRepository()

        // Mock SharedPreferences to return a very recent last-notified time so the
        // hourly throttle fires immediately and maybeFireTodoistReminder returns early —
        // this prevents NotificationChannel construction, which fails on the JVM.
        val recentTime = System.currentTimeMillis()
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true) {
            every { getLong("last_notified_ms", 0L) } returns recentTime
        }
        val context = mockk<android.content.Context>(relaxed = true) {
            every { getSharedPreferences(any(), any()) } returns prefs
        }

        viewModel = HomeTasksViewModel(todoistRepo, statsRepo, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refreshTodoistTasks populates today tasks`() = runTest {
        val todayTask = task("1", "Buy groceries")
        todoistRepo.todayTasks = listOf(todayTask)

        viewModel.todoistTasks.test {
            awaitItem() // initial empty state

            viewModel.refreshTodoistTasks()

            assertEquals(listOf(todayTask), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshTodoistTasks populates overdue tasks`() = runTest {
        val overdueTask = task("2", "Reply to email")
        todoistRepo.overdueTasks = listOf(overdueTask)

        viewModel.overdueTasks.test {
            awaitItem() // initial empty state

            viewModel.refreshTodoistTasks()

            assertEquals(listOf(overdueTask), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completeTodoistTask removes task from list`() = runTest {
        val task = task("42", "Exercise")
        todoistRepo.todayTasks = listOf(task)
        todoistRepo.closeTaskResult = true

        viewModel.todoistTasks.test {
            awaitItem() // initial empty

            viewModel.refreshTodoistTasks()
            val populated = awaitItem()
            assertEquals(1, populated.size)

            viewModel.completeTodoistTask("42")
            val afterComplete = awaitItem()
            assertTrue(afterComplete.none { it.id == "42" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completeTodoistTask saves completion to stats repo`() = runTest {
        val task = task("42", "Exercise")
        todoistRepo.todayTasks = listOf(task)
        todoistRepo.closeTaskResult = true

        viewModel.todoistTasks.test {
            awaitItem()
            viewModel.refreshTodoistTasks()
            awaitItem()
            viewModel.completeTodoistTask("42")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(statsRepo.savedCompletions.any { it.first == "42" })
        assertTrue(todoistRepo.closedTaskIds.contains("42"))
    }

    @Test
    fun `completeTodoistTask keeps task when API returns false`() = runTest {
        val task = task("99", "Failing task")
        todoistRepo.todayTasks = listOf(task)
        todoistRepo.closeTaskResult = false

        viewModel.todoistTasks.test {
            awaitItem()
            viewModel.refreshTodoistTasks()
            awaitItem()
            viewModel.completeTodoistTask("99")
            // No further emission expected since task was not removed
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(viewModel.todoistTasks.value.any { it.id == "99" })
        assertTrue(statsRepo.savedCompletions.none { it.first == "99" })
    }

    @Test
    fun `todoistLoading transitions false-true-false during refresh`() = runTest {
        viewModel.todoistLoading.test {
            assertEquals(false, awaitItem()) // initial

            viewModel.refreshTodoistTasks()

            assertEquals(true, awaitItem())  // loading starts
            assertEquals(false, awaitItem()) // loading ends
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun task(id: String, content: String) = TodoistTask(
        id = id, content = content, description = "", priority = 1,
        dueDateStr = null, deadlineDateStr = null, url = ""
    )
}
