package com.elijah.fitness.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class FormattersTest {

    // Locale.US keeps "Sep" as the short month name. Locale.UK on JDK 20+
    // switched to "Sept" following CLDR 42, which flakes across environments.
    private val locale = Locale.US
    private val zoneLondon: ZoneId = ZoneId.of("Europe/London")

    @Test
    fun `formatDate returns short weekday day month`() {
        // 2026-09-10 12:00 UTC is Thursday in Europe/London
        val instant = Instant.parse("2026-09-10T12:00:00Z")
        assertEquals("Thu 10 Sep", Formatters.formatDate(instant, zoneLondon, locale))
    }

    @Test
    fun `formatDuration for sub-hour prints minutes`() {
        assertEquals("32 min", Formatters.formatDuration(Duration.ofMinutes(32), locale))
    }

    @Test
    fun `formatDuration under a minute prints seconds`() {
        assertEquals("45 s", Formatters.formatDuration(Duration.ofSeconds(45), locale))
    }

    @Test
    fun `formatDuration for hours prints h and mm`() {
        val d = Duration.ofMinutes(65)
        assertEquals("1h 05m", Formatters.formatDuration(d, locale))
    }

    @Test
    fun `formatDuration for exact hour prints 00 minutes`() {
        assertEquals("2h 00m", Formatters.formatDuration(Duration.ofHours(2), locale))
    }

    @Test
    fun `formatDistanceKm to two decimals`() {
        assertEquals("5.20 km", Formatters.formatDistanceKm(5_200.0, locale))
    }

    @Test
    fun `formatPace returns colon-formatted minutes and seconds`() {
        assertEquals("5:20 /km", Formatters.formatPace(320L, locale))
    }

    @Test
    fun `formatPace returns dash for zero`() {
        assertEquals("-", Formatters.formatPace(0L, locale))
    }

    @Test
    fun `formatSpeed returns one decimal km per hour`() {
        assertEquals("22.4 km/h", Formatters.formatSpeed(22.42, locale))
    }
}
