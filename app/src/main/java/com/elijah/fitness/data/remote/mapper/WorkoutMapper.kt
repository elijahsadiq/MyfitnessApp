package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.dto.RoutePointDto
import com.elijah.fitness.data.remote.dto.RoutePointRequestDto
import com.elijah.fitness.data.remote.dto.StrengthSetRequestDto
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.data.remote.dto.WorkoutRequestDto
import com.elijah.fitness.tracking.TrackPoint
import com.elijah.fitness.util.CalorieEstimator
import java.time.Duration
import java.time.Instant

/**
 * Parse a wire / database activity value. `null` for a value this app
 * version doesn't know (e.g. a type added by a newer app on another
 * phone), so callers can skip that workout instead of crashing.
 */
fun activityTypeOrNull(value: String?): ActivityType? =
    value?.let { raw -> ActivityType.entries.firstOrNull { it.name == raw } }

/**
 * The single place a workout is rebuilt from its stored fields. Both the
 * server mapper and the Room mapper call it, so they can never disagree
 * on which class a type becomes. The `when` has no `else`: a new
 * [ActivityType] doesn't compile until it is handled here.
 */
internal fun buildWorkout(
    type: ActivityType,
    id: String,
    startTime: Instant,
    duration: Duration,
    distanceMeters: Double?,
    notes: String?,
    sets: List<StrengthSet>,
    poolLengthM: Int?,
    laps: Int?,
    caloriesKcal: Int?,
): Workout = when (type) {
    ActivityType.RUNNING -> RunningWorkout(
        id = id, startTime = startTime, duration = duration,
        distanceMeters = distanceMeters ?: 0.0, notes = notes, caloriesKcal = caloriesKcal,
    )
    ActivityType.CYCLING -> CyclingWorkout(
        id = id, startTime = startTime, duration = duration,
        distanceMeters = distanceMeters ?: 0.0, notes = notes, caloriesKcal = caloriesKcal,
    )
    ActivityType.SWIMMING -> SwimmingWorkout(
        id = id, startTime = startTime, duration = duration,
        distanceMeters = distanceMeters ?: 0.0, notes = notes,
        poolLengthM = poolLengthM, laps = laps, caloriesKcal = caloriesKcal,
    )
    ActivityType.ROWING -> RowingWorkout(
        id = id, startTime = startTime, duration = duration,
        distanceMeters = distanceMeters ?: 0.0, notes = notes, caloriesKcal = caloriesKcal,
    )
    ActivityType.WEIGHTLIFTING -> StrengthWorkout(
        id = id, startTime = startTime, duration = duration,
        sets = sets, notes = notes, caloriesKcal = caloriesKcal,
    )
}

/**
 * Server DTO -> domain model. The domain hierarchy (Workout ->
 * DistanceWorkout -> Running|Cycling|Swimming|RowingWorkout, and
 * StrengthWorkout) is reconstructed based on `activityType`. `id` is
 * populated from the server's `clientUuid` so the shared repository Flow
 * can still use it as a stable diff key.
 *
 * Returns `null` (never throws) for an unknown activity type or an
 * unreadable start time; list callers use `mapNotNull` to skip it.
 */
fun WorkoutDto.toDomain(): Workout? {
    val type = activityTypeOrNull(activityType) ?: return null
    val start = runCatching { Instant.parse(startTime) }.getOrNull() ?: return null
    return buildWorkout(
        type = type,
        id = clientUuid,
        startTime = start,
        duration = Duration.ofSeconds(durationSeconds.coerceAtLeast(0L)),
        distanceMeters = distanceMeters,
        notes = notes,
        sets = sets
            .sortedBy { it.setOrder }
            .map { StrengthSet(it.exerciseName, it.reps, it.weightKg) },
        poolLengthM = poolLengthM,
        laps = laps,
        caloriesKcal = caloriesKcal,
    )
}

/**
 * Domain model -> server request DTO. `startTime` is emitted as ISO-8601
 * UTC (`Instant.toString()` already gives that shape). Route points are
 * added by [toRequestDto] below when a GPS session is being saved; for
 * manual logs the default `emptyList()` means the server stores no route.
 *
 * Phase 8d: the server answers 422 to a value it doesn't accept, and a
 * rejected workout stays stuck in the upload queue. So the new fields
 * only go out when the server will take them:
 * - `poolLengthM` / `laps`: swims only, both together, inside the
 *   server's ranges ([SwimmingWorkout.hasValidPoolLaps]). Otherwise both
 *   are left out; `distanceMeters` still carries the swim.
 * - `caloriesKcal`: only inside `0..`[CalorieEstimator.MAX_WORKOUT_KCAL].
 * - Swims and lifts never send a route ([canCarryRoute]).
 */
fun Workout.toRequestDto(routePoints: List<TrackPoint> = emptyList()): WorkoutRequestDto {
    val poolSwim = (this as? SwimmingWorkout)?.takeIf { it.hasValidPoolLaps() }
    return WorkoutRequestDto(
        clientUuid = id,
        activityType = type.name,
        startTime = startTime.toString(),
        durationSeconds = duration.seconds,
        distanceMeters = (this as? DistanceWorkout)?.distanceMeters,
        poolLengthM = poolSwim?.poolLengthM,
        laps = poolSwim?.laps,
        caloriesKcal = caloriesKcal?.takeIf { it in 0..CalorieEstimator.MAX_WORKOUT_KCAL },
        notes = notes,
        sets = (this as? StrengthWorkout)?.sets?.map {
            StrengthSetRequestDto(it.exercise, it.reps, it.weightKg)
        } ?: emptyList(),
        routePoints = if (canCarryRoute(type)) routePoints.map { it.toRequestDto() } else emptyList(),
    )
}

/** GPS routes belong to runs, rides and rows; swims and workouts never have one. */
fun canCarryRoute(type: ActivityType): Boolean = when (type) {
    ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.ROWING -> true
    ActivityType.SWIMMING, ActivityType.WEIGHTLIFTING -> false
}

fun TrackPoint.toRequestDto(): RoutePointRequestDto = RoutePointRequestDto(
    seq = seq,
    latitude = latitude,
    longitude = longitude,
    elapsedMs = elapsedMs,
    accuracyM = accuracyM,
    recordedAt = recordedAt.toString(),
)

fun RoutePointDto.toDomain(): TrackPoint = TrackPoint(
    seq = seq,
    latitude = latitude,
    longitude = longitude,
    elapsedMs = elapsedMs,
    accuracyM = accuracyM,
    recordedAt = Instant.parse(recordedAt),
)
