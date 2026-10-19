package com.elijah.fitness.ui.dashboard

import com.elijah.fitness.data.model.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * What the Home calendar draws: [today], the [selected] day (the hero
 * card shows its week, the list below shows its workouts) and every day
 * that has at least one workout (the dots).
 */
data class CalendarUi(
    val today: LocalDate,
    val selected: LocalDate,
    val workoutDays: Set<LocalDate>,
)

/**
 * Date maths for the Home calendar — a week strip that pulls down into
 * a month. Pure Kotlin, so the paging and selection rules are unit
 * tested without a device.
 *
 * Weeks start on Monday, like the rest of the app. Pages run from
 * [MONTHS_BACK] months ago up to the current week / month: nothing after
 * today can be picked.
 */
object CalendarMath {

    /** A month grid always has six rows, so its height never jumps. */
    const val GRID_ROWS = 6
    const val DAYS_PER_WEEK = 7

    /** How far back the calendar pages go (10 years). */
    const val MONTHS_BACK = 120L

    /** Hero card title for the selected day's week. */
    sealed interface WeekLabel {
        data object ThisWeek : WeekLabel
        data object LastWeek : WeekLabel
        data class Range(val start: LocalDate) : WeekLabel
    }

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** First cell of [month]'s grid: the Monday on or before the 1st. */
    fun gridStart(month: YearMonth): LocalDate = weekStart(month.atDay(1))

    /** The grid row (0-based) [date] sits on in its own month. */
    fun rowInMonth(date: LocalDate): Int =
        (ChronoUnit.DAYS.between(gridStart(YearMonth.from(date)), date) / DAYS_PER_WEEK).toInt()

    // ---------- range ----------

    private fun firstMonth(today: LocalDate): YearMonth =
        YearMonth.from(today).minusMonths(MONTHS_BACK)

    /** Earliest day that can be picked: the 1st of the first month. */
    fun earliest(today: LocalDate): LocalDate = firstMonth(today).atDay(1)

    fun isSelectable(date: LocalDate, today: LocalDate): Boolean =
        !date.isAfter(today) && !date.isBefore(earliest(today))

    /** [date] moved into the range that can be picked. */
    fun clamp(date: LocalDate, today: LocalDate): LocalDate = when {
        date.isAfter(today) -> today
        date.isBefore(earliest(today)) -> earliest(today)
        else -> date
    }

    // ---------- paging (the last page is always the current one) ----------

    private fun firstWeek(today: LocalDate): LocalDate = weekStart(earliest(today))

    fun weekPageCount(today: LocalDate): Int =
        (ChronoUnit.WEEKS.between(firstWeek(today), weekStart(today)) + 1).toInt()

    /** Monday of the week shown on [page]. */
    fun weekForPage(page: Int, today: LocalDate): LocalDate =
        firstWeek(today).plusWeeks(page.toLong())

    fun pageForWeek(date: LocalDate, today: LocalDate): Int =
        ChronoUnit.WEEKS.between(firstWeek(today), weekStart(date)).toInt()
            .coerceIn(0, weekPageCount(today) - 1)

    fun monthPageCount(): Int = (MONTHS_BACK + 1).toInt()

    fun monthForPage(page: Int, today: LocalDate): YearMonth =
        firstMonth(today).plusMonths(page.toLong())

    fun pageForMonth(month: YearMonth, today: LocalDate): Int =
        ChronoUnit.MONTHS.between(firstMonth(today), month).toInt()
            .coerceIn(0, monthPageCount() - 1)

    // ---------- swiping moves the selection ----------

    /** Swiping the week strip keeps the same weekday in the new week. */
    fun selectionForWeek(monday: LocalDate, current: LocalDate, today: LocalDate): LocalDate =
        clamp(monday.plusDays((current.dayOfWeek.value - 1).toLong()), today)

    /** Swiping the month keeps the day of the month (31 March → 28 February). */
    fun selectionForMonth(month: YearMonth, current: LocalDate, today: LocalDate): LocalDate =
        clamp(month.atDay(minOf(current.dayOfMonth, month.lengthOfMonth())), today)

    // ---------- workouts ----------

    /** Every local day with at least one workout. */
    fun workoutDays(workouts: List<Workout>, zone: ZoneId): Set<LocalDate> =
        workouts.mapTo(HashSet()) { it.startTime.atZone(zone).toLocalDate() }

    /** Workouts started on [date], newest first (the History order). */
    fun workoutsOn(workouts: List<Workout>, date: LocalDate, zone: ZoneId): List<Workout> =
        workouts
            .filter { it.startTime.atZone(zone).toLocalDate() == date }
            .sortedByDescending { it.startTime }

    // ---------- labels ----------

    fun weekLabel(selected: LocalDate, today: LocalDate): WeekLabel {
        val week = weekStart(selected)
        val thisWeek = weekStart(today)
        return when (week) {
            thisWeek -> WeekLabel.ThisWeek
            thisWeek.minusWeeks(1) -> WeekLabel.LastWeek
            else -> WeekLabel.Range(week)
        }
    }

    /**
     * Monday–Sunday range for the hero card: "13 – 19 Oct",
     * "29 Sep – 5 Oct", "13 – 19 Oct 2025", "29 Dec 2025 – 4 Jan 2026".
     * The year only appears when it isn't the current one.
     */
    fun formatWeekRange(start: LocalDate, today: LocalDate, locale: Locale): String {
        val end = start.plusDays((DAYS_PER_WEEK - 1).toLong())
        fun fmt(date: LocalDate, pattern: String) =
            date.format(DateTimeFormatter.ofPattern(pattern, locale))
        if (start.year != end.year) {
            return "${fmt(start, "d MMM yyyy")} – ${fmt(end, "d MMM yyyy")}"
        }
        val year = if (end.year != today.year) " ${end.year}" else ""
        return if (start.month == end.month) {
            "${fmt(start, "d")} – ${fmt(end, "d MMM")}$year"
        } else {
            "${fmt(start, "d MMM")} – ${fmt(end, "d MMM")}$year"
        }
    }

    /** "Mon 13 Oct", with the year when it isn't the current one. */
    fun formatDay(date: LocalDate, today: LocalDate, locale: Locale): String {
        val pattern = if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** "October 2026" — the calendar header. */
    fun formatMonth(month: YearMonth, locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
}
