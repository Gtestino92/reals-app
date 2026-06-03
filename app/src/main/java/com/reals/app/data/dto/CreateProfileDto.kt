package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateProfileRequestDto(
    val displayName: String,
    val birthDate: String,
    val gender: String,
    val lookingForGender: String,
    val intention: String,
    val city: String,
    val country: String,
    val bio: String? = null,
    val preferredMinAge: Int,
    val preferredMaxAge: Int,
    val maxDistanceKm: Int,
)
