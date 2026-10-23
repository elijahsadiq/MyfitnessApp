package com.elijah.fitness.util

import android.content.Context
import com.elijah.fitness.R
import com.elijah.fitness.data.model.DistanceUnit

/**
 * Swim and row measures with their units (phase 8d): "1,500 m",
 * "2:09 /100 m", "2:35 /500 m". The number comes from [Formatters]; the
 * unit and word order come from `strings.xml` (`measure_metres`,
 * `measure_pace_per_100m`, `measure_pace_per_500m`), so they can be
 * translated and always match the detail screen.
 *
 * Same shape as [DurationFormatter]: the core takes a [TextResolver] so
 * unit tests read the real strings.xml.
 */
object MeasureText {

    /** Shown for a pace that can't be worked out (no distance or time). */
    private const val NO_PACE = "-"

    /** "1,500 m". */
    fun metres(text: TextResolver, meters: Double): String =
        text.string(R.string.measure_metres, Formatters.formatMetresValue(meters))

    /**
     * A distance in [unit]: "1,500 m" for [DistanceUnit.METRES], "5.20 km"
     * for [DistanceUnit.KILOMETRES]. The unit comes from the workout
     * ([com.elijah.fitness.data.model.DistanceWorkout.distanceUnit]) or a
     * total ([DistanceUnit.forTotal]), never from a class check.
     */
    fun distance(text: TextResolver, meters: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.METRES -> metres(text, meters)
        DistanceUnit.KILOMETRES -> Formatters.formatDistanceKm(meters)
    }

    /** "2:09 /100 m" from seconds per 100 m; "-" when there's no pace. */
    fun pacePer100m(text: TextResolver, secondsPer100m: Long): String =
        if (secondsPer100m <= 0L) {
            NO_PACE
        } else {
            text.string(R.string.measure_pace_per_100m, Formatters.formatPaceValue(secondsPer100m))
        }

    /** "2:35 /500 m" from seconds per 500 m; "-" when there's no pace. */
    fun pacePer500m(text: TextResolver, secondsPer500m: Long): String =
        if (secondsPer500m <= 0L) {
            NO_PACE
        } else {
            text.string(R.string.measure_pace_per_500m, Formatters.formatPaceValue(secondsPer500m))
        }

    fun metres(context: Context, meters: Double): String =
        metres(ResourcesTextResolver(context.resources), meters)

    fun distance(context: Context, meters: Double, unit: DistanceUnit): String =
        distance(ResourcesTextResolver(context.resources), meters, unit)

    fun pacePer100m(context: Context, secondsPer100m: Long): String =
        pacePer100m(ResourcesTextResolver(context.resources), secondsPer100m)

    fun pacePer500m(context: Context, secondsPer500m: Long): String =
        pacePer500m(ResourcesTextResolver(context.resources), secondsPer500m)
}
