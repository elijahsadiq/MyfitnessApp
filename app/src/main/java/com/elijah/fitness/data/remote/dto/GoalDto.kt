package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

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
 * DTO carries every editable field.
 */
@Serializable
data class UpdateGoalRequestDto(
    val title: String,
    /**
     * The activity's wire name, or JSON `null` for "any activity" — and
     * always sent. The shared Json config leaves null properties out
     * (`explicitNulls = false`), and the server keeps the stored value
     * for any field that's missing, so a plain `String?` here silently
     * lost an edit that switched a goal to "any activity". A
     * [JsonElement] is never null itself, so the key is always written.
     * Build it with [activityTypeJson]; read it with [activityTypeName].
     */
    val activityType: JsonElement,
    val goalType: String,
    val targetValue: Double,
    val durationDays: Int,
) {
    /** [activityType] as a wire name, `null` for "any activity". */
    val activityTypeName: String?
        get() = (activityType as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    companion object {
        /** The [activityType] value for [name]: a JSON string, or JSON `null` for "any activity". */
        fun activityTypeJson(name: String?): JsonElement =
            if (name == null) JsonNull else JsonPrimitive(name)
    }
}

/** Request body for `POST /goals/{id}/extend`. */
@Serializable
data class ExtendGoalRequestDto(
    val days: Int,
)
