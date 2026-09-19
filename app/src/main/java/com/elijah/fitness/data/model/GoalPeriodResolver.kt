package com.elijah.fitness.data.model

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Resolves period boundaries for [Goal]s in the device's time zone.
 *
 * ### Half-open windows
 * Every result is a `[start, endExclusive)` range. A workout that starts
 * exactly at `endExclusive` belongs to the *next* period — so a workout at
 * midnight Monday is never double-counted between this week's goal and
 * next week's goal.
 *
 * ### Injectable clock and zone
 * Every method takes a [ZoneId] and a [Clock] with sensible defaults, so
 * tests can pin "today" to a known date without touching the real system
 * clock.
 */
object GoalPeriodResolver {

    /**
     * Result of [currentWeek] / [currentMonth]. `endExclusive` is not
     * included in the period — see the class KDoc.
     */
    data class Bounds(val start: Instant, val endExclusive: Instant)

    /**
     * Monday 00:00 in [zone] up to (but not including) the following
     * Monday 00:00. Uses ISO weeks (Monday–Sunday).
     */
    fun currentWeek(
        zone: ZoneId = ZoneId.systemDefault(),
        clock: Clock = Clock.system(zone)
    ): Bounds {
        val today = LocalDate.now(clock)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val nextMonday = monday.plusWeeks(1)
        return Bounds(
            start = monday.atStartOfDay(zone).toInstant(),
            endExclusive = nextMonday.atStartOfDay(zone).toInstant()
        )
    }

    /**
     * The 1st of the current calendar month at 00:00 in [zone], up to (but
     * not including) the 1st of the following month at 00:00.
     */
    fun currentMonth(
        zone: ZoneId = ZoneId.systemDefault(),
        clock: Clock = Clock.system(zone)
    ): Bounds {
        val today = LocalDate.now(clock)
        val first = today.withDayOfMonth(1)
        val nextFirst = first.plusMonths(1)
        return Bounds(
            start = first.atStartOfDay(zone).toInstant(),
            endExclusive = nextFirst.atStartOfDay(zone).toInstant()
        )
    }

    /** Dispatch helper that picks the right window for [period]. */
    fun bounds(
        period: GoalPeriod,
        zone: ZoneId = ZoneId.systemDefault(),
        clock: Clock = Clock.system(zone)
    ): Bounds = when (period) {
        GoalPeriod.THIS_WEEK -> currentWeek(zone, clock)
        GoalPeriod.THIS_MONTH -> currentMonth(zone, clock)
    }
}
