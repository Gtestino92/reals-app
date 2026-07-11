package com.reals.app.domain.model

data class Profile(
    val id: String,
    val userId: String,
    val displayName: String,
    val birthDate: String,
    val age: Int,
    val authenticityVerified: Boolean,
    val authenticityVerificationStatus: String,
    val gender: String,
    val lookingForGenders: Set<String>,
    val intention: String,
    val city: String,
    val countryCode: String,
    val bio: String?,
    val preferredMinAge: Int,
    val preferredMaxAge: Int,
    val maxDistanceKm: Int,
    val status: ProfileStatus,
    val photoCount: Int,
    val createdAt: String,
    val updatedAt: String,
)
