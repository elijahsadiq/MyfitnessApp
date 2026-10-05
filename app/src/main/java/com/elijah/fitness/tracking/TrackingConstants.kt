package com.elijah.fitness.tracking

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Every threshold the live-tracking engine applies, in one place.
 *
 * The written report's "sensor accuracy" discussion (Task 1b) will cite
 * these numbers verbatim, so they are centralised here rather than
 * sprinkled across the engine and the service. Changing any of them is a
 * one-liner, and a reader of the engine can see the whole policy at a
 * glance.
 */
object TrackingConstants {

    /** How often the fused provider is asked for a fix. */
    val UPDATE_INTERVAL: Duration = 2.seconds

    /** The provider is also allowed to emit faster than the request. */
    val FASTEST_INTERVAL: Duration = 1.seconds

    /**
     * Points reporting horizontal accuracy worse than this (metres) are
     * discarded. Phones indoors routinely report 30-80 m; 25 m is tight
     * enough to drop those while still letting outdoor runs through in
     * weather with mediocre sky view.
     */
    const val ACCURACY_THRESHOLD_M: Double = 25.0

    /**
     * Points closer than this to the previous accepted point are dropped.
     * Stops a device sitting on a bench accumulating random drift as
     * "distance" over a long pause.
     */
    const val MIN_STEP_DISTANCE_M: Double = 5.0

    /**
     * Points whose implied speed from the previous point is above this
     * are treated as GPS jumps and dropped. 15 m/s (54 km/h) is well above
     * a club-level cyclist and well below any plausible teleport. If the
     * app ever supports driving/motorsport this would need lifting.
     */
    const val MAX_PLAUSIBLE_SPEED_MPS: Double = 15.0

    /**
     * Earth's mean radius, used by the Haversine distance formula.
     * NASA / IUGG value in metres.
     */
    const val EARTH_RADIUS_M: Double = 6_371_008.8
}
