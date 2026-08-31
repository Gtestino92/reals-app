package com.reals.app.domain.model

data class ProfilePhoto(
    val id: String,
    val url: String,
    val position: Int,
    val isPersonPhoto: Boolean,
    val isFullBody: Boolean,
    val validationStatus: String,
    val moderationStatus: String,
)

const val ProfilePhotoModerationStatusApproved = "APPROVED"
const val ProfilePhotoModerationStatusNeedsReview = "NEEDS_REVIEW"

fun ProfilePhoto.isApprovedForExternalDisplay(): Boolean =
    moderationStatus == ProfilePhotoModerationStatusApproved

fun ProfilePhoto.isPendingModerationReview(): Boolean =
    moderationStatus == ProfilePhotoModerationStatusNeedsReview

data class PhotoPlacementInput(
    val photoId: String,
    val position: Int,
)
