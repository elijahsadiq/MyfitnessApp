package com.elijah.fitness.data.model

import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Joins the goals stream and the workouts stream so progress recomputes
 * automatically whenever either changes.
 *
 * Placement: in the domain (`data/model`) package because it depends
 * only on the [Goal] hierarchy and the two repository interfaces — no
 * Android imports, no Retrofit. That keeps it unit-testable and keeps
 * the "progress lives on the client" trade-off (phase-7 scope decision)
 * enforced by the type system: nothing in this file touches SQL.
 *
 * `combine` re-emits on *any* upstream change, so logging a new workout
 * causes every affected goal card to re-render immediately.
 *
 * Phase-8b: produces the Manage-sheet statistics (daysLeft, averagePerDay,
 * neededPerDay, sessionsLogged) alongside the current value + status, so
 * both the list and the sheet read the same per-goal snapshot without
 * re-doing the arithmetic.
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
            val today = clock.instant().atZone(zone).toLocalDate()
            goals.map { goal -> goal.snapshot(workouts, today) }
        }

    private fun Goal.snapshot(workouts: List<Workout>, today: LocalDate): GoalWithProgress =
        GoalWithProgress(
            goal = this,
            status = status(workouts, today, zone),
            currentValue = currentValue(workouts, zone),
            progressFraction = progressFraction(workouts, zone),
            daysLeft = daysLeft(today),
            averagePerDay = averagePerDay(workouts, today, zone),
            neededPerDay = neededPerDay(workouts, today, zone),
            sessionsLogged = sessionsLogged(workouts, zone),
        )
}
