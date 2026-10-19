package com.elijah.fitness.ui.dashboard

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fixed reference: Wed 2026-09-16 12:00 UTC. That week's Monday is
 * 2026-09-14 00:00 UTC.
 */
class WeeklySummaryCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-09-16T12:00:00Z")
    private val bodyWeightKg = 70.0

    @Test
    fun `empty workout list gives empty summary`() {
        val summary = WeeklySummaryCalculator.compute(
            workouts = emptyList(),
            now = now,
            zone = zone,
            bodyWeightKg = bodyWeightKg,
            heightCm = null
        )
        assertEquals(0, summary.workoutCount)
        assertEquals(0L, summary.activeMinutes)
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
        assertEquals(0.0, summary.estimatedCalories, 0.0)
    }

    @Test
    fun `workouts before Monday of the current week are excluded`() {
        // Sun 2026-09-13 23:00 UTC — one hour before this week's Monday.
        val lastWeekRun = RunningWorkout(
            id = "old",
            startTime = Instant.parse("2026-09-13T23:00:00Z"),
            duration = Duration.ofMinutes(45),
            distanceMeters = 8_000.0
        )
        val summary = WeeklySummaryCalculator.compute(
            workouts = listOf(lastWeekRun),
            now = now,
            zone = zone,
            bodyWeightKg = bodyWeightKg,
            heightCm = null
        )
        assertEquals(0, summary.workoutCount)
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
    }

    @Test
    fun `in-week workouts contribute to count and active minutes`() {
        val workouts: List<Workout> = listOf(
            RunningWorkout(
                id = "r1",
                startTime = Instant.parse("2026-09-14T07:00:00Z"),
                duration = Duration.ofMinutes(30),
                distanceMeters = 5_000.0
            ),
            CyclingWorkout(
                id = "c1",
                startTime = Instant.parse("2026-09-15T18:00:00Z"),
                duration = Duration.ofMinutes(60),
                distanceMeters = 22_000.0
            ),
            StrengthWorkout(
                id = "s1",
                startTime = Instant.parse("2026-09-16T09:00:00Z"),
                duration = Duration.ofMinutes(45),
                sets = listOf(StrengthSet("Bench", 5, 60.0))
            )
        )
        val summary = WeeklySummaryCalculator.compute(workouts, now, zone, bodyWeightKg, heightCm = null)
        assertEquals(3, summary.workoutCount)
        assertEquals(30 + 60 + 45L, summary.activeMinutes)
    }

    @Test
    fun `total distance is running plus cycling and excludes strength`() {
        val workouts: List<Workout> = listOf(
            RunningWorkout(
                id = "r1",
                startTime = Instant.parse("2026-09-14T07:00:00Z"),
                duration = Duration.ofMinutes(30),
                distanceMeters = 5_200.0
            ),
            CyclingWorkout(
                id = "c1",
                startTime = Instant.parse("2026-09-15T18:00:00Z"),
                duration = Duration.ofMinutes(60),
                distanceMeters = 22_400.0
            ),
            StrengthWorkout(
                id = "s1",
                startTime = Instant.parse("2026-09-16T09:00:00Z"),
                duration = Duration.ofMinutes(45),
                sets = listOf(StrengthSet("Bench", 5, 60.0))
            )
        )
        val summary = WeeklySummaryCalculator.compute(workouts, now, zone, bodyWeightKg, heightCm = null)
        assertEquals(5_200.0 + 22_400.0, summary.totalDistanceMeters, 1e-6)
    }

    @Test
    fun `per-activity breakdown counts each type separately`() {
        val workouts: List<Workout> = listOf(
            RunningWorkout(
                id = "r1", startTime = Instant.parse("2026-09-14T07:00:00Z"),
                duration = Duration.ofMinutes(30), distanceMeters = 5_000.0
            ),
            RunningWorkout(
                id = "r2", startTime = Instant.parse("2026-09-15T07:00:00Z"),
                duration = Duration.ofMinutes(40), distanceMeters = 7_000.0
            ),
            CyclingWorkout(
                id = "c1", startTime = Instant.parse("2026-09-16T07:00:00Z"),
                duration = Duration.ofMinutes(60), distanceMeters = 22_000.0
            ),
            StrengthWorkout(
                id = "s1", startTime = Instant.parse("2026-09-16T09:00:00Z"),
                duration = Duration.ofMinutes(45),
                sets = listOf(
                    StrengthSet("Bench", 5, 60.0),
                    StrengthSet("Squat", 5, 100.0)
                )
            )
        )
        val summary = WeeklySummaryCalculator.compute(workouts, now, zone, bodyWeightKg, heightCm = null)

        val running = summary.perActivity[ActivityType.RUNNING]!!
        assertEquals(2, running.count)
        assertEquals(12_000.0, running.distanceMeters, 1e-6)

        val cycling = summary.perActivity[ActivityType.CYCLING]!!
        assertEquals(1, cycling.count)
        assertEquals(22_000.0, cycling.distanceMeters, 1e-6)

        val strength = summary.perActivity[ActivityType.WEIGHTLIFTING]!!
        assertEquals(1, strength.count)
        assertEquals(5 * 60.0 + 5 * 100.0, strength.totalVolumeKg, 1e-6)
    }

    // ---------- streak ----------

    @Test
    fun `streak is zero when the user has no workouts in the last two days`() {
        // Last workout three days ago → both today and yesterday are empty.
        val old = RunningWorkout(
            id = "o", startTime = Instant.parse("2026-09-13T10:00:00Z"),
            duration = Duration.ofMinutes(30), distanceMeters = 5_000.0,
        )
        val summary = WeeklySummaryCalculator.compute(
            listOf(old), now, zone, bodyWeightKg, heightCm = null,
        )
        assertEquals(0, summary.streakDays)
    }

    @Test
    fun `three consecutive days ending today give a streak of three`() {
        val workouts = listOf(
            runOn("2026-09-14T10:00:00Z"), // Mon
            runOn("2026-09-15T10:00:00Z"), // Tue
            runOn("2026-09-16T07:00:00Z"), // Wed (today, before `now`)
        )
        val summary = WeeklySummaryCalculator.compute(
            workouts, now, zone, bodyWeightKg, heightCm = null,
        )
        assertEquals(3, summary.streakDays)
    }

    @Test
    fun `yesterday counts even when today is still empty`() {
        // Today has nothing, but yesterday + the day before do → streak of 2.
        val workouts = listOf(
            runOn("2026-09-14T10:00:00Z"), // Mon
            runOn("2026-09-15T10:00:00Z"), // Tue (yesterday)
        )
        val summary = WeeklySummaryCalculator.compute(
            workouts, now, zone, bodyWeightKg, heightCm = null,
        )
        assertEquals(2, summary.streakDays)
    }

    @Test
    fun `gap of a day breaks the streak`() {
        val workouts = listOf(
            runOn("2026-09-13T10:00:00Z"), // Sun — before the gap
            runOn("2026-09-15T10:00:00Z"), // Tue
            runOn("2026-09-16T08:00:00Z"), // Wed (today)
        )
        val summary = WeeklySummaryCalculator.compute(
            workouts, now, zone, bodyWeightKg, heightCm = null,
        )
        // Walks back from Wed → Tue → stops at Mon (empty). Streak = 2.
        assertEquals(2, summary.streakDays)
    }

    private fun runOn(iso: String): RunningWorkout = RunningWorkout(
        id = iso,
        startTime = Instant.parse(iso),
        duration = Duration.ofMinutes(30),
        distanceMeters = 5_000.0,
    )

    @Test
    fun `calories scale with body weight`() {
        val cycling = CyclingWorkout(
            id = "c1",
            startTime = Instant.parse("2026-09-15T18:00:00Z"),
            duration = Duration.ofMinutes(60),
            // 20 km/h → 8.0 MET band
            distanceMeters = 20_000.0
        )
        val at70 = WeeklySummaryCalculator.compute(listOf(cycling), now, zone, 70.0, heightCm = null)
        val at100 = WeeklySummaryCalculator.compute(listOf(cycling), now, zone, 100.0, heightCm = null)
        // 8 MET * 70 kg * 1h = 560; 8 MET * 100 kg * 1h = 800
        assertEquals(560.0, at70.estimatedCalories, 1e-6)
        assertEquals(800.0, at100.estimatedCalories, 1e-6)
    }

    // ---------- a past week (Home calendar) ----------

    @Test
    fun `weekOf sums only that week and keeps today's streak`() {
        val workouts = listOf(
            runOn("2026-09-06T10:00:00Z"), // Sun, two weeks ago
            runOn("2026-09-08T10:00:00Z"), // Tue last week
            runOn("2026-09-13T22:00:00Z"), // Sun last week, late
            runOn("2026-09-15T10:00:00Z"), // Tue this week
            runOn("2026-09-16T07:00:00Z"), // Wed (today)
        )
        val lastWeek = WeeklySummaryCalculator.compute(
            workouts, now, zone, bodyWeightKg, heightCm = null,
            weekOf = LocalDate.of(2026, 9, 10),
        )
        assertEquals(2, lastWeek.workoutCount)
        assertEquals(10_000.0, lastWeek.totalDistanceMeters, 0.0)
        // Streak: Tue + Wed this week, counted back from today.
        assertEquals(2, lastWeek.streakDays)
    }
}
