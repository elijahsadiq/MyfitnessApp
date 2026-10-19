package com.elijah.fitness.ui.dashboard

import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Today is Monday 19 October 2026 throughout. */
class CalendarMathTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 19)
    private val uk = Locale.ENGLISH

    @Test
    fun `weeks start on Monday`() {
        assertEquals(LocalDate.of(2026, 10, 12), CalendarMath.weekStart(LocalDate.of(2026, 10, 18)))
        assertEquals(today, CalendarMath.weekStart(today))
    }

    @Test
    fun `month grid starts on the Monday on or before the 1st`() {
        // 1 October 2026 is a Thursday.
        assertEquals(LocalDate.of(2026, 9, 28), CalendarMath.gridStart(YearMonth.of(2026, 10)))
        // 1 June 2026 is a Monday.
        assertEquals(LocalDate.of(2026, 6, 1), CalendarMath.gridStart(YearMonth.of(2026, 6)))
    }

    @Test
    fun `row in month counts weeks from the grid start`() {
        assertEquals(0, CalendarMath.rowInMonth(LocalDate.of(2026, 10, 1)))
        assertEquals(3, CalendarMath.rowInMonth(today))
        assertEquals(4, CalendarMath.rowInMonth(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `last pages are the current week and month`() {
        val lastWeek = CalendarMath.weekPageCount(today) - 1
        assertEquals(today, CalendarMath.weekForPage(lastWeek, today))
        assertEquals(lastWeek, CalendarMath.pageForWeek(today, today))

        val lastMonth = CalendarMath.monthPageCount() - 1
        assertEquals(YearMonth.of(2026, 10), CalendarMath.monthForPage(lastMonth, today))
        assertEquals(lastMonth, CalendarMath.pageForMonth(YearMonth.of(2026, 10), today))
    }

    @Test
    fun `pages and dates round-trip`() {
        val date = LocalDate.of(2025, 3, 5)
        val week = CalendarMath.pageForWeek(date, today)
        assertEquals(CalendarMath.weekStart(date), CalendarMath.weekForPage(week, today))
        val month = CalendarMath.pageForMonth(YearMonth.from(date), today)
        assertEquals(YearMonth.of(2025, 3), CalendarMath.monthForPage(month, today))
    }

    @Test
    fun `future and too-old days cannot be picked`() {
        assertTrue(CalendarMath.isSelectable(today, today))
        assertFalse(CalendarMath.isSelectable(today.plusDays(1), today))
        val earliest = CalendarMath.earliest(today)
        assertTrue(CalendarMath.isSelectable(earliest, today))
        assertFalse(CalendarMath.isSelectable(earliest.minusDays(1), today))
        assertEquals(today, CalendarMath.clamp(today.plusDays(3), today))
        assertEquals(earliest, CalendarMath.clamp(earliest.minusYears(1), today))
    }

    @Test
    fun `swiping weeks keeps the weekday`() {
        // From Wed 14 Oct to the week of Mon 5 Oct → Wed 7 Oct.
        assertEquals(
            LocalDate.of(2026, 10, 7),
            CalendarMath.selectionForWeek(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 14), today),
        )
    }

    @Test
    fun `swiping into this week never lands after today`() {
        // Saturday last week → this week's Saturday is in the future → today.
        assertEquals(
            today,
            CalendarMath.selectionForWeek(today, LocalDate.of(2026, 10, 17), today),
        )
    }

    @Test
    fun `swiping months keeps the day of the month where it exists`() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CalendarMath.selectionForMonth(YearMonth.of(2026, 2), LocalDate.of(2026, 3, 31), today),
        )
        assertEquals(
            LocalDate.of(2026, 8, 19),
            CalendarMath.selectionForMonth(YearMonth.of(2026, 8), LocalDate.of(2026, 9, 19), today),
        )
        // 30 September → October: the 30th is still to come → today.
        assertEquals(
            today,
            CalendarMath.selectionForMonth(YearMonth.of(2026, 10), LocalDate.of(2026, 9, 30), today),
        )
    }

    @Test
    fun `workouts are grouped by local day, newest first`() {
        val zone = ZoneId.of("Asia/Singapore") // UTC+8
        // 17:00 UTC on the 12th is 01:00 on the 13th in Singapore.
        val lateRun = RunningWorkout(
            "a", Instant.parse("2026-10-12T17:00:00Z"), Duration.ofMinutes(30), 5_000.0,
        )
        val ride = CyclingWorkout(
            "b", Instant.parse("2026-10-13T09:00:00Z"), Duration.ofMinutes(60), 20_000.0,
        )
        val workouts = listOf(lateRun, ride)

        assertEquals(setOf(LocalDate.of(2026, 10, 13)), CalendarMath.workoutDays(workouts, zone))
        assertEquals(
            listOf("b", "a"),
            CalendarMath.workoutsOn(workouts, LocalDate.of(2026, 10, 13), zone).map { it.id },
        )
        assertTrue(CalendarMath.workoutsOn(workouts, LocalDate.of(2026, 10, 12), zone).isEmpty())
    }

    @Test
    fun `hero card names this week, last week, or the dates`() {
        assertEquals(CalendarMath.WeekLabel.ThisWeek, CalendarMath.weekLabel(today, today))
        assertEquals(
            CalendarMath.WeekLabel.LastWeek,
            CalendarMath.weekLabel(LocalDate.of(2026, 10, 18), today),
        )
        assertEquals(
            CalendarMath.WeekLabel.Range(LocalDate.of(2026, 10, 5)),
            CalendarMath.weekLabel(LocalDate.of(2026, 10, 9), today),
        )
    }

    @Test
    fun `week range shows the year only when it isn't this year`() {
        assertEquals("5 – 11 Oct", CalendarMath.formatWeekRange(LocalDate.of(2026, 10, 5), today, uk))
        assertEquals("28 Sep – 4 Oct", CalendarMath.formatWeekRange(LocalDate.of(2026, 9, 28), today, uk))
        assertEquals("6 – 12 Oct 2025", CalendarMath.formatWeekRange(LocalDate.of(2025, 10, 6), today, uk))
        assertEquals(
            "29 Dec 2025 – 4 Jan 2026",
            CalendarMath.formatWeekRange(LocalDate.of(2025, 12, 29), today, uk),
        )
    }

    @Test
    fun `day and month labels`() {
        assertEquals("Mon 12 Oct", CalendarMath.formatDay(LocalDate.of(2026, 10, 12), today, uk))
        assertEquals("Sun 12 Oct 2025", CalendarMath.formatDay(LocalDate.of(2025, 10, 12), today, uk))
        assertEquals("October 2026", CalendarMath.formatMonth(YearMonth.of(2026, 10), uk))
    }
}
