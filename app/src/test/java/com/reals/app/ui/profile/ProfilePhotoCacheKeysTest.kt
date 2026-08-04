package com.reals.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoCacheKeysTest {
    @Test
    fun stableCacheKeyRemovesQueryParameters() {
        assertEquals(
            "https://cdn.reals.local/photos/photo.jpg",
            "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=a".stableProfilePhotoCacheKey(),
        )
    }

    @Test
    fun differentSignaturesForSameObjectUseSameCanonicalKey() {
        assertEquals(
            "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=a".stableProfilePhotoCacheKey(),
            "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=b".stableProfilePhotoCacheKey(),
        )
    }

    @Test
    fun differentObjectPathsUseDifferentCanonicalKeys() {
        assertTrue(
            "https://cdn.reals.local/photos/photo-a.jpg".stableProfilePhotoCacheKey() !=
                "https://cdn.reals.local/photos/photo-b.jpg".stableProfilePhotoCacheKey(),
        )
    }

    @Test
    fun replacementWithSameCanonicalKeyRequiresCacheEviction() {
        val decision = profilePhotoReplacementCacheRefreshDecision(
            action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, position = 2, photoId = "photo-2"),
            oldCanonicalCacheKey = "https://cdn.reals.local/photos/photo-2.jpg",
            newUrl = "https://cdn.reals.local/photos/photo-2.jpg?X-Amz-Signature=new",
        )

        assertEquals(
            ProfilePhotoCacheRefreshDecision.Evict("https://cdn.reals.local/photos/photo-2.jpg"),
            decision,
        )
    }

    @Test
    fun addDoesNotRequirePriorKeyEviction() {
        val decision = profilePhotoReplacementCacheRefreshDecision(
            action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4),
            oldCanonicalCacheKey = null,
            newUrl = "https://cdn.reals.local/photos/photo-new.jpg?X-Amz-Signature=new",
        )

        assertEquals(ProfilePhotoCacheRefreshDecision.None, decision)
    }
}
