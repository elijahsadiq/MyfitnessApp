package com.elijah.fitness.data.model

import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.tracking.TrackPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Phase 8d step 6: swims and rows count toward the right goals.
 *
 * A goal counts the workouts of its own activity, and every workout when
 * it is for "any activity" (`null`). Swims and rows are distance
 * workouts, so they count toward distance goals in km (a 1,500 m swim is
 * 1.5 km), and like any workout toward session and time goals. They
 * never count toward a strength-volume goal.
 */
class GoalSwimRowProgressTest {

    private val zone = ZoneOffset.UTC
    private val start: LocalDate = LocalDate.parse("2026-10-05")
    private val createdAt: Instant = start.atStartOfDay(zone).toInstant()
    private val inside: Instant = Instant.parse("2026-10-07T07:00:00Z")

    // One of each: a 1,500 m pool swim (32 min), a 6 km row (30 min),
    // a 5 km run (28 min), a 20 km ride (60 min), a 45 min lift.
    private val swim = SwimmingWorkout(
        id = "swim", startTime = inside, duration = Duration.ofMinutes(32),
        distanceMeters = 1_500.0, poolLengthM = 25, laps = 60,
    )
    private val row = RowingWorkout(
        id = "row", startTime = inside, duration = Duration.ofMinutes(30),
        distanceMeters = 6_000.0,
    )
    private val run = RunningWorkout(
        id = "run", startTime = inside, duration = Duration.ofMinutes(28),
        distanceMeters = 5_000.0,
    )
    private val ride = CyclingWorkout(
        id = "ride", startTime = inside, duration = Duration.ofMinutes(60),
        distanceMeters = 20_000.0,
    )
    private val lift = StrengthWorkout(
        id = "lift", startTime = inside, duration = Duration.ofMinutes(45),
        sets = listOf(StrengthSet(exercise = "Squat", reps = 5, weightKg = 100.0)),
    )
    private val all: List<Workout> = listOf(swim, row, run, ride, lift)

    // ---------------- distance ----------------

    @Test
    fun `a swim distance goal counts swims only, in km`() {
        val goal = distanceGoal(ActivityType.SWIMMING, target = 10.0)
        assertEquals(1.5, goal.currentValue(all, zone), 1e-9)
        assertEquals(1, goal.sessionsLogged(all, zone))
        assertEquals(0.15, goal.progressFraction(all, zone), 1e-9)
    }

    @Test
    fun `a row distance goal counts rows only, in km`() {
        val goal = distanceGoal(ActivityType.ROWING, target = 30.0)
        assertEquals(6.0, goal.currentValue(all, zone), 1e-9)
        assertEquals(1, goal.sessionsLogged(all, zone))
    }

    @Test
    fun `run and ride distance goals ignore swims and rows`() {
        assertEquals(5.0, distanceGoal(ActivityType.RUNNING, 50.0).currentValue(all, zone), 1e-9)
        assertEquals(20.0, distanceGoal(ActivityType.CYCLING, 100.0).currentValue(all, zone), 1e-9)
    }

    @Test
    fun `a half-kilometre swim target is met by exactly that distance`() {
        val goal = distanceGoal(ActivityType.SWIMMING, target = 0.5)
        val short = swim(metres = 475.0)
        val enough = swim(metres = 500.0)
        assertEquals(Goal.Status.Active, goal.status(listOf(short), start, zone))
        assertEquals(Goal.Status.Completed, goal.status(listOf(enough), start, zone))
    }

    @Test
    fun `swims that add up to the target exactly complete the goal`() {
        // Newest first, as the DAO returns them: 600 m (Wed), 700 m (Tue),
        // 200 m (Mon). Summed as km that is 1.4999999999999998.
        val swims = listOf(
            swim(metres = 600.0, id = "wed", at = Instant.parse("2026-10-07T07:00:00Z")),
            swim(metres = 700.0, id = "tue", at = Instant.parse("2026-10-06T07:00:00Z")),
            swim(metres = 200.0, id = "mon", at = Instant.parse("2026-10-05T07:00:00Z")),
        )
        val goal = distanceGoal(ActivityType.SWIMMING, target = 1.5)
        assertEquals(1.5, goal.currentValue(swims, zone), 0.0)
        assertEquals(Goal.Status.Completed, goal.status(swims, start, zone))
        assertEquals(1.0, goal.progressFraction(swims, zone), 0.0)
        assertEquals(0.0, goal.neededPerDay(swims, start, zone), 0.0)

        val oneKm = distanceGoal(ActivityType.SWIMMING, target = 1.0)
        val three = listOf(swim(300.0, "a"), swim(600.0, "b"), swim(100.0, "c"))
        assertEquals(Goal.Status.Completed, oneKm.status(three, start, zone))
    }

    @Test
    fun `meeting a target allows for rounding noise but not a real shortfall`() {
        assertTrue(Goal.meetsTarget(0.6 + 0.7 + 0.2, 1.5))
        assertTrue(Goal.meetsTarget(1.5, 1.5))
        assertTrue(Goal.meetsTarget(2.0, 1.5))
        assertFalse(Goal.meetsTarget(1.499, 1.5))
        assertFalse(Goal.meetsTarget(19_999.0, 20_000.0))
        assertFalse(Goal.meetsTarget(Double.NaN, 1.5))
    }

    // ---------------- sessions ----------------

    @Test
    fun `session goals count their own activity, and any counts all`() {
        assertEquals(1.0, countGoal(ActivityType.SWIMMING).currentValue(all, zone), 1e-9)
        assertEquals(1.0, countGoal(ActivityType.ROWING).currentValue(all, zone), 1e-9)
        assertEquals(1.0, countGoal(ActivityType.RUNNING).currentValue(all, zone), 1e-9)
        assertEquals(1.0, countGoal(ActivityType.WEIGHTLIFTING).currentValue(all, zone), 1e-9)
        assertEquals(5.0, countGoal(null).currentValue(all, zone), 1e-9)
    }

    @Test
    fun `two swims make two swim sessions`() {
        val second = swim(metres = 1_000.0, id = "swim-2")
        assertEquals(2.0, countGoal(ActivityType.SWIMMING).currentValue(all + second, zone), 1e-9)
    }

    // ---------------- time ----------------

    @Test
    fun `time goals sum the minutes of their own activity, and any sums all`() {
        assertEquals(32.0, durationGoal(ActivityType.SWIMMING).currentValue(all, zone), 1e-9)
        assertEquals(30.0, durationGoal(ActivityType.ROWING).currentValue(all, zone), 1e-9)
        // 32 + 30 + 28 + 60 + 45 = 195 min = 3 h 15 min.
        assertEquals(195.0, durationGoal(null).currentValue(all, zone), 1e-9)
    }

    // ---------------- weight lifted ----------------

    @Test
    fun `swims and rows never count toward weight lifted`() {
        val goal = StrengthVolumeGoal(
            id = "v", title = "Lift", activityType = ActivityType.WEIGHTLIFTING,
            targetValue = 20_000.0, startDate = start, durationDays = 30, createdAt = createdAt,
        )
        assertEquals(500.0, goal.currentValue(all, zone), 1e-9)
        assertEquals(500.0, goal.currentValue(listOf(lift, swim, row), zone), 1e-9)
    }

    // ---------------- window ----------------

    @Test
    fun `a swim outside the goal's days does not count`() {
        val before = swim(metres = 2_000.0, id = "early", at = Instant.parse("2026-10-04T23:59:00Z"))
        val goal = distanceGoal(ActivityType.SWIMMING, target = 10.0)
        assertEquals(1.5, goal.currentValue(listOf(swim, before), zone), 1e-9)
    }

    // ---------------- through the calculator ----------------

    @Test
    fun `the progress calculator gives each goal its own swims and rows`() = runTest {
        val goals = listOf(
            distanceGoal(ActivityType.SWIMMING, 10.0, id = "swim-km"),
            distanceGoal(ActivityType.ROWING, 30.0, id = "row-km"),
            countGoal(ActivityType.SWIMMING, id = "swims"),
            durationGoal(ActivityType.ROWING, id = "row-time"),
            durationGoal(null, id = "any-time"),
        )
        val calc = GoalProgressCalculator(
            goalRepository = ListGoalRepository(goals),
            workoutRepository = ListWorkoutRepository(all),
            clock = Clock.fixed(inside, zone),
            zone = zone,
        )
        val byId = calc.observe().first().associateBy { it.goal.id }

        assertEquals(1.5, byId.getValue("swim-km").currentValue, 1e-9)
        assertEquals(6.0, byId.getValue("row-km").currentValue, 1e-9)
        assertEquals(1.0, byId.getValue("swims").currentValue, 1e-9)
        assertEquals(30.0, byId.getValue("row-time").currentValue, 1e-9)
        assertEquals(195.0, byId.getValue("any-time").currentValue, 1e-9)
        assertEquals(1, byId.getValue("swim-km").sessionsLogged)
        assertEquals(5, byId.getValue("any-time").sessionsLogged)
    }

    // ---------------- helpers ----------------

    private fun swim(metres: Double, id: String = "swim-x", at: Instant = inside) = SwimmingWorkout(
        id = id, startTime = at, duration = Duration.ofMinutes(20), distanceMeters = metres,
    )

    private fun distanceGoal(activity: ActivityType, target: Double, id: String = "d") = DistanceGoal(
        id = id, title = "Distance", activityType = activity, targetValue = target,
        startDate = start, durationDays = 30, createdAt = createdAt,
    )

    private fun countGoal(activity: ActivityType?, id: String = "c") = WorkoutCountGoal(
        id = id, title = "Sessions", activityType = activity, targetValue = 12.0,
        startDate = start, durationDays = 30, createdAt = createdAt,
    )

    private fun durationGoal(activity: ActivityType?, id: String = "t") = DurationGoal(
        id = id, title = "Time", activityType = activity, targetValue = 150.0,
        startDate = start, durationDays = 30, createdAt = createdAt,
    )

    /** Read-only goal list for the calculator. */
    private class ListGoalRepository(goals: List<Goal>) : GoalRepository {
        private val flow = MutableStateFlow(goals)
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

    /** Read-only workout list for the calculator. */
    private class ListWorkoutRepository(workouts: List<Workout>) : WorkoutRepository {
        private val flow = MutableStateFlow(workouts)
        override fun observeWorkouts(): Flow<List<Workout>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun addWorkout(workout: Workout, routePoints: List<TrackPoint>): ApiResult<Unit> =
            ApiResult.Success(Unit)
        override suspend fun getWorkout(id: String): Workout? = flow.value.firstOrNull { it.id == id }
        override suspend fun getWorkoutDetail(clientUuid: String): ApiResult<WorkoutDetail> =
            ApiResult.Error("not supported in test fake", 404, emptyMap())
        override suspend fun deleteWorkout(clientUuid: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun updateNotes(clientUuid: String, notes: String?): ApiResult<Workout> =
            ApiResult.Error("not supported in test fake", 404, emptyMap())
    }
}
