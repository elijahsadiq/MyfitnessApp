package com.elijah.fitness.data.local.db

import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.data.remote.mapper.activityTypeOrNull
import com.elijah.fitness.data.remote.mapper.buildWorkout
import com.elijah.fitness.data.remote.mapper.canCarryRoute
import com.elijah.fitness.tracking.TrackPoint
import java.time.Duration
import java.time.Instant

/**
 * Database row -> domain model. Rebuilds the same `Workout` hierarchy
 * the server mapper does (it shares `buildWorkout`), keyed on
 * `activityType`. Returns `null` for a type this app version doesn't
 * know, so a row like that is skipped instead of crashing the list.
 */
fun WorkoutWithSets.toDomain(): Workout? {
    val row = workout
    val type = activityTypeOrNull(row.activityType) ?: return null
    return buildWorkout(
        type = type,
        id = row.clientUuid,
        startTime = Instant.ofEpochMilli(row.startTimeMs),
        duration = Duration.ofSeconds(row.durationSeconds.coerceAtLeast(0L)),
        distanceMeters = row.distanceMeters,
        notes = row.notes,
        sets = sets
            .sortedBy { it.setOrder }
            .map { StrengthSet(it.exerciseName, it.reps, it.weightKg) },
        poolLengthM = row.poolLengthM,
        laps = row.laps,
        caloriesKcal = row.caloriesKcal,
    )
}

/** Domain model -> database row, for a workout logged on this phone. */
fun Workout.toEntity(syncState: SyncState, routeCached: Boolean): WorkoutEntity {
    val swim = this as? SwimmingWorkout
    return WorkoutEntity(
        clientUuid = id,
        serverId = null,
        activityType = type.name,
        startTimeMs = startTime.toEpochMilli(),
        durationSeconds = duration.seconds,
        distanceMeters = (this as? DistanceWorkout)?.distanceMeters,
        notes = notes?.takeUnless { it.isBlank() },
        syncState = syncState,
        routeCached = routeCached,
        laps = swim?.laps,
        poolLengthM = swim?.poolLengthM,
        caloriesKcal = caloriesKcal,
    )
}

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
 * only a type that can't have a route (weightlifting, swimming) counts
 * as cached. An unknown type is stored as not cached; it never reaches
 * a screen ([toDomain] skips it). `null` when the start time can't be
 * read, so the caller skips that row.
 */
fun WorkoutDto.toEntity(): WorkoutEntity? {
    val startMs = runCatching { Instant.parse(startTime).toEpochMilli() }.getOrNull()
        ?: return null
    return WorkoutEntity(
        clientUuid = clientUuid,
        serverId = id,
        activityType = activityType,
        startTimeMs = startMs,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        notes = notes?.takeUnless { it.isBlank() },
        syncState = SyncState.SYNCED,
        routeCached = activityTypeOrNull(activityType)?.let { !canCarryRoute(it) } ?: false,
        uploadAttempted = true,
        laps = laps,
        poolLengthM = poolLengthM,
        caloriesKcal = caloriesKcal,
    )
}

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
