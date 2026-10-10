package com.elijah.fitness.notifications

import com.elijah.fitness.data.model.Workout
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * The pure decisions behind the daily reminder, kept free of Android so
 * they're unit-tested: when the next one is due, and whether today's
 * should be shown at all.
 */
object ReminderPlanner {

    /**
     * Time from [now] until the next [at] on the clock. If today's slot
     * has passed (or is this very minute), that's tomorrow's.
     */
    fun delayUntilNext(now: ZonedDateTime, at: LocalTime): Duration {
        var next = now.toLocalDate().atTime(at).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /**
     * What today's reminder should say, or null when there's nothing to
     * remind about because a workout is already logged today.
     *
     * @return the number of workouts so far this week (Monday start),
     *   which the notification mentions so it reads as progress, not a
     *   nag.
     */
    fun workoutsThisWeekIfNoneToday(workouts: List<Workout>, today: LocalDate, zone: ZoneId): Int? {
        val days = workouts.map { LocalDate.ofInstant(it.startTime, zone) }
        if (today in days) return null
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return days.count { !it.isBefore(monday) && !it.isAfter(today) }
    }
}
