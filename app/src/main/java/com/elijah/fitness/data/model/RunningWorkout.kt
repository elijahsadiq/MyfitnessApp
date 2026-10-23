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
    notes: String? = null,
    caloriesKcal: Int? = null,
) : DistanceWorkout(id, startTime, duration, notes, distanceMeters, caloriesKcal) {

    /** Overrides [Workout.type]: marks this workout as a run for the mappers, filters and labels. */
    override val type: ActivityType = ActivityType.RUNNING

    /** Overrides [Workout.metValue]: running MET depends on speed, so it uses the speed bands. */
    override fun metValue(): Double = MetValues.running(averageSpeedKmh())

    /**
     * Pace in seconds per kilometre, rounded to the nearest second. A run
     * keeps the default 1 km split, so this is [paceSecondsPerSplit]: the
     * same rule (and the same "no pace" below 10 m) as swims and rows.
     */
    fun paceSecondsPerKm(): Long = paceSecondsPerSplit()
}
