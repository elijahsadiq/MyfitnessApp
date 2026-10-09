package com.elijah.fitness.tracking

import com.elijah.fitness.data.model.ActivityType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import java.util.UUID

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
        /**
         * Dropped — measured before the session last started or resumed.
         * Providers re-deliver a cached position on resume; accepting it
         * would pin the new segment to where the user *paused* and count
         * the distance walked while paused.
         */
        RejectedStale,
        /** Not tracking (idle / paused / stopped) — the fix is simply ignored. */
        Ignored,
    }

    private var startedAtMs: Long = 0L
    /** Accumulated moving time across pause/resume cycles. */
    private var elapsedBeforePauseMs: Long = 0L
    /** Wall-clock moment of the last resume (or start) — null when paused/stopped. */
    private var runningSinceMs: Long? = null
    /**
     * True between a [resume] and the next accepted fix. That fix starts
     * a new route segment: the user may have walked while paused, so the
     * too-close / teleport checks against the pre-pause point don't
     * apply (they would otherwise reject every fix after a long pause).
     */
    private var segmentStartPending: Boolean = false

    fun start(activityType: ActivityType) {
        val now = clock.instant()
        startedAtMs = now.toEpochMilli()
        elapsedBeforePauseMs = 0L
        runningSinceMs = startedAtMs
        segmentStartPending = false
        _state.value = TrackingState.IDLE.copy(
            status = TrackingState.Status.Tracking,
            activityType = activityType,
            startedAt = now,
            gpsQuality = TrackingState.GpsQuality.Searching,
            sessionId = UUID.randomUUID().toString(),
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
        segmentStartPending = current.points.isNotEmpty()
        _state.value = current.copy(status = TrackingState.Status.Tracking)
    }

    /**
     * Finalise the session. State moves to [TrackingState.Status.Stopped]
     * and the point list is preserved for the save step to read.
     *
     * Idempotent: the screen stops the session before saving and the
     * service stops it again while tearing down. A second call must not
     * touch the elapsed time (it used to drop the last running stretch,
     * so a retried save could store a near-zero duration).
     */
    fun stop() {
        val current = _state.value
        if (current.status == TrackingState.Status.Idle ||
            current.status == TrackingState.Status.Stopped
        ) return
        val running = runningSinceMs
        if (running != null) elapsedBeforePauseMs += clock.millis() - running
        runningSinceMs = null
        segmentStartPending = false
        _state.value = current.copy(
            status = TrackingState.Status.Stopped,
            elapsedMs = elapsedBeforePauseMs,
        )
    }

    /** Reset to the IDLE state. Used after a save, or when the user discards. */
    fun reset() {
        startedAtMs = 0L
        elapsedBeforePauseMs = 0L
        runningSinceMs = null
        segmentStartPending = false
        _state.value = TrackingState.IDLE
    }

    /**
     * Feed one raw fix into the session. Returns a [FilterResult] so the
     * caller can tally rejections or log them.
     *
     * @param latitude WGS 84 degrees.
     * @param longitude WGS 84 degrees.
     * @param accuracyM horizontal accuracy in metres, or null if unknown.
     * @param recordedAt wall-clock moment the fix is accepted (stored with
     *   the point; same clock as the session's elapsed time).
     * @param measuredAt wall-clock moment the provider actually measured
     *   the position — older than [recordedAt] for cached fixes.
     */
    fun onFix(
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        recordedAt: Instant,
        measuredAt: Instant = recordedAt,
    ): FilterResult {
        val current = _state.value
        if (current.status != TrackingState.Status.Tracking) return FilterResult.Ignored

        // Filter 0 — stale: measured before we (re)started running.
        val runningSince = runningSinceMs
        if (runningSince != null && measuredAt.toEpochMilli() < runningSince) {
            return FilterResult.RejectedStale
        }

        // Filter 1 — accuracy band.
        if (accuracyM != null && accuracyM > TrackingConstants.ACCURACY_THRESHOLD_M) {
            // Still a useful signal for the "GPS searching" pill — surface the band.
            _state.value = current.copy(gpsQuality = TrackingState.GpsQuality.Weak)
            return FilterResult.RejectedLowAccuracy
        }

        val previous = current.points.lastOrNull()

        if (previous != null && !segmentStartPending) {
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
        // The pair (pre-pause point, first point after Resume) spans the
        // pause, so it can't give a meaningful "current" speed.
        val startsNewSegment = segmentStartPending && previous != null
        segmentStartPending = false

        _state.value = current.copy(
            elapsedMs = elapsedMs,
            distanceMeters = TrackCalculator.totalDistanceMeters(newPoints),
            currentPaceMsPerKm = TrackCalculator.recentPaceMsPerKm(newPoints),
            averagePaceMsPerKm = TrackCalculator.averagePaceMsPerKm(newPoints),
            currentSpeedKmh = if (startsNewSegment) null else currentSpeedKmhFrom(newPoints),
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
