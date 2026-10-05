package com.elijah.fitness.tracking

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * All distance / pace / speed maths for a tracking session. Pure Kotlin —
 * zero Android imports so unit tests can drive it with synthetic data.
 *
 * ### What goes in
 * A list of already-filtered [TrackPoint]s in `seq` order. The caller
 * (engine) has already dropped outliers and sub-threshold steps against
 * [TrackingConstants] — this class trusts its input.
 *
 * ### What comes out
 * One pass computes everything the live screen and the saved workout need:
 * total distance (m), moving duration (ms, excluding paused gaps),
 * average pace (ms per km), and the last-segment pace / speed for the
 * live readouts.
 */
object TrackCalculator {

    /**
     * Great-circle distance between two WGS 84 points, in metres. Haversine
     * is accurate to ≈0.5 % at terrestrial scales — well under GPS noise.
     */
    fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val radiansLat1 = Math.toRadians(lat1)
        val radiansLat2 = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val a = sin(deltaLat / 2.0).let { it * it } +
            cos(radiansLat1) * cos(radiansLat2) *
            sin(deltaLon / 2.0).let { it * it }
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return TrackingConstants.EARTH_RADIUS_M * c
    }

    /** Total distance in metres across all consecutive point pairs. */
    fun totalDistanceMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            total += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return total
    }

    /**
     * Moving duration in ms — i.e. the elapsed-time span across all
     * consecutive point pairs. Because `elapsedMs` is session-time and
     * pauses *hold it steady*, a 10-minute mid-run pause contributes 0 ms
     * here. If you want wall-clock duration instead, subtract the first
     * `recordedAt` from the last.
     */
    fun movingDurationMs(points: List<TrackPoint>): Long {
        if (points.size < 2) return 0L
        return points.last().elapsedMs - points.first().elapsedMs
    }

    /**
     * Average pace in milliseconds per kilometre. Returns `null` for a
     * session with less than ten metres covered (noise floor — a stationary
     * device filtered to 2–3 points can produce a nonsense pace otherwise).
     */
    fun averagePaceMsPerKm(points: List<TrackPoint>): Long? {
        val meters = totalDistanceMeters(points)
        if (meters < 10.0) return null
        val duration = movingDurationMs(points)
        if (duration <= 0L) return null
        return ((duration.toDouble() / meters) * 1000.0).toLong()
    }

    /** Average speed in km/h, or `null` for a sub-10-metre session. */
    fun averageSpeedKmh(points: List<TrackPoint>): Double? {
        val meters = totalDistanceMeters(points)
        if (meters < 10.0) return null
        val durationMs = movingDurationMs(points)
        if (durationMs <= 0L) return null
        val hours = durationMs / 3_600_000.0
        return (meters / 1000.0) / hours
    }

    /**
     * Pace of the last `windowMeters` of the track, used for the live
     * "current pace" readout. Returns `null` until we have enough recent
     * distance to be meaningful.
     */
    fun recentPaceMsPerKm(points: List<TrackPoint>, windowMeters: Double = 200.0): Long? {
        if (points.size < 2) return null
        var meters = 0.0
        var i = points.size - 1
        while (i > 0 && meters < windowMeters) {
            val a = points[i - 1]
            val b = points[i]
            meters += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            i--
        }
        if (meters < 50.0) return null
        val durationMs = points.last().elapsedMs - points[i].elapsedMs
        if (durationMs <= 0L) return null
        return ((durationMs.toDouble() / meters) * 1000.0).toLong()
    }
}
