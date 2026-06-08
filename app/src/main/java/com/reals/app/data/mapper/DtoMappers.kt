package com.reals.app.data.mapper

import com.reals.app.data.dto.CreateProfileRequestDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.UpdateMatchFiltersRequestDto
import com.reals.app.data.dto.UpdateProfileRequestDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput

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

fun CreateProfileInput.toDto(): CreateProfileRequestDto = CreateProfileRequestDto(
    displayName = displayName,
    birthDate = birthDate,
    gender = gender,
    lookingForGender = lookingForGender,
    intention = intention,
    city = city,
    country = country,
    bio = bio,
    preferredMinAge = preferredMinAge,
    preferredMaxAge = preferredMaxAge,
    maxDistanceKm = maxDistanceKm,
)

fun UpdateProfileInput.toDto(): UpdateProfileRequestDto = UpdateProfileRequestDto(
    displayName = displayName,
    bio = bio,
    city = city,
    country = country,
    intention = intention,
    lookingForGender = lookingForGender,
)

fun UpdateMatchFiltersInput.toDto(): UpdateMatchFiltersRequestDto = UpdateMatchFiltersRequestDto(
    preferredMinAge = preferredMinAge,
    preferredMaxAge = preferredMaxAge,
    maxDistanceKm = maxDistanceKm,
)

fun PhotoResponseDto.toDomain(): ProfilePhoto = ProfilePhoto(
    id = id,
    url = url,
    position = position,
    isPersonPhoto = isPersonPhoto,
    isFullBody = isFullBody,
    validationStatus = validationStatus,
)
