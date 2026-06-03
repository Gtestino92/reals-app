package com.reals.app.domain.model

data class ProfilePhoto(
    val id: String,
    val profileId: String,
    val url: String,
    val storageProvider: String,
    val position: Int,
    val isPersonPhoto: Boolean,
    val isFullBody: Boolean,
    val createdAt: String,
)
