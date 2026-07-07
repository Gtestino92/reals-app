package com.reals.app.domain.model

data class UpdateMatchFiltersInput(
    val lookingForGenders: Set<String>,
    val preferredMinAge: Int,
    val preferredMaxAge: Int,
    val maxDistanceKm: Int,
)
