package com.elijah.fitness.data.model

/**
 * A [Goal] paired with its currently-observed progress, produced by
 * [GoalProgressCalculator]. The UI just reads the fields — no recompute
 * lives in view holders or ViewModels.
 *
 * @property currentValue matches [Goal.currentValue] against the latest
 *   workouts list (unclamped, so the featured card can render "37 / 20 km"
 *   after over-achievement).
 * @property progressFraction is `[0.0, 1.0]` clamped — safe to feed a
 *   progress ring / bar directly.
 * @property daysRemaining is `0` on the last day and negative for expired
 *   goals (the UI can decide to hide those in the "Completed" section).
 */
data class GoalWithProgress(
    val goal: Goal,
    val currentValue: Double,
    val progressFraction: Double,
    val isComplete: Boolean,
    val daysRemaining: Long,
)
