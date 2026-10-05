package com.elijah.fitness.tracking

import com.elijah.fitness.data.model.ActivityType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

/**
 * The pure in-memory state machine for a tracking session. Pure Kotlin —
 * no `FusedLocationProviderClient`, no `Context`. The service pumps raw
 * provider updates through [onFix]; the session applies the filters,
 * updates the [TrackingState] flow, and keeps the authoritative point
 * list so the service / UI / notification all read the same data.
 *
 * Testing-friendly:
 * - Clock is injectable so elapsed-time maths is deterministic.
 * - [onFix] takes primitive doubles rather than an `android.location.Location`.
 * - The three filter decisions are visible as [FilterResult] values.
 */
class TrackingSession(
    private val clock: Clock = Clock.systemUTC(),
) {

    private val _state: MutableStateFlow<TrackingState> =
        MutableStateFlow(TrackingState.IDLE)
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    /**
     * Outcome of applying filters to one incoming fix. Exposed by [onFix]
     * so the service can log/count rejections without re-deriving the
     * reason.
     */
    enum class FilterResult {
        Accepted,
        /** Dropped — accuracy worse than [TrackingConstants.ACCURACY_THRESHOLD_M]. */
        RejectedLowAccuracy,
        /** Dropped — closer than [TrackingConstants.MIN_STEP_DISTANCE_M] to the previous point. */
        RejectedTooClose,
        /** Dropped — implied speed above [TrackingConstants.MAX_PLAUSIBLE_SPEED_MPS]. */
        RejectedTeleport,
        /** Not tracking (idle / paused / stopped) — the fix is simply ignored. */
        Ignored,
    }

    private var startedAtMs: Long = 0L
    /** Accumulated moving time across pause/resume cycles. */
    private var elapsedBeforePauseMs: Long = 0L
    /** Wall-clock moment of the last resume (or start) — null when paused/stopped. */
    private var runningSinceMs: Long? = null

    fun start(activityType: ActivityType) {
        val now = clock.instant()
        startedAtMs = now.toEpochMilli()
        elapsedBeforePauseMs = 0L
        runningSinceMs = startedAtMs
        _state.value = TrackingState.IDLE.copy(
            status = TrackingState.Status.Tracking,
            activityType = activityType,
            startedAt = now,
            gpsQuality = TrackingState.GpsQuality.Searching,
        )
    }

    fun pause() {
        val current = _state.value
        if (current.status != TrackingState.Status.Tracking) return
        val runningSince = runningSinceMs ?: return
        elapsedBeforePauseMs += clock.millis() - runningSince
        runningSinceMs = null
        _state.value = current.copy(
            status = TrackingState.Status.Paused,
            elapsedMs = elapsedBeforePauseMs,
        )
    }

    fun resume() {
        val current = _state.value
        if (current.status != TrackingState.Status.Paused) return
        runningSinceMs = clock.millis()
        _state.value = current.copy(status = TrackingState.Status.Tracking)
    }

    /**
     * Finalise the session. State moves to [TrackingState.Status.Stopped]
     * and the point list is preserved for the save screen to read.
     */
    fun stop() {
        val current = _state.value
        if (current.status == TrackingState.Status.Idle) return
        val running = runningSinceMs
        val finalElapsed = elapsedBeforePauseMs +
            (if (running != null) clock.millis() - running else 0L)
        runningSinceMs = null
        _state.value = current.copy(
            status = TrackingState.Status.Stopped,
            elapsedMs = finalElapsed,
        )
    }

    /** Reset to the IDLE state. Used when the user discards a stopped session. */
    fun reset() {
        startedAtMs = 0L
        elapsedBeforePauseMs = 0L
        runningSinceMs = null
        _state.value = TrackingState.IDLE
    }

    /**
     * Feed one raw fix into the session. Returns a [FilterResult] so the
     * caller can tally rejections or log them.
     *
     * @param latitude WGS 84 degrees.
     * @param longitude WGS 84 degrees.
     * @param accuracyM horizontal accuracy in metres, or null if unknown.
     * @param recordedAt wall-clock moment of the fix.
     */
    fun onFix(
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        recordedAt: Instant,
    ): FilterResult {
        val current = _state.value
        if (current.status != TrackingState.Status.Tracking) return FilterResult.Ignored

        // Filter 1 — accuracy band.
        if (accuracyM != null && accuracyM > TrackingConstants.ACCURACY_THRESHOLD_M) {
            // Still a useful signal for the "GPS searching" pill — surface the band.
            _state.value = current.copy(gpsQuality = TrackingState.GpsQuality.Weak)
            return FilterResult.RejectedLowAccuracy
        }

        val previous = current.points.lastOrNull()

        if (previous != null) {
            // Filter 2 — distance from previous point.
            val stepMeters = TrackCalculator.haversineMeters(
                previous.latitude, previous.longitude, latitude, longitude,
            )
            if (stepMeters < TrackingConstants.MIN_STEP_DISTANCE_M) {
                return FilterResult.RejectedTooClose
            }

            // Filter 3 — plausible speed from previous point.
            val now = clock.millis()
            val elapsedMs = computeElapsedMs(now)
            val deltaMs = elapsedMs - previous.elapsedMs
            if (deltaMs > 0) {
                val mps = stepMeters / (deltaMs / 1000.0)
                if (mps > TrackingConstants.MAX_PLAUSIBLE_SPEED_MPS) {
                    return FilterResult.RejectedTeleport
                }
            }
        }

        val elapsedMs = computeElapsedMs(clock.millis())
        val newPoint = TrackPoint(
            seq = current.pointCount,
            latitude = latitude,
            longitude = longitude,
            elapsedMs = elapsedMs,
            accuracyM = accuracyM,
            recordedAt = recordedAt,
        )
        val newPoints = current.points + newPoint

        _state.value = current.copy(
            elapsedMs = elapsedMs,
            distanceMeters = TrackCalculator.totalDistanceMeters(newPoints),
            currentPaceMsPerKm = TrackCalculator.recentPaceMsPerKm(newPoints),
            averagePaceMsPerKm = TrackCalculator.averagePaceMsPerKm(newPoints),
            currentSpeedKmh = currentSpeedKmhFrom(newPoints),
            averageSpeedKmh = TrackCalculator.averageSpeedKmh(newPoints),
            pointCount = newPoints.size,
            gpsQuality = gpsBandFor(accuracyM),
            points = newPoints,
        )
        return FilterResult.Accepted
    }

    /**
     * Update elapsed-time counters without producing a new point. The
     * service calls this on a 1-second timer so the "Elapsed" readout
     * ticks between GPS fixes.
     */
    fun tickElapsed() {
        val current = _state.value
        if (current.status != TrackingState.Status.Tracking) return
        _state.value = current.copy(elapsedMs = computeElapsedMs(clock.millis()))
    }

    private fun computeElapsedMs(nowMs: Long): Long {
        val running = runningSinceMs ?: return elapsedBeforePauseMs
        return elapsedBeforePauseMs + (nowMs - running)
    }

    private fun currentSpeedKmhFrom(points: List<TrackPoint>): Double? {
        if (points.size < 2) return null
        val a = points[points.size - 2]
        val b = points[points.size - 1]
        val deltaMs = b.elapsedMs - a.elapsedMs
        if (deltaMs <= 0L) return null
        val meters = TrackCalculator.haversineMeters(
            a.latitude, a.longitude, b.latitude, b.longitude,
        )
        val hours = deltaMs / 3_600_000.0
        return (meters / 1000.0) / hours
    }

    private fun gpsBandFor(accuracyM: Double?): TrackingState.GpsQuality =
        when {
            accuracyM == null -> TrackingState.GpsQuality.Searching
            accuracyM <= 10.0 -> TrackingState.GpsQuality.Strong
            else -> TrackingState.GpsQuality.Weak
        }
}
