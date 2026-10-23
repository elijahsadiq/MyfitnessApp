package com.elijah.fitness.data.model

import java.time.Duration
import java.time.Instant

/**
 * A completed bike ride. MET is derived from average speed via
 * [MetValues.cycling].
 */
class CyclingWorkout(
    id: String,
    startTime: Instant,
    duration: Duration,
    distanceMeters: Double,
    notes: String? = null,
    caloriesKcal: Int? = null,
) : DistanceWorkout(id, startTime, duration, notes, distanceMeters, caloriesKcal) {

    /** Overrides [Workout.type]: marks this workout as a ride for the mappers, filters and labels. */
    override val type: ActivityType = ActivityType.CYCLING

    /** Overrides [Workout.metValue]: cycling MET depends on speed, so it uses the speed bands. */
    override fun metValue(): Double = MetValues.cycling(averageSpeedKmh())
}
