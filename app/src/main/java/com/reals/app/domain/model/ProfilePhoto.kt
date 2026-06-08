package com.reals.app.domain.model

data class ProfilePhoto(
    val id: String,
    val url: String,
    val position: Int,
    val isPersonPhoto: Boolean,
    val isFullBody: Boolean,
    val validationStatus: String,
)
