package com.elijah.fitness.tracking

import com.elijah.fitness.data.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Drives the stateful session with a controllable clock so pause / resume
 * and filter-rejection semantics can be asserted exactly.
 */
class TrackingSessionTest {

    private val clock = ControlledClock(Instant.parse("2026-10-05T10:00:00Z"))
    private val session = TrackingSession(clock)

    @Test
    fun `start moves state to Tracking and sets startedAt`() {
        session.start(ActivityType.RUNNING)
        val s = session.state.value
        assertEquals(TrackingState.Status.Tracking, s.status)
        assertEquals(ActivityType.RUNNING, s.activityType)
        assertEquals(clock.instant(), s.startedAt)
    }

    @Test
    fun `low-accuracy fix is rejected and marks GPS Weak`() {
        session.start(ActivityType.RUNNING)
        val result = session.onFix(51.5, -0.12, accuracyM = 50.0, recordedAt = clock.instant())
        assertEquals(TrackingSession.FilterResult.RejectedLowAccuracy, result)
        assertEquals(0, session.state.value.pointCount)
        assertEquals(TrackingState.GpsQuality.Weak, session.state.value.gpsQuality)
    }

    @Test
    fun `too-close fix is rejected without accumulating distance`() {
        session.start(ActivityType.RUNNING)
        session.onFix(51.50000, -0.12000, accuracyM = 5.0, recordedAt = clock.instant())
        clock.advanceMs(1_000)
        val result = session.onFix(51.50001, -0.12000, accuracyM = 5.0, recordedAt = clock.instant())
        assertEquals(TrackingSession.FilterResult.RejectedTooClose, result)
        assertEquals(1, session.state.value.pointCount)
    }

    @Test
    fun `teleport is rejected on implausible speed`() {
        session.start(ActivityType.RUNNING)
        session.onFix(51.5000, -0.1200, accuracyM = 5.0, recordedAt = clock.instant())
        clock.advanceMs(1_000)
        // Jump of ~0.01° lat ≈ 1.1 km in 1 s = 1 100 m/s — far above the 15 m/s cap.
        val result = session.onFix(51.5100, -0.1200, accuracyM = 5.0, recordedAt = clock.instant())
        assertEquals(TrackingSession.FilterResult.RejectedTeleport, result)
        assertEquals(1, session.state.value.pointCount)
    }

    @Test
    fun `accepted fix advances distance and point count`() {
        session.start(ActivityType.RUNNING)
        session.onFix(51.5000, -0.1200, accuracyM = 5.0, recordedAt = clock.instant())
        clock.advanceMs(10_000)
        session.onFix(51.5004, -0.1200, accuracyM = 5.0, recordedAt = clock.instant()) // ≈44 m
        val s = session.state.value
        assertEquals(2, s.pointCount)
        assertTrue("expected ~44 m, got ${s.distanceMeters}", s.distanceMeters in 40.0..50.0)
    }

    @Test
    fun `pause then resume keeps elapsedMs steady during the gap`() {
        session.start(ActivityType.RUNNING)
        session.onFix(51.5000, -0.1200, accuracyM = 5.0, recordedAt = clock.instant())
        clock.advanceMs(60_000)                              // ran 1 min
        session.onFix(51.5004, -0.1200, accuracyM = 5.0, recordedAt = clock.instant())
        session.pause()
        val pausedElapsed = session.state.value.elapsedMs
        clock.advanceMs(10 * 60_000)                         // 10 min paused
        session.resume()
        clock.advanceMs(60_000)                              // ran another 1 min
        session.onFix(51.5008, -0.1200, accuracyM = 5.0, recordedAt = clock.instant())
        val s = session.state.value
        // After pause we had ~60 000; resume + 60 000 more → ~120 000, NOT ~720 000.
        assertTrue("paused elapsed was $pausedElapsed", pausedElapsed in 55_000..65_000)
        assertTrue(
            "elapsed after pause+resume+run was ${s.elapsedMs}; expected ~120 000",
            s.elapsedMs in 115_000..125_000,
        )
    }

    @Test
    fun `fix while paused is Ignored and does not accumulate`() {
        session.start(ActivityType.RUNNING)
        session.onFix(51.5, -0.12, 5.0, clock.instant())
        session.pause()
        clock.advanceMs(5_000)
        val result = session.onFix(51.5005, -0.12, 5.0, clock.instant())
        assertEquals(TrackingSession.FilterResult.Ignored, result)
        assertEquals(1, session.state.value.pointCount)
    }

    @Test
    fun `fix measured before resume is rejected as stale`() {
        session.start(ActivityType.RUNNING)
        session.onFix(51.5000, -0.1200, 5.0, clock.instant())
        session.pause()
        clock.advanceMs(60_000)
        session.resume()
        val resumedAt = clock.instant()
        clock.advanceMs(1_000)
        // A cached position measured during the pause, delivered now.
        val result = session.onFix(
            51.5050, -0.1200, 5.0,
            recordedAt = clock.instant(),
            measuredAt = resumedAt.minusMillis(5_000),
        )
        assertEquals(TrackingSession.FilterResult.RejectedStale, result)
        assertEquals(1, session.state.value.pointCount)
    }

    @Test
    fun `first fix after resume starts a new segment and skips the teleport check`() {
        session.start(ActivityType.RUNNING)
        session.onFix(51.5000, -0.1200, 5.0, clock.instant())
        clock.advanceMs(10_000)
        session.onFix(51.5005, -0.1200, 5.0, clock.instant())   // ~55 m
        session.pause()
        clock.advanceMs(120_000)                                // walked ~500 m while paused
        session.resume()
        clock.advanceMs(1_000)
        val result = session.onFix(51.5050, -0.1200, 5.0, clock.instant())
        assertEquals(TrackingSession.FilterResult.Accepted, result)
        // Distance walked while paused is not counted.
        val d = session.state.value.distanceMeters
        assertTrue("expected ~55 m (pause gap excluded), got $d", d in 50.0..60.0)
    }

    @Test
    fun `stop twice keeps the final elapsed time`() {
        session.start(ActivityType.RUNNING)
        clock.advanceMs(90_000)
        session.stop()
        val first = session.state.value.elapsedMs
        clock.advanceMs(30_000)
        session.stop()
        assertEquals(first, session.state.value.elapsedMs)
        assertEquals(90_000L, first)
    }

    private class ControlledClock(private var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        fun advanceMs(ms: Long) { now = now.plusMillis(ms) }
    }
}
