package com.elijah.fitness.util

import android.content.Context
import com.elijah.fitness.R
import java.time.Duration

/**
 * Turns a number of minutes into readable text (phase 8d): "45 min",
 * "2 h", "2 h 30 min" — never "150 min". The words come from
 * `strings.xml` (`duration_minutes`, `duration_hours`,
 * `duration_hours_minutes`, `duration_seconds`) so they can be
 * translated.
 *
 * The pure core ([split] and [format] with a [TextResolver]) has no
 * Android dependency, so unit tests call it directly.
 */
object DurationFormatter {

    /** Whole hours and the minutes left over. */
    data class Parts(val hours: Long, val minutes: Long)

    /** 150 → 2 h 30 min. Negative input counts as 0. */
    fun split(totalMinutes: Long): Parts {
        val safe = totalMinutes.coerceAtLeast(0L)
        return Parts(hours = safe / 60L, minutes = safe % 60L)
    }

    /** "45 min", "2 h" or "2 h 30 min". */
    fun format(text: TextResolver, totalMinutes: Long): String {
        val (hours, minutes) = split(totalMinutes)
        return when {
            hours == 0L -> text.string(R.string.duration_minutes, minutes)
            minutes == 0L -> text.string(R.string.duration_hours, hours)
            else -> text.string(R.string.duration_hours_minutes, hours, minutes)
        }
    }

    /**
     * A workout's [duration]: "32 min", "1 h 5 min". Under a minute it
     * reads in seconds ("45 s") rather than a confusing "0 min".
     */
    fun format(text: TextResolver, duration: Duration): String {
        val seconds = duration.seconds.coerceAtLeast(0L)
        return if (seconds < 60L) {
            text.string(R.string.duration_seconds, seconds)
        } else {
            format(text, seconds / 60L)
        }
    }

    fun format(context: Context, totalMinutes: Long): String =
        format(ResourcesTextResolver(context.resources), totalMinutes)

    fun format(context: Context, duration: Duration): String =
        format(ResourcesTextResolver(context.resources), duration)
}
