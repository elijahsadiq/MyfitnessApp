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

    /**
     * Pace in seconds per kilometre. Returns 0 ("no pace") below
     * [MIN_PACE_DISTANCE_KM]: a few metres of GPS drift would otherwise
     * show as a nonsense pace like "1250:00 /km".
     */
    fun paceSecondsPerKm(): Long {
        val km = distanceKm()
        if (km < MIN_PACE_DISTANCE_KM) return 0L
        return (duration.seconds.toDouble() / km).toLong()
    }

    companion object {
        /** 10 m — below this, pace isn't meaningful. */
        const val MIN_PACE_DISTANCE_KM: Double = 0.01
    }
}
