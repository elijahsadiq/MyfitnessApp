package com.elijah.fitness.tracking

/**
 * Breaks a route into per-kilometre splits. Pure Kotlin — no Android
 * imports — so the detail view can bind directly and tests can drive it
 * with synthetic point lists.
 *
 * ### Definition
 * The first split covers metres `[0, 1000)`, the second `[1000, 2000)`,
 * etc. A split's `durationMs` is the elapsed-time span across the fixes
 * that fall inside its range; its `paceMsPerKm` is derived from that
 * span. The last split is the "tail" (< 1 km) and is marked as partial.
 */
data class RouteSplit(
    /** 1-based. Split #1 is km 0-1. */
    val km: Int,
    val durationMs: Long,
    val paceMsPerKm: Long,
    val distanceMeters: Double,
    val isPartial: Boolean,
)

object RouteSplits {

    fun compute(points: List<TrackPoint>): List<RouteSplit> {
        if (points.size < 2) return emptyList()

        val result = mutableListOf<RouteSplit>()
        var kmBucket = 1
        var bucketStartIdx = 0
        var bucketStartDistance = 0.0
        var totalDistance = 0.0

        for (i in 1 until points.size) {
            val step = TrackCalculator.haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
            totalDistance += step
            val threshold = kmBucket * 1000.0
            if (totalDistance >= threshold) {
                val durationMs = points[i].elapsedMs - points[bucketStartIdx].elapsedMs
                val bucketMeters = totalDistance - bucketStartDistance
                result += RouteSplit(
                    km = kmBucket,
                    durationMs = durationMs,
                    paceMsPerKm = paceOrZero(durationMs, bucketMeters),
                    distanceMeters = bucketMeters,
                    isPartial = false,
                )
                bucketStartIdx = i
                bucketStartDistance = totalDistance
                kmBucket++
            }
        }

        // Tail — the last (< 1 km) stretch.
        if (bucketStartIdx < points.size - 1) {
            val tailMeters = totalDistance - bucketStartDistance
            if (tailMeters > 0.0) {
                val durationMs = points.last().elapsedMs - points[bucketStartIdx].elapsedMs
                result += RouteSplit(
                    km = kmBucket,
                    durationMs = durationMs,
                    paceMsPerKm = paceOrZero(durationMs, tailMeters),
                    distanceMeters = tailMeters,
                    isPartial = true,
                )
            }
        }
        return result
    }

    private fun paceOrZero(durationMs: Long, meters: Double): Long =
        if (durationMs <= 0L || meters <= 0.0) 0L
        else ((durationMs.toDouble() / meters) * 1000.0).toLong()
}
