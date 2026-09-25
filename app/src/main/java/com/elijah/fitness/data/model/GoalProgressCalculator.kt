package com.elijah.fitness.data.model

import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
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
) {

    fun observe(): Flow<List<GoalWithProgress>> =
        combine(
            goalRepository.observeGoals(),
            workoutRepository.observeWorkouts(),
        ) { goals, workouts ->
            val now = clock.instant()
            goals.map { goal -> goal.snapshot(workouts, now) }
        }

    private fun Goal.snapshot(workouts: List<Workout>, now: Instant): GoalWithProgress =
        GoalWithProgress(
            goal = this,
            currentValue = currentValue(workouts),
            progressFraction = progressFraction(workouts),
            isComplete = isComplete(workouts),
            daysRemaining = daysRemainingFrom(now),
        )

    /**
     * Days between "today" and the goal's exclusive end boundary. Uses
     * [ChronoUnit.DAYS] on the underlying [Instant]s so DST + zone
     * transitions don't skew the number.
     */
    private fun Goal.daysRemainingFrom(now: Instant): Long =
        ChronoUnit.DAYS.between(now, periodEnd)
}
