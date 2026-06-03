package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PingResponseDto(
    val status: String,
)
