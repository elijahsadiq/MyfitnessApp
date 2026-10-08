package com.elijah.fitness.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RouteGeometryTest {

    private val start = Instant.parse("2026-10-08T10:00:00Z")

    // ---------- boundingBox ----------

    @Test
    fun `empty list returns null bounding box`() {
        assertNull(RouteGeometry.boundingBox(emptyList()))
    }

    @Test
    fun `single-point list returns a tiny envelope centred on the point`() {
        val only = fix(seq = 0, lat = 51.5074, lon = -0.1278, elapsedMs = 0L, offsetMs = 0L)
        val bbox = RouteGeometry.boundingBox(listOf(only))
        assertNotNull(bbox)
        // Caller still gets a usable envelope, not a zero-area point.
        assertTrue(bbox!!.latSpan > 0)
        assertTrue(bbox.lonSpan > 0)
        // Centred: midpoint matches the fix lat/lon.
        assertEquals(51.5074, (bbox.north + bbox.south) / 2, 1e-9)
        assertEquals(-0.1278, (bbox.east + bbox.west) / 2, 1e-9)
    }

    @Test
    fun `multi-point envelope pads every edge by padFraction of the span`() {
        val points = listOf(
            fix(0, 51.50, -0.10, 0L, 0L),
            fix(1, 51.60, -0.00, 10_000L, 10_000L),
        )
        val bbox = RouteGeometry.boundingBox(points, padFraction = 0.1)!!
        // Raw span is 0.1° in both dimensions; 10 % pad = 0.01° per side.
        assertEquals(51.61, bbox.north, 1e-9)
        assertEquals(51.49, bbox.south, 1e-9)
        assertEquals(0.01, bbox.east, 1e-9)
        assertEquals(-0.11, bbox.west, 1e-9)
    }

    @Test
    fun `padFraction of zero returns the exact envelope`() {
        val points = listOf(
            fix(0, 51.50, -0.10, 0L, 0L),
            fix(1, 51.60, -0.05, 10_000L, 10_000L),
        )
        val bbox = RouteGeometry.boundingBox(points, padFraction = 0.0)!!
        assertEquals(51.60, bbox.north, 1e-9)
        assertEquals(51.50, bbox.south, 1e-9)
        assertEquals(-0.05, bbox.east, 1e-9)
        assertEquals(-0.10, bbox.west, 1e-9)
    }

    // ---------- splitByPause ----------

    @Test
    fun `no pause produces a single segment`() {
        // Three contiguous points, 2 s apart, elapsed matches wall.
        val points = (0..2).map { i ->
            fix(seq = i, lat = 51.5 + i * 0.001, lon = -0.1, elapsedMs = i * 2_000L, offsetMs = i * 2_000L)
        }
        val segments = RouteGeometry.splitByPause(points)
        assertEquals(1, segments.size)
        assertEquals(3, segments[0].size)
    }

    @Test
    fun `empty list yields no segments and a single-point list yields one segment`() {
        assertEquals(emptyList<List<TrackPoint>>(), RouteGeometry.splitByPause(emptyList()))
        val one = listOf(fix(0, 51.5, -0.1, 0L, 0L))
        assertEquals(1, RouteGeometry.splitByPause(one).size)
    }

    @Test
    fun `a 30s pause between two points splits the route into two segments`() {
        // Session runs for 10 s, user pauses for 30 s wall-clock (elapsed
        // doesn't advance during the pause), then resumes for another 10 s.
        val before = (0..2).map { i ->
            fix(seq = i, lat = 51.5 + i * 0.001, lon = -0.1,
                elapsedMs = i * 5_000L, offsetMs = i * 5_000L)
        }
        // After the pause: wall offset jumps by 30 s but elapsed carries on
        // from where it left (10_000 ms).
        val after = (0..1).map { j ->
            fix(
                seq = 3 + j,
                lat = 51.504 + j * 0.001,
                lon = -0.1,
                elapsedMs = 10_000L + j * 5_000L,
                offsetMs = 10_000L + 30_000L + j * 5_000L,
            )
        }
        val segments = RouteGeometry.splitByPause(before + after)
        assertEquals(2, segments.size)
        assertEquals(3, segments[0].size)
        assertEquals(2, segments[1].size)
    }

    @Test
    fun `a GPS dropout grows wall and elapsed equally and must not split`() {
        // Fifteen seconds of normal tracking, then the device loses GPS for
        // 20 s — no points are recorded during that time but the session
        // clock keeps running — then fixes return. wall-delta = elapsed-
        // delta so pauseGap is ~0 and the segment stays intact.
        val before = (0..2).map { i ->
            fix(seq = i, lat = 51.5 + i * 0.001, lon = -0.1,
                elapsedMs = i * 5_000L, offsetMs = i * 5_000L)
        }
        val afterDropout = (0..1).map { j ->
            fix(
                seq = 3 + j,
                lat = 51.504 + j * 0.001,
                lon = -0.1,
                elapsedMs = 10_000L + 20_000L + j * 5_000L,
                offsetMs = 10_000L + 20_000L + j * 5_000L,
            )
        }
        val segments = RouteGeometry.splitByPause(before + afterDropout)
        assertEquals(1, segments.size)
        assertEquals(5, segments[0].size)
    }

    @Test
    fun `two pauses produce three segments`() {
        val p0 = (0..1).map { fix(it, 51.5 + it * 0.001, -0.1, it * 5_000L, it * 5_000L) }
        // 10 s pause after p0
        val p1 = (0..1).map {
            fix(2 + it, 51.502 + it * 0.001, -0.1,
                5_000L + it * 5_000L,
                5_000L + 10_000L + it * 5_000L)
        }
        // 20 s pause after p1
        val p2 = (0..1).map {
            fix(4 + it, 51.504 + it * 0.001, -0.1,
                10_000L + it * 5_000L,
                15_000L + 20_000L + it * 5_000L)
        }
        val segments = RouteGeometry.splitByPause(p0 + p1 + p2)
        assertEquals(3, segments.size)
        assertEquals(2, segments.map { it.size }.distinct().single())
    }

    @Test
    fun `custom minPauseGapMs lowers the threshold`() {
        // 500 ms pause: ignored by default (threshold = 2 s), caught by a
        // 200 ms threshold.
        val points = listOf(
            fix(0, 51.5, -0.1, 0L, 0L),
            fix(1, 51.501, -0.1, 5_000L, 5_500L),
        )
        assertEquals(1, RouteGeometry.splitByPause(points).size)
        assertEquals(2, RouteGeometry.splitByPause(points, minPauseGapMs = 200L).size)
    }

    // ---------- helpers ----------

    /**
     * Build a `TrackPoint`. `elapsedMs` is session-time (held during
     * pauses); `offsetMs` is wall-clock time since the session's
     * `start` instant. Keeping them separate parameters is what makes
     * pause vs GPS-dropout testable.
     */
    private fun fix(
        seq: Int,
        lat: Double,
        lon: Double,
        elapsedMs: Long,
        offsetMs: Long,
    ): TrackPoint = TrackPoint(
        seq = seq,
        latitude = lat,
        longitude = lon,
        elapsedMs = elapsedMs,
        accuracyM = 5.0,
        recordedAt = start.plusMillis(offsetMs),
    )
}
