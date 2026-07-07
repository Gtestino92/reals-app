package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponseDto(
    val id: String,
    val userId: String,
    val displayName: String,
    val birthDate: String,
    val age: Int,
    val identityVerified: Boolean,
    val identityVerificationStatus: String? = null,
    val gender: String,
    val lookingForGenders: Set<String>,
    val intention: String,
    val city: String,
    val country: String,
    val bio: String? = null,
    val preferredMinAge: Int,
    val preferredMaxAge: Int,
    val maxDistanceKm: Int,
    val status: String,
    val photoCount: Int,
    val createdAt: String,
    val updatedAt: String,
)
