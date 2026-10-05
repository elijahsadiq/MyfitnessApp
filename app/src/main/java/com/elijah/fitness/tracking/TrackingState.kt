package com.elijah.fitness.tracking

import com.elijah.fitness.data.model.ActivityType
import java.time.Instant

/**
 * Snapshot of a live tracking session, exposed by the service as a
 * [kotlinx.coroutines.flow.StateFlow]. The screen rebuilds from this on
 * rotation and the notification mirrors a subset of it, so the data class
 * is designed to be cheap to compare for equality (DiffUtil-friendly).
 */
data class TrackingState(
    val status: Status,
    val activityType: ActivityType,
    val startedAt: Instant?,
    val elapsedMs: Long,
    val distanceMeters: Double,
    val currentPaceMsPerKm: Long?,
    val averagePaceMsPerKm: Long?,
    val currentSpeedKmh: Double?,
    val averageSpeedKmh: Double?,
    val pointCount: Int,
    val gpsQuality: GpsQuality,
    val points: List<TrackPoint>,
) {

    enum class Status { Idle, Tracking, Paused, Stopped }

    /** Signal-quality band derived from the most recent accuracy reading. */
    enum class GpsQuality {
        /** No fix yet. */
        Searching,
        /** Last accuracy ≤ 10 m. */
        Strong,
        /** Last accuracy between 10 m and the filter threshold. */
        Weak,
    }

    companion object {
        val IDLE: TrackingState = TrackingState(
            status = Status.Idle,
            activityType = ActivityType.RUNNING,
            startedAt = null,
            elapsedMs = 0L,
            distanceMeters = 0.0,
            currentPaceMsPerKm = null,
            averagePaceMsPerKm = null,
            currentSpeedKmh = null,
            averageSpeedKmh = null,
            pointCount = 0,
            gpsQuality = GpsQuality.Searching,
            points = emptyList(),
        )
    }
}
