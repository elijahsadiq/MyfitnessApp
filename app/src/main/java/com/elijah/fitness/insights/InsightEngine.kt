package com.elijah.fitness.insights

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.util.TextResolver
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-Kotlin rule engine. No Android imports; the Home ViewModel drives
 * it, the fragment binds whatever string comes out. The wording lives in
 * `strings.xml` / `plurals.xml` and is read through [text] (phase 8d).
 *
 * ### Enough data
 * Each rule declares its own "enough data" check inline. The engine
 * returns an empty list when nothing qualifies; the ViewModel falls
 * back to the [Insight.Kind.Fallback] copy in that case.
 */
class InsightEngine(
    private val text: TextResolver,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Rank every triggered insight; the UI usually displays `.first()`. */
    fun rank(
        workouts: List<Workout>,
        goals: List<GoalWithProgress> = emptyList(),
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        out += personalRecordInsight(workouts)
        out += goalProjectionInsight(goals)
        out += paceImprovementInsight(workouts)
        out += volumeChangeInsight(workouts)
        out += dormancyInsight(workouts)
        return out.sortedBy { it.priority }
    }

    // -----------------------------------------------------------------
    // Rule 1 — new personal record (last 7 days)
    // -----------------------------------------------------------------
    private fun personalRecordInsight(workouts: List<Workout>): List<Insight> {
        val now = clock.instant()
        val recentCutoff = now.minus(7, ChronoUnit.DAYS)
        val previous = workouts.filter { it.startTime.isBefore(recentCutoff) }
        val recent = workouts.filter { !it.startTime.isBefore(recentCutoff) }

        val insights = mutableListOf<Insight>()

        // Running: longest run in the last 7 days beats the all-time
        // record from before that window.
        val recentLongestRun = recent.filterIsInstance<RunningWorkout>()
            .maxOfOrNull { it.distanceMeters } ?: 0.0
        val priorLongestRun = previous.filterIsInstance<RunningWorkout>()
            .maxOfOrNull { it.distanceMeters } ?: 0.0
        if (recentLongestRun > priorLongestRun && recentLongestRun >= 1_000.0) {
            insights += Insight(
                text = text.string(R.string.insight_pr_longest_run, formatKm(recentLongestRun)),
                priority = 10,
                kind = Insight.Kind.PersonalRecord,
            )
        }

        // Strength: a new 1RM on an exercise, computed via Epley.
        val recentOneRm = oneRepMaxes(recent.filterIsInstance<StrengthWorkout>())
        val priorOneRm = oneRepMaxes(previous.filterIsInstance<StrengthWorkout>())
        recentOneRm.forEach { (exercise, kg) ->
            val previousBest = priorOneRm[exercise] ?: 0.0
            if (kg > previousBest + 0.5) {
                insights += Insight(
                    text = text.string(R.string.insight_pr_exercise, exercise),
                    priority = 10,
                    kind = Insight.Kind.PersonalRecord,
                )
            }
        }

        return insights.take(1) // only surface one PR, keep the home card tight
    }

    private fun oneRepMaxes(sessions: List<StrengthWorkout>): Map<String, Double> =
        sessions.flatMap { it.sets }
            .groupBy { it.exercise.trim() }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, s) -> s.maxOf { it.weightKg * (1.0 + it.reps / 30.0) } }

    // -----------------------------------------------------------------
    // Rule 2 — goal projection
    //
    // Phase-8b: goals no longer carry a WEEK/MONTH period, so the old
    // "monthly goal only" filter is dropped in favour of "any active
    // goal with at least 7 days of total duration". That matches the
    // spirit of the original rule — projections are only interesting
    // when the window is long enough for a meaningful per-day rate.
    // -----------------------------------------------------------------
    private fun goalProjectionInsight(goals: List<GoalWithProgress>): List<Insight> {
        val target = goals.firstOrNull { candidate ->
            candidate.isActive &&
                candidate.goal.durationDays >= 7 &&
                candidate.progressFraction in 0.3..0.95
        } ?: return emptyList()

        val today = clock.instant().atZone(zone).toLocalDate()
        val remainingValue = target.goal.targetValue - target.currentValue
        if (remainingValue <= 0.0) return emptyList()

        val daysElapsed = ChronoUnit.DAYS.between(target.goal.startDate, today)
            .coerceAtLeast(1L)
        val perDayRate = target.currentValue / daysElapsed.toDouble()
        if (perDayRate <= 0.0) return emptyList()
        val daysNeeded = (remainingValue / perDayRate).roundToInt().coerceAtLeast(1)
        val projected = today.plusDays(daysNeeded.toLong())

        if (projected.isAfter(target.goal.lastDay)) return emptyList()

        return listOf(
            Insight(
                text = text.string(R.string.insight_goal_projection, ordinal(projected.dayOfMonth)),
                priority = 20,
                kind = Insight.Kind.GoalProjection,
            )
        )
    }

    // -----------------------------------------------------------------
    // Rule 3 — pace improvement this month vs last month (running)
    // -----------------------------------------------------------------
    private fun paceImprovementInsight(workouts: List<Workout>): List<Insight> {
        val today = clock.instant().atZone(zone).toLocalDate()
        val thisMonthStart = today.withDayOfMonth(1)
        val lastMonthStart = thisMonthStart.minusMonths(1)

        val thisMonth = runsBetween(workouts, thisMonthStart, thisMonthStart.plusMonths(1))
        val lastMonth = runsBetween(workouts, lastMonthStart, thisMonthStart)
        if (thisMonth.size < 2 || lastMonth.size < 2) return emptyList()

        val thisPace = averagePace(thisMonth)
        val lastPace = averagePace(lastMonth)
        if (thisPace == null || lastPace == null) return emptyList()
        if (lastPace <= thisPace) return emptyList() // no improvement
        val diffSeconds = lastPace - thisPace
        if (diffSeconds < 5) return emptyList() // negligible

        return listOf(
            Insight(
                text = text.plural(
                    R.plurals.insight_pace_improved, diffSeconds.toInt(), diffSeconds.toInt(),
                ),
                priority = 30,
                kind = Insight.Kind.PaceImprovement,
            )
        )
    }

    // -----------------------------------------------------------------
    // Rule 4 — "you ran X% further than last week" (any distance sport)
    // -----------------------------------------------------------------
    private fun volumeChangeInsight(workouts: List<Workout>): List<Insight> {
        val today = clock.instant().atZone(zone).toLocalDate()
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeekStart = thisWeekStart.minusWeeks(1)

        for (type in DISTANCE_TYPES) {
            val thisWeek = distanceSum(workouts, type, thisWeekStart, thisWeekStart.plusWeeks(1))
            val lastWeek = distanceSum(workouts, type, lastWeekStart, thisWeekStart)
            if (lastWeek <= 0.0 || thisWeek <= 0.0) continue
            val diffPct = ((thisWeek - lastWeek) / lastWeek) * 100.0
            if (abs(diffPct) < 10.0) continue // too small to be worth saying
            val res = volumeChangeRes(type, further = diffPct > 0) ?: continue
            return listOf(
                Insight(
                    text = text.string(res, abs(diffPct).roundToInt()),
                    priority = 40,
                    kind = Insight.Kind.VolumeChange,
                )
            )
        }
        return emptyList()
    }

    /** "You ran / cycled / swam / rowed X% further|less …". */
    private fun volumeChangeRes(type: ActivityType, further: Boolean): Int? = when (type) {
        ActivityType.RUNNING ->
            if (further) R.string.insight_volume_further_run else R.string.insight_volume_less_run
        ActivityType.CYCLING ->
            if (further) R.string.insight_volume_further_ride else R.string.insight_volume_less_ride
        ActivityType.SWIMMING ->
            if (further) R.string.insight_volume_further_swim else R.string.insight_volume_less_swim
        ActivityType.ROWING ->
            if (further) R.string.insight_volume_further_row else R.string.insight_volume_less_row
        ActivityType.WEIGHTLIFTING -> null // no distance to compare
    }

    // -----------------------------------------------------------------
    // Rule 5 — dormancy nudge
    // -----------------------------------------------------------------
    private fun dormancyInsight(workouts: List<Workout>): List<Insight> {
        if (workouts.isEmpty()) return emptyList()
        val now = clock.instant()
        for (type in DISTANCE_TYPES) {
            val latest = workouts.filter { it.type == type }
                .maxByOrNull { it.startTime } ?: continue
            val days = ChronoUnit.DAYS.between(latest.startTime, now)
            if (days in 7..60) {
                val res = dormancyRes(type) ?: continue
                return listOf(
                    Insight(
                        text = text.plural(res, days.toInt(), days.toInt()),
                        priority = 50,
                        kind = Insight.Kind.Dormancy,
                    )
                )
            }
        }
        return emptyList()
    }

    /** "You haven't run / cycled / swum / rowed in N days." */
    private fun dormancyRes(type: ActivityType): Int? = when (type) {
        ActivityType.RUNNING -> R.plurals.insight_dormant_run
        ActivityType.CYCLING -> R.plurals.insight_dormant_ride
        ActivityType.SWIMMING -> R.plurals.insight_dormant_swim
        ActivityType.ROWING -> R.plurals.insight_dormant_row
        ActivityType.WEIGHTLIFTING -> null
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private fun runsBetween(
        workouts: List<Workout>,
        startInclusive: LocalDate,
        endExclusive: LocalDate,
    ): List<RunningWorkout> {
        val fromInstant = startInclusive.atStartOfDay(zone).toInstant()
        val toInstant = endExclusive.atStartOfDay(zone).toInstant()
        return workouts.filterIsInstance<RunningWorkout>()
            .filter { !it.startTime.isBefore(fromInstant) && it.startTime.isBefore(toInstant) }
    }

    private fun averagePace(runs: List<RunningWorkout>): Long? {
        val totalMeters = runs.sumOf { it.distanceMeters }
        val totalSeconds = runs.sumOf { it.duration.seconds }
        if (totalMeters <= 0.0 || totalSeconds <= 0L) return null
        return (totalSeconds.toDouble() / (totalMeters / 1000.0)).toLong()
    }

    private fun distanceSum(
        workouts: List<Workout>,
        type: ActivityType,
        startInclusive: LocalDate,
        endExclusive: LocalDate,
    ): Double {
        val fromInstant = startInclusive.atStartOfDay(zone).toInstant()
        val toInstant = endExclusive.atStartOfDay(zone).toInstant()
        return workouts.asSequence()
            .filter { it.type == type }
            .filterIsInstance<DistanceWorkout>()
            .filter { !it.startTime.isBefore(fromInstant) && it.startTime.isBefore(toInstant) }
            .sumOf { it.distanceMeters }
    }

    /** "1st", "2nd", "23rd", "11th" … — English ordinal rules, words from strings.xml. */
    private fun ordinal(dayOfMonth: Int): String {
        val res = when {
            dayOfMonth % 100 in 11..13 -> R.string.insight_ordinal_th
            dayOfMonth % 10 == 1 -> R.string.insight_ordinal_st
            dayOfMonth % 10 == 2 -> R.string.insight_ordinal_nd
            dayOfMonth % 10 == 3 -> R.string.insight_ordinal_rd
            else -> R.string.insight_ordinal_th
        }
        return text.string(res, dayOfMonth)
    }

    private fun formatKm(meters: Double): String = "%.1f".format(meters / 1000.0)

    private companion object {
        /** The sports compared week on week, in the order they're checked. */
        val DISTANCE_TYPES = listOf(
            ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.SWIMMING, ActivityType.ROWING,
        )
    }
}
