package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserResponseDto(
    val id: String,
    val email: String? = null,
    val status: String,
    val deletedAt: String? = null,
    val deletionFinalizesAt: String? = null,
    val createdAt: String,
)
