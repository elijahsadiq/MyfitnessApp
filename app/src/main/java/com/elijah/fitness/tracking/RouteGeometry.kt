package com.elijah.fitness.tracking

/**
 * Pure-Kotlin geometry helpers for a GPS route. **No Android imports**
 * on purpose — this file sits in `tracking/` so it ships with the
 * domain model, is unit-testable on a JVM, and can be mocked out
 * trivially in rendering tests.
 *
 * Two independent helpers live here:
 *
 * - [boundingBox] — the lat/long envelope of a route with an optional
 *   `padFraction` of breathing room so the fitted camera doesn't crop
 *   the first and last points.
 * - [splitByPause] — segments a route list wherever the user hit
 *   Pause. A paused section must leave a visible gap in the drawn
 *   polyline rather than a straight line jumping across the map.
 *
 * The pause detector is based on `TrackPoint.elapsedMs` (session-
 * time, which **stops** while paused) vs `TrackPoint.recordedAt`
 * (wall-clock, which keeps running). Where the two diverge is where
 * the pause was. A GPS dropout — where tracking kept counting but the
 * fix was lost — grows both deltas equally and so is **not** detected
 * as a pause.
 */
object RouteGeometry {

    /**
     * Lat/long envelope of [points] with every edge expanded by
     * [padFraction] × span (so `padFraction = 0.1` adds 10 % of the
     * route's width and height as margin).
     *
     * Returns `null` for an empty or single-point list — the caller
     * must decide whether to show a default map viewport or hide the
     * card entirely.
     */
    fun boundingBox(
        points: List<TrackPoint>,
        padFraction: Double = 0.1,
    ): BoundingBoxDegrees? {
        if (points.isEmpty()) return null
        if (points.size == 1) {
            val only = points[0]
            // A tiny ~0.0005° envelope around the single point so the
            // caller still gets *some* viewport. 0.0005° ≈ 55 m.
            val pad = 0.0005
            return BoundingBoxDegrees(
                north = only.latitude + pad,
                south = only.latitude - pad,
                east = only.longitude + pad,
                west = only.longitude - pad,
            )
        }
        var north = points[0].latitude
        var south = points[0].latitude
        var east = points[0].longitude
        var west = points[0].longitude
        for (i in 1 until points.size) {
            val p = points[i]
            if (p.latitude > north) north = p.latitude
            if (p.latitude < south) south = p.latitude
            if (p.longitude > east) east = p.longitude
            if (p.longitude < west) west = p.longitude
        }
        val latSpan = (north - south).coerceAtLeast(1e-9)
        val lonSpan = (east - west).coerceAtLeast(1e-9)
        val latPad = latSpan * padFraction
        val lonPad = lonSpan * padFraction
        return BoundingBoxDegrees(
            north = north + latPad,
            south = south - latPad,
            east = east + lonPad,
            west = west - lonPad,
        )
    }

    /**
     * Split [points] into a list of segments wherever the user paused.
     *
     * ### Detection rule
     *
     * For consecutive points `p[i-1]` → `p[i]`:
     *
     * - `wallDelta = recordedAt(i) - recordedAt(i-1)` (ms, always ≥ 0)
     * - `elapsedDelta = elapsedMs(i) - elapsedMs(i-1)` (ms)
     * - `pauseGap = wallDelta - elapsedDelta`
     *
     * A `pauseGap` of more than [minPauseGapMs] means the elapsed-time
     * clock was paused for that long — the user hit Pause. The segment
     * is cut here and the next segment starts at `p[i]`.
     *
     * A GPS dropout grows both deltas by roughly the same amount, so
     * `pauseGap` stays small and no split happens — the renderer draws
     * one continuous line even if the device missed a few fixes.
     *
     * @param points typically already filtered + ordered by [TrackPoint.seq].
     * @param minPauseGapMs smallest observed `wall − elapsed` that counts
     *   as a pause. 2 s is tight enough to catch any realistic Pause tap
     *   (the service's pause call-chain fires in milliseconds) yet wide
     *   enough to tolerate point-ordering jitter and clock-skew noise.
     */
    fun splitByPause(
        points: List<TrackPoint>,
        minPauseGapMs: Long = DEFAULT_MIN_PAUSE_GAP_MS,
    ): List<List<TrackPoint>> {
        if (points.size <= 1) return if (points.isEmpty()) emptyList() else listOf(points)

        val segments = mutableListOf<MutableList<TrackPoint>>()
        var current = mutableListOf<TrackPoint>()
        current += points[0]

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val wallDelta = curr.recordedAt.toEpochMilli() - prev.recordedAt.toEpochMilli()
            val elapsedDelta = curr.elapsedMs - prev.elapsedMs
            val pauseGap = wallDelta - elapsedDelta
            if (pauseGap >= minPauseGapMs) {
                // Close the current segment and start a new one.
                segments += current
                current = mutableListOf()
            }
            current += curr
        }
        if (current.isNotEmpty()) segments += current
        return segments
    }

    /**
     * 2-second threshold. Keeps GPS-dropout tolerance in step with the
     * `ACCURACY_THRESHOLD_M` + sampling cadence used elsewhere in the
     * tracking engine; see [TrackingConstants].
     */
    const val DEFAULT_MIN_PAUSE_GAP_MS: Long = 2_000L
}

/**
 * Immutable lat / long envelope in WGS 84 degrees. Carries only the four
 * edges — convert into an `org.osmdroid.util.BoundingBox` at the Android
 * boundary.
 */
data class BoundingBoxDegrees(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
) {
    val latSpan: Double get() = north - south
    val lonSpan: Double get() = east - west
}
