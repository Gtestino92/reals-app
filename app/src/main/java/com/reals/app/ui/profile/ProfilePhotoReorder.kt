package com.reals.app.ui.profile

import com.reals.app.domain.model.PhotoPlacementInput
import com.reals.app.domain.model.ProfilePhoto

fun photosWithPendingOrder(
    photos: List<ProfilePhoto>,
    pendingOrder: List<PhotoPlacementInput>?,
): List<ProfilePhoto> {
    if (pendingOrder == null) return photos.sortedBy { it.position }

    val positionById = pendingOrder.associate { it.photoId to it.position }
    return photos
        .map { photo ->
            positionById[photo.id]?.let { position -> photo.copy(position = position) } ?: photo
        }
        .sortedBy { it.position }
}

fun buildCompletePhotoPlacements(
    photos: List<ProfilePhoto>,
): List<PhotoPlacementInput> =
    photos
        .sortedBy { it.position }
        .map { photo ->
            PhotoPlacementInput(
                photoId = photo.id,
                position = photo.position,
            )
        }

fun movePhotoLocally(
    photos: List<ProfilePhoto>,
    pendingOrder: List<PhotoPlacementInput>?,
    photoId: String,
    targetPosition: Int,
): List<PhotoPlacementInput> {
    if (targetPosition !in ProfilePhotoGridPositions) {
        return buildCompletePhotoPlacements(photosWithPendingOrder(photos, pendingOrder))
    }

    val orderedPhotos = photosWithPendingOrder(photos, pendingOrder)
    val sourcePhoto = orderedPhotos.firstOrNull { it.id == photoId }
        ?: return buildCompletePhotoPlacements(orderedPhotos)
    if (sourcePhoto.position == targetPosition) {
        return buildCompletePhotoPlacements(orderedPhotos)
    }

    val targetPhoto = orderedPhotos.firstOrNull { it.position == targetPosition }
    val moved = orderedPhotos.map { photo ->
        when {
            photo.id == sourcePhoto.id -> photo.copy(position = targetPosition)
            targetPhoto != null && photo.id == targetPhoto.id -> photo.copy(position = sourcePhoto.position)
            else -> photo
        }
    }
    return buildCompletePhotoPlacements(moved)
}
