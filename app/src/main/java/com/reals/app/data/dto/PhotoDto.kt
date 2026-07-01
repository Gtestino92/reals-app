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
    val moderationStatus: String? = null,
)

@Serializable
data class ReorderProfilePhotosRequestDto(
    val placements: List<PhotoPlacementRequestDto>,
)

@Serializable
data class PhotoPlacementRequestDto(
    val photoId: String,
    val position: Int,
)
