package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutDto(
    val id: Long,
    val clientUuid: String,
    /** One of `RUNNING`, `CYCLING`, `WEIGHTLIFTING`. */
    val activityType: String,
    /** ISO-8601 UTC. */
    val startTime: String,
    val durationSeconds: Long,
    val distanceMeters: Double? = null,
    val notes: String? = null,
    /** Only populated for `WEIGHTLIFTING`. */
    val sets: List<StrengthSetDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class StrengthSetDto(
    val setOrder: Int,
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double,
)

@Serializable
data class WorkoutRequestDto(
    val clientUuid: String,
    val activityType: String,
    val startTime: String,
    val durationSeconds: Long,
    val distanceMeters: Double? = null,
    val notes: String? = null,
    val sets: List<StrengthSetRequestDto> = emptyList(),
)

@Serializable
data class StrengthSetRequestDto(
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double,
)
