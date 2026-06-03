package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserResponseDto(
    val id: String,
    val email: String? = null,
    val createdAt: String,
)
