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
import java.time.ZoneOffset

/**
 * Phase-8b `GoalProgressCalculator` tests.
 *
 * All dates run in UTC so the Kotlin side and the server (which also runs
 * in UTC) agree on "today" without any surprising zone drift.
 *
 * Reference window: a 7-day goal starting Mon 2026-10-05, so lastDay is
 * Sun 2026-10-11.
 */
class GoalProgressCalculatorTest {

    private val zone = ZoneOffset.UTC

    private val startDate: LocalDate = LocalDate.parse("2026-10-05") // Monday
    private val lastDay: LocalDate   = LocalDate.parse("2026-10-11") // Sunday
    private val createdAt: Instant   = startDate.atStartOfDay(zone).toInstant()

    @Test
    fun `status on the first day is Active, no workouts yet`() = runTest {
        val snap = snapshotFor(now = startDate.atStartOfDay(zone).toInstant())
        assertEquals(Goal.Status.Active, snap.status)
        assertEquals(7L, snap.daysLeft)
    }

    @Test
    fun `status mid-week is Active with the right days left`() = runTest {
        val wed = LocalDate.parse("2026-10-07").atStartOfDay(zone).toInstant()
        val snap = snapshotFor(now = wed)
        assertEquals(Goal.Status.Active, snap.status)
        assertEquals(5L, snap.daysLeft) // Wed..Sun inclusive = 5
    }

    @Test
    fun `status on the last day is Active, daysLeft = 1 ("Last day")`() = runTest {
        val sun = LocalDate.parse("2026-10-11").atStartOfDay(zone).toInstant()
        val snap = snapshotFor(now = sun)
        assertEquals(Goal.Status.Active, snap.status)
        assertEquals(1L, snap.daysLeft)
    }

    @Test
    fun `day after the last day with target unmet flips to Ended`() = runTest {
        val mon = LocalDate.parse("2026-10-12").atStartOfDay(zone).toInstant()
        val snap = snapshotFor(now = mon)
        assertEquals(Goal.Status.Ended, snap.status)
        assertEquals(0L, snap.daysLeft)
    }

    @Test
    fun `goal completed early stays Completed even after the last day passes`() = runTest {
        // Target 10 km; a 15 km run inside the window hits it; now we fast
        // forward to a week after the goal's lastDay — status must still
        // be Completed, not Ended.
        val run = RunningWorkout(
            id = "r", startTime = startDate.atStartOfDay(zone).toInstant()
                .plus(Duration.ofHours(10)),
            duration = Duration.ofMinutes(60), distanceMeters = 15_000.0,
        )
        val afterWindow = lastDay.plusDays(7).atStartOfDay(zone).toInstant()
        val snap = snapshotFor(now = afterWindow, workouts = listOf(run))
        assertEquals(Goal.Status.Completed, snap.status)
    }

    @Test
    fun `workout at 23 59 on last day counts, 00 00 next day does not`() = runTest {
        val lateOnLastDay = lastDay.atStartOfDay(zone)
            .plusHours(23).plusMinutes(59).toInstant()
        val firstMinOfNextDay = lastDay.plusDays(1).atStartOfDay(zone).toInstant()
        val workouts = listOf(
            RunningWorkout(id = "late", startTime = lateOnLastDay,
                duration = Duration.ofMinutes(30), distanceMeters = 5_000.0),
            RunningWorkout(id = "next", startTime = firstMinOfNextDay,
                duration = Duration.ofMinutes(30), distanceMeters = 5_000.0),
        )
        // Evaluated at midnight of the day AFTER last-day so BOTH exist in
        // the dataset but only `late` falls inside the goal's window.
        val snap = snapshotFor(now = firstMinOfNextDay, workouts = workouts)
        assertEquals(1, snap.sessionsLogged)
        assertEquals(5.0, snap.currentValue, 1e-6)
    }

    @Test
    fun `averagePerDay on the first day divides by 1, not zero`() = runTest {
        // 3 km run on day 1 → avg = 3 km / 1 day
        val run = RunningWorkout(
            id = "r",
            startTime = startDate.atStartOfDay(zone).plusHours(6).toInstant(),
            duration = Duration.ofMinutes(20), distanceMeters = 3_000.0,
        )
        val day1 = startDate.atStartOfDay(zone).plusHours(23).toInstant()
        val snap = snapshotFor(now = day1, workouts = listOf(run))
        assertEquals(3.0, snap.averagePerDay, 1e-6)
    }

    @Test
    fun `neededPerDay on day 1 is target divided by durationDays`() = runTest {
        val day1 = startDate.atStartOfDay(zone).toInstant()
        val snap = snapshotFor(now = day1)
        // 10 km / 7 days ≈ 1.4286
        assertTrue("expected ≈ 1.43, got ${snap.neededPerDay}",
            kotlin.math.abs(snap.neededPerDay - (10.0 / 7.0)) < 1e-6)
    }

    @Test
    fun `neededPerDay returns 0 once the goal is completed`() = runTest {
        val run = RunningWorkout(
            id = "r", startTime = startDate.atStartOfDay(zone).toInstant(),
            duration = Duration.ofMinutes(60), distanceMeters = 12_000.0,
        )
        val snap = snapshotFor(
            now = startDate.plusDays(2).atStartOfDay(zone).toInstant(),
            workouts = listOf(run),
        )
        assertEquals(0.0, snap.neededPerDay, 0.0)
    }

    @Test
    fun `neededPerDay returns 0 once the goal has ended`() = runTest {
        val snap = snapshotFor(now = lastDay.plusDays(3).atStartOfDay(zone).toInstant())
        assertEquals(0.0, snap.neededPerDay, 0.0)
    }

    // ---------- helpers ----------

    private suspend fun snapshotFor(
        now: Instant,
        workouts: List<Workout> = emptyList(),
    ): GoalWithProgress {
        val goals = MutableStateFlow<List<Goal>>(
            listOf(
                DistanceGoal(
                    id = "g", title = "Run 10 km",
                    activityType = ActivityType.RUNNING,
                    targetValue = 10.0,
                    startDate = startDate,
                    durationDays = 7,
                    createdAt = createdAt,
                )
            )
        )
        val workoutFlow = MutableStateFlow(workouts)
        val calc = GoalProgressCalculator(
            goalRepository = FakeGoalRepo(goals),
            workoutRepository = FakeWorkoutRepo(workoutFlow),
            clock = Clock.fixed(now, zone),
            zone = zone,
        )
        return calc.observe().first().single()
    }

    // ---- test doubles ----

    private class FakeGoalRepo(private val flow: MutableStateFlow<List<Goal>>) : GoalRepository {
        override fun observeGoals(): Flow<List<Goal>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun createGoal(
            title: String, activityType: ActivityType?, goalType: GoalType,
            targetValue: Double, startDate: LocalDate, durationDays: Int,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun updateGoal(
            id: String, title: String, activityType: ActivityType?,
            goalType: GoalType, targetValue: Double, durationDays: Int,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun deleteGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun markCompletedNotified(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun makeMainGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun extendGoal(id: String, days: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private class FakeWorkoutRepo(
        private val flow: MutableStateFlow<List<Workout>>,
    ) : WorkoutRepository {
        override fun observeWorkouts(): Flow<List<Workout>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun addWorkout(
            workout: Workout, routePoints: List<com.elijah.fitness.tracking.TrackPoint>,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun getWorkout(id: String): Workout? = flow.value.firstOrNull { it.id == id }
        override suspend fun getWorkoutDetail(
            clientUuid: String,
        ): ApiResult<WorkoutDetail> = ApiResult.Error("not supported in test fake", 404, emptyMap())
    }
}
