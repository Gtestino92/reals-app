package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePhotoPrefetcherTest {
    @Test
    fun ownProfilePhotoPrefetchPlanPrioritizesPhotosByGridPosition() {
        val plan = ownProfilePhotoPrefetchPlan(
            profileId = "profile-1",
            photos = listOf(photo(id = "photo-3", position = 3), photo(id = "photo-1", position = 1)),
        )

        assertEquals(listOf("photo-1", "photo-3"), plan?.photos?.map { it.id })
    }

    @Test
    fun ownProfilePhotoPrefetchPlanUsesTwoParallelRequestsAndGridThumbnailSize() {
        val plan = ownProfilePhotoPrefetchPlan(
            profileId = "profile-1",
            photos = listOf(photo(id = "photo-1", position = 1)),
        )

        assertEquals(ProfilePhotoPrefetchMaxConcurrency, plan?.maxConcurrency)
        assertEquals(ProfilePhotoGridThumbnailDecodeSizePx, plan?.thumbnailDecodeSizePx)
    }

    @Test
    fun ownProfilePhotoPrefetchPlanReturnsNullForEmptyList() {
        assertNull(ownProfilePhotoPrefetchPlan(profileId = "profile-1", photos = emptyList()))
    }

    @Test
    fun ownProfilePhotoPrefetchScopeKeyIgnoresPresignedQueryRefresh() {
        assertEquals(
            ownProfilePhotoPrefetchScopeKey(
                profileId = "profile-1",
                photos = listOf(
                    photo(
                        id = "photo-1",
                        position = 1,
                        url = "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=a",
                    )
                ),
            ),
            ownProfilePhotoPrefetchScopeKey(
                profileId = "profile-1",
                photos = listOf(
                    photo(
                        id = "photo-1",
                        position = 1,
                        url = "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=b",
                    )
                ),
            ),
        )
    }

    @Test
    fun ownProfilePhotoPrefetchScopeKeyChangesWhenReplacementUsesNewObjectPath() {
        assertNotEquals(
            ownProfilePhotoPrefetchScopeKey(
                profileId = "profile-1",
                photos = listOf(
                    photo(
                        id = "photo-1",
                        position = 1,
                        url = "https://cdn.reals.local/photos/photo-a.jpg?X-Amz-Signature=a",
                    )
                ),
            ),
            ownProfilePhotoPrefetchScopeKey(
                profileId = "profile-1",
                photos = listOf(
                    photo(
                        id = "photo-1",
                        position = 1,
                        url = "https://cdn.reals.local/photos/photo-b.jpg?X-Amz-Signature=b",
                    )
                ),
            ),
        )
    }

    @Test
    fun ownProfilePhotoPrefetchScopeKeySeparatesProfilesWithSamePhotoIdentity() {
        val photos = listOf(photo(id = "photo-1", position = 1))

        assertNotEquals(
            ownProfilePhotoPrefetchScopeKey(profileId = "profile-1", photos = photos),
            ownProfilePhotoPrefetchScopeKey(profileId = "profile-2", photos = photos),
        )
    }

    private fun photo(
        id: String,
        position: Int,
        url: String = "https://cdn.reals.local/photos/$id.jpg",
    ) = ProfilePhoto(
        id = id,
        url = url,
        position = position,
        isPersonPhoto = true,
        isFullBody = false,
        validationStatus = "VALIDATED",
        moderationStatus = "APPROVED",
    )
}
