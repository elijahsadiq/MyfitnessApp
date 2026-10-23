package com.elijah.fitness.ui.log

import com.elijah.fitness.R
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.util.CalorieEstimator
import com.elijah.fitness.util.Formatters
import com.elijah.fitness.util.TextResolver
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The maths and text of the Log a swim screen (phase 8d, LogSwim.dc.html).
 * Pure Kotlin (no Android imports), so it is unit-tested directly.
 *
 * Pace and calories come from a temporary [SwimmingWorkout] — the same
 * class the screen saves — so the live summary always shows exactly what
 * the saved swim will show: pace per 100 m through the class's own split
 * ([SwimmingWorkout.pacePer100mSeconds]) and calories through its MET
 * ([CalorieEstimator.workoutKcal]).
 */
object LogSwimCalculator {

    /** The pool lengths offered, in metres (segmented control order). */
    val POOL_LENGTHS_M: List<Int> = listOf(25, 50)

    const val DEFAULT_POOL_LENGTH_M: Int = 25
    const val DEFAULT_LAPS: Int = 60

    /** Laps the stepper allows: what the server accepts (1–400). */
    val LAPS_RANGE: IntRange = SwimmingWorkout.LAPS_RANGE

    /** What the dark summary card shows. */
    data class Summary(
        /** Laps × pool length. */
        val distanceMeters: Int,
        /** Seconds per 100 m, rounded; 0 while there is no duration yet. */
        val pacePer100mSeconds: Long,
        /** Calories at the profile weight, rounded; 0 while there is no duration yet. */
        val kcal: Int,
    )

    /** Laps × pool length in metres; never negative. */
    fun distanceMeters(laps: Int, poolLengthM: Int): Int =
        laps.coerceAtLeast(0) * poolLengthM.coerceAtLeast(0)

    /** One step of the laps stepper: [laps] + [direction], kept inside [LAPS_RANGE]. */
    fun stepLaps(laps: Int, direction: Int): Int =
        (laps + direction).coerceIn(LAPS_RANGE.first, LAPS_RANGE.last)

    /** The live summary for [laps] of a [poolLengthM] pool swum in [durationSeconds]. */
    fun summary(laps: Int, poolLengthM: Int, durationSeconds: Long, weightKg: Double): Summary {
        val metres = distanceMeters(laps, poolLengthM)
        if (durationSeconds <= 0L || metres <= 0) {
            return Summary(distanceMeters = metres, pacePer100mSeconds = 0L, kcal = 0)
        }
        val probe = SwimmingWorkout(
            id = PROBE_ID,
            startTime = Instant.EPOCH,
            duration = Duration.ofSeconds(durationSeconds),
            distanceMeters = metres.toDouble(),
            poolLengthM = poolLengthM,
            laps = laps,
        )
        return Summary(
            distanceMeters = metres,
            pacePer100mSeconds = probe.pacePer100mSeconds().coerceAtLeast(0L),
            kcal = if (weightKg.isFinite() && weightKg > 0.0) {
                CalorieEstimator.workoutKcal(probe, weightKg)
            } else {
                0
            },
        )
    }

    /** "2:09" for 129 s; "0:00" while there is no pace yet (the design's empty state). */
    fun paceText(secondsPer100m: Long, locale: Locale = Locale.getDefault()): String =
        if (secondsPer100m <= 0L) {
            String.format(locale, "%d:%02d", 0, 0)
        } else {
            Formatters.formatPaceValue(secondsPer100m, locale)
        }

    /** "60 × 25 m = 1,500 m", under the Laps label. */
    fun lapsHint(
        text: TextResolver,
        laps: Int,
        poolLengthM: Int,
        locale: Locale = Locale.getDefault(),
    ): String = text.string(
        R.string.log_swim_laps_hint,
        laps,
        poolLengthM,
        Formatters.formatMetresValue(distanceMeters(laps, poolLengthM).toDouble(), locale),
    )

    /** "Today, Mon 19 Oct", "Yesterday, Sun 18 Oct", else "Sat 17 Oct". */
    fun dateText(
        text: TextResolver,
        date: LocalDate,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String {
        val formatted = date.format(DateTimeFormatter.ofPattern(DATE_PATTERN, locale))
        return when (date) {
            today -> text.string(R.string.log_swim_date_today, formatted)
            today.minusDays(1) -> text.string(R.string.log_swim_date_yesterday, formatted)
            else -> formatted
        }
    }

    private const val PROBE_ID = "swim-probe"
    private const val DATE_PATTERN = "EEE d MMM"
}
