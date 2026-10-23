package com.elijah.fitness.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Phase 8d: the two new DistanceWorkout subclasses and stored calories. */
class SwimRowWorkoutTest {

    private val t0: Instant = Instant.parse("2026-10-18T07:00:00Z")

    private fun swim(
        seconds: Long = 1_935,
        meters: Double = 1_500.0,
        kcal: Int? = null,
    ) = SwimmingWorkout(
        id = "swim", startTime = t0, duration = Duration.ofSeconds(seconds),
        distanceMeters = meters, poolLengthM = 25, laps = 60, caloriesKcal = kcal,
    )

    @Test
    fun `swimming is a distance workout of type SWIMMING`() {
        val w: Workout = swim()
        assertTrue(w is DistanceWorkout)
        assertEquals(ActivityType.SWIMMING, w.type)
        assertEquals(1.5, (w as DistanceWorkout).distanceKm(), 1e-9)
    }

    @Test
    fun `swim calories use the 7_0 swimming MET`() {
        // 7.0 × 72 × (1935 / 3600) h
        assertEquals(7.0 * 72.0 * (1_935 / 3_600.0), swim().caloriesBurned(72.0), 1e-9)
        assertEquals(MetValues.SWIMMING, 7.0, 0.0)
    }

    @Test
    fun `swim pace is per 100 m`() {
        // 1935 s over 15 × 100 m = 129 s → "2:09 /100 m"
        assertEquals(129L, swim().pacePer100mSeconds())
        assertEquals(100.0, swim().paceSplitMeters, 0.0)
    }

    @Test
    fun `swim pace rounds to the nearest second like the design`() {
        // The LogSwim design's own example: 60 × 25 m in 32:10.
        // 1930 s / 15 = 128.67 s → 129 → "2:09 /100 m" (truncating gave 2:08).
        assertEquals(129L, swim(seconds = 1_930).pacePer100mSeconds())
        // 128.4 s rounds down.
        assertEquals(128L, swim(seconds = 1_926).pacePer100mSeconds())
    }

    @Test
    fun `row and run pace round too`() {
        // 6 km in 30:59 → 1859 / 12 = 154.9 s → 155 → "2:35 /500 m".
        val row = RowingWorkout("row", t0, Duration.ofSeconds(1_859), distanceMeters = 6_000.0)
        assertEquals(155L, row.pacePer500mSeconds())
        // 5 km in 27:43 → 1663 / 5 = 332.6 s → 333 → "5:33 /km".
        val run = RunningWorkout("r", t0, Duration.ofSeconds(1_663), distanceMeters = 5_000.0)
        assertEquals(333L, run.paceSecondsPerKm())
        assertEquals(run.paceSecondsPerKm(), run.paceSecondsPerSplit())
        assertEquals(0L, RunningWorkout("r0", t0, Duration.ofSeconds(60), distanceMeters = 9.0).paceSecondsPerKm())
    }

    @Test
    fun `swims show distance in metres, every other distance workout in km`() {
        assertEquals(DistanceUnit.METRES, swim().distanceUnit)
        val row = RowingWorkout("row", t0, Duration.ofMinutes(30), distanceMeters = 6_000.0)
        val run = RunningWorkout("r", t0, Duration.ofMinutes(30), distanceMeters = 5_000.0)
        val ride = CyclingWorkout("c", t0, Duration.ofMinutes(30), distanceMeters = 10_000.0)
        assertEquals(DistanceUnit.KILOMETRES, row.distanceUnit)
        assertEquals(DistanceUnit.KILOMETRES, run.distanceUnit)
        assertEquals(DistanceUnit.KILOMETRES, ride.distanceUnit)
    }

    @Test
    fun `a total is in metres only when every workout is`() {
        val row = RowingWorkout("row", t0, Duration.ofMinutes(30), distanceMeters = 6_000.0)
        assertEquals(DistanceUnit.METRES, DistanceUnit.forTotal(listOf(swim(), swim())))
        assertEquals(DistanceUnit.KILOMETRES, DistanceUnit.forTotal(listOf(swim(), row)))
        assertEquals(DistanceUnit.KILOMETRES, DistanceUnit.forTotal(emptyList()))
    }

    @Test
    fun `pool and laps are valid only together and inside the server's ranges`() {
        fun pool(laps: Int?, length: Int?) = SwimmingWorkout(
            id = "s", startTime = t0, duration = Duration.ofMinutes(30),
            distanceMeters = 1_500.0, poolLengthM = length, laps = laps,
        )
        assertTrue(pool(60, 25).hasValidPoolLaps())
        assertTrue(pool(1, 10).hasValidPoolLaps())
        assertTrue(pool(400, 100).hasValidPoolLaps())
        assertFalse(pool(60, null).hasValidPoolLaps())
        assertFalse(pool(null, 25).hasValidPoolLaps())
        assertFalse(pool(null, null).hasValidPoolLaps())
        assertFalse(pool(0, 25).hasValidPoolLaps())
        assertFalse(pool(401, 25).hasValidPoolLaps())
        assertFalse(pool(60, 9).hasValidPoolLaps())
        assertFalse(pool(60, 101).hasValidPoolLaps())
    }

    @Test
    fun `no pace for a few metres`() {
        assertEquals(0L, swim(meters = 5.0).pacePer100mSeconds())
    }

    @Test
    fun `rowing calories use the 7_0 rowing MET and pace is per 500 m`() {
        // 6 km in 31 min: 12 splits of 155 s → "2:35 /500 m"
        val row = RowingWorkout(
            id = "row", startTime = t0, duration = Duration.ofSeconds(1_860), distanceMeters = 6_000.0,
        )
        assertEquals(ActivityType.ROWING, row.type)
        assertEquals(155L, row.pacePer500mSeconds())
        assertEquals(500.0, row.paceSplitMeters, 0.0)
        assertEquals(7.0 * 70.0 * (1_860 / 3_600.0), row.caloriesBurned(70.0), 1e-9)
        assertEquals(MetValues.ROWING, 7.0, 0.0)
    }

    @Test
    fun `a run keeps its per-km split`() {
        val run = RunningWorkout("r", t0, Duration.ofMinutes(60), distanceMeters = 10_000.0)
        assertEquals(1_000.0, run.paceSplitMeters, 0.0)
        assertEquals(run.paceSecondsPerKm(), run.paceSecondsPerSplit())
    }

    @Test
    fun `energyKcal prefers the stored value and falls back to the formula`() {
        assertEquals(300.0, swim(kcal = 300).energyKcal(72.0), 0.0)
        assertEquals(swim().caloriesBurned(72.0), swim().energyKcal(72.0), 1e-9)
        // A stored value doesn't change when the weight does.
        assertEquals(300.0, swim(kcal = 300).energyKcal(95.0), 0.0)
    }

    @Test
    fun `equality covers calories, laps and pool length`() {
        assertEquals(swim(kcal = 270), swim(kcal = 270))
        assertEquals(swim(kcal = 270).hashCode(), swim(kcal = 270).hashCode())
        assertNotEquals(swim(kcal = 270), swim(kcal = 271))
        val otherPool = SwimmingWorkout(
            id = "swim", startTime = t0, duration = Duration.ofSeconds(1_935),
            distanceMeters = 1_500.0, poolLengthM = 50, laps = 30,
        )
        assertNotEquals(swim(), otherPool)
    }

    @Test
    fun `distance goals accept swims and rows and count only their own type`() {
        val goal = DistanceGoal(
            id = "g", title = "Swim 10 km", activityType = ActivityType.SWIMMING,
            targetValue = 10.0, startDate = java.time.LocalDate.of(2026, 10, 1),
            durationDays = 30, createdAt = t0,
        )
        val row = RowingWorkout("row", t0, Duration.ofMinutes(30), distanceMeters = 6_000.0)
        val zone = java.time.ZoneOffset.UTC
        assertEquals(1.5, goal.currentValue(listOf(swim(), row), zone), 1e-9)
        // Rowing distance goals construct fine too.
        DistanceGoal(
            id = "g2", title = "Row", activityType = ActivityType.ROWING,
            targetValue = 30.0, startDate = java.time.LocalDate.of(2026, 10, 1),
            durationDays = 30, createdAt = t0,
        )
    }
}
