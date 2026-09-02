package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val code: String,
    val error: String,
    val message: String? = null,
    val expiresAt: String? = null,
)
