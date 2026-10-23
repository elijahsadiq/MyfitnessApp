package com.elijah.fitness.data.model

import com.elijah.fitness.data.model.GoalCompletionStats.AchievedLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Phase 8d, Step 7: the numbers on the goal-complete pop-up. */
class GoalCompletionStatsTest {

    private val zone = ZoneOffset.UTC
    private val start = LocalDate.parse("2026-10-01")
    private val created = Instant.parse("2026-10-01T00:00:00Z")

    private fun rideGoal(target: Double = 100.0) = DistanceGoal(
        id = "g1", title = "Ride 100 km in 30 days", activityType = ActivityType.CYCLING,
        targetValue = target, startDate = start, durationDays = 30, createdAt = created,
    )

    private fun ride(day: String, km: Double, kcal: Int? = 900) = CyclingWorkout(
        id = "ride-$day-$km",
        startTime = Instant.parse("${day}T08:00:00Z"),
        duration = Duration.ofMinutes(90),
        distanceMeters = km * 1_000.0,
        caloriesKcal = kcal,
    )

    private val rides = listOf(
        ride("2026-09-30", 50.0),  // before the goal: never counts
        ride("2026-10-03", 40.0),
        ride("2026-10-10", 40.0),
        ride("2026-10-26", 30.0),  // this one reaches 100 km
    )

    @Test
    fun `completion date is the day of the workout that reached the target`() {
        val r = GoalCompletionStats.compute(rideGoal(), rides, 70.0, LocalDate.parse("2026-10-28"), zone)
        assertEquals(LocalDate.parse("2026-10-26"), r.completionDate)
    }

    @Test
    fun `days taken counts the start day and days early counts to the last day`() {
        val r = GoalCompletionStats.compute(rideGoal(), rides, 70.0, LocalDate.parse("2026-10-28"), zone)
        assertEquals(26, r.daysTaken)  // 1 Oct .. 26 Oct inclusive
        assertEquals(4, r.daysEarly)   // last day is 30 Oct
    }

    @Test
    fun `finishing on the last day is zero days early`() {
        val r = GoalCompletionStats.compute(
            rideGoal(),
            listOf(ride("2026-10-03", 70.0), ride("2026-10-30", 30.0)),
            70.0, LocalDate.parse("2026-10-30"), zone,
        )
        assertEquals(30, r.daysTaken)
        assertEquals(0, r.daysEarly)
    }

    @Test
    fun `falls back to today when no workout reaches the target`() {
        val r = GoalCompletionStats.compute(
            rideGoal(), listOf(ride("2026-10-03", 10.0)), 70.0, LocalDate.parse("2026-10-12"), zone,
        )
        assertEquals(LocalDate.parse("2026-10-12"), r.completionDate)
        assertEquals(12, r.daysTaken)
    }

    @Test
    fun `kcal sums the counted workouts only`() {
        val r = GoalCompletionStats.compute(rideGoal(), rides, 70.0, LocalDate.parse("2026-10-28"), zone)
        assertEquals(2_700.0, r.kcal, 0.001)
    }

    @Test
    fun `kcal skips other activities in the window`() {
        val run = RunningWorkout(
            id = "run", startTime = Instant.parse("2026-10-05T07:00:00Z"),
            duration = Duration.ofMinutes(30), distanceMeters = 5_000.0, caloriesKcal = 400,
        )
        val r = GoalCompletionStats.compute(rideGoal(), rides + run, 70.0, LocalDate.parse("2026-10-28"), zone)
        assertEquals(2_700.0, r.kcal, 0.001)
    }

    @Test
    fun `label follows the goal type and activity`() {
        fun count(activity: ActivityType?) = WorkoutCountGoal(
            id = "c", title = "c", activityType = activity, targetValue = 12.0,
            startDate = start, durationDays = 30, createdAt = created,
        )
        assertEquals(AchievedLabel.KM_RIDDEN, GoalCompletionStats.achievedLabel(rideGoal()))
        assertEquals(
            AchievedLabel.KM_SWUM,
            GoalCompletionStats.achievedLabel(
                DistanceGoal("s", "s", ActivityType.SWIMMING, 10.0, start, 30, created),
            ),
        )
        assertEquals(
            AchievedLabel.KM_RUN,
            GoalCompletionStats.achievedLabel(
                DistanceGoal("r", "r", ActivityType.RUNNING, 50.0, start, 30, created),
            ),
        )
        assertEquals(AchievedLabel.RUNS, GoalCompletionStats.achievedLabel(count(ActivityType.RUNNING)))
        assertEquals(AchievedLabel.ROWS, GoalCompletionStats.achievedLabel(count(ActivityType.ROWING)))
        assertEquals(AchievedLabel.WORKOUTS, GoalCompletionStats.achievedLabel(count(ActivityType.WEIGHTLIFTING)))
        assertEquals(AchievedLabel.SESSIONS, GoalCompletionStats.achievedLabel(count(null)))
        assertEquals(
            AchievedLabel.TIME_ACTIVE,
            GoalCompletionStats.achievedLabel(
                DurationGoal("d", "d", null, 150.0, start, 30, created),
            ),
        )
        assertEquals(
            AchievedLabel.KG_LIFTED,
            GoalCompletionStats.achievedLabel(
                StrengthVolumeGoal("v", "v", ActivityType.WEIGHTLIFTING, 20_000.0, start, 30, created),
            ),
        )
    }

    @Test
    fun `celebration carries the target and the computed numbers`() {
        val c = GoalCompletionStats.celebrationFor(
            rideGoal(), "Ride 100 km in 30 days", rides, 70.0, LocalDate.parse("2026-10-28"), zone,
        )
        assertEquals("g1", c.goalId)
        assertEquals(AchievedLabel.KM_RIDDEN, c.label)
        assertEquals(100.0, c.achievedValue, 0.0)
        assertEquals(26, c.daysTaken)
        assertEquals(4, c.daysEarly)
        assertEquals(2_700.0, c.kcal, 0.001)
    }
}
