package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MatchResponseDto(
    val id: String,
    val userAId: String,
    val userBId: String,
    val state: String,
    val connectionId: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ChatDecisionRequestDto(
    val decision: String,
)
