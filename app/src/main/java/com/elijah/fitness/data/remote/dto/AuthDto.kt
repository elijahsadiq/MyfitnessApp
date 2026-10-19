package com.elijah.fitness.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val fullName: String,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

/**
 * Payload returned by /auth/register: the new account only. Registering
 * doesn't sign the user in, so there is no token.
 */
@Serializable
data class RegisterResponseDto(
    val user: UserDto,
)

/** Payload returned by /auth/login. */
@Serializable
data class AuthResponseDto(
    val token: String,
    val user: UserDto,
)
