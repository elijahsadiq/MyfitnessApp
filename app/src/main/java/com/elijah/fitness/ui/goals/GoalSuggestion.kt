package com.elijah.fitness.ui.goals

import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.util.TextResolver
import java.util.Locale

/**
 * One of the three Quick Start rows on the empty Goals screen
 * (phase 8b step 8). Each suggestion is a self-contained seed for the
 * New goal sheet — tapping a row opens `NewGoalBottomSheet` pre-filled.
 */
data class GoalSuggestion(
    val id: String,
    val activity: NewGoalViewModel.ActivityOption,
    val metric: GoalType,
    val target: Double,
    val days: Int,
) {
    /**
     * The row's title: exactly the sentence of the goal it creates
     * ("Run 20 km in 14 days", "Work out for 2 h 30 min in 7 days"), so
     * the row and the sheet it opens always agree.
     */
    fun title(text: TextResolver, locale: Locale = Locale.getDefault()): String =
        autoTitle(text, activity, metric, target, days, locale)

    /**
     * The row's subtitle: the same daily pace the New goal sheet shows
     * under its target ("About 1.4 km a day", "About 21 min a day"),
     * worked out from [target] and [days] in the device's locale.
     */
    fun subtitle(text: TextResolver, locale: Locale = Locale.getDefault()): String =
        NewGoalPlan.targetHint(text, metric, target, days, locale)

    companion object {
        /** The exact three rows shown on `Empty.dc.html`. */
        val DEFAULTS: List<GoalSuggestion> = listOf(
            GoalSuggestion(
                id = "quick_run_20_14",
                activity = NewGoalViewModel.ActivityOption.RUN,
                metric = GoalType.DISTANCE,
                target = 20.0,
                days = 14,
            ),
            GoalSuggestion(
                id = "quick_ride_100_30",
                activity = NewGoalViewModel.ActivityOption.RIDE,
                metric = GoalType.DISTANCE,
                target = 100.0,
                days = 30,
            ),
            GoalSuggestion(
                id = "quick_lift_150_7",
                activity = NewGoalViewModel.ActivityOption.LIFT,
                metric = GoalType.DURATION,
                target = 150.0,
                days = 7,
            ),
        )
    }
}
