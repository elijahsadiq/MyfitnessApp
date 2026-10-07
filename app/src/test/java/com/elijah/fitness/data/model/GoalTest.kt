package com.elijah.fitness.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Phase-8b Goal hierarchy tests. The shared base filter now operates on
 * calendar dates (not Instants); each subclass still owns its `measure`
 * and still throws on illegal activity scopes.
 */
class GoalTest {

    private val zone = ZoneOffset.UTC

    // A fixed 7-day window: 2026-01-05 Monday → 2026-01-11 Sunday (inclusive).
    private val startDate = LocalDate.parse("2026-01-05")
    private val durationDays = 7
    private val createdAt = startDate.atStartOfDay(zone).toInstant()

    private val insideDate: Instant = Instant.parse("2026-01-07T10:00:00Z")
    private val beforeStart: Instant = Instant.parse("2026-01-04T23:59:59Z")
    private val firstMinOfDayAfterLast: Instant = Instant.parse("2026-01-12T00:00:00Z")

    private fun run(distanceKm: Double, at: Instant = insideDate) =
        RunningWorkout(id = at.toString(), startTime = at,
            duration = Duration.ofMinutes(30), distanceMeters = distanceKm * 1000.0)

    private fun ride(distanceKm: Double, at: Instant = insideDate) =
        CyclingWorkout(id = at.toString(), startTime = at,
            duration = Duration.ofMinutes(60), distanceMeters = distanceKm * 1000.0)

    private fun lift(volumeKg: Double, at: Instant = insideDate): StrengthWorkout {
        // Encode volume as reps=1 × weight so the maths stays exact.
        val sets = listOf(StrengthSet(exercise = "Squat", reps = 1, weightKg = volumeKg))
        return StrengthWorkout(id = at.toString(), startTime = at,
            duration = Duration.ofMinutes(45), sets = sets)
    }

    // ---------------- DistanceGoal ----------------

    @Test
    fun `DistanceGoal (RUNNING) sums matching distances, ignores other activities`() {
        val goal = DistanceGoal(
            id = "g", title = "Run 20 km",
            activityType = ActivityType.RUNNING, targetValue = 20.0,
            startDate = startDate, durationDays = durationDays, createdAt = createdAt,
        )
        val workouts = listOf(run(5.0), run(3.0), ride(10.0), lift(100.0))
        assertEquals(8.0, goal.currentValue(workouts, zone), 1e-6)
    }

    @Test
    fun `DistanceGoal rejects WEIGHTLIFTING and null (phase-8b matrix)`() {
        assertThrows(IllegalArgumentException::class.java) {
            DistanceGoal(id = "bad", title = "x", activityType = ActivityType.WEIGHTLIFTING,
                targetValue = 10.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DistanceGoal(id = "bad", title = "x", activityType = null,
                targetValue = 10.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        }
    }

    // ---------------- WorkoutCountGoal ----------------

    @Test
    fun `WorkoutCountGoal counts matching workouts in the window`() {
        val goal = WorkoutCountGoal(
            id = "g", title = "4 sessions",
            activityType = null, targetValue = 4.0,
            startDate = startDate, durationDays = durationDays, createdAt = createdAt,
        )
        val workouts = listOf(run(5.0), ride(8.0), lift(60.0))
        assertEquals(3.0, goal.currentValue(workouts, zone), 1e-6)
    }

    // ---------------- DurationGoal ----------------

    @Test
    fun `DurationGoal sums active minutes across any matching workout`() {
        val goal = DurationGoal(
            id = "g", title = "300 min",
            activityType = null, targetValue = 300.0,
            startDate = startDate, durationDays = durationDays, createdAt = createdAt,
        )
        val workouts = listOf(run(5.0), ride(8.0), lift(60.0)) // 30 + 60 + 45 = 135
        assertEquals(135.0, goal.currentValue(workouts, zone), 1e-6)
    }

    // ---------------- StrengthVolumeGoal ----------------

    @Test
    fun `StrengthVolumeGoal sums total volume, ignores non-strength workouts`() {
        val goal = StrengthVolumeGoal(
            id = "g", title = "5000 kg",
            activityType = ActivityType.WEIGHTLIFTING, targetValue = 5000.0,
            startDate = startDate, durationDays = durationDays, createdAt = createdAt,
        )
        val workouts = listOf(lift(80.0), lift(100.0), run(5.0))
        assertEquals(180.0, goal.currentValue(workouts, zone), 1e-6)
    }

    @Test
    fun `StrengthVolumeGoal rejects RUNNING, CYCLING, and null`() {
        assertThrows(IllegalArgumentException::class.java) {
            StrengthVolumeGoal(id = "bad", title = "x", activityType = ActivityType.RUNNING,
                targetValue = 100.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StrengthVolumeGoal(id = "bad", title = "x", activityType = ActivityType.CYCLING,
                targetValue = 100.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StrengthVolumeGoal(id = "bad", title = "x", activityType = null,
                targetValue = 100.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        }
    }

    // ---------------- Base filter: date boundaries ----------------

    @Test
    fun `workout before startDate is excluded`() {
        val goal = DistanceGoal(id = "g", title = "x", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        val workouts = listOf(run(10.0, at = beforeStart), run(3.0, at = insideDate))
        assertEquals(3.0, goal.currentValue(workouts, zone), 1e-6)
    }

    @Test
    fun `workout at 00 00 the day after lastDay is excluded`() {
        val goal = DistanceGoal(id = "g", title = "x", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        val workouts = listOf(run(10.0, at = firstMinOfDayAfterLast), run(2.0, at = insideDate))
        assertEquals(2.0, goal.currentValue(workouts, zone), 1e-6)
    }

    // ---------------- progressFraction + isComplete ----------------

    @Test
    fun `progressFraction clamps to 0 to 1 and isComplete flips at 100 percent`() {
        val goal = DistanceGoal(id = "g", title = "x", activityType = ActivityType.RUNNING,
            targetValue = 10.0, startDate = startDate, durationDays = 7, createdAt = createdAt)
        assertEquals(0.0, goal.progressFraction(emptyList(), zone), 1e-6)
        assertEquals(0.5, goal.progressFraction(listOf(run(5.0)), zone), 1e-6)
        assertEquals(1.0, goal.progressFraction(listOf(run(20.0)), zone), 1e-6)
        assert(goal.isComplete(listOf(run(10.0)), zone))
    }

    // ---------------- durationDays bounds ----------------

    @Test
    fun `durationDays outside 1 to 365 fails fast`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutCountGoal(id = "g", title = "x", activityType = null,
                targetValue = 1.0, startDate = startDate, durationDays = 0, createdAt = createdAt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutCountGoal(id = "g", title = "x", activityType = null,
                targetValue = 1.0, startDate = startDate, durationDays = 366, createdAt = createdAt)
        }
    }
}
