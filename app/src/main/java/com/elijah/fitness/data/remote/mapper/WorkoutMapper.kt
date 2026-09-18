package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.dto.StrengthSetRequestDto
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.data.remote.dto.WorkoutRequestDto
import java.time.Duration
import java.time.Instant

/**
 * Server DTO -> domain model. The domain hierarchy from phase 1 (Workout ->
 * DistanceWorkout -> RunningWorkout|CyclingWorkout, and StrengthWorkout)
 * is reconstructed based on `activityType`. `id` is populated from the
 * server's `clientUuid` so the shared repository Flow can still use it as
 * a stable diff key.
 */
fun WorkoutDto.toDomain(): Workout {
    val start = Instant.parse(startTime)
    val duration = Duration.ofSeconds(durationSeconds)
    return when (activityType) {
        ActivityType.RUNNING.name -> RunningWorkout(
            id = clientUuid,
            startTime = start,
            duration = duration,
            distanceMeters = distanceMeters ?: 0.0,
            notes = notes,
        )
        ActivityType.CYCLING.name -> CyclingWorkout(
            id = clientUuid,
            startTime = start,
            duration = duration,
            distanceMeters = distanceMeters ?: 0.0,
            notes = notes,
        )
        ActivityType.WEIGHTLIFTING.name -> StrengthWorkout(
            id = clientUuid,
            startTime = start,
            duration = duration,
            sets = sets
                .sortedBy { it.setOrder }
                .map { StrengthSet(it.exerciseName, it.reps, it.weightKg) },
            notes = notes,
        )
        else -> error("Unknown activityType: $activityType")
    }
}

/**
 * Domain model -> server request DTO. `startTime` is emitted as ISO-8601
 * UTC (`Instant.toString()` already gives that shape).
 */
fun Workout.toRequestDto(): WorkoutRequestDto = WorkoutRequestDto(
    clientUuid = id,
    activityType = type.name,
    startTime = startTime.toString(),
    durationSeconds = duration.seconds,
    distanceMeters = (this as? DistanceWorkout)?.distanceMeters,
    notes = notes,
    sets = (this as? StrengthWorkout)?.sets?.map {
        StrengthSetRequestDto(it.exercise, it.reps, it.weightKg)
    } ?: emptyList(),
)
