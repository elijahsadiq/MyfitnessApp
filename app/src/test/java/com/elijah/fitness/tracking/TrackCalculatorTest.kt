package com.elijah.fitness.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pure-Kotlin tests for the track maths. The Haversine case uses the
 * canonical London-to-Paris great-circle distance (≈343.5 km) as the
 * reference point; the synthetic cases use a short straight-line segment
 * whose geometry we know exactly.
 */
class TrackCalculatorTest {

    @Test
    fun `haversine returns the known London to Paris distance within 0_5 percent`() {
        // London (51.5007, -0.1246) → Paris (48.8566, 2.3522)
        val meters = TrackCalculator.haversineMeters(
            51.5007, -0.1246,
            48.8566, 2.3522,
        )
        val expected = 343_500.0
        val tolerance = expected * 0.005 // 0.5 %
        assertTrue(
            "haversine returned $meters; expected $expected ± $tolerance",
            kotlin.math.abs(meters - expected) < tolerance,
        )
    }

    @Test
    fun `total distance is zero for a single point`() {
        val points = listOf(point(0, 51.5, -0.12, elapsedMs = 0))
        assertEquals(0.0, TrackCalculator.totalDistanceMeters(points), 0.0001)
    }

    @Test
    fun `total distance sums consecutive segments`() {
        val points = listOf(
            point(0, 51.5000, -0.1200, elapsedMs = 0),
            point(1, 51.5010, -0.1200, elapsedMs = 60_000),
            point(2, 51.5020, -0.1200, elapsedMs = 120_000),
        )
        val d = TrackCalculator.totalDistanceMeters(points)
        // Each 0.0010° lat ≈ 111 m. Two steps → ≈222 m.
        assertTrue("expected ~222 m, got $d", d in 220.0..225.0)
    }

    @Test
    fun `moving duration spans first to last elapsedMs`() {
        val points = listOf(
            point(0, 51.5, -0.12, elapsedMs = 0),
            point(1, 51.5, -0.11, elapsedMs = 60_000),
            point(2, 51.5, -0.10, elapsedMs = 180_000),
        )
        assertEquals(180_000L, TrackCalculator.movingDurationMs(points))
    }

    @Test
    fun `average pace returns null for a sub-ten-metre session`() {
        val points = listOf(
            point(0, 51.5, -0.12, elapsedMs = 0),
            point(1, 51.50001, -0.12, elapsedMs = 60_000), // ≈1 m
        )
        assertNull(TrackCalculator.averagePaceMsPerKm(points))
    }

    @Test
    fun `average pace matches 10 km in 60 minutes = 360_000 ms per km`() {
        // 10 km in 60 min = 6:00/km = 360 000 ms/km.
        val points = listOf(
            point(0, 51.5000, -0.1200, elapsedMs = 0),
            point(1, 51.5898, -0.1200, elapsedMs = 60 * 60 * 1000L), // ≈10 km north
        )
        val pace = TrackCalculator.averagePaceMsPerKm(points)!!
        assertTrue("pace was $pace, expected ~360 000", pace in 355_000..365_000)
    }

    @Test
    fun `pause in the middle contributes zero elapsed time to the moving duration`() {
        // Minute 0 → minute 1 — ran 1 min. Pause for 10 min (elapsedMs holds).
        // Minute 11 (wall-clock) resumes; the next fix stamps elapsedMs=120_000
        // (minute 2 of actual running), *not* 720_000.
        val points = listOf(
            point(0, 51.5000, -0.1200, elapsedMs = 0),
            point(1, 51.5010, -0.1200, elapsedMs = 60_000),
            // user paused for 10 min; elapsedMs did not advance
            point(2, 51.5020, -0.1200, elapsedMs = 120_000),
        )
        assertEquals(120_000L, TrackCalculator.movingDurationMs(points))
    }

    private fun point(
        seq: Int,
        lat: Double,
        lon: Double,
        elapsedMs: Long,
    ): TrackPoint = TrackPoint(
        seq = seq,
        latitude = lat,
        longitude = lon,
        elapsedMs = elapsedMs,
        accuracyM = 5.0,
        recordedAt = Instant.ofEpochMilli(1_700_000_000_000L + elapsedMs),
    )
}
