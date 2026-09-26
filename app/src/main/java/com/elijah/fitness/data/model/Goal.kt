package com.elijah.fitness.data.model

import java.time.Instant

/**
 * The two goal windows phase 7 ships with. Custom date ranges and
 * auto-recurring goals are explicitly out of scope.
 */
enum class GoalPeriod { THIS_WEEK, THIS_MONTH }

/**
 * Root of the goal hierarchy — a target amount within a bounded time
 * window, optionally scoped to a single activity type.
 *
 * ### Inheritance level #4 (assignment task 5b)
 *
 * Every subclass shares the same *structural* fields (id, title, activity
 * scope, target, window, timestamps) **and** the same *filtering* logic:
 * "which workouts count for this goal?" That question is answered here on
 * the base class in [relevantWorkouts] — a workout counts when it falls
 * inside the half-open `[periodStart, periodEnd)` range and matches
 * [activityType] (or the goal applies to "any" activity when it's null).
 *
 * Subclasses only supply *how to measure*, through [measure]. That split
 * is the whole point of the hierarchy: adding a fifth goal type (e.g.
 * calories burned) is a one-class change — no reimplementation of period
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
 */
abstract class Goal(
    val id: String,
    val title: String,
    val activityType: ActivityType?,   // null means "any activity"
    val targetValue: Double,
    val period: GoalPeriod,
    val periodStart: Instant,
    val periodEnd: Instant,             // exclusive — see GoalPeriodResolver
    val createdAt: Instant,
    /**
     * True once the client has celebrated this goal's completion. The
     * server persists it so a completion is only announced the first
     * time; a fresh fetch of an already-notified goal never fires the
     * Snackbar again (phase 7 step 7).
     */
    val completedNotified: Boolean = false,
) {

    /** Short unit string shown next to progress ("km", "workouts", "min", "kg"). */
    abstract val unitLabel: String

    /**
     * Compute the goal's current progress value from [workouts]. Subclasses
     * are handed only the *relevant* subset (period + activity already
     * filtered) and just need to sum/count the shapes they care about.
     */
    protected abstract fun measure(workouts: List<Workout>): Double

    /** Current progress value against the goal's target (unclamped). */
    fun currentValue(workouts: List<Workout>): Double =
        measure(relevantWorkouts(workouts))

    /** Progress fraction, clamped to `[0.0, 1.0]`. */
    fun progressFraction(workouts: List<Workout>): Double {
        if (targetValue <= 0.0) return 0.0
        val raw = currentValue(workouts) / targetValue
        return raw.coerceIn(0.0, 1.0)
    }

    /** True when [currentValue] meets or exceeds [targetValue]. */
    fun isComplete(workouts: List<Workout>): Boolean =
        currentValue(workouts) >= targetValue

    /**
     * The subset of [workouts] that falls inside this goal's half-open
     * period and matches its [activityType] (or every type when it's
     * "any"). Subclasses never need to re-implement this filter — that's
     * exactly the point of the shared base class.
     */
    protected fun relevantWorkouts(workouts: List<Workout>): List<Workout> =
        workouts.filter { w ->
            val inPeriod = !w.startTime.isBefore(periodStart) &&
                w.startTime.isBefore(periodEnd)
            val matchesActivity = activityType == null || w.type == activityType
            inPeriod && matchesActivity
        }
}

/**
 * Sum of kilometres for [DistanceWorkout]s inside the window. Strength
 * workouts in a mixed list are ignored (no distance to measure).
 *
 * Only [ActivityType.RUNNING], [ActivityType.CYCLING] or "any" (null)
 * make sense as an activity scope; a [DistanceGoal] scoped to
 * WEIGHTLIFTING is rejected in [init].
 */
class DistanceGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    period: GoalPeriod,
    periodStart: Instant,
    periodEnd: Instant,
    createdAt: Instant,
    completedNotified: Boolean = false,
) : Goal(id, title, activityType, targetValue, period, periodStart, periodEnd,
    createdAt, completedNotified) {

    init {
        require(activityType != ActivityType.WEIGHTLIFTING) {
            "DistanceGoal doesn't apply to WEIGHTLIFTING"
        }
    }

    override val unitLabel: String = "km"

    override fun measure(workouts: List<Workout>): Double =
        workouts.filterIsInstance<DistanceWorkout>().sumOf { it.distanceKm() }
}

/**
 * Count of workouts inside the window, regardless of type — the plainest
 * goal. Activity scope narrows the count if set.
 */
class WorkoutCountGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    period: GoalPeriod,
    periodStart: Instant,
    periodEnd: Instant,
    createdAt: Instant,
    completedNotified: Boolean = false,
) : Goal(id, title, activityType, targetValue, period, periodStart, periodEnd,
    createdAt, completedNotified) {

    override val unitLabel: String = "workouts"

    override fun measure(workouts: List<Workout>): Double = workouts.size.toDouble()
}

/**
 * Sum of active minutes across every workout in the window, regardless of
 * type. Activity scope narrows the sum if set.
 */
class DurationGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    period: GoalPeriod,
    periodStart: Instant,
    periodEnd: Instant,
    createdAt: Instant,
    completedNotified: Boolean = false,
) : Goal(id, title, activityType, targetValue, period, periodStart, periodEnd,
    createdAt, completedNotified) {

    override val unitLabel: String = "min"

    override fun measure(workouts: List<Workout>): Double =
        workouts.sumOf { it.duration.toMinutes().toDouble() }
}

/**
 * Sum of `reps × weight` across every [StrengthWorkout] in the window.
 * Distance workouts in a mixed list are ignored.
 *
 * Only [ActivityType.WEIGHTLIFTING] or "any" (null) make sense as an
 * activity scope; anything else is rejected in [init].
 */
class StrengthVolumeGoal(
    id: String,
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
    period: GoalPeriod,
    periodStart: Instant,
    periodEnd: Instant,
    createdAt: Instant,
    completedNotified: Boolean = false,
) : Goal(id, title, activityType, targetValue, period, periodStart, periodEnd,
    createdAt, completedNotified) {

    init {
        require(activityType == null || activityType == ActivityType.WEIGHTLIFTING) {
            "StrengthVolumeGoal must be scoped to WEIGHTLIFTING or any activity"
        }
    }

    override val unitLabel: String = "kg"

    override fun measure(workouts: List<Workout>): Double =
        workouts.filterIsInstance<StrengthWorkout>().sumOf { it.totalVolumeKg() }
}
