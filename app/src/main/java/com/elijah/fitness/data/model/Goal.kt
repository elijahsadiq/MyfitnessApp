package com.elijah.fitness.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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

    /**
     * The unit the stored [targetValue] is measured in ("km", "sessions",
     * "min", "kg"). A data label, not screen text: screens word goals
     * through `ui/goals/GoalDisplay` ("2 h 30 min", never "150 min").
     */
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

    /**
     * Progress fraction, clamped to `[0.0, 1.0]`; exactly 1.0 once the
     * target is met ([meetsTarget]), so a met goal never reads "99%".
     */
    fun progressFraction(workouts: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): Double {
        if (targetValue <= 0.0) return 0.0
        val current = currentValue(workouts, zone)
        if (meetsTarget(current, targetValue)) return 1.0
        return (current / targetValue).coerceIn(0.0, 1.0)
    }

    /** True when [currentValue] meets or exceeds [targetValue] ([meetsTarget]). */
    fun isComplete(workouts: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        meetsTarget(currentValue(workouts, zone), targetValue)

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
            val d = w.startTime.atZone(zone).toLocalDate()
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
        val current = currentValue(workouts, zone)
        if (meetsTarget(current, targetValue)) return 0.0
        val remainingValue = (targetValue - current).coerceAtLeast(0.0)
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

    companion object {
        /** Relative slack for [meetsTarget]: far below anything a screen shows. */
        private const val TARGET_TOLERANCE = 1e-9

        /**
         * True when [current] meets [target], allowing for floating-point
         * noise: workouts are summed as Doubles, so swims of 600 m, 700 m
         * and 200 m can come to 1.4999999999999998 km rather than 1.5.
         * The slack is a billionth of the target (at least 1e-9), never
         * enough to count a real shortfall. `GoalDisplay` uses the same
         * test, so a goal that reads "1.5 / 1.5 km" is always Completed.
         */
        fun meetsTarget(current: Double, target: Double): Boolean {
            if (!current.isFinite() || !target.isFinite()) return false
            return current >= target - TARGET_TOLERANCE * maxOf(1.0, abs(target))
        }
    }
}

/**
 * Sum of kilometres for [DistanceWorkout]s inside the window. Strength
 * workouts in a mixed list are ignored (no distance to measure).
 *
 * Only the distance activities ([DISTANCE_ACTIVITIES]: running, cycling,
 * swimming, rowing) are allowed as an activity scope; anything else is
 * rejected in [init]. "Any activity" (null) is also rejected per the
 * activity×goalType matrix. Swim goals are stored and shown in km too.
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
        require(activityType in DISTANCE_ACTIVITIES) {
            "DistanceGoal requires RUNNING, CYCLING, SWIMMING or ROWING"
        }
    }

    override val unitLabel: String = "km"

    /**
     * Sums metres and converts once: whole-metre swims (laps × 25 or 50 m)
     * then add up exactly, where summing each workout's km would not
     * (0.6 + 0.7 + 0.2 = 1.4999999999999998).
     */
    override fun measure(workouts: List<Workout>): Double =
        workouts.filterIsInstance<DistanceWorkout>().sumOf { it.distanceMeters } / METRES_PER_KM

    companion object {
        private const val METRES_PER_KM = 1_000.0

        /** Activities a distance goal can track (phase 8d adds swimming and rowing). */
        val DISTANCE_ACTIVITIES: Set<ActivityType> = setOf(
            ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.SWIMMING, ActivityType.ROWING,
        )
    }
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
