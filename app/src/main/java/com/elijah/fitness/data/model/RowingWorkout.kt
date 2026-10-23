package com.elijah.fitness.data.model

import java.time.Duration
import java.time.Instant

/**
 * A completed row (phase 8d), on the water or on a machine. A
 * [DistanceWorkout], so it inherits the distance, speed and pace maths
 * and counts toward distance goals. It can carry a GPS route like a run
 * or a ride.
 *
 * Screens show the distance in km and the pace per 500 m
 * ("2:35 /500 m"), the standard rowing split.
 */
class RowingWorkout(
    id: String,
    startTime: Instant,
    duration: Duration,
    distanceMeters: Double,
    notes: String? = null,
    caloriesKcal: Int? = null,
) : DistanceWorkout(id, startTime, duration, notes, distanceMeters, caloriesKcal) {

    /** Overrides [Workout.type]: marks this workout as a row for the mappers, filters and labels. */
    override val type: ActivityType = ActivityType.ROWING

    /**
     * Overrides [Workout.metValue] with one rowing MET (7.0) for every
     * pace, like swimming.
     */
    override fun metValue(): Double = MetValues.ROWING

    /** Overrides [DistanceWorkout.paceSplitMeters]: rowers quote pace per 500 m. */
    override val paceSplitMeters: Double = 500.0

    /** Seconds per 500 m, e.g. 155 for "2:35 /500 m". 0 when there is no pace. */
    fun pacePer500mSeconds(): Long = paceSecondsPerSplit()
}
