package com.elijah.fitness.data.model

import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Joins the goals stream and the workouts stream so progress recomputes
 * automatically whenever either changes.
 *
 * Placement: in the domain (`data/model`) package because it depends only
 * on the [Goal] hierarchy and the two repository interfaces — no Android
 * imports, no Retrofit. That keeps it unit-testable and keeps the
 * "progress lives on the client" trade-off (phase-7 scope decision)
 * enforced by the type system: nothing in this file touches SQL.
 *
 * `combine` re-emits on *any* upstream change, so logging a new workout
 * causes every affected goal card to re-render immediately.
 */
class GoalProgressCalculator(
    private val goalRepository: GoalRepository,
    private val workoutRepository: WorkoutRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun observe(): Flow<List<GoalWithProgress>> =
        combine(
            goalRepository.observeGoals(),
            workoutRepository.observeWorkouts(),
        ) { goals, workouts ->
            val now = clock.instant()
            goals.map { goal -> goal.snapshot(workouts, now) }
        }

    private fun Goal.snapshot(workouts: List<Workout>, now: Instant): GoalWithProgress {
        val days = daysRemainingFrom(now)
        return GoalWithProgress(
            goal = this,
            currentValue = currentValue(workouts),
            progressFraction = progressFraction(workouts),
            isComplete = isComplete(workouts),
            daysRemaining = days,
            isExpired = days < 0,
        )
    }

    /**
     * Calendar-day distance between "today" in [zone] and the period's
     * inclusive last day. A period that runs `[Mon 00:00, next Mon 00:00)`
     * has an inclusive last day of Sunday; on Monday that returns 6, on
     * Sunday 0 ("Last day"), and on the following Monday −1 (expired).
     *
     * Reading in calendar days rather than full 24-hour blocks (the old
     * `ChronoUnit.DAYS.between(nowInstant, periodEndInstant)` approach)
     * is what fixes the phase-8 defect: a stale goal whose stored
     * `periodEnd` is in the past now reports negative days, so the UI
     * can file it under "Ended" instead of mis-labelling it "Last day".
     */
    private fun Goal.daysRemainingFrom(now: Instant): Long {
        val today = LocalDate.ofInstant(now, zone)
        // periodEnd is exclusive; subtract one nano to land on the last
        // instant of the included period, then take its calendar date.
        val lastDay = LocalDate.ofInstant(periodEnd.minusNanos(1), zone)
        return ChronoUnit.DAYS.between(today, lastDay)
    }
}
