package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Every API response is wrapped in this envelope. Only one of [data] or
 * [error] is populated — [success] tells us which. The generic parameter
 * is the payload type for that endpoint.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val message: String,
    /** Field-scoped validation messages, keyed by the request-body field. */
    val fields: Map<String, String>? = null,
)
