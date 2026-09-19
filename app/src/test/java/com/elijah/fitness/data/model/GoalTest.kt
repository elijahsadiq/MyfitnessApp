package com.elijah.fitness.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Covers the Step 1 Goal hierarchy: the shared base filter, each
 * subclass's `currentValue`, `progressFraction` clamping, "any activity"
 * behaviour, and the type-scope precondition checks.
 *
 * Every test pins the period to a fixed `[t0, tEnd)` window and hands
 * the goal a hand-picked list of workouts, so no clock is involved.
 */
class GoalTest {

    // A fixed one-week window: 2026-01-05 Monday 00:00Z → 2026-01-12 Monday 00:00Z.
    private val t0: Instant = Instant.parse("2026-01-05T00:00:00Z")
    private val tEnd: Instant = Instant.parse("2026-01-12T00:00:00Z")
    private val insidePeriod: Instant = Instant.parse("2026-01-07T10:00:00Z")
    private val beforePeriod: Instant = Instant.parse("2026-01-04T23:59:59Z")
    private val atPeriodEnd: Instant = tEnd // exact boundary — must be excluded

    // Small fixtures.
    private fun run(distanceKm: Double, at: Instant = insidePeriod) =
        RunningWorkout(id = at.toString(), startTime = at,
            duration = Duration.ofMinutes(30), distanceMeters = distanceKm * 1000.0)

    private fun ride(distanceKm: Double, at: Instant = insidePeriod) =
        CyclingWorkout(id = at.toString(), startTime = at,
            duration = Duration.ofMinutes(60), distanceMeters = distanceKm * 1000.0)

    private fun lift(volumeKg: Double, at: Instant = insidePeriod): StrengthWorkout {
        // Encode volume as a single set with reps=1 so the maths is exact.
        val sets = listOf(StrengthSet(exercise = "Squat", reps = 1, weightKg = volumeKg))
        return StrengthWorkout(id = at.toString(), startTime = at,
            duration = Duration.ofMinutes(45), sets = sets)
    }

    // ---------------- DistanceGoal ----------------

    @Test
    fun `DistanceGoal running-only sums matching distance, ignores strength`() {
        val goal = DistanceGoal(
            id = "g1", title = "Run 20 km this week",
            activityType = ActivityType.RUNNING,
            targetValue = 20.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(
            run(5.0),
            run(3.0),
            ride(10.0),   // wrong activity — skipped by base filter
            lift(100.0)   // wrong activity — skipped by base filter
        )
        assertEquals(8.0, goal.currentValue(workouts), 1e-6)
    }

    @Test
    fun `DistanceGoal any activity sums running plus cycling, ignores strength`() {
        val goal = DistanceGoal(
            id = "g", title = "Cover 50 km",
            activityType = null,
            targetValue = 50.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(run(5.0), ride(20.0), lift(300.0))
        // Strength has no distance to contribute — filterIsInstance drops it.
        assertEquals(25.0, goal.currentValue(workouts), 1e-6)
    }

    @Test
    fun `DistanceGoal rejects weightlifting scope`() {
        assertThrows(IllegalArgumentException::class.java) {
            DistanceGoal(
                id = "bad", title = "nonsense",
                activityType = ActivityType.WEIGHTLIFTING,
                targetValue = 10.0, period = GoalPeriod.THIS_WEEK,
                periodStart = t0, periodEnd = tEnd, createdAt = t0
            )
        }
    }

    // ---------------- WorkoutCountGoal ----------------

    @Test
    fun `WorkoutCountGoal counts every workout in period regardless of type`() {
        val goal = WorkoutCountGoal(
            id = "g", title = "Move 4 times this week",
            activityType = null,
            targetValue = 4.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(run(3.0), ride(15.0), lift(60.0))
        assertEquals(3.0, goal.currentValue(workouts), 1e-6)
    }

    @Test
    fun `WorkoutCountGoal activity scope narrows the count`() {
        val goal = WorkoutCountGoal(
            id = "g", title = "3 rides this week",
            activityType = ActivityType.CYCLING,
            targetValue = 3.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(run(3.0), ride(15.0), ride(20.0), lift(60.0))
        assertEquals(2.0, goal.currentValue(workouts), 1e-6)
    }

    // ---------------- DurationGoal ----------------

    @Test
    fun `DurationGoal sums minutes across mixed types`() {
        val goal = DurationGoal(
            id = "g", title = "150 active minutes",
            activityType = null,
            targetValue = 150.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        // fixtures use 30 min run, 60 min ride, 45 min lift → 135
        val workouts = listOf(run(3.0), ride(15.0), lift(60.0))
        assertEquals(135.0, goal.currentValue(workouts), 1e-6)
    }

    // ---------------- StrengthVolumeGoal ----------------

    @Test
    fun `StrengthVolumeGoal sums total volume, ignores distance workouts`() {
        val goal = StrengthVolumeGoal(
            id = "g", title = "Lift 500 kg total this week",
            activityType = ActivityType.WEIGHTLIFTING,
            targetValue = 500.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        // Fixture encodes volume as reps=1 × weight, so 100+80+60 = 240.
        val workouts = listOf(lift(100.0), lift(80.0), lift(60.0), run(5.0), ride(15.0))
        assertEquals(240.0, goal.currentValue(workouts), 1e-6)
    }

    @Test
    fun `StrengthVolumeGoal rejects running or cycling scope`() {
        assertThrows(IllegalArgumentException::class.java) {
            StrengthVolumeGoal(
                id = "bad", title = "nonsense",
                activityType = ActivityType.RUNNING,
                targetValue = 500.0, period = GoalPeriod.THIS_WEEK,
                periodStart = t0, periodEnd = tEnd, createdAt = t0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StrengthVolumeGoal(
                id = "bad", title = "nonsense",
                activityType = ActivityType.CYCLING,
                targetValue = 500.0, period = GoalPeriod.THIS_WEEK,
                periodStart = t0, periodEnd = tEnd, createdAt = t0
            )
        }
    }

    @Test
    fun `StrengthVolumeGoal accepts any activity scope`() {
        // null activityType is allowed — the filterIsInstance in measure()
        // keeps it type-safe regardless.
        val goal = StrengthVolumeGoal(
            id = "g", title = "Lift 100 kg total",
            activityType = null,
            targetValue = 100.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(lift(80.0), run(5.0))
        assertEquals(80.0, goal.currentValue(workouts), 1e-6)
    }

    // ---------------- Period filtering ----------------

    @Test
    fun `workouts outside the period are excluded`() {
        val goal = DistanceGoal(
            id = "g", title = "5 km",
            activityType = ActivityType.RUNNING,
            targetValue = 5.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(
            run(4.0, at = insidePeriod),
            run(10.0, at = beforePeriod) // last week — must be excluded
        )
        assertEquals(4.0, goal.currentValue(workouts), 1e-6)
    }

    @Test
    fun `workout exactly at period end is excluded, workout exactly at start is included`() {
        val goal = DistanceGoal(
            id = "g", title = "half-open range",
            activityType = ActivityType.RUNNING,
            targetValue = 100.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(
            run(2.0, at = t0),           // at start — included
            run(99.0, at = atPeriodEnd)  // at end — must be excluded (belongs to next week)
        )
        assertEquals(2.0, goal.currentValue(workouts), 1e-6)
    }

    // ---------------- Progress + completion ----------------

    @Test
    fun `progressFraction clamps at 1_0 when target is exceeded`() {
        val goal = DistanceGoal(
            id = "g", title = "10 km",
            activityType = ActivityType.RUNNING,
            targetValue = 10.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        val workouts = listOf(run(15.0)) // 150%
        assertEquals(1.0, goal.progressFraction(workouts), 1e-6)
        assertTrue(goal.isComplete(workouts))
    }

    @Test
    fun `progressFraction is 0_0 for empty workouts`() {
        val goal = WorkoutCountGoal(
            id = "g", title = "4 sessions",
            activityType = null,
            targetValue = 4.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        assertEquals(0.0, goal.progressFraction(emptyList()), 1e-6)
        assertFalse(goal.isComplete(emptyList()))
    }

    @Test
    fun `progressFraction handles a fractional partial correctly`() {
        val goal = DurationGoal(
            id = "g", title = "60 minutes",
            activityType = null,
            targetValue = 60.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        // fixture: run = 30 min → 50%
        val workouts = listOf(run(3.0))
        assertEquals(0.5, goal.progressFraction(workouts), 1e-6)
        assertFalse(goal.isComplete(workouts))
    }

    @Test
    fun `progressFraction returns 0_0 when target is not positive`() {
        val goal = DistanceGoal(
            id = "g", title = "invalid target",
            activityType = null,
            targetValue = 0.0, period = GoalPeriod.THIS_WEEK,
            periodStart = t0, periodEnd = tEnd, createdAt = t0
        )
        // Guard against divide-by-zero in the calculator.
        assertEquals(0.0, goal.progressFraction(listOf(run(10.0))), 1e-6)
    }

    @Test
    fun `unitLabel matches the expected phase-7 units`() {
        val d = DistanceGoal("i", "t", null, 1.0, GoalPeriod.THIS_WEEK, t0, tEnd, t0)
        val c = WorkoutCountGoal("i", "t", null, 1.0, GoalPeriod.THIS_WEEK, t0, tEnd, t0)
        val m = DurationGoal("i", "t", null, 1.0, GoalPeriod.THIS_WEEK, t0, tEnd, t0)
        val v = StrengthVolumeGoal("i", "t", ActivityType.WEIGHTLIFTING, 1.0,
            GoalPeriod.THIS_WEEK, t0, tEnd, t0)
        assertEquals("km", d.unitLabel)
        assertEquals("workouts", c.unitLabel)
        assertEquals("min", m.unitLabel)
        assertEquals("kg", v.unitLabel)
    }
}
