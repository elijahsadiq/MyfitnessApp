package com.elijah.fitness.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Every test pins the clock to a known instant + zone so the boundaries
 * are deterministic. `Africa/Nairobi` (UTC+3, no DST) is used to make the
 * offset arithmetic obvious; the code under test is zone-agnostic.
 */
class GoalPeriodResolverTest {

    private val zone: ZoneId = ZoneId.of("Africa/Nairobi") // UTC+3, no DST

    private fun clockAt(iso: String): Clock =
        Clock.fixed(Instant.parse(iso), zone)

    // ---------------- currentWeek ----------------

    @Test
    fun `currentWeek returns Monday-to-next-Monday for a mid-week day`() {
        // 2026-09-16 is a Wednesday. In Nairobi (UTC+3) it's already the
        // 16th at 10:00 local when UTC is 07:00 on the 16th.
        val clock = clockAt("2026-09-16T07:00:00Z")
        val bounds = GoalPeriodResolver.currentWeek(zone, clock)
        // Monday 2026-09-14 00:00 Nairobi = 2026-09-13T21:00:00Z
        assertEquals(Instant.parse("2026-09-13T21:00:00Z"), bounds.start)
        // Next Monday 2026-09-21 00:00 Nairobi = 2026-09-20T21:00:00Z
        assertEquals(Instant.parse("2026-09-20T21:00:00Z"), bounds.endExclusive)
    }

    @Test
    fun `currentWeek treats Monday itself as the first day`() {
        val clock = clockAt("2026-09-14T05:00:00Z") // Mon 08:00 Nairobi
        val bounds = GoalPeriodResolver.currentWeek(zone, clock)
        assertEquals(Instant.parse("2026-09-13T21:00:00Z"), bounds.start)
        assertEquals(Instant.parse("2026-09-20T21:00:00Z"), bounds.endExclusive)
    }

    @Test
    fun `currentWeek on Sunday stays inside the same Mon-Sun week`() {
        val clock = clockAt("2026-09-20T18:00:00Z") // Sun 21:00 Nairobi
        val bounds = GoalPeriodResolver.currentWeek(zone, clock)
        assertEquals(Instant.parse("2026-09-13T21:00:00Z"), bounds.start)
        assertEquals(Instant.parse("2026-09-20T21:00:00Z"), bounds.endExclusive)
    }

    // ---------------- currentMonth ----------------

    @Test
    fun `currentMonth returns 1st-of-month to 1st-of-next-month`() {
        val clock = clockAt("2026-09-16T07:00:00Z") // mid-Sep in Nairobi
        val bounds = GoalPeriodResolver.currentMonth(zone, clock)
        // Sep 1 00:00 Nairobi = Aug 31 21:00 UTC
        assertEquals(Instant.parse("2026-08-31T21:00:00Z"), bounds.start)
        // Oct 1 00:00 Nairobi = Sep 30 21:00 UTC
        assertEquals(Instant.parse("2026-09-30T21:00:00Z"), bounds.endExclusive)
    }

    @Test
    fun `currentMonth handles month rollover to a shorter month`() {
        // 31 January in UTC → still January in Nairobi.
        val clock = clockAt("2026-01-31T12:00:00Z")
        val bounds = GoalPeriodResolver.currentMonth(zone, clock)
        assertEquals(Instant.parse("2025-12-31T21:00:00Z"), bounds.start)
        // Feb 1 00:00 Nairobi = Jan 31 21:00 UTC
        assertEquals(Instant.parse("2026-01-31T21:00:00Z"), bounds.endExclusive)
    }

    @Test
    fun `currentMonth handles year rollover`() {
        val clock = clockAt("2026-12-16T07:00:00Z")
        val bounds = GoalPeriodResolver.currentMonth(zone, clock)
        assertEquals(Instant.parse("2026-11-30T21:00:00Z"), bounds.start)
        assertEquals(Instant.parse("2026-12-31T21:00:00Z"), bounds.endExclusive)
    }

    // ---------------- bounds dispatch ----------------

    @Test
    fun `bounds dispatches on GoalPeriod`() {
        val clock = clockAt("2026-09-16T07:00:00Z")
        assertEquals(
            GoalPeriodResolver.currentWeek(zone, clock),
            GoalPeriodResolver.bounds(GoalPeriod.THIS_WEEK, zone, clock)
        )
        assertEquals(
            GoalPeriodResolver.currentMonth(zone, clock),
            GoalPeriodResolver.bounds(GoalPeriod.THIS_MONTH, zone, clock)
        )
    }
}
