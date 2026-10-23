package com.elijah.fitness.util

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.Workout
import kotlin.math.roundToInt

/**
 * The one place calories are estimated (phase 8d). Formula everywhere:
 * `kcal = MET × body-weight-kg × hours`.
 *
 * - [workoutKcal]: a logged workout. Uses its real duration and the MET
 *   its class picks ([Workout.caloriesBurned], polymorphism).
 * - [storedWorkoutKcal]: what a save path stores with the workout —
 *   [workoutKcal] at the weight to save with, or `null` when the profile
 *   hasn't loaded yet.
 * - [goalEstimateKcal]: what a goal will roughly burn, for the New goal
 *   summary. A goal has no real duration yet, so the hours come from a
 *   typical speed / session length per activity (table below).
 *
 * | Activity | MET | Typical speed | Typical session |
 * |---|---|---|---|
 * | Running  | 9.8 | 10 km/h  | 0.5 h  |
 * | Cycling  | 7.5 | 20 km/h  | 1 h    |
 * | Swimming | 7.0 | 2.5 km/h | 0.75 h |
 * | Rowing   | 7.0 | 8 km/h   | 0.5 h  |
 * | Workout  | 5.0 | —        | 1 h    |
 * | Any      | 6.0 | —        | 0.75 h |
 *
 * Pure Kotlin (no Android imports) so it is unit-tested directly.
 */
object CalorieEstimator {

    /** Highest value stored with a workout (the server's limit). */
    const val MAX_WORKOUT_KCAL: Int = 20_000

    /** Weight lifted per "hour" of training for a weight goal. */
    private const val KG_LIFTED_PER_HOUR: Double = 5_000.0

    /**
     * Calories for a logged [workout] at [weightKg], rounded to a whole
     * kcal and kept in `0..`[MAX_WORKOUT_KCAL].
     */
    fun workoutKcal(workout: Workout, weightKg: Double): Int {
        val raw = workout.caloriesBurned(weightKg)
        if (!raw.isFinite() || raw <= 0.0) return 0
        return raw.roundToInt().coerceIn(0, MAX_WORKOUT_KCAL)
    }

    /**
     * Calories to store with a workout that is being saved, at
     * [weightKg] (`UserSettings.weightForSavingKgOrNull`: the profile
     * weight, or 70 kg when the loaded profile has none). `null` when
     * [weightKg] is `null` or unusable (the profile hasn't loaded yet):
     * the workout then keeps no stored value and screens work it out
     * from the current weight ([Workout.energyKcal]).
     */
    fun storedWorkoutKcal(workout: Workout, weightKg: Double?): Int? {
        val weight = weightKg?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return workoutKcal(workout, weight)
    }

    /**
     * Rough calories a goal will burn: [target] of [goalType] for
     * [activity] (`null` = any activity) at [weightKg]. Rounded to the
     * nearest 10 kcal. 0 for a target, weight or combination that can't
     * be estimated (e.g. distance with no typical speed).
     */
    fun goalEstimateKcal(
        activity: ActivityType?,
        goalType: GoalType,
        target: Double,
        weightKg: Double,
    ): Int {
        if (!target.isFinite() || target <= 0.0) return 0
        if (!weightKg.isFinite() || weightKg <= 0.0) return 0
        val profile = profileFor(activity)
        val hours = when (goalType) {
            GoalType.DISTANCE -> profile.speedKmh?.let { target / it } ?: 0.0
            GoalType.DURATION -> target / 60.0
            GoalType.COUNT -> target * profile.sessionHours
            GoalType.VOLUME -> target / KG_LIFTED_PER_HOUR
        }
        val kcal = profile.met * weightKg * hours
        if (!kcal.isFinite() || kcal <= 0.0) return 0
        return (kcal / 10.0).roundToInt() * 10
    }

    /** MET, typical speed (km/h, null when distance doesn't apply) and session length (h). */
    private data class Profile(val met: Double, val speedKmh: Double?, val sessionHours: Double)

    private fun profileFor(activity: ActivityType?): Profile = when (activity) {
        ActivityType.RUNNING -> Profile(met = 9.8, speedKmh = 10.0, sessionHours = 0.5)
        ActivityType.CYCLING -> Profile(met = 7.5, speedKmh = 20.0, sessionHours = 1.0)
        ActivityType.SWIMMING -> Profile(met = 7.0, speedKmh = 2.5, sessionHours = 0.75)
        ActivityType.ROWING -> Profile(met = 7.0, speedKmh = 8.0, sessionHours = 0.5)
        ActivityType.WEIGHTLIFTING -> Profile(met = 5.0, speedKmh = null, sessionHours = 1.0)
        null -> Profile(met = 6.0, speedKmh = null, sessionHours = 0.75)
    }
}
