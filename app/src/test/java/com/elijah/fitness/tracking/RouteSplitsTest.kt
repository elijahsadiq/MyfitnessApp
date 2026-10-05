package com.elijah.fitness.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RouteSplitsTest {

    @Test
    fun `empty track yields no splits`() {
        assertEquals(emptyList<RouteSplit>(), RouteSplits.compute(emptyList()))
    }

    @Test
    fun `single point yields no splits`() {
        val p = TrackPoint(0, 51.5, -0.12, 0, 5.0, Instant.EPOCH)
        assertEquals(emptyList<RouteSplit>(), RouteSplits.compute(listOf(p)))
    }

    @Test
    fun `well over two km produces two full splits plus a tail`() {
        // 1° lat ≈ 111.2 km on a sphere of radius 6 371 008.8 m. We use a
        // slightly larger step so Haversine rounding doesn't park the final
        // segment exactly on the 2 km boundary.
        val stepLat = 0.0010 // ≈ 111 m per step
        val points = mutableListOf<TrackPoint>()
        for (i in 0..24) {
            points += TrackPoint(
                seq = i,
                latitude = 51.5 + stepLat * i,
                longitude = -0.12,
                elapsedMs = i * 40_000L, // 40 s per ~111 m → ~6 min/km
                accuracyM = 5.0,
                recordedAt = Instant.ofEpochMilli(i * 40_000L),
            )
        }
        val splits = RouteSplits.compute(points)
        // ~2.67 km → two full-km splits + a tail.
        assertEquals(3, splits.size)
        assertFalse(splits[0].isPartial)
        assertFalse(splits[1].isPartial)
        assertTrue(splits[2].isPartial)
        splits.take(2).forEach { split ->
            assertTrue(
                "pace was ${split.paceMsPerKm}, expected ~360 000 ms/km",
                split.paceMsPerKm in 350_000..375_000,
            )
        }
    }

    @Test
    fun `tail less than a km is reported as partial`() {
        val stepLat = 0.0010 // ≈ 111 m per step
        val points = mutableListOf<TrackPoint>()
        for (i in 0..13) {
            points += TrackPoint(
                seq = i,
                latitude = 51.5 + stepLat * i,
                longitude = -0.12,
                elapsedMs = i * 40_000L,
                accuracyM = 5.0,
                recordedAt = Instant.ofEpochMilli(i * 40_000L),
            )
        }
        val splits = RouteSplits.compute(points)
        // ~1.45 km → first full km + ~0.45 km tail.
        assertEquals(2, splits.size)
        assertFalse(splits[0].isPartial)
        assertTrue(splits[1].isPartial)
        assertTrue(
            "tail distance was ${splits[1].distanceMeters}",
            splits[1].distanceMeters in 300.0..600.0,
        )
    }
}
