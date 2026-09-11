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
    notes: String? = null
) : DistanceWorkout(id, startTime, duration, notes, distanceMeters) {

    override val type: ActivityType = ActivityType.CYCLING

    override fun metValue(): Double = MetValues.cycling(averageSpeedKmh())
}
