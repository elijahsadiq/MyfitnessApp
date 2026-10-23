package com.elijah.fitness.data.model

/**
 * The unit a distance is shown in (phase 8d). Each [DistanceWorkout]
 * answers it through [DistanceWorkout.distanceUnit]: kilometres by
 * default, metres for a swim ("1,500 m"). Screens read it instead of
 * checking the workout's class.
 */
enum class DistanceUnit {
    KILOMETRES,
    METRES;

    companion object {
        /**
         * The unit for a total over several [workouts] (a History section,
         * a day on Home): metres only when every one of them is shown in
         * metres, otherwise kilometres. An empty list reads in kilometres.
         */
        fun forTotal(workouts: List<DistanceWorkout>): DistanceUnit =
            if (workouts.isNotEmpty() && workouts.all { it.distanceUnit == METRES }) METRES else KILOMETRES
    }
}
