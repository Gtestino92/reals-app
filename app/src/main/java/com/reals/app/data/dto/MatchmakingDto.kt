package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class EnqueueMatchmakingRequestDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Int? = null,
)

@Serializable
data class QueueStatusResponseDto(
    val userId: String,
    val inQueue: Boolean,
)
