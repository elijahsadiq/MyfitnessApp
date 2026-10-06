package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val fullName: String,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    /** Relative path returned by the server, e.g. `uploads/avatars/abc.jpg`. */
    val avatarPath: String? = null,
    /** ISO-8601 UTC, e.g. `2026-09-15T10:30:00Z`. */
    val createdAt: String,
)

@Serializable
data class UpdateProfileRequestDto(
    val fullName: String,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
)

@Serializable
data class DeleteAccountRequestDto(
    val password: String,
)
