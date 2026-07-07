package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequestDto(
    val displayName: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val country: String? = null,
)
