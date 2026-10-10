package com.elijah.fitness.data.local.db

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.tracking.TrackPoint
import java.time.Duration
import java.time.Instant

/**
 * Database row -> domain model. Rebuilds the same `Workout` hierarchy
 * the server mapper does, keyed on `activityType`.
 */
fun WorkoutWithSets.toDomain(): Workout {
    val row = workout
    val start = Instant.ofEpochMilli(row.startTimeMs)
    val duration = Duration.ofSeconds(row.durationSeconds)
    return when (row.activityType) {
        ActivityType.RUNNING.name -> RunningWorkout(
            id = row.clientUuid,
            startTime = start,
            duration = duration,
            distanceMeters = row.distanceMeters ?: 0.0,
            notes = row.notes,
        )
        ActivityType.CYCLING.name -> CyclingWorkout(
            id = row.clientUuid,
            startTime = start,
            duration = duration,
            distanceMeters = row.distanceMeters ?: 0.0,
            notes = row.notes,
        )
        ActivityType.WEIGHTLIFTING.name -> StrengthWorkout(
            id = row.clientUuid,
            startTime = start,
            duration = duration,
            sets = sets
                .sortedBy { it.setOrder }
                .map { StrengthSet(it.exerciseName, it.reps, it.weightKg) },
            notes = row.notes,
        )
        else -> error("Unknown activityType: ${row.activityType}")
    }
}

/** Domain model -> database row, for a workout logged on this phone. */
fun Workout.toEntity(syncState: SyncState, routeCached: Boolean): WorkoutEntity = WorkoutEntity(
    clientUuid = id,
    serverId = null,
    activityType = type.name,
    startTimeMs = startTime.toEpochMilli(),
    durationSeconds = duration.seconds,
    distanceMeters = (this as? DistanceWorkout)?.distanceMeters,
    notes = notes?.takeUnless { it.isBlank() },
    syncState = syncState,
    routeCached = routeCached,
)

fun Workout.setEntities(): List<StrengthSetEntity> =
    (this as? StrengthWorkout)?.sets?.mapIndexed { index, set ->
        StrengthSetEntity(
            workoutUuid = id,
            setOrder = index + 1,
            exerciseName = set.exercise,
            reps = set.reps,
            weightKg = set.weightKg,
        )
    } ?: emptyList()

/**
 * Server copy -> database row. The list endpoint leaves routes out, so
 * only a weightlifting row (which can't have one) counts as cached.
 */
fun WorkoutDto.toEntity(): WorkoutEntity = WorkoutEntity(
    clientUuid = clientUuid,
    serverId = id,
    activityType = activityType,
    startTimeMs = Instant.parse(startTime).toEpochMilli(),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    notes = notes?.takeUnless { it.isBlank() },
    syncState = SyncState.SYNCED,
    routeCached = activityType == ActivityType.WEIGHTLIFTING.name,
    uploadAttempted = true,
)

fun WorkoutDto.setEntities(): List<StrengthSetEntity> = sets.map {
    StrengthSetEntity(
        workoutUuid = clientUuid,
        setOrder = it.setOrder,
        exerciseName = it.exerciseName,
        reps = it.reps,
        weightKg = it.weightKg,
    )
}

fun TrackPoint.toEntity(workoutUuid: String): RoutePointEntity = RoutePointEntity(
    workoutUuid = workoutUuid,
    seq = seq,
    latitude = latitude,
    longitude = longitude,
    elapsedMs = elapsedMs,
    accuracyM = accuracyM,
    recordedAtMs = recordedAt.toEpochMilli(),
)

fun RoutePointEntity.toDomain(): TrackPoint = TrackPoint(
    seq = seq,
    latitude = latitude,
    longitude = longitude,
    elapsedMs = elapsedMs,
    accuracyM = accuracyM,
    recordedAt = Instant.ofEpochMilli(recordedAtMs),
)
