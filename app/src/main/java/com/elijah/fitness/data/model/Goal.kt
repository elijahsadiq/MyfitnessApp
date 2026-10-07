package com.elijah.fitness.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Root of the goal hierarchy — a target amount within a bounded day
 * window, optionally scoped to a single activity type.
 *
 * ### Inheritance level #4 (assignment task 5b)
 *
 * Every subclass shares the same *structural* fields (id, title, activity
 * scope, target, window, timestamps) **and** the same *filtering* logic:
 * "which workouts count for this goal?" That question is answered here on
 * the base class in [relevantWorkouts] — a workout counts when its start
 * date falls between [startDate] and [lastDay] inclusive, in the device's
 * local zone, and it matches [activityType] (or the goal applies to "any"
 * activity when it's null).
 *
 * Subclasses only supply *how to measure*, through [measure]. That split
 * is the whole point of the hierarchy: adding a fifth goal type (e.g.
 * calories burned) is a one-class change — no reimplementation of window
 * or activity filtering, no risk of drift between the four existing
 * subclasses.
 *
 * ### Type-safety across a mixed list
 *
 * The list of workouts a goal sees can contain any [Workout] subclass. A
 * [DistanceGoal] measuring strength volume, or a [StrengthVolumeGoal]
 * measuring kilometres, would be nonsense — so each subclass narrows the
 * list through `filterIsInstance` (never an unchecked cast) inside its
 * own [measure], silently ignoring shapes it can't measure.
 *
 * ### Phase-8b shape
 *
 * The window is a **chosen number of days** starting on [startDate].
 * [lastDay] is derived as `startDate + durationDays - 1` and is the LAST
 * day INCLUDED in the goal. The brief dropped the fixed week/month
 * periods along with the `GoalPeriod` enum.
 */
abstract class Goal(
    val id: String,
    val title: String,
    val activityType: ActivityType?,   // null means "any activity"
    val targetValue: Double,
    val startDate: LocalDate,
    val durationDays: Int,
    val createdAt: Instant,
    /**
     * True once the client has celebrated this goal's completion. The
     * server persists it so a completion is only announced the first
     * time; a fresh fetch of an already-notified goal never fires the
     * Snackbar again (phase 7 step 7).
     */
    val completedNotified: Boolean = false,
    /**
     * `true` when this goal is the user's featured "main" goal — at most
     * one per user, enforced in the server-side transaction. The Goals
     * screen's dark card renders this one.
     */
    val isMain: Boolean = false,
) {

    init {
        require(durationDays in 1..365) {
            "durationDays must be between 1 and 365; was $durationDays"
        }
    }

    /** The LAST day included in the goal (inclusive). */
    val lastDay: LocalDate get() = startDate.plusDays((durationDays - 1).toLong())

    /** Short unit string shown next to progress ("km", "sessions", "min", "kg"). */
    abstract val unitLabel: String

    /**
     * Compute the goal's current progress value from [workouts]. Subclasses
     * are handed only the *relevant* subset (window + activity already
     * filtered) and just need to sum/count the shapes they care about.
     */
    protected abstract fun measure(workouts: List<Workout>): Double

    /** Current progress value against the goal's target (unclamped). */
    fun currentValue(workouts: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): Double =
        measure(relevantWorkouts(workouts, zone))

    /** Progress fraction, clamped to `[0.0, 1.0]`. */
    fun progressFraction(workouts: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): Double {
        if (targetValue <= 0.0) return 0.0
        val raw = currentValue(workouts, zone) / targetValue
        return raw.coerceIn(0.0, 1.0)
    }

    /** True when [currentValue] meets or exceeds [targetValue]. */
    fun isComplete(workouts: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        currentValue(workouts, zone) >= targetValue

    /**
     * Days left including today — Monday with a last day of Sunday
     * returns 7, Sunday returns 1 ("Last day" territory), the day after
     * the window returns 0, further returns negative. The UI's "Last
     * day" label fires when this value is 1.
     */
    fun daysLeft(today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, lastDay) + 1

    /**
     * The subset of [workouts] whose local-date falls in `[startDate,
     * lastDay]` inclusive and whose [activityType] matches (or every
     * type when "any"). Subclasses never re-implement this filter —
     * that's exactly the point of the shared base class.
     */
    protected fun relevantWorkouts(
        workouts: List<Workout>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Workout> =
        workouts.filter { w ->
            val d = LocalDate.ofInstant(w.startTime, zone)
            val inWindow = !d.isBefore(startDate) && !d.isAfter(lastDay)
            val matchesActivity = activityType == null || w.type == activityType
            inWindow && matchesActivity
        }

    // -----------------------------------------------------------------
    // Phase-8b Manage-sheet statistics (same shape across all four
    // subclasses because they live on the base class)
    // -----------------------------------------------------------------

    /**
     * Current status in the goal's lifecycle:
     *   - [Status.Completed] takes priority — hitting the target locks
     *     the goal into Completed even once [lastDay] has passed.
     *   - [Status.Ended] when today is strictly after [lastDay] and the
     *     target wasn't met.
     *   - [Status.Active] otherwise (today is within the goal's days
     *     and the target isn't met yet).
     */
    fun status(
        workouts: List<Workout>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Status = when {
        isComplete(workouts, zone) -> Status.Completed
        today.isAfter(lastDay) -> Status.Ended
        else -> Status.Active
    }

    /** Number of matching workouts in the goal's days (post-filtering). */
    fun sessionsLogged(
        workouts: List<Workout>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int = relevantWorkouts(workouts, zone).size

    /**
     * Progress per day so far, counting today as a full day. Days
     * elapsed is clamped to a minimum of 1 so the first day never
     * divides by zero.
     */
    fun averagePerDay(
        workouts: List<Workout>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Double {
        val elapsed = daysElapsedInclusive(today).coerceAtLeast(1L)
        return currentValue(workouts, zone) / elapsed.toDouble()
    }

    /**
     * Target remaining divided by days remaining; `0.0` once complete,
     * and `0.0` once ended (the "needed per day" number stops being
     * meaningful after the window closes).
     */
    fun neededPerDay(
        workouts: List<Workout>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Double {
        val remainingValue = (targetValue - currentValue(workouts, zone)).coerceAtLeast(0.0)
        if (remainingValue <= 0.0) return 0.0
        val remainingDays = daysLeft(today)
        if (remainingDays <= 0L) return 0.0
        return remainingValue / remainingDays.toDouble()
    }

    private fun daysElapsedInclusive(today: LocalDate): Long {
        val clampedToday = when {
            today.isBefore(startDate) -> startDate
            today.isAfter(lastDay) -> lastDay
            else -> today
        }
        return ChronoUnit.DAYS.between(startDate, clampedToday) + 1
    }

    enum class Status { Active, Completed, Ended }
}

/**
 * Sum of kilometres for [DistanceWorkout]s inside the window. Strength
 * workouts in a mixed list are ignored (no distance to measure).
 *
 * Only [ActivityType.RUNNING] or [ActivityType.CYCLING] are allowed as
 * an activity scope; anything else is rejected in [init]. "Any activity"
 * (null) is also rejected per the Phase-8b activity×goalType matrix.
 */
class DistanceGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    startDate: LocalDate,
    durationDays: Int,
    createdAt: Instant,
    completedNotified: Boolean = false,
    isMain: Boolean = false,
) : Goal(id, title, activityType, targetValue, startDate, durationDays,
    createdAt, completedNotified, isMain) {

    init {
        require(activityType == ActivityType.RUNNING || activityType == ActivityType.CYCLING) {
            "DistanceGoal requires RUNNING or CYCLING"
        }
    }

    override val unitLabel: String = "km"

    override fun measure(workouts: List<Workout>): Double =
        workouts.filterIsInstance<DistanceWorkout>().sumOf { it.distanceKm() }
}

/**
 * Count of workouts inside the window — the plainest goal. Activity
 * scope narrows the count if set; null = any activity.
 */
class WorkoutCountGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    startDate: LocalDate,
    durationDays: Int,
    createdAt: Instant,
    completedNotified: Boolean = false,
    isMain: Boolean = false,
) : Goal(id, title, activityType, targetValue, startDate, durationDays,
    createdAt, completedNotified, isMain) {

    override val unitLabel: String = "sessions"

    override fun measure(workouts: List<Workout>): Double = workouts.size.toDouble()
}

/**
 * Sum of active minutes across every workout in the window. Activity
 * scope narrows the sum if set; null = any activity.
 */
class DurationGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    startDate: LocalDate,
    durationDays: Int,
    createdAt: Instant,
    completedNotified: Boolean = false,
    isMain: Boolean = false,
) : Goal(id, title, activityType, targetValue, startDate, durationDays,
    createdAt, completedNotified, isMain) {

    override val unitLabel: String = "min"

    override fun measure(workouts: List<Workout>): Double =
        workouts.sumOf { it.duration.toMinutes().toDouble() }
}

/**
 * Sum of `reps × weight` across every [StrengthWorkout] in the window.
 * Distance workouts in a mixed list are ignored. Must be scoped to
 * WEIGHTLIFTING per the Phase-8b matrix.
 */
class StrengthVolumeGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    startDate: LocalDate,
    durationDays: Int,
    createdAt: Instant,
    completedNotified: Boolean = false,
    isMain: Boolean = false,
) : Goal(id, title, activityType, targetValue, startDate, durationDays,
    createdAt, completedNotified, isMain) {

    init {
        require(activityType == ActivityType.WEIGHTLIFTING) {
            "StrengthVolumeGoal requires WEIGHTLIFTING"
        }
    }

    override val unitLabel: String = "kg"

    override fun measure(workouts: List<Workout>): Double =
        workouts.filterIsInstance<StrengthWorkout>().sumOf { it.totalVolumeKg() }
}
