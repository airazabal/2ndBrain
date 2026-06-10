package com.alex.a2ndbrain

import app.cash.turbine.test
import com.alex.a2ndbrain.core.goals.GoalEntity
import com.alex.a2ndbrain.core.goals.GoalProgress
import com.alex.a2ndbrain.core.goals.GoalProgressCalculator
import com.alex.a2ndbrain.core.goals.GoalTrend
import com.alex.a2ndbrain.core.goals.GoalType
import com.alex.a2ndbrain.core.exercise.ExerciseType
import com.alex.a2ndbrain.fakes.FakeExerciseRepository
import com.alex.a2ndbrain.fakes.FakeGoalDao
import com.alex.a2ndbrain.fakes.FakeHabitRepository
import com.alex.a2ndbrain.ui.goals.GoalsViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeGoalDao
    private lateinit var habitRepo: FakeHabitRepository
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var calculator: GoalProgressCalculator
    private lateinit var viewModel: GoalsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeGoalDao()
        habitRepo = FakeHabitRepository()
        exerciseRepo = FakeExerciseRepository()

        val healthRepo = mockk<com.alex.a2ndbrain.core.health.HealthRepository>(relaxed = true)
        coEvery { healthRepo.getPeriodMetrics(any()) } returns Pair(emptyList(), false)

        calculator = GoalProgressCalculator(exerciseRepo, habitRepo, healthRepo)
        viewModel = GoalsViewModel(dao, calculator, habitRepo, exerciseRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── showAddSheet ──────────────────────────────────────────────────────────

    @Test
    fun `showAddSheet clears all sheet fields`() = runTest {
        // Pre-populate some state by simulating a previous edit
        val goal = seedGoal()
        viewModel.showEditSheet(goal)
        advanceUntilIdle()

        viewModel.showAddSheet()

        val state = viewModel.state.value
        assertTrue(state.showSheet)
        assertNull(state.editingGoalId)
        assertEquals("", state.sheetTitle)
        assertEquals("", state.sheetTarget)
        assertEquals(7, state.sheetPeriod)
        assertNull(state.sheetLinkedHabitId)
    }

    // ── showEditSheet ─────────────────────────────────────────────────────────

    @Test
    fun `showEditSheet pre-fills state from goal`() = runTest {
        val goal = GoalEntity(
            id = "g1",
            title = "Run 3x/week",
            type = GoalType.EXERCISE_SESSIONS.name,
            targetValue = 3f,
            periodDays = 7
        )

        viewModel.showEditSheet(goal)

        val state = viewModel.state.value
        assertTrue(state.showSheet)
        assertEquals("g1", state.editingGoalId)
        assertEquals("Run 3x/week", state.sheetTitle)
        assertEquals(GoalType.EXERCISE_SESSIONS, state.sheetType)
        assertEquals("3", state.sheetTarget)
        assertEquals(7, state.sheetPeriod)
    }

    @Test
    fun `showEditSheet formats decimal target without trailing zero`() = runTest {
        val goal = GoalEntity(
            id = "g2", title = "Sleep", type = GoalType.SLEEP_DAILY.name,
            targetValue = 7.5f, periodDays = 7
        )
        viewModel.showEditSheet(goal)

        assertEquals("7.5", viewModel.state.value.sheetTarget)
    }

    @Test
    fun `showEditSheet formats integer target without decimal point`() = runTest {
        val goal = GoalEntity(
            id = "g3", title = "Steps", type = GoalType.STEPS_DAILY.name,
            targetValue = 10000f, periodDays = 7
        )
        viewModel.showEditSheet(goal)

        assertEquals("10000", viewModel.state.value.sheetTarget)
    }

    // ── saveGoal — add mode ──────────────────────────────────────────────────

    @Test
    fun `saveGoal in add mode inserts new entity with unique id`() = runTest {
        viewModel.showAddSheet()
        viewModel.setTitle("Morning Run")
        viewModel.setType(GoalType.EXERCISE_SESSIONS)
        viewModel.setTarget("3")

        viewModel.saveGoal()
        advanceUntilIdle()

        assertEquals(1, dao.upserted.size)
        assertNull(dao.upserted.first().linkedHabitId)
        assertEquals("Morning Run", dao.upserted.first().title)
        assertEquals(GoalType.EXERCISE_SESSIONS.name, dao.upserted.first().type)
    }

    @Test
    fun `saveGoal in add mode does nothing when title is blank`() = runTest {
        viewModel.showAddSheet()
        viewModel.setTitle("  ")
        viewModel.setTarget("3")

        viewModel.saveGoal()
        advanceUntilIdle()

        assertTrue(dao.upserted.isEmpty())
    }

    @Test
    fun `saveGoal in add mode does nothing when target is not a number`() = runTest {
        viewModel.showAddSheet()
        viewModel.setTitle("My Goal")
        viewModel.setTarget("abc")

        viewModel.saveGoal()
        advanceUntilIdle()

        assertTrue(dao.upserted.isEmpty())
    }

    // ── saveGoal — edit mode ─────────────────────────────────────────────────

    @Test
    fun `saveGoal in edit mode reuses same id`() = runTest {
        val original = seedGoal(id = "original-id", title = "Old Title")

        viewModel.showEditSheet(original)
        viewModel.setTitle("New Title")
        viewModel.saveGoal()
        advanceUntilIdle()

        val saved = dao.upserted.last()
        assertEquals("original-id", saved.id)
        assertEquals("New Title", saved.title)
    }

    @Test
    fun `saveGoal in edit mode preserves original createdAt`() = runTest {
        val createdAt = 1_700_000_000_000L
        val original = seedGoal(id = "g99", createdAt = createdAt)

        viewModel.showEditSheet(original)
        viewModel.setTitle("Updated")
        viewModel.saveGoal()
        advanceUntilIdle()

        assertEquals(createdAt, dao.upserted.last().createdAt)
    }

    @Test
    fun `saveGoal in add mode generates different id each time`() = runTest {
        // Use Turbine to wait for each save to complete (sheet closes on success)
        viewModel.state.test {
            awaitItem() // initial state

            viewModel.showAddSheet()
            viewModel.setTitle("Goal A")
            viewModel.setTarget("3")
            viewModel.saveGoal()
            // Wait until sheet closes (isSaving → false, showSheet → false)
            var s = awaitItem()
            while (s.showSheet || s.isSaving) s = awaitItem()

            viewModel.showAddSheet()
            viewModel.setTitle("Goal B")
            viewModel.setTarget("3")
            viewModel.saveGoal()
            s = awaitItem()
            while (s.showSheet || s.isSaving) s = awaitItem()

            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, dao.upserted.size)
        assertNotEquals(dao.upserted[0].id, dao.upserted[1].id)
    }

    // ── sheet closes after save ──────────────────────────────────────────────

    @Test
    fun `showSheet is false after saveGoal completes`() = runTest {
        viewModel.showAddSheet()
        viewModel.setTitle("Test")
        viewModel.setTarget("5")

        viewModel.saveGoal()
        advanceUntilIdle()

        assertTrue(!viewModel.state.value.showSheet)
    }

    // ── reactive updates ─────────────────────────────────────────────────────

    @Test
    fun `progresses recompute when a new exercise session is logged`() = runTest {
        val goal = seedGoal(type = GoalType.EXERCISE_SESSIONS, target = 3f)

        viewModel.state.test {
            // Drain until we see the goal appear in progresses (initial compute from seeded goal)
            var s = awaitItem()
            while (s.progresses.isEmpty()) s = awaitItem()

            // Adding a session should trigger recompute via getAllSessionsFlow
            exerciseRepo.logSession(ExerciseType.WALKING, 30)

            // Wait for an update where the session is counted
            s = awaitItem()
            while (s.progresses.firstOrNull { it.goal.id == goal.id }?.currentValue?.toInt() != 1) {
                s = awaitItem()
            }

            val progress = s.progresses.first { it.goal.id == goal.id }
            assertEquals(1, progress.currentValue.toInt())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteGoal removes from dao`() = runTest {
        val goal = seedGoal(id = "del-1")

        viewModel.deleteGoal("del-1")
        advanceUntilIdle()

        assertTrue("del-1" in dao.deleted)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun seedGoal(
        id: String = "g-${System.nanoTime()}",
        title: String = "Test Goal",
        type: GoalType = GoalType.EXERCISE_SESSIONS,
        target: Float = 3f,
        createdAt: Long = System.currentTimeMillis()
    ): GoalEntity {
        val goal = GoalEntity(
            id = id, title = title,
            type = type.name, targetValue = target,
            periodDays = 7, createdAt = createdAt
        )
        dao.seed(goal)
        return goal
    }

    private fun fakeProgress(goal: GoalEntity) = GoalProgress(
        goal = goal, currentValue = 0f, progressFraction = 0f,
        trend = GoalTrend.CRITICAL, displayCurrent = "0", displayTarget = "3"
    )
}
