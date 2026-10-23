package com.elijah.fitness.analytics

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToLong

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

    /** Per-activity statistics over an already-filtered-by-period list. */
    fun statsFor(workouts: List<Workout>, activityType: ActivityType): ProgressStats {
        val subset = workouts.filter { it.type == activityType }
        if (subset.isEmpty()) return ProgressStats.EMPTY
        val count = subset.size
        return when (activityType) {
            ActivityType.RUNNING -> runStats(subset.filterIsInstance<RunningWorkout>(), count)
            ActivityType.CYCLING -> cycleStats(subset.filterIsInstance<CyclingWorkout>(), count)
            ActivityType.SWIMMING -> splitStats(subset.filterIsInstance<SwimmingWorkout>(), count)
            ActivityType.ROWING -> splitStats(subset.filterIsInstance<RowingWorkout>(), count)
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
        val longestSwim = allWorkouts.filterIsInstance<SwimmingWorkout>().maxOfOrNull { it.distanceMeters }
        val longestRow = allWorkouts.filterIsInstance<RowingWorkout>().maxOfOrNull { it.distanceMeters }

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
            longestSwimMeters = longestSwim,
            longestRowMeters = longestRow,
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
        val today = now.atZone(zone).toLocalDate()
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
        val today = now.atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startOfWeek = monday.atStartOfDay(zone).toInstant()
        val endOfWeek = monday.plusWeeks(1).atStartOfDay(zone).toInstant()
        val perDay = DayOfWeek.values().associateWith { 0L }.toMutableMap()
        workouts.asSequence()
            .filter { !it.startTime.isBefore(startOfWeek) && it.startTime.isBefore(endOfWeek) }
            .forEach {
                val day = it.startTime.atZone(zone).toLocalDate().dayOfWeek
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
        val today = now.atZone(zone).toLocalDate()
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
                val date = it.startTime.atZone(zone).toLocalDate()
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
        val today = now.atZone(zone).toLocalDate()
        val year = today.year
        val perMonth = LongArray(12)
        workouts.asSequence()
            .filter { it.startTime.atZone(zone).toLocalDate().year == year }
            .forEach {
                val m = it.startTime.atZone(zone).toLocalDate().monthValue - 1
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
        val totalSeconds = runs.sumOf { it.duration.seconds }
        val totalMinutes = runs.sumOf { it.duration.toMinutes() }
        val paces = runs.map { it.paceSecondsPerKm() }.filter { it > 0 }
        return ProgressStats(
            workoutCount = count,
            totalDistanceMeters = totalMeters,
            totalDurationMinutes = totalMinutes,
            // Whole seconds, rounded like each run's own pace.
            averagePaceSecondsPerKm = if (totalMeters > 0.0) {
                (totalSeconds.toDouble() / (totalMeters / 1000.0)).roundToLong()
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

    /**
     * Swims and rows (phase 8d): totals plus pace per split. The split
     * comes from each class's own [DistanceWorkout.paceSplitMeters]
     * (100 m for a swim, 500 m for a row), so one function serves both.
     */
    private fun splitStats(workouts: List<DistanceWorkout>, count: Int): ProgressStats {
        val totalMeters = workouts.sumOf { it.distanceMeters }
        val totalSeconds = workouts.sumOf { it.duration.seconds }
        val splitMeters = workouts.firstOrNull()?.paceSplitMeters
        val splits = workouts.map { it.paceSecondsPerSplit() }.filter { it > 0 }
        return ProgressStats(
            workoutCount = count,
            totalDistanceMeters = totalMeters,
            totalDurationMinutes = workouts.sumOf { it.duration.toMinutes() },
            averagePaceSecondsPerKm = null,
            bestPaceSecondsPerKm = null,
            longestDistanceMeters = workouts.maxOfOrNull { it.distanceMeters },
            averageSpeedKmh = null,
            maxSpeedKmh = null,
            totalSets = null,
            totalVolumeKg = null,
            mostFrequentExercise = null,
            averageSplitSeconds = if (splitMeters != null && totalMeters >= DistanceWorkout.MIN_PACE_DISTANCE_M) {
                (totalSeconds.toDouble() / (totalMeters / splitMeters)).roundToLong()
            } else null,
            bestSplitSeconds = splits.minOrNull(),
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

