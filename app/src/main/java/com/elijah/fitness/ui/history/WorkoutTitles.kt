package com.elijah.fitness.ui.history

import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Workout
import java.time.ZoneId

/**
 * "Morning run", "Evening swim", "Night workout" … — the time-of-day
 * title shared by the History / Home rows and the workout detail screen,
 * so a workout has the same name everywhere.
 */
object WorkoutTitles {

    @StringRes
    fun timeOfDayTitleRes(workout: Workout, zone: ZoneId = ZoneId.systemDefault()): Int {
        val titles = when (workout.type) {
            ActivityType.RUNNING -> RUN_TITLES
            ActivityType.CYCLING -> RIDE_TITLES
            ActivityType.SWIMMING -> SWIM_TITLES
            ActivityType.ROWING -> ROW_TITLES
            ActivityType.WEIGHTLIFTING -> WORKOUT_TITLES
        }
        return titles[slotOf(workout.startTime.atZone(zone).hour)]
    }

    /** 0 morning (5–11), 1 afternoon (12–16), 2 evening (17–20), 3 night. */
    internal fun slotOf(hour: Int): Int = when (hour) {
        in 5..11 -> 0
        in 12..16 -> 1
        in 17..20 -> 2
        else -> 3
    }

    // Morning, afternoon, evening, night — same order as slotOf.
    private val RUN_TITLES = intArrayOf(
        R.string.detail_time_morning_run, R.string.detail_time_afternoon_run,
        R.string.detail_time_evening_run, R.string.detail_time_night_run,
    )
    private val RIDE_TITLES = intArrayOf(
        R.string.detail_time_morning_ride, R.string.detail_time_afternoon_ride,
        R.string.detail_time_evening_ride, R.string.detail_time_night_ride,
    )
    private val SWIM_TITLES = intArrayOf(
        R.string.detail_time_morning_swim, R.string.detail_time_afternoon_swim,
        R.string.detail_time_evening_swim, R.string.detail_time_night_swim,
    )
    private val ROW_TITLES = intArrayOf(
        R.string.detail_time_morning_row, R.string.detail_time_afternoon_row,
        R.string.detail_time_evening_row, R.string.detail_time_night_row,
    )
    private val WORKOUT_TITLES = intArrayOf(
        R.string.detail_time_morning_workout, R.string.detail_time_afternoon_workout,
        R.string.detail_time_evening_workout, R.string.detail_time_night_workout,
    )
}
