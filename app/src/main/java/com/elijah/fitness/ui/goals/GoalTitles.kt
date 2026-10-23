package com.elijah.fitness.ui.goals

import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.util.TextResolver

/**
 * The headline shown for a goal ("Run 50 km in 30 days"), rebuilt from
 * the goal's *current* fields every time.
 *
 * The title stored on the server is written once, when the goal is
 * created or edited. "Add 7 more days" only changes the number of days,
 * so the stored text went stale ("Run for 225 min in 13 days" next to
 * "17 days left"). Building it from the fields keeps every screen — list
 * rows, the main card, the Manage sheet, the delete dialog and the
 * snackbars — in step with the real goal.
 */
fun Goal.displayTitle(text: TextResolver): String = autoTitle(
    text = text,
    activity = NewGoalViewModel.ActivityOption.fromApi(activityType),
    metric = goalType(),
    // The stored Double, so a 10.5 km swim goal reads "Swim 10.5 km …".
    target = targetValue,
    days = durationDays,
)
