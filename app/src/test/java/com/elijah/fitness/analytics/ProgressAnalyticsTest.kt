package com.elijah.fitness.analytics

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.model.RowingWorkout

/**
 * Reference moment: Wednesday 2026-09-16 12:00 UTC. That week's Monday
 * is 2026-09-14 00:00 UTC. September is a 30-day month → ~5 week bars.
 */
class ProgressAnalyticsTest {

    private val zone = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-09-16T12:00:00Z")

    @Test
    fun `week bars yield seven buckets with Monday first`() {
        val workouts: List<Workout> = listOf(
            run(iso = "2026-09-14T10:00:00Z", meters = 5_000.0, minutes = 30), // Mon
            run(iso = "2026-09-16T07:00:00Z", meters = 5_000.0, minutes = 30), // Wed
        )
        val bars = ProgressAnalytics.activityBars(
            workouts, AnalyticsPeriod.WEEK, activityType = null, now = now, zone = zone,
        )
        assertEquals(7, bars.size)
        assertEquals(30.0, bars[0].value, 0.0) // Mon
        assertEquals(0.0, bars[1].value, 0.0)  // Tue
        assertEquals(30.0, bars[2].value, 0.0) // Wed
    }

    @Test
    fun `month bars put the right workouts in the right week bucket`() {
        val workouts: List<Workout> = listOf(
            run(iso = "2026-09-02T10:00:00Z", meters = 5_000.0, minutes = 25), // W1
            run(iso = "2026-09-14T10:00:00Z", meters = 5_000.0, minutes = 45), // Mon of W3
        )
        val bars = ProgressAnalytics.activityBars(
            workouts, AnalyticsPeriod.MONTH, activityType = null, now = now, zone = zone,
        )
        // September 2026 starts on Tuesday; dayOfMonth 2 lands in week-of-month index 0.
        assertTrue("bars = $bars", bars.size in 4..5)
        assertTrue(bars[0].value > 0.0) // W1 saw the first run
        // dayOfMonth 14 lands in index floor((14 + firstDayOfWeek(= 2) - 2)/7) = floor(14/7) = 2
        assertTrue(bars[2].value > 0.0) // W3
    }

    @Test
    fun `year bars index September correctly`() {
        val workouts: List<Workout> = listOf(
            run("2026-01-15T10:00:00Z", 5_000.0, 25),
            run("2026-09-14T10:00:00Z", 5_000.0, 45),
        )
        val bars = ProgressAnalytics.activityBars(
            workouts, AnalyticsPeriod.YEAR, activityType = null, now = now, zone = zone,
        )
        assertEquals(12, bars.size)
        assertEquals(25.0, bars[0].value, 0.0) // Jan
        assertEquals(45.0, bars[8].value, 0.0) // Sep (index 8)
    }

    @Test
    fun `running stats compute total distance, average pace and best pace`() {
        val runs: List<Workout> = listOf(
            // 5 km in 25 min → 300 s/km
            RunningWorkout(
                id = "r1", startTime = Instant.parse("2026-09-14T10:00:00Z"),
                duration = Duration.ofMinutes(25), distanceMeters = 5_000.0,
            ),
            // 10 km in 60 min → 360 s/km
            RunningWorkout(
                id = "r2", startTime = Instant.parse("2026-09-15T10:00:00Z"),
                duration = Duration.ofMinutes(60), distanceMeters = 10_000.0,
            ),
        )
        val stats = ProgressAnalytics.statsFor(runs, ActivityType.RUNNING)
        assertEquals(2, stats.workoutCount)
        assertEquals(15_000.0, stats.totalDistanceMeters!!, 1e-6)
        assertEquals(85L, stats.totalDurationMinutes)
        // Average pace: 85 min / 15 km = 340 s/km (within rounding).
        val avg = stats.averagePaceSecondsPerKm!!
        assertTrue("avg pace was $avg", avg in 335L..345L)
        assertEquals(300L, stats.bestPaceSecondsPerKm)
        assertEquals(10_000.0, stats.longestDistanceMeters!!, 0.0)
    }

    @Test
    fun `cycling stats compute average speed from totals`() {
        val rides: List<Workout> = listOf(
            CyclingWorkout(
                id = "c1", startTime = Instant.parse("2026-09-14T10:00:00Z"),
                duration = Duration.ofMinutes(60), distanceMeters = 20_000.0,
            ),
            CyclingWorkout(
                id = "c2", startTime = Instant.parse("2026-09-15T10:00:00Z"),
                duration = Duration.ofMinutes(30), distanceMeters = 10_000.0,
            ),
        )
        val stats = ProgressAnalytics.statsFor(rides, ActivityType.CYCLING)
        assertEquals(2, stats.workoutCount)
        // 30 km in 90 min = 20 km/h average.
        assertEquals(20.0, stats.averageSpeedKmh!!, 1e-6)
        // Fastest single ride was c1 and c2 both at 20 km/h — tie.
        assertEquals(20.0, stats.maxSpeedKmh!!, 1e-6)
    }

    @Test
    fun `strength stats surface the most frequent exercise`() {
        val sessions: List<Workout> = listOf(
            StrengthWorkout(
                id = "s1", startTime = Instant.parse("2026-09-14T10:00:00Z"),
                duration = Duration.ofMinutes(45),
                sets = listOf(
                    StrengthSet("Bench", 5, 60.0),
                    StrengthSet("Bench", 5, 65.0),
                    StrengthSet("Squat", 5, 80.0),
                ),
            ),
            StrengthWorkout(
                id = "s2", startTime = Instant.parse("2026-09-15T10:00:00Z"),
                duration = Duration.ofMinutes(30),
                sets = listOf(StrengthSet("Bench", 5, 65.0)),
            ),
        )
        val stats = ProgressAnalytics.statsFor(sessions, ActivityType.WEIGHTLIFTING)
        assertEquals(2, stats.workoutCount)
        assertEquals(4, stats.totalSets)
        assertEquals("Bench", stats.mostFrequentExercise)
    }

    @Test
    fun `personal records find fastest 5k, longest run, longest ride, best 1RM`() {
        val workouts: List<Workout> = listOf(
            // 3 km run — doesn't qualify for the 5k PB.
            RunningWorkout(
                id = "r0", startTime = Instant.parse("2026-08-01T10:00:00Z"),
                duration = Duration.ofMinutes(10), distanceMeters = 3_000.0,
            ),
            // 5 km in 25 min → 300 s/km
            RunningWorkout(
                id = "r1", startTime = Instant.parse("2026-08-02T10:00:00Z"),
                duration = Duration.ofMinutes(25), distanceMeters = 5_000.0,
            ),
            // 10 km in 55 min → 330 s/km (longest run)
            RunningWorkout(
                id = "r2", startTime = Instant.parse("2026-08-03T10:00:00Z"),
                duration = Duration.ofMinutes(55), distanceMeters = 10_000.0,
            ),
            CyclingWorkout(
                id = "c1", startTime = Instant.parse("2026-08-04T10:00:00Z"),
                duration = Duration.ofMinutes(90), distanceMeters = 40_000.0,
            ),
            StrengthWorkout(
                id = "s1", startTime = Instant.parse("2026-08-05T10:00:00Z"),
                duration = Duration.ofMinutes(30),
                sets = listOf(StrengthSet("Bench", 5, 80.0)),
            ),
        )
        val pr = ProgressAnalytics.personalRecords(workouts)
        assertEquals(300L, pr.fastest5kSecondsPerKm)
        assertEquals(10_000.0, pr.longestRunMeters!!, 0.0)
        assertEquals(40_000.0, pr.longestRideMeters!!, 0.0)
        // 1RM Epley = 80 × (1 + 5/30) = 80 × 1.1667 ≈ 93.33
        val benchPr = pr.bestOneRepMaxes["Bench"]
        assertNotNull("expected a Bench PR", benchPr)
        assertTrue("1RM was $benchPr", benchPr!! > 93.0 && benchPr < 94.0)
    }

    @Test
    fun `empty history yields EMPTY personal records`() {
        val pr = ProgressAnalytics.personalRecords(emptyList())
        assertNull(pr.fastest5kSecondsPerKm)
        assertNull(pr.longestRunMeters)
        assertNull(pr.longestRideMeters)
        assertTrue(pr.bestOneRepMaxes.isEmpty())
    }

    private fun run(iso: String, meters: Double, minutes: Long): RunningWorkout =
        RunningWorkout(
            id = iso,
            startTime = Instant.parse(iso),
            duration = Duration.ofMinutes(minutes),
            distanceMeters = meters,
        )

    // --------- Phase 8d: swims and rows ---------

    @Test
    fun `swim stats are in metres with pace per 100 m`() {
        val swims = listOf(
            SwimmingWorkout("s1", Instant.parse("2026-09-14T07:00:00Z"), Duration.ofSeconds(1_935), 1_500.0),
            SwimmingWorkout("s2", Instant.parse("2026-09-15T07:00:00Z"), Duration.ofSeconds(1_200), 1_000.0),
        )
        val stats = ProgressAnalytics.statsFor(swims, ActivityType.SWIMMING)
        assertEquals(2, stats.workoutCount)
        assertEquals(2_500.0, stats.totalDistanceMeters!!, 0.0)
        // (1935 + 1200) s over 25 × 100 m = 125.4 s
        assertEquals(125L, stats.averageSplitSeconds)
        assertEquals(120L, stats.bestSplitSeconds) // 1200 s / 10
        assertEquals(1_500.0, stats.longestDistanceMeters!!, 0.0)
        assertNull(stats.averagePaceSecondsPerKm)
    }

    @Test
    fun `swim paces round to the nearest second`() {
        // The design's swim: 1,500 m in 32:10 → 128.67 s → "2:09 /100 m".
        val swim = SwimmingWorkout("s1", Instant.parse("2026-09-14T07:00:00Z"), Duration.ofSeconds(1_930), 1_500.0)
        val stats = ProgressAnalytics.statsFor(listOf(swim), ActivityType.SWIMMING)
        assertEquals(129L, stats.averageSplitSeconds)
        assertEquals(129L, stats.bestSplitSeconds)
    }

    @Test
    fun `row stats use pace per 500 m and records include the longest swim and row`() {
        val row = RowingWorkout("r1", Instant.parse("2026-09-14T07:00:00Z"), Duration.ofSeconds(1_860), 6_000.0)
        val stats = ProgressAnalytics.statsFor(listOf(row), ActivityType.ROWING)
        assertEquals(155L, stats.averageSplitSeconds)
        assertEquals(155L, stats.bestSplitSeconds)

        val swim = SwimmingWorkout("s1", Instant.parse("2026-09-14T07:00:00Z"), Duration.ofMinutes(30), 1_500.0)
        val records = ProgressAnalytics.personalRecords(listOf(row, swim))
        assertEquals(6_000.0, records.longestRowMeters!!, 0.0)
        assertEquals(1_500.0, records.longestSwimMeters!!, 0.0)
        assertNull(records.longestRunMeters)
    }

    @Test
    fun `every activity type has stats without crashing`() {
        ActivityType.entries.forEach { type ->
            assertEquals(ProgressStats.EMPTY, ProgressAnalytics.statsFor(emptyList(), type))
        }
    }
}
