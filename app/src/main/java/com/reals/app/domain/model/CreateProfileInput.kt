package com.reals.app.domain.model

data class CreateProfileInput(
    val displayName: String,
    val birthDate: String,
    val gender: String,
    val lookingForGender: String,
    val intention: String,
    val city: String,
    val country: String,
    val bio: String?,
    val preferredMinAge: Int,
    val preferredMaxAge: Int,
    val maxDistanceKm: Int,
)
