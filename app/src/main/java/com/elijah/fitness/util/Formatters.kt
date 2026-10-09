package com.elijah.fitness.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure formatting helpers for workout data. No Android imports so callers
 * can be unit-tested. Zone and locale default to system values but are
 * overridable for tests.
 */
object Formatters {

    /** e.g. "Wed 10 Sep" */
    fun formatDate(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale)
            .withZone(zone)
            .format(instant)

    /** e.g. "45 s", "32 min", "1h 05m", "2h 00m". */
    fun formatDuration(duration: Duration, locale: Locale = Locale.getDefault()): String {
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> String.format(locale, "%dh %02dm", hours, minutes)
            // Under a minute: show seconds rather than a confusing "0 min".
            totalMinutes == 0L -> String.format(locale, "%d s", duration.seconds.coerceAtLeast(0))
            else -> String.format(locale, "%d min", minutes)
        }
    }

    /** e.g. "5.20 km" */
    fun formatDistanceKm(meters: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.2f km", meters / 1000.0)

    /** e.g. "5:20 /km" — from raw seconds-per-km. Returns "-" for zero. */
    fun formatPace(secondsPerKm: Long, locale: Locale = Locale.getDefault()): String {
        if (secondsPerKm <= 0L) return "-"
        return formatPaceValue(secondsPerKm, locale) + " /km"
    }

    /** e.g. "5:20" — pace without the unit, for screens that style the unit separately. */
    fun formatPaceValue(secondsPerKm: Long, locale: Locale = Locale.getDefault()): String {
        if (secondsPerKm <= 0L) return "-"
        val minutes = secondsPerKm / 60
        val seconds = secondsPerKm % 60
        return String.format(locale, "%d:%02d", minutes, seconds)
    }

    /** e.g. "22.4 km/h" */
    fun formatSpeed(kmh: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f km/h", kmh)
}
