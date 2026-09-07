package com.reals.app.ui.profile

import android.content.Context
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.reals.app.domain.model.ProfilePhoto

internal const val ProfilePhotoGridThumbnailDecodeSizePx = 384

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
    listener: ImageRequest.Listener? = null,
): ImageRequest {
    val displayUrl = photo.url.toEmulatorReachableUrl()
    val diskCacheKey = photo.stableProfilePhotoCacheKey(displayUrl)
    val memoryCacheKey = profilePhotoMemoryCacheKey(
        photo = photo,
        variant = variant,
        widthPx = widthPx,
        heightPx = heightPx,
        displayUrl = displayUrl,
    )
    return ImageRequest.Builder(context)
        .data(displayUrl)
        .memoryCacheKey(memoryCacheKey)
        .apply {
            diskCacheKey(diskCacheKey)
            placeholderMemoryCacheKey(memoryCacheKey)
            if (widthPx != null && heightPx != null) {
                size(widthPx, heightPx)
            }
            this.listener(listener)
        }
        .build()
}

internal fun profilePhotoMemoryCacheKey(
    photo: ProfilePhoto,
    variant: ProfilePhotoImageVariant = ProfilePhotoImageVariant.Full,
    widthPx: Int? = null,
    heightPx: Int? = null,
    displayUrl: String = photo.url,
): MemoryCache.Key {
    val cacheKey = photo.stableProfilePhotoCacheKey(displayUrl)
    val memoryVariant = profilePhotoMemoryVariant(variant, widthPx, heightPx)
    return MemoryCache.Key(
        key = cacheKey,
        extras = memoryVariant
            ?.let { mapOf(ProfilePhotoMemoryVariantExtraKey to it) }
            .orEmpty(),
    )
}

private fun profilePhotoMemoryVariant(
    variant: ProfilePhotoImageVariant,
    widthPx: Int?,
    heightPx: Int?,
): String? =
    when (variant) {
        ProfilePhotoImageVariant.Full -> sizedMemoryVariant("full", widthPx, heightPx)
        ProfilePhotoImageVariant.Thumbnail -> "thumbnail:${widthPx ?: 0}x${heightPx ?: 0}"
    }

private fun sizedMemoryVariant(
    name: String,
    widthPx: Int?,
    heightPx: Int?,
): String? {
    if (widthPx == null || heightPx == null) return null
    return "$name:${widthPx}x$heightPx"
}

private const val ProfilePhotoMemoryVariantExtraKey = "profilePhotoVariant"
