package com.elijah.fitness.data.model

/**
 * MET (metabolic equivalent of task) lookup used by [Workout.caloriesBurned].
 *
 * Values approximate the Compendium of Physical Activities (Ainsworth et al.),
 * grouped into speed bands for running and cycling. The exact numbers and
 * their citations go into the assignment report. Verified against the 2011
 * Adult Compendium: running codes 12020-12070, cycling 01010-01050,
 * resistance training (multiple exercises, 8-15 reps) 02054.
 */
object MetValues {

    /** MET for running at [kmh]. Returns 0 for non-positive speeds. */
    fun running(kmh: Double): Double = when {
        kmh <= 0.0 -> 0.0
        kmh < 8.0 -> 6.0
        kmh < 9.7 -> 8.3
        kmh < 11.3 -> 9.8
        kmh < 12.9 -> 11.0
        kmh < 14.5 -> 11.8
        else -> 12.8
    }

    /** MET for cycling at [kmh]. Returns 0 for non-positive speeds. */
    fun cycling(kmh: Double): Double = when {
        kmh <= 0.0 -> 0.0
        kmh < 16.0 -> 4.0
        kmh < 19.0 -> 6.8
        kmh < 22.0 -> 8.0
        kmh < 25.0 -> 10.0
        else -> 12.0
    }

    /** MET for general resistance/weight training at moderate effort. */
    const val WEIGHTLIFTING_MODERATE: Double = 3.5

    /**
     * Swimming laps at a light to moderate effort, as listed in the
     * Compendium. One value for every pace: swims are logged after the
     * session, so there is no live speed to band on.
     */
    const val SWIMMING: Double = 7.0

    /**
     * Rowing (boat or machine) at a moderate effort, as listed in the
     * Compendium. One value for every pace, like [SWIMMING].
     */
    const val ROWING: Double = 7.0
}
