package com.elijah.fitness.data.model

import java.time.Duration
import java.time.Instant
import java.util.Objects

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
 *
 * ### What this level leaves abstract
 * - [type] identifies the concrete kind (running, cycling, weightlifting).
 * - [metValue] returns the MET (metabolic equivalent of task) for the
 *   workout. It's a function, not a `val`, because distance workouts derive
 *   their MET from the speed they were performed at.
 *
 * [caloriesBurned] is `open` (Kotlin's word for "may be overridden") rather
 * than `abstract` because the formula `MET × body-weight-kg × hours` is the
 * same for every subclass in phase 1. A future subclass could override it
 * if a more accurate model is needed.
 *
 * Note on inheritance level #1 (assignment task 5b): this two-level hierarchy
 * (`Workout` → `DistanceWorkout` → concrete) lets running and cycling share
 * distance/speed maths while keeping the strength type free of unrelated
 * fields.
 */
abstract class Workout(
    val id: String,
    val startTime: Instant,
    val duration: Duration,
    val notes: String?
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Workout) return false
        return type == other.type &&
            id == other.id &&
            startTime == other.startTime &&
            duration == other.duration &&
            notes == other.notes
    }

    override fun hashCode(): Int =
        Objects.hash(type, id, startTime, duration, notes)
}

/**
 * Intermediate abstract class for workouts measured by distance
 * (running, cycling).
 *
 * ### Why this level exists
 * It owns [distanceMeters] plus the [distanceKm] and [averageSpeedKmh]
 * derivations so both `RunningWorkout` and `CyclingWorkout` inherit them for
 * free. Concrete subclasses only need to pick the right MET band for their
 * activity and, for running, expose pace.
 */
abstract class DistanceWorkout(
    id: String,
    startTime: Instant,
    duration: Duration,
    notes: String?,
    val distanceMeters: Double
) : Workout(id, startTime, duration, notes) {

    fun distanceKm(): Double = distanceMeters / 1000.0

    fun averageSpeedKmh(): Double {
        val hours = duration.toMillis() / 3_600_000.0
        return if (hours == 0.0) 0.0 else distanceKm() / hours
    }

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is DistanceWorkout) return false
        return distanceMeters == other.distanceMeters
    }

    override fun hashCode(): Int =
        31 * super.hashCode() + distanceMeters.hashCode()
}
