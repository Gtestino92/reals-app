package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AddPhotoRequestDto(
    val url: String,
    val position: Int,
    val isPersonPhoto: Boolean? = null,
    val isFullBody: Boolean? = null,
)

@Serializable
data class ReplacePhotoRequestDto(
    val url: String,
    val isPersonPhoto: Boolean? = null,
    val isFullBody: Boolean? = null,
)

@Serializable
data class PhotoResponseDto(
    val id: String,
    val profileId: String,
    val url: String,
    val storageProvider: String,
    val position: Int,
    val isPersonPhoto: Boolean,
    val isFullBody: Boolean,
    val createdAt: String,
)
