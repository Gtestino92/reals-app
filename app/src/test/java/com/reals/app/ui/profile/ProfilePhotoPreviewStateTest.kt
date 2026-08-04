package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun awaitingRemoteDeleteStartClearsPreview() {
        val result = awaiting("generation-1").clearForNewPhotoMutation()

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertEquals("file:///crop.jpg", result.cleanupUriString)
    }

    @Test
    fun priorPreviewCleanupReturnsCorrectUriOnce() {
        val first = awaiting("generation-1").clearForNewPhotoMutation()
        val second = first.state.clearForNewPhotoMutation()

        assertEquals("file:///crop.jpg", first.cleanupUriString)
        assertEquals(ProfilePhotoPreviewState.None, first.state)
        assertNull(second.cleanupUriString)
        assertEquals(ProfilePhotoPreviewState.None, second.state)
    }

    @Test
    fun awaitingRemoteReorderStartClearsPreview() {
        val result = awaiting("generation-1").clearForNewPhotoMutation()

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertEquals("file:///crop.jpg", result.cleanupUriString)
    }

    @Test
    fun deleteSuccessCannotPreserveOldAwaitingPreview() {
        val result = awaiting("generation-1").clearForNewPhotoMutation()
        val photosAfterDelete = emptyList<ProfilePhoto>()

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertTrue(photosAfterDelete.none { it.id == "photo-new" })
    }

    @Test
    fun deleteFailureCannotPreserveStatePointingToDeletedFile() {
        val oldPhoto = photo("photo-new", 4)
        val result = awaiting("generation-1").clearForNewPhotoMutation()

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertEquals("file:///crop.jpg", result.cleanupUriString)
        assertEquals(oldPhoto, listOf(oldPhoto).first())
    }

    @Test
    fun staleTerminalErrorDoesNotClearUnrelatedAwaitingPreview() {
        val awaiting = awaiting("generation-1")

        val result = awaiting.onMatchingUploadFailed(addAction)

        assertSame(awaiting, result.state)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun staleTerminalSuccessDoesNotTransitionUnrelatedUploadingPreview() {
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val result = uploading.onMatchingUploadSucceeded(replaceAction, listOf(photo("photo-new", 4)), 42L)

        assertSame(uploading, result.state)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun matchingAddSuccessTransitionsToAwaitingRemote() {
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val result = uploading.onMatchingUploadSucceeded(addAction, listOf(photo("photo-new", 4)), 42L)

        val state = result.state as ProfilePhotoPreviewState.AwaitingRemote
        assertEquals("photo-new", state.remotePhotoId)
        assertEquals(42L, state.uploadResponseAtElapsedMillis)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun matchingReplaceSuccessTransitionsToAwaitingRemote() {
        val uploading = startProfilePhotoPreview(replaceAction, "file:///crop.jpg", "generation-1", 10L, "old-key")

        val result = uploading.onMatchingUploadSucceeded(replaceAction, listOf(photo("photo-2", 2)), 42L)

        val state = result.state as ProfilePhotoPreviewState.AwaitingRemote
        assertEquals("photo-2", state.remotePhotoId)
        assertEquals("old-key", state.preview.oldCanonicalCacheKey)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun replaceSuccessRequiresMatchingBackendPhotoId() {
        val uploading = startProfilePhotoPreview(replaceAction, "file:///crop.jpg", "generation-1", 10L, "old-key")

        val result = uploading.onMatchingUploadSucceeded(replaceAction, listOf(photo("photo-other", 2)), 42L)

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertEquals("file:///crop.jpg", result.cleanupUriString)
    }

    @Test
    fun mismatchedActionKindIsIgnored() {
        val replaceAtSamePosition = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, 4, "photo-4")
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val result = uploading.onMatchingUploadFailed(replaceAtSamePosition)

        assertSame(uploading, result.state)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun mismatchedPositionIsIgnored() {
        val otherPosition = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, 5)
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val result = uploading.onMatchingUploadFailed(otherPosition)

        assertSame(uploading, result.state)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun mismatchedPhotoIdIsIgnored() {
        val otherPhoto = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, 2, "photo-other")
        val uploading = startProfilePhotoPreview(replaceAction, "file:///crop.jpg", "generation-1", 10L, null)

        val result = uploading.onMatchingUploadFailed(otherPhoto)

        assertSame(uploading, result.state)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun uploadFailureClearsMatchingUploadingPreview() {
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val result = uploading.onMatchingUploadFailed(addAction)

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertEquals("file:///crop.jpg", result.cleanupUriString)
    }

    @Test
    fun replaceFailureLeavesAuthoritativeOldPhotoToPresentation() {
        val oldPhoto = photo("photo-2", 2, "https://static.reals.local/old.jpg")
        val uploading = startProfilePhotoPreview(replaceAction, "file:///crop.jpg", "generation-1", 10L, "old-key")

        val result = uploading.onMatchingUploadFailed(replaceAction)

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertEquals(oldPhoto, listOf(oldPhoto).first())
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
    fun sameProcessSaverRestorePreservesUploading() {
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val restored = restoreProfilePhotoPreviewState(
            saveProfilePhotoPreviewState(uploading, processSessionId = "session-a"),
            currentProcessSessionId = "session-a",
        )

        assertEquals(uploading, restored)
    }

    @Test
    fun differentProcessRestoreBecomesOrphaned() {
        val uploading = startProfilePhotoPreview(addAction, "file:///crop.jpg", "generation-1", 10L, null)

        val restored = restoreProfilePhotoPreviewState(
            saveProfilePhotoPreviewState(uploading, processSessionId = "session-a"),
            currentProcessSessionId = "session-b",
        )

        assertEquals(ProfilePhotoPreviewState.Orphaned("file:///crop.jpg"), restored)
    }

    @Test
    fun unknownSaverPhaseRestoresNone() {
        assertEquals(ProfilePhotoPreviewState.None, restoreProfilePhotoPreviewState(listOf("unknown")))
    }

    @Test
    fun malformedSaverPayloadRestoresNone() {
        assertEquals(ProfilePhotoPreviewState.None, restoreProfilePhotoPreviewState(listOf("uploading", "session-a")))
        assertEquals(
            ProfilePhotoPreviewState.None,
            restoreProfilePhotoPreviewState(
                listOf("awaiting", "session-a", "Add", 4, null, "file:///crop.jpg", "generation-1", 10L),
            ),
        )
    }

    @Test
    fun staleRemoteGenerationDoesNotClearCurrentPreview() {
        val awaiting = awaiting("generation-2")

        val result = awaiting.onMatchingRemoteSucceeded("photo-new", "generation-1")

        assertSame(awaiting, result.state)
        assertNull(result.cleanupUriString)
    }

    @Test
    fun matchingRemoteSuccessClearsOnlyMatchingPhotoAndGeneration() {
        val awaiting = awaiting("generation-1")

        val result = awaiting.onMatchingRemoteSucceeded("photo-new", "generation-1")

        assertEquals(ProfilePhotoPreviewState.None, result.state)
        assertEquals("file:///crop.jpg", result.cleanupUriString)
    }

    @Test
    fun staleRemotePhotoIdDoesNotClearCurrentPreview() {
        val awaiting = awaiting("generation-1")

        val result = awaiting.onMatchingRemoteSucceeded("photo-other", "generation-1")

        assertSame(awaiting, result.state)
        assertNull(result.cleanupUriString)
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
