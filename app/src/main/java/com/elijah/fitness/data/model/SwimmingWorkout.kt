package com.elijah.fitness.data.model

import java.time.Duration
import java.time.Instant
import java.util.Objects

/**
 * A completed swim (phase 8d). A [DistanceWorkout], so it inherits the
 * distance, speed and pace maths and counts toward distance goals.
 *
 * Pool swims carry [poolLengthM] and [laps], and [distanceMeters] is
 * `laps × poolLengthM`. Both are `null` for an open-water swim or an
 * older row without them. Screens show a swim's distance in metres
 * ("1,500 m") and its pace per 100 m ("2:09 /100 m"). A swim never
 * carries a GPS route.
 */
class SwimmingWorkout(
    id: String,
    startTime: Instant,
    duration: Duration,
    distanceMeters: Double,
    notes: String? = null,
    val poolLengthM: Int? = null,
    val laps: Int? = null,
    caloriesKcal: Int? = null,
) : DistanceWorkout(id, startTime, duration, notes, distanceMeters, caloriesKcal) {

    /** Overrides [Workout.type]: marks this workout as a swim for the mappers, filters and labels. */
    override val type: ActivityType = ActivityType.SWIMMING

    /**
     * Overrides [Workout.metValue] with one swimming MET (7.0): a swim is
     * logged after the session, so there is no speed to band on.
     */
    override fun metValue(): Double = MetValues.SWIMMING

    /** Overrides [DistanceWorkout.distanceUnit]: swims are measured in metres ("1,500 m"), not km. */
    override val distanceUnit: DistanceUnit = DistanceUnit.METRES

    /** Overrides [DistanceWorkout.paceSplitMeters]: swimmers quote pace per 100 m. */
    override val paceSplitMeters: Double = 100.0

    /** Seconds per 100 m, e.g. 129 for "2:09 /100 m". 0 when there is no pace. */
    fun pacePer100mSeconds(): Long = paceSecondsPerSplit()

    /**
     * True when both [laps] and [poolLengthM] are set and inside the
     * ranges the server accepts ([LAPS_RANGE], [POOL_LENGTH_RANGE_M]).
     * Only then are they uploaded; the distance alone still carries the
     * swim otherwise, so one odd value can never block a sync.
     */
    fun hasValidPoolLaps(): Boolean {
        val l = laps ?: return false
        val p = poolLengthM ?: return false
        return l in LAPS_RANGE && p in POOL_LENGTH_RANGE_M
    }

    /** Overrides [DistanceWorkout.equals] to compare laps and pool length as well. */
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is SwimmingWorkout) return false
        return poolLengthM == other.poolLengthM && laps == other.laps
    }

    /** Overrides [DistanceWorkout.hashCode] to match [equals]. */
    override fun hashCode(): Int =
        31 * super.hashCode() + Objects.hash(poolLengthM, laps)

    companion object {
        /** Laps the server accepts (`WorkoutController::LAPS_MIN..LAPS_MAX`). */
        val LAPS_RANGE: IntRange = 1..400

        /** Pool lengths the server accepts, in metres (`POOL_LENGTH_MIN..POOL_LENGTH_MAX`). */
        val POOL_LENGTH_RANGE_M: IntRange = 10..100
    }
}
