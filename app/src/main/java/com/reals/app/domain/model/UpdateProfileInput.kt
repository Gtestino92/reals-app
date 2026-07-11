package com.reals.app.domain.model

data class UpdateProfileInput(
    val displayName: String,
    val bio: String?,
    val city: String,
    val countryCode: String,
)
