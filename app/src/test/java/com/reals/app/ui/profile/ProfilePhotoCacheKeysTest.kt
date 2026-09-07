package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun partnerPhotoStableCacheKeyIgnoresSignatureChanges() {
        assertEquals(
            photo(
                id = "photo-1",
                url = "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=a",
            ).stableProfilePhotoCacheKey(),
            photo(
                id = "photo-1",
                url = "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=b",
            ).stableProfilePhotoCacheKey(),
        )
    }

    @Test
    fun partnerPhotoStableCacheKeyChangesWhenPhotoIdChangesForSamePath() {
        assertNotEquals(
            photo(
                id = "photo-1",
                url = "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=a",
            ).stableProfilePhotoCacheKey(),
            photo(
                id = "photo-2",
                url = "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=b",
            ).stableProfilePhotoCacheKey(),
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

    private fun photo(
        id: String,
        url: String,
    ) = ProfilePhoto(
        id = id,
        url = url,
        position = 1,
        isPersonPhoto = true,
        isFullBody = false,
        validationStatus = "VALIDATED",
        moderationStatus = "APPROVED",
    )
}
