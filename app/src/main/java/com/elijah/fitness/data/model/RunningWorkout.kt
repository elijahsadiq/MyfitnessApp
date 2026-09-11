package com.elijah.fitness.data.model

import java.time.Duration
import java.time.Instant

/**
 * A completed run. MET is derived from average speed via
 * [MetValues.running]. Pace is exposed on top of the inherited
 * distance/speed derivations because runners think in min/km, not km/h.
 */
class RunningWorkout(
    id: String,
    startTime: Instant,
    duration: Duration,
    distanceMeters: Double,
    notes: String? = null
) : DistanceWorkout(id, startTime, duration, notes, distanceMeters) {

    override val type: ActivityType = ActivityType.RUNNING

    override fun metValue(): Double = MetValues.running(averageSpeedKmh())

    /** Pace in seconds per kilometre. Returns 0 for zero distance. */
    fun paceSecondsPerKm(): Long {
        val km = distanceKm()
        if (km <= 0.0) return 0L
        return (duration.seconds.toDouble() / km).toLong()
    }
}
