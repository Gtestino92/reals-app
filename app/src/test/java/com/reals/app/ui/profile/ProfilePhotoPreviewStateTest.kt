package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoPreviewStateTest {
    @Test
    fun addCropConfirmationCreatesUploadingAddPreview() {
        val state = startProfilePhotoPreview(addAction, "file:///crop-a.jpg", "generation-1", 10L, null)

        assertEquals(ProfilePhotoActionKind.Add, state.preview.action.kind)
        assertEquals(4, state.preview.action.position)
        assertEquals("file:///crop-a.jpg", state.preview.uriString)
    }

    @Test
    fun replaceCropConfirmationCreatesUploadingReplacePreview() {
        val state = startProfilePhotoPreview(replaceAction, "file:///crop-r.jpg", "generation-2", 10L, "old-key")

        assertEquals(ProfilePhotoActionKind.Replace, state.preview.action.kind)
        assertEquals("photo-2", state.preview.action.photoId)
        assertEquals("old-key", state.preview.oldCanonicalCacheKey)
    }

    @Test
    fun uploadSuccessTransitionsToAwaitingRemote() {
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val state = uploading.onUploadSucceeded(listOf(photo("photo-new", 4)), uploadResponseAtElapsedMillis = 42L)

        assertTrue(state is ProfilePhotoPreviewState.AwaitingRemote)
        state as ProfilePhotoPreviewState.AwaitingRemote
        assertEquals("photo-new", state.remotePhotoId)
        assertEquals(42L, state.uploadResponseAtElapsedMillis)
    }

    @Test
    fun remoteSuccessWithMatchingGenerationClearsPreview() {
        val awaiting = awaiting("generation-1")

        assertEquals(ProfilePhotoPreviewState.None, awaiting.onRemoteSucceeded("generation-1"))
    }

    @Test
    fun remoteSuccessWithStaleGenerationDoesNotClearPreview() {
        val awaiting = awaiting("generation-2")

        assertSame(awaiting, awaiting.onRemoteSucceeded("generation-1"))
    }

    @Test
    fun uploadFailureClearsUploadingPreview() {
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        assertEquals(ProfilePhotoPreviewState.None, uploading.onUploadFailed())
    }

    @Test
    fun replaceFailureLeavesAuthoritativeOldPhotoToPresentation() {
        val oldPhoto = photo("photo-2", 2, "https://static.reals.local/old.jpg")
        val photos = listOf(oldPhoto)
        val uploading = startProfilePhotoPreview(replaceAction, "file:///crop.jpg", "generation-1", 10L, "old-key")

        val state = uploading.onUploadFailed()

        assertEquals(ProfilePhotoPreviewState.None, state)
        assertEquals(oldPhoto, photos.first())
    }

    @Test
    fun newerPreviewHasDifferentGenerationThanPriorPreview() {
        val oldPreview = startProfilePhotoPreview(addAction, "file:///old.jpg", "generation-1", 10L, null)
        val newPreview = startProfilePhotoPreview(addAction, "file:///new.jpg", "generation-2", 20L, null)

        assertTrue(oldPreview.preview.generation != newPreview.preview.generation)
    }

    @Test
    fun deleteActionDoesNotCreateImagePreview() {
        val deleteAction = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Delete, position = 2, photoId = "photo-2")

        assertEquals(ProfilePhotoPreviewState.None, previewStateForAction(deleteAction))
    }

    @Test
    fun sameCanonicalReplacementRequiresCacheEviction() {
        val decision = profilePhotoReplacementCacheRefreshDecision(
            action = replaceAction,
            oldCanonicalCacheKey = "https://cdn.reals.local/photos/photo-2.jpg",
            newUrl = "https://cdn.reals.local/photos/photo-2.jpg?X-Amz-Signature=new",
        )

        assertEquals(
            ProfilePhotoCacheRefreshDecision.Evict("https://cdn.reals.local/photos/photo-2.jpg"),
            decision,
        )
    }

    @Test
    fun addDoesNotRequireCacheEviction() {
        val decision = profilePhotoReplacementCacheRefreshDecision(
            action = addAction,
            oldCanonicalCacheKey = null,
            newUrl = "https://cdn.reals.local/photos/photo-new.jpg?X-Amz-Signature=new",
        )

        assertEquals(ProfilePhotoCacheRefreshDecision.None, decision)
    }

    @Test
    fun stableCacheKeyRemovesOnlyQueryParameters() {
        assertEquals(
            "https://cdn.reals.local/photos/photo.jpg",
            "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=a".stableProfilePhotoCacheKey(),
        )
        assertEquals(
            "https://cdn.reals.local/photos/photo.jpg",
            "https://cdn.reals.local/photos/photo.jpg?X-Amz-Signature=b".stableProfilePhotoCacheKey(),
        )
        assertTrue(
            "https://cdn.reals.local/photos/photo-a.jpg".stableProfilePhotoCacheKey() !=
                "https://cdn.reals.local/photos/photo-b.jpg".stableProfilePhotoCacheKey(),
        )
    }

    private fun previewStateForAction(action: ProfilePhotoActionPresentation): ProfilePhotoPreviewState =
        if (action.kind == ProfilePhotoActionKind.Delete) {
            ProfilePhotoPreviewState.None
        } else {
            startProfilePhotoPreview(action, "file:///crop.jpg", "generation", 10L, null)
        }

    private fun awaiting(generation: String): ProfilePhotoPreviewState.AwaitingRemote =
        ProfilePhotoPreviewState.AwaitingRemote(
            preview = PendingProfilePhotoPreview(addAction, "file:///crop.jpg", generation, 10L),
            remotePhotoId = "photo-new",
            remoteUrl = "https://static.reals.local/photo-new.jpg",
            uploadResponseAtElapsedMillis = 42L,
        )

    private fun photo(
        id: String,
        position: Int,
        url: String = "https://static.reals.local/$id.jpg",
    ): ProfilePhoto =
        ProfilePhoto(
            id = id,
            url = url,
            position = position,
            isPersonPhoto = true,
            isFullBody = false,
            validationStatus = "APPROVED",
            moderationStatus = "APPROVED",
        )

    private val addAction = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4)
    private val replaceAction = ProfilePhotoActionPresentation(
        kind = ProfilePhotoActionKind.Replace,
        position = 2,
        photoId = "photo-2",
    )
}
