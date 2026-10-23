package com.elijah.fitness.util

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType

/**
 * The one place an [ActivityType] gets its user-facing name and icon
 * (phase 8d). Every screen asks here instead of keeping its own copy,
 * and the `when`s have no `else`, so a new activity doesn't compile
 * until it has both.
 */
object ActivityLabels {

    /** "Running", "Cycling", "Swimming", "Rowing", "Workout". */
    @StringRes
    fun nameRes(type: ActivityType): Int = when (type) {
        ActivityType.RUNNING -> R.string.activity_type_running
        ActivityType.CYCLING -> R.string.activity_type_cycling
        ActivityType.SWIMMING -> R.string.activity_type_swimming
        ActivityType.ROWING -> R.string.activity_type_rowing
        ActivityType.WEIGHTLIFTING -> R.string.activity_type_weightlifting
    }

    @DrawableRes
    fun iconRes(type: ActivityType): Int = when (type) {
        ActivityType.RUNNING -> R.drawable.ic_run_24
        ActivityType.CYCLING -> R.drawable.ic_bike_24
        ActivityType.SWIMMING -> R.drawable.ic_swim_24
        ActivityType.ROWING -> R.drawable.ic_row_24
        ActivityType.WEIGHTLIFTING -> R.drawable.ic_dumbbell_24
    }

    /**
     * The 2dp-stroke glyph the activities-v3 designs draw on the swipeable
     * activity cards (Log a workout sheet, New goal sheet), so all five
     * cards match. Swims and rows are stroke icons everywhere already;
     * the rest of the app keeps [iconRes].
     */
    @DrawableRes
    fun cardIconRes(type: ActivityType): Int = when (type) {
        ActivityType.RUNNING -> R.drawable.ic_run_outline_24
        ActivityType.CYCLING -> R.drawable.ic_bike_outline_24
        ActivityType.SWIMMING -> R.drawable.ic_swim_24
        ActivityType.ROWING -> R.drawable.ic_row_24
        ActivityType.WEIGHTLIFTING -> R.drawable.ic_dumbbell_outline_24
    }

    /**
     * What one session of [type] is called in a count goal: "runs",
     * "rides", "swims", "rows", "workouts" — and "workouts" too for a
     * goal that counts any activity (`null`), as on the New goal sheet.
     * Pick the form with `getQuantityString(…, count)`.
     */
    @PluralsRes
    fun sessionsUnitRes(type: ActivityType?): Int = when (type) {
        ActivityType.RUNNING -> R.plurals.new_goal_metric_unit_sessions_run
        ActivityType.CYCLING -> R.plurals.new_goal_metric_unit_sessions_ride
        ActivityType.SWIMMING -> R.plurals.new_goal_metric_unit_sessions_swim
        ActivityType.ROWING -> R.plurals.new_goal_metric_unit_sessions_row
        ActivityType.WEIGHTLIFTING -> R.plurals.new_goal_metric_unit_sessions_lift
        null -> R.plurals.new_goal_metric_unit_sessions_any
    }

    /**
     * [sessionsUnitRes] in the form that matches [count]: "1 swim",
     * "12 swims". A goal value is a Double; anything that isn't a
     * usable number reads as plural.
     */
    fun sessionsUnit(resources: Resources, type: ActivityType?, count: Double): String {
        val quantity = if (count.isFinite()) {
            count.toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        } else {
            0
        }
        return resources.getQuantityString(sessionsUnitRes(type), quantity)
    }
}
