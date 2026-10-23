package com.elijah.fitness.data.model

import java.time.Duration
import java.time.Instant
import java.util.Objects
import kotlin.math.roundToLong

/**
 * Root of the workout hierarchy — a completed workout of any type.
 *
 * ### What this level owns
 * The fields every workout shares live here so subclasses never have to
 * redeclare them:
 * - [id]: a device-generated UUID string. It's the stable identity used by
 *   `DiffUtil` in Step 6 and becomes the sync key when a real backend arrives
 *   in phase 4.
 * - [startTime], [duration]: when the workout started and how long it lasted.
 * - [notes]: free-form user note, optional.
 * - [caloriesKcal]: the calories worked out when the workout was saved
 *   (phase 8d), from the profile weight at that time (70 kg when the
 *   profile has none). The weight is kept on the device, so this works
 *   offline too. `null` on workouts saved before that phase, and on ones
 *   saved before any profile weight was known on this device (offline
 *   right after updating from an older version) — those are worked out
 *   when shown.
 *
 * ### What this level leaves abstract
 * - [type] identifies the concrete kind (running, cycling, swimming,
 *   rowing, weightlifting).
 * - [metValue] returns the MET (metabolic equivalent of task) for the
 *   workout. It's a function, not a `val`, because distance workouts derive
 *   their MET from the speed they were performed at.
 *
 * [caloriesBurned] is `open` (Kotlin's word for "may be overridden") rather
 * than `abstract` because the formula `MET × body-weight-kg × hours` is the
 * same for every subclass in phase 1. A future subclass could override it
 * if a more accurate model is needed. Every subclass gets its calories
 * through polymorphism: the base runs the formula, each subclass only
 * answers [metValue].
 *
 * [energyKcal] is what screens show: the stored [caloriesKcal] when there
 * is one (so history doesn't change when the user edits their weight),
 * otherwise [caloriesBurned] worked out now.
 *
 * Note on inheritance level #1 (assignment task 5b): this two-level hierarchy
 * (`Workout` → `DistanceWorkout` → concrete) lets running, cycling,
 * swimming and rowing share distance/speed maths while keeping the
 * strength type free of unrelated fields.
 */
abstract class Workout(
    val id: String,
    val startTime: Instant,
    val duration: Duration,
    val notes: String?,
    val caloriesKcal: Int? = null,
) {

    abstract val type: ActivityType

    /**
     * MET for this workout. Protected so callers always go through
     * [caloriesBurned] — which is the only place the formula lives.
     */
    protected abstract fun metValue(): Double

    /** Estimated calories burned for a user of [userWeightKg]. */
    open fun caloriesBurned(userWeightKg: Double): Double {
        val hours = duration.toMillis() / 3_600_000.0
        return metValue() * userWeightKg * hours
    }

    /**
     * Calories to show for this workout: the value stored at save time,
     * or [caloriesBurned] for [weightKg] on older workouts without one.
     */
    fun energyKcal(weightKg: Double): Double =
        caloriesKcal?.toDouble() ?: caloriesBurned(weightKg)

    /**
     * Overrides [Any.equals]: two workouts are equal when every shared
     * field matches, so `DiffUtil` and the tests compare by value.
     * Subclasses extend it with their own fields.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Workout) return false
        return type == other.type &&
            id == other.id &&
            startTime == other.startTime &&
            duration == other.duration &&
            notes == other.notes &&
            caloriesKcal == other.caloriesKcal
    }

    /** Overrides [Any.hashCode] to match [equals]. */
    override fun hashCode(): Int =
        Objects.hash(type, id, startTime, duration, notes, caloriesKcal)
}

/**
 * Intermediate abstract class for workouts measured by distance
 * (running, cycling, swimming, rowing).
 *
 * ### Why this level exists
 * It owns [distanceMeters] plus the [distanceKm] and [averageSpeedKmh]
 * derivations so `RunningWorkout`, `CyclingWorkout`, `SwimmingWorkout` and
 * `RowingWorkout` all inherit them for free. Concrete subclasses only need
 * to pick the right MET for their activity, the split their sport quotes
 * pace over ([paceSplitMeters]: per km, per 100 m, per 500 m) and, for
 * swims, the unit the distance is shown in ([distanceUnit]).
 */
abstract class DistanceWorkout(
    id: String,
    startTime: Instant,
    duration: Duration,
    notes: String?,
    val distanceMeters: Double,
    caloriesKcal: Int? = null,
) : Workout(id, startTime, duration, notes, caloriesKcal) {

    fun distanceKm(): Double = distanceMeters / 1000.0

    fun averageSpeedKmh(): Double {
        val hours = duration.toMillis() / 3_600_000.0
        return if (hours == 0.0) 0.0 else distanceKm() / hours
    }

    /**
     * The unit this workout's distance is shown in: kilometres by
     * default. Swimming overrides it to metres ("1,500 m").
     */
    open val distanceUnit: DistanceUnit = DistanceUnit.KILOMETRES

    /**
     * The distance a pace is quoted over: 1 km by default. Swimming
     * overrides it to 100 m and rowing to 500 m, the splits those sports
     * use.
     */
    open val paceSplitMeters: Double = 1_000.0

    /**
     * Seconds per [paceSplitMeters], rounded to the nearest second
     * (1,500 m in 32:10 is 128.7 s per 100 m, shown as "2:09"). Returns 0
     * ("no pace") below [MIN_PACE_DISTANCE_M]: a few metres of GPS drift
     * would otherwise show a nonsense pace.
     */
    fun paceSecondsPerSplit(): Long {
        if (distanceMeters < MIN_PACE_DISTANCE_M) return 0L
        return (duration.seconds.toDouble() / (distanceMeters / paceSplitMeters)).roundToLong()
    }

    /** Overrides [Workout.equals] to compare the distance as well. */
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is DistanceWorkout) return false
        return distanceMeters == other.distanceMeters
    }

    /** Overrides [Workout.hashCode] to match [equals]. */
    override fun hashCode(): Int =
        31 * super.hashCode() + distanceMeters.hashCode()

    companion object {
        /** 10 m — below this, pace isn't meaningful. */
        const val MIN_PACE_DISTANCE_M: Double = 10.0
    }
}
