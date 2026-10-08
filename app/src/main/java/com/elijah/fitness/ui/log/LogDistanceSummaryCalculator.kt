package com.elijah.fitness.ui.log

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import java.time.Duration
import java.time.Instant

/**
 * Pure-Kotlin live-summary helper for the manual log form (phase 8c
 * step 5). The fragment calls [calculate] on every text change and
 * renders the result into the dark summary card.
 *
 * ### Reuse of the domain calorie model
 *
 * Calories go through the existing `Workout.caloriesBurned(kg)`
 * formula by **building a temporary `RunningWorkout` or
 * `CyclingWorkout` and asking it**. That keeps the formula in one
 * place (`Workout.kt`), uses the real profile weight (never the
 * design's placeholder 70 kg), and keeps this calculator free of
 * Android imports so it stays JVM-testable.
 */
object LogDistanceSummaryCalculator {

    /**
     * The three numbers the dark summary card shows. `null` fields are
     * treated as "—" by the fragment; the whole object is `null` when
     * the inputs are incomplete (zero duration or zero / missing
     * distance), which triggers the "Add a time and distance…" copy.
     */
    data class Summary(
        /** Avg-pace in whole seconds per kilometre. */
        val paceSecondsPerKm: Int,
        /** Avg speed, km / h. */
        val speedKmh: Double,
        /** Rounded calories, kcal. */
        val calories: Int,
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
     * @param activity routes the calorie calculation to the right MET
     *   value (running vs cycling).
     * @param bodyWeightKg profile weight — never the design's 70 kg
     *   placeholder.
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
        val paceSecondsPerKm = (durationSeconds.toDouble() / km).toInt()
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
            ActivityType.WEIGHTLIFTING -> return null
        }
        val calories = probe.caloriesBurned(bodyWeightKg).toInt().coerceAtLeast(0)

        return Summary(
            paceSecondsPerKm = paceSecondsPerKm,
            speedKmh = speedKmh,
            calories = calories,
        )
    }
}
