package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Server representation of a goal. Matches `GoalController::toDto` on the
 * PHP side one-for-one.
 *
 * `periodStart` / `periodEnd` are DATE strings (`YYYY-MM-DD`) interpreted
 * as midnight in the device's local zone; the mapper converts them to
 * `java.time.Instant` for the domain layer.
 */
@Serializable
data class GoalDto(
    val id: Long,
    val title: String,
    val activityType: String? = null,
    /** `"distance"`, `"count"`, `"duration"`, `"volume"`. */
    val goalType: String,
    /** `"WEEK"` or `"MONTH"`. */
    val period: String,
    val targetValue: Double,
    val periodStart: String,
    val periodEnd: String,
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
    val period: String,
    val targetValue: Double,
    val periodStart: String,
    val periodEnd: String,
)

/**
 * Request body for `PUT /goals/{id}`. The controller only edits these
 * three fields; period + dates are immutable so historical progress
 * stays meaningful.
 */
@Serializable
data class UpdateGoalRequestDto(
    val title: String,
    val activityType: String? = null,
    val targetValue: Double,
)
