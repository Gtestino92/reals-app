package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CountryReferenceResponseDto(
    val code: String,
    val displayName: String,
)
