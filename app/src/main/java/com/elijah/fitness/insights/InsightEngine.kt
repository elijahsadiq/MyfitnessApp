package com.elijah.fitness.insights

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
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
 * it, the fragment binds whatever string comes out.
 *
 * ### Enough data
 * Each rule declares its own "enough data" check inline. The engine
 * returns an empty list when nothing qualifies; the ViewModel falls
 * back to the [Insight.Kind.Fallback] copy in that case.
 */
class InsightEngine(
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
                text = "New personal record: longest run — ${formatKm(recentLongestRun)} km.",
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
                    text = "New personal record on $exercise.",
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
    // -----------------------------------------------------------------
    private fun goalProjectionInsight(goals: List<GoalWithProgress>): List<Insight> {
        val target = goals.firstOrNull { candidate ->
            !candidate.isComplete && !candidate.isExpired &&
                candidate.progressFraction in 0.3..0.95 &&
                candidate.goal.period == GoalPeriod.THIS_MONTH
        } ?: return emptyList()

        val today = LocalDate.ofInstant(clock.instant(), zone)
        val remainingValue = target.goal.targetValue - target.currentValue
        if (remainingValue <= 0.0) return emptyList()

        val periodStart = LocalDate.ofInstant(target.goal.periodStart, zone)
        val daysElapsed = ChronoUnit.DAYS.between(periodStart, today).coerceAtLeast(1L)
        val perDayRate = target.currentValue / daysElapsed.toDouble()
        if (perDayRate <= 0.0) return emptyList()
        val daysNeeded = (remainingValue / perDayRate).roundToInt().coerceAtLeast(1)
        val projected = today.plusDays(daysNeeded.toLong())
        val lastDay = LocalDate.ofInstant(target.goal.periodEnd.minusNanos(1), zone)

        if (projected.isAfter(lastDay)) return emptyList()

        return listOf(
            Insight(
                text = "At this rate you'll reach your monthly goal by the ${ordinal(projected.dayOfMonth)}.",
                priority = 20,
                kind = Insight.Kind.GoalProjection,
            )
        )
    }

    // -----------------------------------------------------------------
    // Rule 3 — pace improvement this month vs last month (running)
    // -----------------------------------------------------------------
    private fun paceImprovementInsight(workouts: List<Workout>): List<Insight> {
        val today = LocalDate.ofInstant(clock.instant(), zone)
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
                text = "Your average pace improved by $diffSeconds seconds per kilometre this month.",
                priority = 30,
                kind = Insight.Kind.PaceImprovement,
            )
        )
    }

    // -----------------------------------------------------------------
    // Rule 4 — "you ran X% further than last week"
    // -----------------------------------------------------------------
    private fun volumeChangeInsight(workouts: List<Workout>): List<Insight> {
        val today = LocalDate.ofInstant(clock.instant(), zone)
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeekStart = thisWeekStart.minusWeeks(1)

        for (type in listOf(ActivityType.RUNNING, ActivityType.CYCLING)) {
            val thisWeek = distanceSum(workouts, type, thisWeekStart, thisWeekStart.plusWeeks(1))
            val lastWeek = distanceSum(workouts, type, lastWeekStart, thisWeekStart)
            if (lastWeek <= 0.0 || thisWeek <= 0.0) continue
            val diffPct = ((thisWeek - lastWeek) / lastWeek) * 100.0
            if (abs(diffPct) < 10.0) continue // too small to be worth saying
            val verb = if (type == ActivityType.RUNNING) "ran" else "cycled"
            val direction = if (diffPct > 0) "further" else "less"
            return listOf(
                Insight(
                    text = "You $verb ${abs(diffPct).roundToInt()}% $direction than last week.",
                    priority = 40,
                    kind = Insight.Kind.VolumeChange,
                )
            )
        }
        return emptyList()
    }

    // -----------------------------------------------------------------
    // Rule 5 — dormancy nudge
    // -----------------------------------------------------------------
    private fun dormancyInsight(workouts: List<Workout>): List<Insight> {
        if (workouts.isEmpty()) return emptyList()
        val now = clock.instant()
        for (type in listOf(ActivityType.RUNNING, ActivityType.CYCLING)) {
            val latest = workouts.filter { it.type == type }
                .maxByOrNull { it.startTime } ?: continue
            val days = ChronoUnit.DAYS.between(latest.startTime, now)
            if (days in 7..60) {
                val label = when (type) {
                    ActivityType.RUNNING -> "run"
                    ActivityType.CYCLING -> "cycled"
                    else -> continue
                }
                return listOf(
                    Insight(
                        text = "You haven't $label in $days days.",
                        priority = 50,
                        kind = Insight.Kind.Dormancy,
                    )
                )
            }
        }
        return emptyList()
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

    private fun ordinal(dayOfMonth: Int): String {
        val suffix = when {
            dayOfMonth % 100 in 11..13 -> "th"
            dayOfMonth % 10 == 1 -> "st"
            dayOfMonth % 10 == 2 -> "nd"
            dayOfMonth % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$dayOfMonth$suffix"
    }

    private fun formatKm(meters: Double): String = "%.1f".format(meters / 1000.0)

    /** Keep the previous CyclingWorkout generic path reachable for type-safety. */
    @Suppress("unused")
    private fun forceCyclingReference(workout: CyclingWorkout): Workout = workout

    /** Avoid an unused-import warning on `Goal` since the signature exposes it. */
    @Suppress("unused")
    private fun forceGoalReference(goal: Goal): Goal = goal
}
