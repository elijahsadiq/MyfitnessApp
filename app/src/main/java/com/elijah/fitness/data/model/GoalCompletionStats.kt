package com.elijah.fitness.data.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The numbers on the goal-complete pop-up (phase 8d, Step 7), worked out
 * from a goal and the workouts list. Pure Kotlin, so unit tests call it
 * directly.
 *
 * - **Completion date**: the local date of the workout that made the
 *   running total reach the target (today when none can be found).
 * - **Days taken**: completion date − start date + 1.
 * - **Days early**: last day − completion date (0 = "on the last day").
 * - **kcal**: the sum of [Workout.energyKcal] over the workouts that
 *   counted toward the goal.
 */
object GoalCompletionStats {

    /**
     * Which words go under the "target achieved" tile: "km ridden",
     * "12 runs", "2 h 30 min active", "20,000 kg lifted".
     */
    enum class AchievedLabel {
        KM_RUN, KM_RIDDEN, KM_SWUM, KM_ROWED,
        RUNS, RIDES, SWIMS, ROWS, WORKOUTS, SESSIONS,
        TIME_ACTIVE, KG_LIFTED,
    }

    data class Result(
        val completionDate: LocalDate,
        val daysTaken: Int,
        val daysEarly: Int,
        val kcal: Double,
    )

    fun compute(
        goal: Goal,
        workouts: List<Workout>,
        weightKg: Double,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result {
        val counted = countedWorkouts(goal, workouts, zone)
        val completion = completionDate(goal, counted, today, zone)
        return Result(
            completionDate = completion,
            daysTaken = daysTaken(goal.startDate, completion),
            daysEarly = daysEarly(completion, goal.lastDay),
            kcal = kcal(counted, weightKg),
        )
    }

    /**
     * Workouts in the goal's days, of its activity (any when null) and of a
     * shape the goal can measure, oldest first.
     */
    fun countedWorkouts(goal: Goal, workouts: List<Workout>, zone: ZoneId): List<Workout> =
        workouts.filter { w ->
            val d = w.startTime.atZone(zone).toLocalDate()
            val inWindow = !d.isBefore(goal.startDate) && !d.isAfter(goal.lastDay)
            val matchesActivity = goal.activityType == null || w.type == goal.activityType
            val measurable = when (goal) {
                is DistanceGoal -> w is DistanceWorkout
                is StrengthVolumeGoal -> w is StrengthWorkout
                else -> true
            }
            inWindow && matchesActivity && measurable
        }.sortedBy { it.startTime }

    /**
     * Local date of the workout whose running total first meets the
     * target; [today] when the target is never met, kept inside the goal's
     * days so "days taken" can't go below 1 or past the window.
     */
    fun completionDate(goal: Goal, counted: List<Workout>, today: LocalDate, zone: ZoneId): LocalDate {
        var total = 0.0
        for (w in counted) {
            total += amount(goal, w)
            val current = if (goal is DistanceGoal) total / METRES_PER_KM else total
            if (Goal.meetsTarget(current, goal.targetValue)) {
                return w.startTime.atZone(zone).toLocalDate()
            }
        }
        return clamp(today, goal.startDate, goal.lastDay)
    }

    fun daysTaken(startDate: LocalDate, completionDate: LocalDate): Int =
        (ChronoUnit.DAYS.between(startDate, completionDate) + 1)
            .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    fun daysEarly(completionDate: LocalDate, lastDay: LocalDate): Int =
        ChronoUnit.DAYS.between(completionDate, lastDay)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun kcal(counted: List<Workout>, weightKg: Double): Double =
        counted.sumOf { w ->
            val kcal = w.energyKcal(weightKg)
            if (kcal.isFinite() && kcal > 0.0) kcal else 0.0
        }

    fun achievedLabel(goal: Goal): AchievedLabel = when (goal) {
        is DistanceGoal -> when (goal.activityType) {
            ActivityType.CYCLING -> AchievedLabel.KM_RIDDEN
            ActivityType.SWIMMING -> AchievedLabel.KM_SWUM
            ActivityType.ROWING -> AchievedLabel.KM_ROWED
            else -> AchievedLabel.KM_RUN
        }
        is DurationGoal -> AchievedLabel.TIME_ACTIVE
        is StrengthVolumeGoal -> AchievedLabel.KG_LIFTED
        else -> when (goal.activityType) {
            ActivityType.RUNNING -> AchievedLabel.RUNS
            ActivityType.CYCLING -> AchievedLabel.RIDES
            ActivityType.SWIMMING -> AchievedLabel.SWIMS
            ActivityType.ROWING -> AchievedLabel.ROWS
            ActivityType.WEIGHTLIFTING -> AchievedLabel.WORKOUTS
            null -> AchievedLabel.SESSIONS
        }
    }

    /**
     * Everything the pop-up shows for [goal], titled [title] (the goal's
     * display title). The first tile shows the target that was reached,
     * in the goal's own unit (km, sessions, minutes, kg).
     */
    fun celebrationFor(
        goal: Goal,
        title: String,
        workouts: List<Workout>,
        weightKg: Double,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): GoalCelebration {
        val result = compute(goal, workouts, weightKg, today, zone)
        return GoalCelebration(
            goalId = goal.id,
            title = title,
            label = achievedLabel(goal),
            achievedValue = if (goal.targetValue.isFinite()) goal.targetValue.coerceAtLeast(0.0) else 0.0,
            daysTaken = result.daysTaken,
            daysEarly = result.daysEarly,
            kcal = result.kcal,
        )
    }

    /** One workout's contribution; distance in metres so swims add up exactly. */
    private fun amount(goal: Goal, w: Workout): Double = when (goal) {
        is DistanceGoal -> (w as? DistanceWorkout)?.distanceMeters ?: 0.0
        is StrengthVolumeGoal -> (w as? StrengthWorkout)?.totalVolumeKg() ?: 0.0
        is DurationGoal -> w.duration.toMinutes().toDouble()
        else -> 1.0
    }

    private fun clamp(day: LocalDate, first: LocalDate, last: LocalDate): LocalDate = when {
        day.isBefore(first) -> first
        day.isAfter(last) -> last
        else -> day
    }

    private const val METRES_PER_KM = 1_000.0
}
