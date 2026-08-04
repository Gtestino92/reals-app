package com.reals.app.ui.profile

internal fun String.stableProfilePhotoCacheKey(): String = substringBefore("?")

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
    val newCanonicalCacheKey = newUrl.stableProfilePhotoCacheKey()
    return if (oldCanonicalCacheKey == newCanonicalCacheKey) {
        ProfilePhotoCacheRefreshDecision.Evict(oldCanonicalCacheKey)
    } else {
        ProfilePhotoCacheRefreshDecision.None
    }
}
