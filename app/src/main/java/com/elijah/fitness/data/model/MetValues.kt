package com.elijah.fitness.data.model

/**
 * MET (metabolic equivalent of task) lookup used by [Workout.caloriesBurned].
 *
 * Values approximate the Compendium of Physical Activities (Ainsworth et al.),
 * grouped into speed bands for running and cycling. The exact numbers and
 * their citations go into the assignment report — every entry is marked
 * `TODO: verify against Compendium` so I remember to cross-check.
 */
object MetValues {

    /** MET for running at [kmh]. Returns 0 for non-positive speeds. */
    fun running(kmh: Double): Double = when {
        kmh <= 0.0 -> 0.0
        kmh < 8.0 -> 6.0    // TODO: verify against Compendium
        kmh < 9.7 -> 8.3    // TODO: verify against Compendium
        kmh < 11.3 -> 9.8   // TODO: verify against Compendium
        kmh < 12.9 -> 11.0  // TODO: verify against Compendium
        kmh < 14.5 -> 11.8  // TODO: verify against Compendium
        else -> 12.8        // TODO: verify against Compendium
    }

    /** MET for cycling at [kmh]. Returns 0 for non-positive speeds. */
    fun cycling(kmh: Double): Double = when {
        kmh <= 0.0 -> 0.0
        kmh < 16.0 -> 4.0   // TODO: verify against Compendium
        kmh < 19.0 -> 6.8   // TODO: verify against Compendium
        kmh < 22.0 -> 8.0   // TODO: verify against Compendium
        kmh < 25.0 -> 10.0  // TODO: verify against Compendium
        else -> 12.0        // TODO: verify against Compendium
    }

    /** MET for general resistance/weight training at moderate effort. */
    const val WEIGHTLIFTING_MODERATE: Double = 3.5   // TODO: verify against Compendium
}
