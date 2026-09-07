package com.reals.app.ui.profile

import android.content.Context
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.reals.app.domain.model.ProfilePhoto

internal enum class ProfilePhotoImageVariant {
    Full,
    Thumbnail,
}

internal fun profilePhotoImageRequest(
    context: Context,
    photo: ProfilePhoto,
    variant: ProfilePhotoImageVariant = ProfilePhotoImageVariant.Full,
    widthPx: Int? = null,
    heightPx: Int? = null,
): ImageRequest {
    val displayUrl = photo.url.toEmulatorReachableUrl()
    val cacheKey = photo.stableProfilePhotoCacheKey(displayUrl)
    val memoryVariant = profilePhotoMemoryVariant(variant, widthPx, heightPx)
    return ImageRequest.Builder(context)
        .data(displayUrl)
        .memoryCacheKey(cacheKey)
        .apply {
            memoryVariant?.let { memoryCacheKeyExtra(ProfilePhotoMemoryVariantExtraKey, it) }
            diskCacheKey(cacheKey)
            placeholderMemoryCacheKey(MemoryCache.Key(cacheKey))
            if (widthPx != null && heightPx != null) {
                size(widthPx, heightPx)
            }
        }
        .build()
}

private fun profilePhotoMemoryVariant(
    variant: ProfilePhotoImageVariant,
    widthPx: Int?,
    heightPx: Int?,
): String? =
    when (variant) {
        ProfilePhotoImageVariant.Full -> null
        ProfilePhotoImageVariant.Thumbnail -> "thumbnail:${widthPx ?: 0}x${heightPx ?: 0}"
    }

private const val ProfilePhotoMemoryVariantExtraKey = "profilePhotoVariant"
