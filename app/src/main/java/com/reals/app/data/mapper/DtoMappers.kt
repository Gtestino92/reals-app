package com.reals.app.data.mapper

import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileStatus

fun UserResponseDto.toDomain(): BackendUser = BackendUser(
    id = id,
    email = email,
    createdAt = createdAt,
)

fun ProfileResponseDto.toDomain(): Profile = Profile(
    id = id,
    userId = userId,
    displayName = displayName,
    birthDate = birthDate,
    age = age,
    identityVerified = identityVerified,
    gender = gender,
    lookingForGender = lookingForGender,
    intention = intention,
    city = city,
    country = country,
    bio = bio,
    preferredMinAge = preferredMinAge,
    preferredMaxAge = preferredMaxAge,
    maxDistanceKm = maxDistanceKm,
    status = ProfileStatus.fromBackend(status),
    photoCount = photoCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
