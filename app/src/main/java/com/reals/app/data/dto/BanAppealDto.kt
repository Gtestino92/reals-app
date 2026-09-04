package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class BanAppealResponseDto(
    val status: String,
    val banActive: Boolean,
    val appealedAt: String? = null,
    val reviewedAt: String? = null,
)

@Serializable
data class SubmitBanAppealRequestDto(
    val statement: String,
)
