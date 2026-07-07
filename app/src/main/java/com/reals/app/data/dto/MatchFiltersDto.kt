package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateMatchFiltersRequestDto(
    val lookingForGenders: Set<String>,
    val preferredMinAge: Int,
    val preferredMaxAge: Int,
    val maxDistanceKm: Int,
)
