package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterPushTokenRequestDto(
    val token: String,
    val platform: String,
)

@Serializable
data class RegisterPushTokenResponseDto(
    val registered: Boolean,
)
