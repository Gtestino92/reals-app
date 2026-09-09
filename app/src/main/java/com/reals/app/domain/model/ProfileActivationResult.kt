package com.reals.app.domain.model

data class ProfileActivationResult(
    val profile: Profile,
    val addedPhotoCount: Int,
    val totalPhotoCount: Int,
    val generatedUrls: List<String>,
)
