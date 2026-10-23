package com.elijah.fitness.ui.log

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.util.CalorieEstimator
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Pure-Kotlin live-summary helper for the manual log form (phase 8c
 * step 5; swims and rows since phase 8d). The fragment calls
 * [calculate] on every text change and renders the result into the
 * dark summary card.
 *
 * ### Reuse of the domain calorie model
 *
 * Calories go through [CalorieEstimator.workoutKcal] by **building a
 * temporary workout of the right class — `RunningWorkout`,
 * `CyclingWorkout`, `SwimmingWorkout` or `RowingWorkout` — and asking
 * it** (each class supplies its own MET through polymorphism). That
 * keeps the formula in one place, gives the same number the saved
 * workout stores, uses the profile weight (or the 70 kg fallback the
 * caller passes), and keeps this calculator free of Android imports so
 * it stays JVM-testable. The split pace comes from the same probe: per
 * km for runs and rides, per 500 m for rows, per 100 m for swims.
 */
object LogDistanceSummaryCalculator {

    /**
     * The three numbers the dark summary card shows. `null` fields are
     * treated as "—" by the fragment; the whole object is `null` when
     * the inputs are incomplete (zero duration or zero / missing
     * distance), which triggers the "Add a time and distance…" copy.
     */
    data class Summary(
        /** Avg-pace in seconds per kilometre, rounded to the nearest second. */
        val paceSecondsPerKm: Int,
        /** Avg speed, km / h. */
        val speedKmh: Double,
        /** Rounded calories, kcal. */
        val calories: Int,
        /**
         * Pace over the activity's own split (phase 8d): per km for runs,
         * per 500 m for rows, per 100 m for swims. Rounded to the
         * nearest second, 0 below 10 m.
         */
        val splitPaceSeconds: Int = paceSecondsPerKm,
    )

    /**
     * Compute pace + speed + calories for a (possibly in-progress)
     * manual-log entry. Returns `null` when either input is zero or
     * missing — the caller renders dashes in that case.
     *
     * @param durationSeconds total duration in whole seconds. Negative
     *   values are treated as zero.
     * @param distanceKm distance in km. `null` or non-positive counts
     *   as empty.
     * @param activity picks the workout class, so the calories use its
     *   MET and the split pace its distance (running, cycling, swimming,
     *   rowing). A lift has no distance summary: `null`.
     * @param bodyWeightKg profile weight (the 70 kg fallback while the
     *   profile has none).
     */
    fun calculate(
        durationSeconds: Long,
        distanceKm: Double?,
        activity: ActivityType,
        bodyWeightKg: Double,
    ): Summary? {
        if (durationSeconds <= 0L) return null
        val km = distanceKm ?: return null
        if (km <= 0.0) return null

        val duration = Duration.ofSeconds(durationSeconds)
        val meters = km * 1_000.0
        val hoursDecimal = durationSeconds / 3_600.0
        val speedKmh = km / hoursDecimal
        // Rounded to the nearest second, like every saved workout's pace.
        val paceSecondsPerKm = (durationSeconds.toDouble() / km).roundToInt()
            .coerceAtLeast(0)

        // Dummy startTime is fine — `caloriesBurned` only reads duration
        // + MET + weight; `startTime` doesn't participate.
        val probeStart = Instant.EPOCH
        val probe = when (activity) {
            ActivityType.RUNNING -> RunningWorkout(
                id = "probe", startTime = probeStart,
                duration = duration, distanceMeters = meters, notes = null,
            )
            ActivityType.CYCLING -> CyclingWorkout(
                id = "probe", startTime = probeStart,
                duration = duration, distanceMeters = meters, notes = null,
            )
            ActivityType.SWIMMING -> SwimmingWorkout(
                id = "probe", startTime = probeStart,
                duration = duration, distanceMeters = meters, notes = null,
            )
            ActivityType.ROWING -> RowingWorkout(
                id = "probe", startTime = probeStart,
                duration = duration, distanceMeters = meters, notes = null,
            )
            ActivityType.WEIGHTLIFTING -> return null
        }
        // Same number the workout stores when it's saved.
        val calories = CalorieEstimator.workoutKcal(probe, bodyWeightKg)
        // Same rounded split pace the saved workout shows (2:09 /100 m
        // for 1,500 m in 32:10), straight from the class's own split.
        val splitPace = probe.paceSecondsPerSplit().toInt().coerceAtLeast(0)

        return Summary(
            paceSecondsPerKm = paceSecondsPerKm,
            speedKmh = speedKmh,
            calories = calories,
            splitPaceSeconds = splitPace,
        )
    }
}
