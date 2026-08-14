package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoInteractionStateTest {
    @Test
    fun selectingEmptySlotCreatesAddPickerTarget() {
        val state = ProfilePhotoInteractionState()

        state.startAddSelection(position = 3)

        val transaction = state.selectionTransaction as ProfilePhotoSelectionTransaction.AwaitingPicker
        assertEquals(ProfilePhotoSelectionTarget.Add(3), transaction.target)
    }

    @Test
    fun selectingOccupiedSlotCreatesReplacePickerTarget() {
        val state = ProfilePhotoInteractionState()

        state.startReplacementSelection(photoId = "photo-7", position = 7)

        val transaction = state.selectionTransaction as ProfilePhotoSelectionTransaction.AwaitingPicker
        assertEquals(ProfilePhotoSelectionTarget.Replace(photoId = "photo-7", position = 7), transaction.target)
    }

    @Test
    fun pickerCancellationClearsSelectionTransaction() {
        val state = ProfilePhotoInteractionState()
        state.startAddSelection(position = 3)

        state.onPickerResult(uriString = null)

        assertNull(state.selectionTransaction)
    }

    @Test
    fun pickerSuccessAdvancesToCropWithOriginalTarget() {
        val state = ProfilePhotoInteractionState()
        state.startReplacementSelection(photoId = "photo-2", position = 2)

        state.onPickerResult(uriString = "content://picker/photo")

        val transaction = state.cropTransaction
        assertEquals(ProfilePhotoSelectionTarget.Replace(photoId = "photo-2", position = 2), transaction?.target)
        assertEquals("content://picker/photo", transaction?.sourceUriString)
    }

    @Test
    fun cropCancellationClearsTransaction() {
        val state = ProfilePhotoInteractionState()
        state.startAddSelection(position = 4)
        state.onPickerResult(uriString = "content://picker/photo")

        state.cancelCrop()

        assertNull(state.selectionTransaction)
    }

    @Test
    fun cropSuccessCreatesAddPreviewAndActiveAction() {
        val state = ProfilePhotoInteractionState()
        state.startAddSelection(position = 4)
        state.onPickerResult(uriString = "content://picker/photo")

        val confirmation = state.confirmCrop(
            croppedUriString = "file:///cache/crop-a.jpg",
            photos = emptyList(),
            cropConfirmedAtElapsedMillis = 10L,
        )

        assertEquals(ProfilePhotoSelectionTarget.Add(4), confirmation?.target)
        assertNull(confirmation?.cleanupUriString)
        assertEquals(ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, 4), state.activeAction)
        val uploading = state.previewState as ProfilePhotoPreviewState.Uploading
        assertEquals("file:///cache/crop-a.jpg", uploading.preview.uriString)
        assertEquals("local-1", uploading.preview.generation)
    }

    @Test
    fun cropSuccessCreatesReplacePreviewWithPhotoIdAndOldCacheKey() {
        val state = ProfilePhotoInteractionState()
        state.startReplacementSelection(photoId = "photo-2", position = 2)
        state.onPickerResult(uriString = "content://picker/photo")

        state.confirmCrop(
            croppedUriString = "file:///cache/crop-r.jpg",
            photos = listOf(photo("photo-2", 2, "https://static.reals.local/photo-2.jpg")),
            cropConfirmedAtElapsedMillis = 10L,
        )

        assertEquals(
            ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, 2, "photo-2"),
            state.activeAction,
        )
        val uploading = state.previewState as ProfilePhotoPreviewState.Uploading
        assertEquals("photo-2", uploading.preview.action.photoId)
        assertEquals("https://static.reals.local/photo-2.jpg".stableProfilePhotoCacheKey(), uploading.preview.oldCanonicalCacheKey)
    }

    @Test
    fun uploadSuccessForExpectedBackendPhotoAwaitsRemoteHandoff() {
        val state = uploadingAddState(position = 4)

        val transition = state.prepareMatchingUploadSucceeded(
            photos = listOf(photo("photo-new", 4)),
            uploadResponseAtElapsedMillis = 42L,
        ) as ProfilePhotoUploadSuccessTransition.AwaitingRemote
        state.commitPreparedUploadSuccess(transition.prepared)

        val awaiting = state.previewState as ProfilePhotoPreviewState.AwaitingRemote
        assertEquals("photo-new", awaiting.remotePhotoId)
        assertEquals("local-1", awaiting.preview.generation)
    }

    @Test
    fun uploadErrorClearsOnlyMatchingUploadingPreview() {
        val state = uploadingAddState(position = 4)

        val transition = state.onMatchingUploadFailed(responseAtElapsedMillis = 42L)

        assertEquals("file:///cache/crop.jpg", transition?.cleanupUriString)
        assertEquals(ProfilePhotoPreviewState.None, state.previewState)
    }

    @Test
    fun deleteActionClearsExistingPreview() {
        val state = uploadingAddState(position = 4)

        val cleanupUriString = state.startDelete(photoId = "photo-2", position = 2)

        assertEquals("file:///cache/crop.jpg", cleanupUriString)
        assertEquals(ProfilePhotoPreviewState.None, state.previewState)
        assertEquals(ProfilePhotoActionPresentation(ProfilePhotoActionKind.Delete, 2, "photo-2"), state.activeAction)
    }

    @Test
    fun oldPreparedSuccessCannotClearNewerPreview() {
        val state = uploadingAddState(position = 4)
        val oldTransition = state.prepareMatchingUploadSucceeded(
            photos = listOf(photo("photo-new", 4)),
            uploadResponseAtElapsedMillis = 42L,
        ) as ProfilePhotoUploadSuccessTransition.AwaitingRemote

        state.startAddSelection(position = 5)
        state.onPickerResult(uriString = "content://picker/next")
        state.confirmCrop(
            croppedUriString = "file:///cache/new-crop.jpg",
            photos = emptyList(),
            cropConfirmedAtElapsedMillis = 50L,
        )

        val cleanup = state.commitPreparedUploadSuccess(oldTransition.prepared)

        assertNull(cleanup)
        val uploading = state.previewState as ProfilePhotoPreviewState.Uploading
        assertEquals("file:///cache/new-crop.jpg", uploading.preview.uriString)
        assertEquals("local-2", uploading.preview.generation)
    }

    @Test
    fun remoteHandoffClearsOnlyMatchingPhotoAndGeneration() {
        val state = uploadingAddState(position = 4)
        val transition = state.prepareMatchingUploadSucceeded(
            photos = listOf(photo("photo-new", 4)),
            uploadResponseAtElapsedMillis = 42L,
        ) as ProfilePhotoUploadSuccessTransition.AwaitingRemote
        state.commitPreparedUploadSuccess(transition.prepared)

        val stale = state.onRemotePreviewDisplayed("photo-other", "local-1", displayedAtElapsedMillis = 55L)
        val matching = state.onRemotePreviewDisplayed("photo-new", "local-1", displayedAtElapsedMillis = 60L)

        assertNull(stale)
        assertEquals("file:///cache/crop.jpg", matching?.cleanupUriString)
        assertEquals(ProfilePhotoPreviewState.None, state.previewState)
    }

    @Test
    fun saveRestorePreservesPickerCropActiveActionAndPreview() {
        val state = uploadingAddState(position = 4)
        state.startReplacementSelection(photoId = "photo-2", position = 2)
        state.onPickerResult(uriString = "content://picker/replacement")

        val restored = restoreProfilePhotoInteractionState(saveProfilePhotoInteractionState(state))

        assertEquals(state.activeAction, restored.activeAction)
        assertEquals(state.previewGenerationCounter, restored.previewGenerationCounter)
        assertEquals(state.previewState, restored.previewState)
        assertEquals(state.cropTransaction, restored.cropTransaction)
    }

    private fun uploadingAddState(position: Int): ProfilePhotoInteractionState =
        ProfilePhotoInteractionState().also { state ->
            state.startAddSelection(position)
            state.onPickerResult(uriString = "content://picker/photo")
            state.confirmCrop(
                croppedUriString = "file:///cache/crop.jpg",
                photos = emptyList(),
                cropConfirmedAtElapsedMillis = 10L,
            )
        }

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
}
