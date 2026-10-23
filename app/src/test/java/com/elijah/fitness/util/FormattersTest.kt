package com.elijah.fitness.util

import org.junit.Assert.assertEquals
import org.junit.Test
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

    // Phase 8d: goal amounts are Doubles (swims move in 0.5 km steps).

    @Test
    fun `formatGoalAmount keeps whole numbers whole and halves to one decimal`() {
        assertEquals("10", Formatters.formatGoalAmount(10.0, locale))
        assertEquals("10.5", Formatters.formatGoalAmount(10.5, locale))
        assertEquals("6.5", Formatters.formatGoalAmount(6.5, locale))
        assertEquals("20,000", Formatters.formatGoalAmount(20_000.0, locale))
        assertEquals("12.3", Formatters.formatGoalAmount(12.25, locale))
        assertEquals("0", Formatters.formatGoalAmount(Double.NaN, locale))
    }

    @Test
    fun `formatTenths rounds to one decimal and drops a trailing zero`() {
        assertEquals("1.7", Formatters.formatTenths(50.0 / 30.0, locale))
        assertEquals("2.8", Formatters.formatTenths(12.0 * 7.0 / 30.0, locale))
        assertEquals("2", Formatters.formatTenths(1.96, locale))
        assertEquals("11.7", Formatters.formatTenths(50.0 * 7.0 / 30.0, locale))
    }
}
