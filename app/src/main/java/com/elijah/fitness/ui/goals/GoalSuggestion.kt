package com.elijah.fitness.ui.goals

import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.data.model.GoalType

/**
 * One of the three Quick Start rows on the empty Goals screen
 * (phase 8b step 8). Each suggestion is a self-contained seed for the
 * New Goal screen — tapping a row opens `NewGoalFragment` pre-filled.
 */
data class GoalSuggestion(
    val id: String,
    val activity: NewGoalViewModel.ActivityOption,
    val metric: GoalType,
    val target: Int,
    val days: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val subtitleArg: String,
) {
    companion object {
        /** The exact three rows shown on `Empty.dc.html`. */
        val DEFAULTS: List<GoalSuggestion> = listOf(
            GoalSuggestion(
                id = "quick_run_20_14",
                activity = NewGoalViewModel.ActivityOption.RUN,
                metric = GoalType.DISTANCE,
                target = 20,
                days = 14,
                titleRes = R.string.goals_quick_start_run_title,
                subtitleRes = R.string.goals_quick_start_subtitle_distance,
                subtitleArg = "1.4",
            ),
            GoalSuggestion(
                id = "quick_ride_100_30",
                activity = NewGoalViewModel.ActivityOption.RIDE,
                metric = GoalType.DISTANCE,
                target = 100,
                days = 30,
                titleRes = R.string.goals_quick_start_ride_title,
                subtitleRes = R.string.goals_quick_start_subtitle_distance,
                subtitleArg = "3.3",
            ),
            GoalSuggestion(
                id = "quick_lift_150_7",
                activity = NewGoalViewModel.ActivityOption.LIFT,
                metric = GoalType.DURATION,
                target = 150,
                days = 7,
                titleRes = R.string.goals_quick_start_lift_title,
                subtitleRes = R.string.goals_quick_start_subtitle_time,
                subtitleArg = "21",
            ),
        )
    }
}
