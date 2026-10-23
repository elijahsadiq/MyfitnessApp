package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutDto(
    val id: Long,
    val clientUuid: String,
    /**
     * One of `RUNNING`, `CYCLING`, `SWIMMING`, `ROWING`, `WEIGHTLIFTING`.
     * Kept as a raw string so a type this app version doesn't know yet
     * is skipped by the mapper instead of failing the whole list.
     */
    val activityType: String,
    /** ISO-8601 UTC. */
    val startTime: String,
    val durationSeconds: Long,
    val distanceMeters: Double? = null,
    /** Swims only (phase 8d): pool length in metres, e.g. 25 or 50. */
    val poolLengthM: Int? = null,
    /** Swims only (phase 8d): laps swum; `distanceMeters = laps × poolLengthM`. */
    val laps: Int? = null,
    /** Calories stored at save time (phase 8d). `null` on older workouts. */
    val caloriesKcal: Int? = null,
    val notes: String? = null,
    /** Only populated for `WEIGHTLIFTING`. */
    val sets: List<StrengthSetDto> = emptyList(),
    /**
     * Populated by `GET /workouts/{id}` only. The list endpoint omits route
     * points to keep its payload small — the detail call reads them.
     */
    val routePoints: List<RoutePointDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class RoutePointDto(
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    val elapsedMs: Long,
    val accuracyM: Double? = null,
    /** ISO-8601 UTC. */
    val recordedAt: String,
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
    /** Swims only; the server rejects it on any other type. */
    val poolLengthM: Int? = null,
    /** Swims only; the server rejects it on any other type. */
    val laps: Int? = null,
    val caloriesKcal: Int? = null,
    val notes: String? = null,
    val sets: List<StrengthSetRequestDto> = emptyList(),
    /** Runs, rides and rows only. */
    val routePoints: List<RoutePointRequestDto> = emptyList(),
)

@Serializable
data class RoutePointRequestDto(
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    val elapsedMs: Long,
    val accuracyM: Double? = null,
    val recordedAt: String,
)

@Serializable
data class StrengthSetRequestDto(
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double,
)
