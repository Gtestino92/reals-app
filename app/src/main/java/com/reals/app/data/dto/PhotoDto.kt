package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PhotoResponseDto(
    val id: String,
    val url: String,
    val position: Int,
    val isPersonPhoto: Boolean,
    val isFullBody: Boolean,
    val validationStatus: String,
)
