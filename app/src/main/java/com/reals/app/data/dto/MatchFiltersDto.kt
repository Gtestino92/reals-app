package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateMatchFiltersRequestDto(
    val preferredMinAge: Int,
    val preferredMaxAge: Int,
    val maxDistanceKm: Int,
)
