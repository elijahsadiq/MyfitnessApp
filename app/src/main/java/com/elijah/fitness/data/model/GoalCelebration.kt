package com.elijah.fitness.data.model

/**
 * One goal-complete pop-up waiting to be shown (phase 8d, Step 7). Built
 * by [GoalCompletionStats.celebrationFor]; pure data, so it travels in
 * the dialog's arguments and survives a rotation.
 *
 * @property achievedValue the target reached, in the goal's unit:
 *   km, sessions, minutes or kg (read with [label]).
 * @property daysTaken completion date − start date + 1.
 * @property daysEarly last day − completion date (0 = "on the last day").
 * @property kcal energy burned by the workouts that counted.
 */
data class GoalCelebration(
    val goalId: String,
    val title: String,
    val label: GoalCompletionStats.AchievedLabel,
    val achievedValue: Double,
    val daysTaken: Int,
    val daysEarly: Int,
    val kcal: Double,
)
