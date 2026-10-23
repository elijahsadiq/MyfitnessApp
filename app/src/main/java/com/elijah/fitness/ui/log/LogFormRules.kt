package com.elijah.fitness.ui.log

import androidx.annotation.StringRes
import com.elijah.fitness.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The checks every log form shares (run / ride / row, swim, workout):
 * the start can't be in the future, and the duration's minutes and
 * seconds must be 0–59 with a total above zero and no more than 24 h
 * (the server's limit). One place, so the forms can't drift apart. Pure
 * Kotlin, unit-tested.
 */
object LogFormRules {

    /** Highest minutes or seconds value a duration field accepts. */
    private const val MAX_MINUTES_OR_SECONDS = 59

    /**
     * Longest duration the server accepts (`WorkoutController`:
     * `durationSeconds` 1–86,400). Anything longer would save on the
     * phone, then be refused on upload and never sync.
     */
    const val MAX_DURATION_SECONDS: Long = 86_400L

    /** When the workout started, from the Date and Start time cards. */
    fun startInstant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        LocalDateTime.of(date, time).atZone(zone).toInstant()

    /** Whole seconds of an h / min / s entry (no range check). */
    fun durationSeconds(hours: Int, minutes: Int, seconds: Int): Long =
        hours * 3_600L + minutes * 60L + seconds.toLong()

    /** The error to show under the Date card, or `null` when [start] isn't after [now]. */
    @StringRes
    fun startError(start: Instant, now: Instant): Int? =
        if (start.isAfter(now)) R.string.log_error_date_in_future else null

    /**
     * The error to show under the duration cells, or `null` when the
     * entry is valid. 75 minutes is not a valid "minutes" entry (it's
     * 1 h 15 min), so values are checked, not just capped. More than
     * 24 h (say "32 h" typed for 32 min) is refused here rather than by
     * the server later.
     */
    @StringRes
    fun durationError(hours: Int, minutes: Int, seconds: Int): Int? = when {
        hours < 0 ||
            minutes !in 0..MAX_MINUTES_OR_SECONDS ||
            seconds !in 0..MAX_MINUTES_OR_SECONDS -> R.string.log_error_duration_out_of_range
        durationSeconds(hours, minutes, seconds) <= 0L -> R.string.log_error_duration_zero
        durationSeconds(hours, minutes, seconds) > MAX_DURATION_SECONDS ->
            R.string.log_error_duration_too_long
        else -> null
    }
}
