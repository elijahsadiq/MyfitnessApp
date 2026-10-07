package com.elijah.fitness.data.model

/**
 * A [Goal] paired with its currently-observed progress, produced by
 * [GoalProgressCalculator]. The UI reads the fields directly — no
 * recompute lives in view holders or ViewModels.
 *
 * Phase-8b: carries everything the Goals list AND the Manage sheet
 * need, so neither surface has to redo clock-dependent arithmetic.
 *
 * @property status one of [Goal.Status]; the UI partitions the list on this.
 * @property currentValue measured value against the latest workouts list
 *   (unclamped, so the Manage sheet can show "37 / 20 km" after over-
 *   achievement).
 * @property progressFraction `[0.0, 1.0]`, clamped — safe to feed a bar
 *   or ring directly.
 * @property daysLeft inclusive of today (1 = "Last day", 0 = ended
 *   yesterday). Driven by the goal's `lastDay` and the calculator's clock.
 * @property averagePerDay current value / days elapsed (counting today).
 * @property neededPerDay target remaining / days left (0 once complete
 *   or once the window has closed).
 * @property sessionsLogged count of matching workouts in the goal's days.
 */
data class GoalWithProgress(
    val goal: Goal,
    val status: Goal.Status,
    val currentValue: Double,
    val progressFraction: Double,
    val daysLeft: Long,
    val averagePerDay: Double,
    val neededPerDay: Double,
    val sessionsLogged: Int,
) {
    /** Convenience so call sites don't have to spell out `status == …`. */
    val isActive: Boolean get() = status == Goal.Status.Active
    val isCompleted: Boolean get() = status == Goal.Status.Completed
    val isEnded: Boolean get() = status == Goal.Status.Ended
    val isMain: Boolean get() = goal.isMain
}
