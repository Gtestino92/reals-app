package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto

internal fun String.stableProfilePhotoCacheKey(): String = substringBefore("?")

internal fun ProfilePhoto.stableProfilePhotoCacheKey(displayUrl: String = url): String {
    return stableProfilePhotoCacheKey(photoId = id, displayUrl = displayUrl)
}

internal fun stableProfilePhotoCacheKey(photoId: String?, displayUrl: String): String {
    val canonicalUrl = displayUrl.stableProfilePhotoCacheKey()
    val cleanPhotoId = photoId?.trim().orEmpty()
    return if (cleanPhotoId.isBlank()) {
        canonicalUrl
    } else {
        "profile-photo:$cleanPhotoId:$canonicalUrl"
    }
}

internal sealed interface ProfilePhotoCacheRefreshDecision {
    data object None : ProfilePhotoCacheRefreshDecision
    data class Evict(val canonicalCacheKey: String) : ProfilePhotoCacheRefreshDecision
}

internal fun profilePhotoReplacementCacheRefreshDecision(
    action: ProfilePhotoActionPresentation,
    oldCanonicalCacheKey: String?,
    newUrl: String,
): ProfilePhotoCacheRefreshDecision {
    if (action.kind != ProfilePhotoActionKind.Replace) return ProfilePhotoCacheRefreshDecision.None
    if (oldCanonicalCacheKey.isNullOrBlank()) return ProfilePhotoCacheRefreshDecision.None
    val newCanonicalCacheKey = stableProfilePhotoCacheKey(
        photoId = action.photoId,
        displayUrl = newUrl,
    )
    return if (oldCanonicalCacheKey == newCanonicalCacheKey) {
        ProfilePhotoCacheRefreshDecision.Evict(oldCanonicalCacheKey)
    } else {
        ProfilePhotoCacheRefreshDecision.None
    }
}
