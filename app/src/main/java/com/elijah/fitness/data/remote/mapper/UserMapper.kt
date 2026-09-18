package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.UserProfile
import com.elijah.fitness.data.remote.dto.UpdateProfileRequestDto
import com.elijah.fitness.data.remote.dto.UserDto
import java.time.Instant

fun UserDto.toDomain(): UserProfile = UserProfile(
    id = id,
    email = email,
    fullName = fullName,
    weightKg = weightKg,
    heightCm = heightCm,
    createdAt = Instant.parse(createdAt),
)

fun UserProfile.toUpdateRequest(): UpdateProfileRequestDto =
    UpdateProfileRequestDto(
        fullName = fullName,
        weightKg = weightKg,
        heightCm = heightCm,
    )
