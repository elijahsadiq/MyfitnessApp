package com.elijah.fitness.notifications

import com.elijah.fitness.data.model.RunningWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {

    private val zone = ZoneId.of("Europe/London")
    private val sixPm = LocalTime.of(18, 0)

    @Test
    fun `before the reminder time it fires later today`() {
        val now = ZonedDateTime.of(2026, 10, 7, 9, 30, 0, 0, zone)
        assertEquals(Duration.ofHours(8).plusMinutes(30), ReminderPlanner.delayUntilNext(now, sixPm))
    }

    @Test
    fun `after the reminder time it waits until tomorrow`() {
        val now = ZonedDateTime.of(2026, 10, 7, 19, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(23), ReminderPlanner.delayUntilNext(now, sixPm))
    }

    @Test
    fun `exactly at the reminder time it books tomorrow, not now`() {
        val now = ZonedDateTime.of(2026, 10, 7, 18, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(24), ReminderPlanner.delayUntilNext(now, sixPm))
    }

    @Test
    fun `no reminder when a workout is already logged today`() {
        val today = LocalDate.of(2026, 10, 7)
        val workouts = listOf(runAt("2026-10-07T06:30:00Z"))
        assertNull(ReminderPlanner.workoutsThisWeekIfNoneToday(workouts, today, zone))
    }

    @Test
    fun `counts this week's workouts from Monday when today is empty`() {
        val today = LocalDate.of(2026, 10, 7) // Wednesday
        val workouts = listOf(
            runAt("2026-10-05T07:00:00Z"), // Monday
            runAt("2026-10-06T07:00:00Z"), // Tuesday
            runAt("2026-10-04T07:00:00Z"), // last Sunday: previous week
        )
        assertEquals(2, ReminderPlanner.workoutsThisWeekIfNoneToday(workouts, today, zone))
    }

    @Test
    fun `an empty week counts zero`() {
        val today = LocalDate.of(2026, 10, 5)
        assertEquals(0, ReminderPlanner.workoutsThisWeekIfNoneToday(emptyList(), today, zone))
    }

    private fun runAt(iso: String) = RunningWorkout(
        id = iso,
        startTime = Instant.parse(iso),
        duration = Duration.ofMinutes(30),
        distanceMeters = 5_000.0,
    )
}
