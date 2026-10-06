package com.elijah.fitness.analytics

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Pure-Kotlin analytics for the Progress tab. Zero Android imports so
 * every function is unit-testable with a `List<Workout>` + a fixed
 * `Instant` + a `ZoneId`.
 *
 * The computations are deliberately read-only: no caching, no state. The
 * ViewModel re-runs them whenever the underlying workout list or the
 * filters change. The dataset is bounded by phase-1 scope (≤ a few
 * hundred workouts per user) so the cost is negligible.
 */
object ProgressAnalytics {

    /** Workouts falling inside the selected [period] window, newest first. */
    fun periodWorkouts(
        workouts: List<Workout>,
        period: AnalyticsPeriod,
        now: Instant,
        zone: ZoneId,
    ): List<Workout> {
        val bounds = periodBounds(period, now, zone)
        return workouts.filter {
            !it.startTime.isBefore(bounds.first) && it.startTime.isBefore(bounds.second)
        }
    }

    /**
     * Per-bucket active-minutes bar chart for the selected period:
     *   WEEK  → 7 bars (Mon..Sun)
     *   MONTH → 4-5 bars (W1..W5 by ISO week number within the month)
     *   YEAR  → 12 bars (Jan..Dec)
     * Filtering by `activityType` narrows which workouts contribute.
     */
    fun activityBars(
        workouts: List<Workout>,
        period: AnalyticsPeriod,
        activityType: ActivityType?,
        now: Instant,
        zone: ZoneId,
    ): List<BarPoint> {
        val filtered = (if (activityType != null) workouts.filter { it.type == activityType }
                       else workouts)
        return when (period) {
            AnalyticsPeriod.WEEK -> weekBars(filtered, now, zone)
            AnalyticsPeriod.MONTH -> monthBars(filtered, now, zone)
            AnalyticsPeriod.YEAR -> yearBars(filtered, now, zone)
        }
    }

    /**
     * Trend bars for the selected activity across the period. Shape:
     *   RUNNING       → pace (seconds per km) per workout, newest right
     *   CYCLING       → average speed (km/h) per workout, newest right
     *   WEIGHTLIFTING → total volume per workout, newest right
     *   null (ALL)    → active minutes per workout, newest right
     */
    fun trendBars(
        workouts: List<Workout>,
        activityType: ActivityType?,
    ): List<BarPoint> {
        val ordered = workouts.sortedBy { it.startTime }
        if (ordered.isEmpty()) return emptyList()
        val maxBars = 12
        val sampled = if (ordered.size <= maxBars) ordered
                      else ordered.takeLast(maxBars)
        return sampled.map { workout ->
            val label = workout.startTime.atZone(ZoneId.systemDefault())
                .toLocalDate().dayOfMonth.toString()
            val value: Double = when (activityType) {
                ActivityType.RUNNING -> (workout as? RunningWorkout)?.paceSecondsPerKm()?.toDouble() ?: 0.0
                ActivityType.CYCLING -> (workout as? CyclingWorkout)?.averageSpeedKmh() ?: 0.0
                ActivityType.WEIGHTLIFTING -> (workout as? StrengthWorkout)?.totalVolumeKg() ?: 0.0
                null -> workout.duration.toMinutes().toDouble()
            }
            BarPoint(label, value)
        }
    }

    /** Per-activity statistics over an already-filtered-by-period list. */
    fun statsFor(workouts: List<Workout>, activityType: ActivityType): ProgressStats {
        val subset = workouts.filter { it.type == activityType }
        if (subset.isEmpty()) return ProgressStats.EMPTY
        val count = subset.size
        return when (activityType) {
            ActivityType.RUNNING -> runStats(subset.filterIsInstance<RunningWorkout>(), count)
            ActivityType.CYCLING -> cycleStats(subset.filterIsInstance<CyclingWorkout>(), count)
            ActivityType.WEIGHTLIFTING -> strengthStats(subset.filterIsInstance<StrengthWorkout>(), count)
        }
    }

    /**
     * Lifetime personal records across *every* workout the user has — not
     * just the current period. "Fastest 5 km" is the best average pace
     * recorded by any single run that covered at least 5 km.
     */
    fun personalRecords(allWorkouts: List<Workout>): PersonalRecords {
        val runs = allWorkouts.filterIsInstance<RunningWorkout>()
        val rides = allWorkouts.filterIsInstance<CyclingWorkout>()
        val strengths = allWorkouts.filterIsInstance<StrengthWorkout>()

        val fastest5k = runs
            .filter { it.distanceMeters >= 5_000.0 }
            .map { it.paceSecondsPerKm() }
            .filter { it > 0 }
            .minOrNull()

        val longestRun = runs.maxOfOrNull { it.distanceMeters }
        val longestRide = rides.maxOfOrNull { it.distanceMeters }

        val bestOneRm: Map<String, Double> = strengths
            .flatMap { it.sets }
            .groupBy { it.exercise.trim().lowercase(Locale.getDefault()) }
            .mapValues { (_, sets) ->
                sets.maxOf { it.weightKg * (1.0 + it.reps / 30.0) }
            }
            .filter { it.key.isNotBlank() }
            // Re-key with the display-cased exercise name (whichever casing
            // the user has used most recently wins — same key, latest display).
            .let { lowered ->
                val canonical = strengths.asSequence()
                    .flatMap { w -> w.sets.asSequence().map { s -> w.startTime to s.exercise } }
                    .sortedByDescending { it.first }
                    .map { it.second }
                    .toList()
                lowered.mapKeys { (key, _) ->
                    canonical.firstOrNull { it.trim().lowercase(Locale.getDefault()) == key }
                        ?: key
                }
            }

        return PersonalRecords(
            fastest5kSecondsPerKm = fastest5k,
            longestRunMeters = longestRun,
            longestRideMeters = longestRide,
            bestOneRepMaxes = bestOneRm,
        )
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    private fun periodBounds(
        period: AnalyticsPeriod,
        now: Instant,
        zone: ZoneId,
    ): Pair<Instant, Instant> {
        val today = LocalDate.ofInstant(now, zone)
        return when (period) {
            AnalyticsPeriod.WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday.atStartOfDay(zone).toInstant() to
                    monday.plusWeeks(1).atStartOfDay(zone).toInstant()
            }
            AnalyticsPeriod.MONTH -> {
                val first = today.withDayOfMonth(1)
                first.atStartOfDay(zone).toInstant() to
                    first.plusMonths(1).atStartOfDay(zone).toInstant()
            }
            AnalyticsPeriod.YEAR -> {
                val jan1 = today.withDayOfYear(1)
                jan1.atStartOfDay(zone).toInstant() to
                    jan1.plusYears(1).atStartOfDay(zone).toInstant()
            }
        }
    }

    private fun weekBars(
        workouts: List<Workout>,
        now: Instant,
        zone: ZoneId,
    ): List<BarPoint> {
        val today = LocalDate.ofInstant(now, zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startOfWeek = monday.atStartOfDay(zone).toInstant()
        val endOfWeek = monday.plusWeeks(1).atStartOfDay(zone).toInstant()
        val perDay = DayOfWeek.values().associateWith { 0L }.toMutableMap()
        workouts.asSequence()
            .filter { !it.startTime.isBefore(startOfWeek) && it.startTime.isBefore(endOfWeek) }
            .forEach {
                val day = LocalDate.ofInstant(it.startTime, zone).dayOfWeek
                perDay[day] = (perDay[day] ?: 0L) + it.duration.toMinutes()
            }
        return listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        ).map { d ->
            BarPoint(
                label = d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                value = (perDay[d] ?: 0L).toDouble(),
            )
        }
    }

    private fun monthBars(
        workouts: List<Workout>,
        now: Instant,
        zone: ZoneId,
    ): List<BarPoint> {
        val today = LocalDate.ofInstant(now, zone)
        val first = today.withDayOfMonth(1)
        val endExclusive = first.plusMonths(1)
        val startOfMonth = first.atStartOfDay(zone).toInstant()
        val endOfMonth = endExclusive.atStartOfDay(zone).toInstant()
        val weekCount = ((first.lengthOfMonth() + first.dayOfWeek.value - 1 + 6) / 7)
            .coerceAtLeast(4)

        val perWeek = LongArray(weekCount)
        workouts.asSequence()
            .filter { !it.startTime.isBefore(startOfMonth) && it.startTime.isBefore(endOfMonth) }
            .forEach {
                val date = LocalDate.ofInstant(it.startTime, zone)
                val weekIdx = ((date.dayOfMonth + first.dayOfWeek.value - 2) / 7)
                    .coerceIn(0, weekCount - 1)
                perWeek[weekIdx] += it.duration.toMinutes()
            }
        return (0 until weekCount).map {
            BarPoint(label = "W${it + 1}", value = perWeek[it].toDouble())
        }
    }

    private fun yearBars(
        workouts: List<Workout>,
        now: Instant,
        zone: ZoneId,
    ): List<BarPoint> {
        val today = LocalDate.ofInstant(now, zone)
        val year = today.year
        val perMonth = LongArray(12)
        workouts.asSequence()
            .filter { LocalDate.ofInstant(it.startTime, zone).year == year }
            .forEach {
                val m = LocalDate.ofInstant(it.startTime, zone).monthValue - 1
                perMonth[m] += it.duration.toMinutes()
            }
        return (0 until 12).map {
            BarPoint(
                label = Month.of(it + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                value = perMonth[it].toDouble(),
            )
        }
    }

    private fun runStats(runs: List<RunningWorkout>, count: Int): ProgressStats {
        val totalMeters = runs.sumOf { it.distanceMeters }
        val totalMinutes = runs.sumOf { it.duration.toMinutes() }
        val paces = runs.map { it.paceSecondsPerKm() }.filter { it > 0 }
        return ProgressStats(
            workoutCount = count,
            totalDistanceMeters = totalMeters,
            totalDurationMinutes = totalMinutes,
            averagePaceSecondsPerKm = if (totalMeters > 0.0) {
                ((totalMinutes * 60).toDouble() / (totalMeters / 1000.0)).toLong()
            } else null,
            bestPaceSecondsPerKm = paces.minOrNull(),
            longestDistanceMeters = runs.maxOfOrNull { it.distanceMeters },
            averageSpeedKmh = null,
            maxSpeedKmh = null,
            totalSets = null,
            totalVolumeKg = null,
            mostFrequentExercise = null,
        )
    }

    private fun cycleStats(rides: List<CyclingWorkout>, count: Int): ProgressStats {
        val totalMeters = rides.sumOf { it.distanceMeters }
        val totalMinutes = rides.sumOf { it.duration.toMinutes() }
        val speeds = rides.map { it.averageSpeedKmh() }.filter { it > 0.0 }
        return ProgressStats(
            workoutCount = count,
            totalDistanceMeters = totalMeters,
            totalDurationMinutes = totalMinutes,
            averagePaceSecondsPerKm = null,
            bestPaceSecondsPerKm = null,
            longestDistanceMeters = rides.maxOfOrNull { it.distanceMeters },
            averageSpeedKmh = if (totalMinutes > 0L) {
                (totalMeters / 1000.0) / (totalMinutes / 60.0)
            } else null,
            maxSpeedKmh = speeds.maxOrNull(),
            totalSets = null,
            totalVolumeKg = null,
            mostFrequentExercise = null,
        )
    }

    private fun strengthStats(sessions: List<StrengthWorkout>, count: Int): ProgressStats {
        val totalSets = sessions.sumOf { it.sets.size }
        val totalVolume = sessions.sumOf { it.totalVolumeKg() }
        val totalMinutes = sessions.sumOf { it.duration.toMinutes() }
        val mostFrequent = sessions.flatMap { it.sets }
            .groupingBy { it.exercise.trim() }
            .eachCount()
            .filterKeys { it.isNotBlank() }
            .maxByOrNull { it.value }
            ?.key
        return ProgressStats(
            workoutCount = count,
            totalDistanceMeters = null,
            totalDurationMinutes = totalMinutes,
            averagePaceSecondsPerKm = null,
            bestPaceSecondsPerKm = null,
            longestDistanceMeters = null,
            averageSpeedKmh = null,
            maxSpeedKmh = null,
            totalSets = totalSets,
            totalVolumeKg = totalVolume,
            mostFrequentExercise = mostFrequent,
        )
    }
}

