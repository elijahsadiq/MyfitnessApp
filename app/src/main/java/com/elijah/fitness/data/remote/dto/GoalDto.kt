package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Server representation of a goal (phase-8b shape). Matches
 * `GoalController::toDto` on the PHP side one-for-one.
 *
 * `startDate` / `lastDay` are DATE strings (`YYYY-MM-DD`) in the device's
 * local zone — the mapper parses them to `LocalDate` for the domain
 * layer. `lastDay` is INCLUSIVE (the last day the window covers).
 */
@Serializable
data class GoalDto(
    val id: Long,
    val title: String,
    val activityType: String? = null,
    /** `"distance"`, `"count"`, `"duration"`, `"volume"`. */
    val goalType: String,
    val targetValue: Double,
    val startDate: String,
    val lastDay: String,
    val durationDays: Int,
    val isMain: Boolean = false,
    val completedNotified: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
)

/** Request body for `POST /goals`. */
@Serializable
data class CreateGoalRequestDto(
    val title: String,
    val activityType: String? = null,
    val goalType: String,
    val targetValue: Double,
    val startDate: String,
    val durationDays: Int,
)

/**
 * Request body for `PUT /goals/{id}`. The server merges unchanged fields
 * from the current row, so clients can send only what's changing; this
 * DTO carries all four editable fields.
 */
@Serializable
data class UpdateGoalRequestDto(
    val title: String,
    val activityType: String? = null,
    val goalType: String,
    val targetValue: Double,
    val durationDays: Int,
)

/** Request body for `POST /goals/{id}/extend`. */
@Serializable
data class ExtendGoalRequestDto(
    val days: Int,
)
