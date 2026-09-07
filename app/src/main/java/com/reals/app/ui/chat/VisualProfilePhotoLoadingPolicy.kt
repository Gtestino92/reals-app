package com.reals.app.ui.chat

import com.reals.app.domain.model.ProfilePhoto

internal const val VisualProfileInitialPhotoLoadCount = 1
internal const val VisualProfileProgressivePhotoBatchSize = 2
internal const val VisualProfileProgressivePhotoDelayMillis = 650L
internal const val VisualProfilePrefetchStartDelayMillis = 250L
internal const val VisualProfilePrefetchPhotoCount = 2

internal fun initialVisualProfileLoadedPhotoCount(totalPhotos: Int): Int =
    totalPhotos.coerceAtLeast(0).coerceAtMost(VisualProfileInitialPhotoLoadCount)

internal fun nextVisualProfileLoadedPhotoCount(currentCount: Int, totalPhotos: Int): Int =
    (currentCount + VisualProfileProgressivePhotoBatchSize)
        .coerceAtMost(totalPhotos.coerceAtLeast(0))

internal fun visualProfilePrefetchCandidates(
    photos: List<ProfilePhoto>,
    selectedPhotoId: String?,
    maxCount: Int = VisualProfilePrefetchPhotoCount,
): List<ProfilePhoto> {
    if (maxCount <= 0) return emptyList()
    val selectedIndex = photos.indexOfFirst { it.id == selectedPhotoId }
        .takeIf { it >= 0 }
        ?: 0
    return photos
        .drop(selectedIndex + 1)
        .take(maxCount)
}
