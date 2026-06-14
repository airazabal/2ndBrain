package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.goals.Goal
import com.alex.a2ndbrain.core.goals.GoalProgressCalculator
import com.alex.a2ndbrain.core.goals.GoalTrend
import com.alex.a2ndbrain.core.goals.GoalType
import com.alex.a2ndbrain.core.habits.HabitCompletionEntity
import com.alex.a2ndbrain.core.health.DailyHealthMetrics
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.exercise.ExerciseType
import com.alex.a2ndbrain.fakes.FakeExerciseRepository
import com.alex.a2ndbrain.fakes.FakeHabitRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GoalProgressCalculatorTest {

    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var habitRepo: FakeHabitRepository
    private lateinit var healthRepo: HealthRepository
    private lateinit var calculator: GoalProgressCalculator

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val today = dateFmt.format(Date())
    private fun daysAgo(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return dateFmt.format(cal.time)
    }

    @Before
    fun setUp() {
        exerciseRepo = FakeExerciseRepository()
        habitRepo = FakeHabitRepository()
        healthRepo = mockk(relaxed = true)
        calculator = GoalProgressCalculator(exerciseRepo, habitRepo, healthRepo)
    }

    // ── EXERCISE_SESSIONS ────────────────────────────────────────────────────

    @Test
    fun `exercise goal counts sessions in period`() = runTest {
        exerciseRepo.logSession(ExerciseType.WALKING, 30, startedAt = 0L)
        exerciseRepo.logSession(ExerciseType.RUNNING, 45, startedAt = 0L)

        val goal = goal(GoalType.EXERCISE_SESSIONS, target = 3f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(2, result.currentValue.toInt())
        assertEquals(GoalTrend.BEHIND, result.trend)
    }

    @Test
    fun `exercise goal AHEAD when sessions meet target`() = runTest {
        repeat(3) { exerciseRepo.logSession(ExerciseType.WALKING, 30) }

        val goal = goal(GoalType.EXERCISE_SESSIONS, target = 3f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(GoalTrend.AHEAD, result.trend)
        assertEquals(1f, result.progressFraction, 0.001f)
    }

    @Test
    fun `exercise goal ignores soft-deleted sessions`() = runTest {
        exerciseRepo.logSession(ExerciseType.WALKING, 30)
        val id = exerciseRepo.currentSessions().first().id
        exerciseRepo.deleteSession(id)

        val goal = goal(GoalType.EXERCISE_SESSIONS, target = 1f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(0, result.currentValue.toInt())
        assertEquals(GoalTrend.CRITICAL, result.trend)
    }

    // ── HABIT_COMPLETION ────────────────────────────────────────────────────

    @Test
    fun `habit goal counts recent completions`() = runTest {
        habitRepo.seedCompletions(
            HabitCompletionEntity("h1", today),
            HabitCompletionEntity("h1", daysAgo(3)),
            HabitCompletionEntity("h1", daysAgo(5)),
        )

        val goal = goal(GoalType.HABIT_COMPLETION, target = 5f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(3, result.currentValue.toInt())
        assertEquals(GoalTrend.BEHIND, result.trend)
    }

    @Test
    fun `habit goal filters by linkedHabitId when set`() = runTest {
        habitRepo.seedCompletions(
            HabitCompletionEntity("h1", today),
            HabitCompletionEntity("h2", today),
            HabitCompletionEntity("h1", daysAgo(2)),
        )

        val goal = goal(GoalType.HABIT_COMPLETION, target = 3f, period = 7, linkedHabitId = "h1")
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(2, result.currentValue.toInt())
    }

    @Test
    fun `habit goal ON_TRACK at 70 percent`() = runTest {
        habitRepo.seedCompletions(
            HabitCompletionEntity("h1", today),
            HabitCompletionEntity("h1", daysAgo(1)),
            HabitCompletionEntity("h1", daysAgo(2)),
            HabitCompletionEntity("h1", daysAgo(3)),
            HabitCompletionEntity("h1", daysAgo(4)),
            HabitCompletionEntity("h1", daysAgo(5)),
            HabitCompletionEntity("h1", daysAgo(6)),
        )

        val goal = goal(GoalType.HABIT_COMPLETION, target = 10f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(GoalTrend.ON_TRACK, result.trend)
    }

    // ── STEPS_DAILY ─────────────────────────────────────────────────────────

    @Test
    fun `steps goal averages daily steps across period`() = runTest {
        coEvery { healthRepo.getPeriodMetrics(7) } returns Pair(
            listOf(
                DailyHealthMetrics(date = today,       steps = 8_000),
                DailyHealthMetrics(date = daysAgo(1),  steps = 10_000),
                DailyHealthMetrics(date = daysAgo(2),  steps = 12_000),
            ),
            false
        )

        val goal = goal(GoalType.STEPS_DAILY, target = 10_000f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(10_000, result.currentValue.toInt())
        assertEquals(GoalTrend.AHEAD, result.trend)
    }

    @Test
    fun `steps goal ignores days with no steps data`() = runTest {
        coEvery { healthRepo.getPeriodMetrics(7) } returns Pair(
            listOf(
                DailyHealthMetrics(date = today,      steps = 6_000),
                DailyHealthMetrics(date = daysAgo(1), steps = 0),     // no data day
                DailyHealthMetrics(date = daysAgo(2), steps = 6_000),
            ),
            false
        )

        val goal = goal(GoalType.STEPS_DAILY, target = 6_000f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        // Average of the two days with steps: (6000 + 6000) / 2 = 6000
        assertEquals(6_000, result.currentValue.toInt())
        assertEquals(GoalTrend.AHEAD, result.trend)
    }

    // ── SLEEP_DAILY ──────────────────────────────────────────────────────────

    @Test
    fun `sleep goal averages nightly hours`() = runTest {
        coEvery { healthRepo.getPeriodMetrics(7) } returns Pair(
            listOf(
                DailyHealthMetrics(date = today,      sleepMinutes = 420),  // 7h
                DailyHealthMetrics(date = daysAgo(1), sleepMinutes = 480),  // 8h
            ),
            false
        )

        val goal = goal(GoalType.SLEEP_DAILY, target = 8f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        // avg = (7 + 8) / 2 = 7.5h  → fraction = 7.5/8 = 0.9375 → ON_TRACK (≥0.7, <1.0)
        assertEquals(7.5f, result.currentValue, 0.01f)
        assertEquals(GoalTrend.ON_TRACK, result.trend)
    }

    @Test
    fun `sleep goal CRITICAL when far below target`() = runTest {
        coEvery { healthRepo.getPeriodMetrics(7) } returns Pair(
            listOf(DailyHealthMetrics(date = today, sleepMinutes = 180)), // 3h
            false
        )

        val goal = goal(GoalType.SLEEP_DAILY, target = 8f, period = 7)
        val result = calculator.compute(listOf(goal)).first()

        assertEquals(GoalTrend.CRITICAL, result.trend)
    }

    // ── Multiple goals computed in parallel ──────────────────────────────────

    @Test
    fun `compute handles multiple goals and returns one result per goal`() = runTest {
        exerciseRepo.logSession(ExerciseType.WALKING, 30)
        coEvery { healthRepo.getPeriodMetrics(any()) } returns Pair(emptyList(), false)

        val goals = listOf(
            goal(GoalType.EXERCISE_SESSIONS, target = 3f, period = 7),
            goal(GoalType.STEPS_DAILY,       target = 10_000f, period = 7),
        )
        val results = calculator.compute(goals)

        assertEquals(2, results.size)
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun goal(
        type: GoalType,
        target: Float,
        period: Int,
        linkedHabitId: String? = null
    ) = Goal(
        id = "test-${type.name}",
        title = type.name,
        type = type,
        targetValue = target,
        periodDays = period,
        isActive = true,
        createdAt = System.currentTimeMillis(),
        linkedHabitId = linkedHabitId,
        linkedExerciseType = null
    )
}
