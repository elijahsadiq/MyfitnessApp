package com.elijah.fitness.data.model

import java.time.Instant

/**
 * Authenticated user profile. Pure Kotlin — no Android imports, no
 * serialization annotations — so it can move through any layer without
 * dragging Retrofit or datastore with it. Mapped from `UserDto` in
 * `data/remote/mapper/`.
 */
data class UserProfile(
    val id: Long,
    val email: String,
    val fullName: String,
    val weightKg: Double?,
    val heightCm: Double?,
    val createdAt: Instant,
)
