package com.elijah.fitness.data.model

import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class GoalProgressCalculatorTest {

    private val periodStart = Instant.parse("2026-09-14T00:00:00Z") // Monday
    private val periodEnd   = Instant.parse("2026-09-21T00:00:00Z") // exclusive
    private val now         = Instant.parse("2026-09-16T09:00:00Z") // Wed 09:00Z

    private val fixedClock: Clock = Clock.fixed(now, java.time.ZoneOffset.UTC)

    @Test
    fun `emits one snapshot per goal with computed progress`() = runTest {
        val goals = MutableStateFlow<List<Goal>>(
            listOf(
                DistanceGoal(
                    id = "1", title = "Run 10 km",
                    activityType = ActivityType.RUNNING,
                    targetValue = 10.0, period = GoalPeriod.THIS_WEEK,
                    periodStart = periodStart, periodEnd = periodEnd,
                    createdAt = periodStart,
                )
            )
        )
        val workouts = MutableStateFlow<List<Workout>>(
            listOf(
                RunningWorkout(
                    id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
                    duration = Duration.ofMinutes(30), distanceMeters = 4_000.0
                )
            )
        )
        val calc = GoalProgressCalculator(
            goalRepository = FakeGoalRepo(goals),
            workoutRepository = FakeWorkoutRepo(workouts),
            clock = fixedClock,
        )

        val snapshot = calc.observe().first().single()
        assertEquals("1", snapshot.goal.id)
        assertEquals(4.0, snapshot.currentValue, 1e-6)
        assertEquals(0.4, snapshot.progressFraction, 1e-6)
        assertEquals(false, snapshot.isComplete)
        // Wed 09:00 UTC → periodEnd Mon 00:00 UTC = 4 days 15 hours ≈ 4 whole days
        assertEquals(4L, snapshot.daysRemaining)
    }

    @Test
    fun `combine re-emits when workouts change`() = runTest {
        val goals = MutableStateFlow<List<Goal>>(
            listOf(
                DistanceGoal(
                    id = "1", title = "Run 10 km",
                    activityType = ActivityType.RUNNING,
                    targetValue = 10.0, period = GoalPeriod.THIS_WEEK,
                    periodStart = periodStart, periodEnd = periodEnd,
                    createdAt = periodStart,
                )
            )
        )
        val workouts = MutableStateFlow<List<Workout>>(emptyList())
        val calc = GoalProgressCalculator(
            FakeGoalRepo(goals), FakeWorkoutRepo(workouts), fixedClock,
        )

        // Snapshot 1: no workouts → progress 0.
        assertEquals(0.0, calc.observe().first().single().progressFraction, 1e-6)

        // Log a matching workout after subscribing.
        workouts.value = listOf(
            RunningWorkout(
                id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
                duration = Duration.ofMinutes(30), distanceMeters = 10_000.0
            )
        )
        // Combine's next emission reflects the new upstream value.
        val snap = calc.observe().first().single()
        assertEquals(1.0, snap.progressFraction, 1e-6)
        assertTrue(snap.isComplete)
    }

    @Test
    fun `combine re-emits when goals change`() = runTest {
        val goals = MutableStateFlow<List<Goal>>(emptyList())
        val workouts = MutableStateFlow<List<Workout>>(emptyList())
        val calc = GoalProgressCalculator(
            FakeGoalRepo(goals), FakeWorkoutRepo(workouts), fixedClock,
        )
        assertEquals(0, calc.observe().first().size)

        goals.value = listOf(
            WorkoutCountGoal(
                id = "g", title = "3 workouts", activityType = null,
                targetValue = 3.0, period = GoalPeriod.THIS_WEEK,
                periodStart = periodStart, periodEnd = periodEnd,
                createdAt = periodStart,
            )
        )
        assertEquals(1, calc.observe().first().size)
    }

    @Test
    fun `daysRemaining is negative after the period end`() = runTest {
        val afterEnd = Instant.parse("2026-09-22T09:00:00Z")
        val clockAfter = Clock.fixed(afterEnd, java.time.ZoneOffset.UTC)
        val goals = MutableStateFlow<List<Goal>>(
            listOf(
                WorkoutCountGoal(
                    id = "g", title = "expired", activityType = null,
                    targetValue = 4.0, period = GoalPeriod.THIS_WEEK,
                    periodStart = periodStart, periodEnd = periodEnd,
                    createdAt = periodStart,
                )
            )
        )
        val workouts = MutableStateFlow<List<Workout>>(emptyList())
        val calc = GoalProgressCalculator(
            FakeGoalRepo(goals), FakeWorkoutRepo(workouts), clockAfter,
        )
        val snap = calc.observe().first().single()
        assertTrue("expected negative, got ${snap.daysRemaining}", snap.daysRemaining < 0)
    }

    // ---------- test doubles ----------

    private class FakeGoalRepo(
        private val flow: MutableStateFlow<List<Goal>>,
    ) : GoalRepository {
        override fun observeGoals(): Flow<List<Goal>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun createGoal(
            title: String, activityType: ActivityType?, goalType: GoalType,
            period: GoalPeriod, targetValue: Double,
            periodStart: LocalDate, periodEnd: LocalDate,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun updateGoal(
            id: String, title: String, activityType: ActivityType?, targetValue: Double,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun deleteGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun markCompletedNotified(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private class FakeWorkoutRepo(
        private val flow: MutableStateFlow<List<Workout>>,
    ) : WorkoutRepository {
        override fun observeWorkouts(): Flow<List<Workout>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun addWorkout(workout: Workout, routePoints: List<com.elijah.fitness.tracking.TrackPoint>): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun getWorkout(id: String): Workout? = flow.value.firstOrNull { it.id == id }
    }
}
