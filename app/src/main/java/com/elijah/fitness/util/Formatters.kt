package com.elijah.fitness.util

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pure formatting helpers for workout data. No Android imports so callers
 * can be unit-tested. Zone and locale default to system values but are
 * overridable for tests. Durations are not here: they go through
 * [DurationFormatter] so their words come from strings.xml.
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

    /**
     * e.g. "1,500" — whole metres with a thousands separator, no unit
     * (swims). The unit comes from strings.xml ([MeasureText.metres]).
     */
    fun formatMetresValue(meters: Double, locale: Locale = Locale.getDefault()): String =
        formatWholeNumber(meters, locale)

    /**
     * e.g. "1,982" — calories rounded to a whole kcal with a thousands
     * separator, no unit. Rounds (never truncates) so a value worked out
     * now matches the same workout's stored value.
     */
    fun formatKcalValue(kcal: Double, locale: Locale = Locale.getDefault()): String =
        formatWholeNumber(kcal, locale)

    fun formatKcalValue(kcal: Int, locale: Locale = Locale.getDefault()): String =
        formatWholeNumber(kcal.toDouble(), locale)

    /**
     * A goal amount (phase 8d): a whole number reads whole with a
     * thousands separator ("50", "20,000"), anything else with one
     * decimal ("10.5", "12.3"). Goal targets are Doubles because a swim
     * distance moves in 0.5 km steps, so this never truncates.
     */
    fun formatGoalAmount(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) return formatWholeNumber(0.0, locale)
        return if (isWhole(value)) {
            formatWholeNumber(value, locale)
        } else {
            oneDecimal(locale).format(value)
        }
    }

    /**
     * [value] rounded to one decimal, without a trailing ".0": 1.66 →
     * "1.7", 2.8 → "2.8", 1.96 → "2" (the New goal sheet's per-day and
     * per-week amounts).
     */
    fun formatTenths(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) return formatWholeNumber(0.0, locale)
        return formatGoalAmount(roundToTenths(value), locale)
    }

    /** [value] rounded half up to one decimal place. */
    fun roundToTenths(value: Double): Double =
        if (value.isFinite()) (value * 10.0).roundToLong() / 10.0 else 0.0

    private fun isWhole(value: Double): Boolean =
        abs(value - value.roundToLong().toDouble()) < WHOLE_EPSILON

    private fun oneDecimal(locale: Locale): NumberFormat =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            roundingMode = RoundingMode.HALF_UP
        }

    /** Closer to a whole number than this counts as whole (floating-point noise). */
    private const val WHOLE_EPSILON = 1e-9

    /** Rounded, never negative; NaN / infinity read "0" instead of crashing. */
    private fun formatWholeNumber(value: Double, locale: Locale): String {
        val whole = if (value.isFinite()) value.coerceAtLeast(0.0).roundToLong() else 0L
        return NumberFormat.getIntegerInstance(locale).format(whole)
    }
}
