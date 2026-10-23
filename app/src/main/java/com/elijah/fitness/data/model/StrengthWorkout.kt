package com.elijah.fitness.data.model

import java.time.Duration
import java.time.Instant

/**
 * A single set of a strength exercise. `data class` gives us equality,
 * `hashCode`, `toString` and `copy` for free.
 */
data class StrengthSet(
    val exercise: String,
    val reps: Int,
    val weightKg: Double
)

/**
 * A completed weightlifting session, made up of one or more [StrengthSet]s.
 * Uses [MetValues.WEIGHTLIFTING_MODERATE] regardless of intensity in phase 1.
 * Shown to the user as "Workout" (phase 8d); the type stays WEIGHTLIFTING.
 */
class StrengthWorkout(
    id: String,
    startTime: Instant,
    duration: Duration,
    val sets: List<StrengthSet>,
    notes: String? = null,
    caloriesKcal: Int? = null,
) : Workout(id, startTime, duration, notes, caloriesKcal) {

    /** Overrides [Workout.type]: marks this workout as a lifting session ("Workout") for the mappers and filters. */
    override val type: ActivityType = ActivityType.WEIGHTLIFTING

    /** Overrides [Workout.metValue]: one moderate-effort MET, there is no speed to band on. */
    override fun metValue(): Double = MetValues.WEIGHTLIFTING_MODERATE

    /** Sum of `reps × weight` across every set. */
    fun totalVolumeKg(): Double = sets.sumOf { it.reps * it.weightKg }

    /**
     * Epley 1RM: `weight × (1 + reps / 30)`. Returns the highest estimate
     * across all sets of [exercise] (case-insensitive match), or `null` if
     * no set of that exercise is present.
     */
    fun estimatedOneRepMaxKg(exercise: String): Double? {
        val relevant = sets.filter { it.exercise.equals(exercise, ignoreCase = true) }
        if (relevant.isEmpty()) return null
        return relevant.maxOf { it.weightKg * (1.0 + it.reps / 30.0) }
    }

    /** Overrides [Workout.equals] to compare the sets as well. */
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is StrengthWorkout) return false
        return sets == other.sets
    }

    /** Overrides [Workout.hashCode] to match [equals]. */
    override fun hashCode(): Int =
        31 * super.hashCode() + sets.hashCode()
}
