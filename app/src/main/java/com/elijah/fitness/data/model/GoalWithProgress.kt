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
    /**
     * `true` once the goal's period has ended (`daysRemaining < 0`). The
     * UI treats this as a third goal state alongside active + completed:
     * expired goals move to their own "Ended" section and are never
     * eligible for the featured card. Phase 7 deliberately excluded
     * auto-recurring goals, so expiry is the terminal state for an
     * uncompleted goal.
     */
    val isExpired: Boolean = false,
)
