package com.elijah.fitness.ui.log

import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType

/** How a run, ride or row is logged from the sheet. */
enum class LogMode { LIVE, MANUAL }

/** Where the sheet's lime button goes. */
enum class LogDestination {
    /** GPS tracking screen + foreground service (run, ride, row). */
    LIVE_TRACKING,

    /** Manual distance form (run, ride, row). */
    MANUAL_DISTANCE,

    /** Log a swim. */
    SWIM,

    /** Sets and reps form. */
    STRENGTH,
}

/**
 * The rules of the Log a workout sheet (phase 8d, LogSheet.dc.html) as
 * plain Kotlin, so they can be unit-tested: which activities it offers
 * and in what order, which ones can use GPS, what the lime button says
 * and where it goes.
 *
 * Every `when` lists all activities with no `else`, so a new activity
 * won't compile until the sheet knows what to do with it.
 */
object LogSheetOptions {

    /** Card order on the sheet. */
    val activities: List<ActivityType> = listOf(
        ActivityType.RUNNING,
        ActivityType.CYCLING,
        ActivityType.SWIMMING,
        ActivityType.ROWING,
        ActivityType.WEIGHTLIFTING,
    )

    /** Order of the "Live with GPS" / "Add manually" segments. */
    val modes: List<LogMode> = listOf(LogMode.LIVE, LogMode.MANUAL)

    /** The card the sheet opens on: the last activity logged, else Running. */
    fun initialIndex(remembered: ActivityType?): Int =
        activities.indexOf(remembered).takeIf { it >= 0 } ?: 0

    /** Runs, rides and rows can be tracked live; swims and workouts can't. */
    fun supportsGps(type: ActivityType): Boolean = when (type) {
        ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.ROWING -> true
        ActivityType.SWIMMING, ActivityType.WEIGHTLIFTING -> false
    }

    /** The line under the card name. */
    @StringRes
    fun descriptionRes(type: ActivityType): Int = when (type) {
        ActivityType.RUNNING -> R.string.log_sheet_card_running
        ActivityType.CYCLING -> R.string.log_sheet_card_cycling
        ActivityType.SWIMMING -> R.string.log_sheet_card_swimming
        ActivityType.ROWING -> R.string.log_sheet_card_rowing
        ActivityType.WEIGHTLIFTING -> R.string.log_sheet_card_workout
    }

    /** The note shown instead of the GPS / manual choice, or `null` when the choice shows. */
    @StringRes
    fun noteRes(type: ActivityType): Int? = when (type) {
        ActivityType.SWIMMING -> R.string.log_sheet_note_swim
        ActivityType.WEIGHTLIFTING -> R.string.log_sheet_note_workout
        ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.ROWING -> null
    }

    /** The lime button's label: "Start run with GPS", "Add a swim", … */
    @StringRes
    fun ctaLabelRes(type: ActivityType, mode: LogMode): Int = when (type) {
        ActivityType.RUNNING ->
            if (mode == LogMode.LIVE) R.string.log_sheet_cta_run_live else R.string.log_sheet_cta_run_manual
        ActivityType.CYCLING ->
            if (mode == LogMode.LIVE) R.string.log_sheet_cta_ride_live else R.string.log_sheet_cta_ride_manual
        ActivityType.ROWING ->
            if (mode == LogMode.LIVE) R.string.log_sheet_cta_row_live else R.string.log_sheet_cta_row_manual
        ActivityType.SWIMMING -> R.string.log_sheet_cta_swim
        ActivityType.WEIGHTLIFTING -> R.string.log_sheet_cta_workout
    }

    /** Where the lime button goes. [mode] only matters for GPS activities. */
    fun destination(type: ActivityType, mode: LogMode): LogDestination = when (type) {
        ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.ROWING ->
            if (mode == LogMode.LIVE) LogDestination.LIVE_TRACKING else LogDestination.MANUAL_DISTANCE
        ActivityType.SWIMMING -> LogDestination.SWIM
        ActivityType.WEIGHTLIFTING -> LogDestination.STRENGTH
    }
}

/**
 * What the sheet shows: the selected card and the GPS / manual choice.
 * The choice is kept while the user swipes, as in the design, and only
 * used by the activities that support GPS.
 */
data class LogSheetUiState(
    val selectedIndex: Int,
    val mode: LogMode = LogMode.LIVE,
) {
    val activity: ActivityType
        get() = LogSheetOptions.activities[selectedIndex.coerceIn(0, LogSheetOptions.activities.lastIndex)]

    val showsModeChoice: Boolean get() = LogSheetOptions.supportsGps(activity)

    @get:StringRes
    val noteRes: Int? get() = LogSheetOptions.noteRes(activity)

    @get:StringRes
    val ctaLabelRes: Int get() = LogSheetOptions.ctaLabelRes(activity, mode)

    val destination: LogDestination get() = LogSheetOptions.destination(activity, mode)
}
