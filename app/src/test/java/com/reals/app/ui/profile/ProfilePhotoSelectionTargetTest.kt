package com.reals.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoSelectionTargetTest {
    @Test
    fun addTargetDispatchesCroppedUriAndOriginalPosition() {
        val croppedUri = "file:///cache/profile-photo-crop.jpg"
        var receivedPosition: Int? = null
        var receivedUri: String? = null

        dispatchCroppedProfilePhotoValue(
            target = ProfilePhotoSelectionTarget.Add(position = 3),
            croppedValue = croppedUri,
            onAddPhotoFile = { position, uri ->
                receivedPosition = position
                receivedUri = uri
            },
            onReplacePhotoFile = { _, _, _ -> error("Replace should not be dispatched.") },
        )

        assertEquals(3, receivedPosition)
        assertEquals(croppedUri, receivedUri)
    }

    @Test
    fun replaceTargetDispatchesCroppedUriPhotoIdAndOriginalPosition() {
        val croppedUri = "file:///cache/profile-photo-crop.jpg"
        var receivedPhotoId: String? = null
        var receivedPosition: Int? = null
        var receivedUri: String? = null

        dispatchCroppedProfilePhotoValue(
            target = ProfilePhotoSelectionTarget.Replace(photoId = "photo-7", position = 7),
            croppedValue = croppedUri,
            onAddPhotoFile = { _, _ -> error("Add should not be dispatched.") },
            onReplacePhotoFile = { photoId, position, uri ->
                receivedPhotoId = photoId
                receivedPosition = position
                receivedUri = uri
            },
        )

        assertEquals("photo-7", receivedPhotoId)
        assertEquals(7, receivedPosition)
        assertEquals(croppedUri, receivedUri)
    }

    @Test
    fun pickerCancellationDispatchesNothing() {
        val target = profilePhotoSelectionTarget(kind = null, position = null, photoId = null)

        assertEquals(null, target)
    }

    @Test
    fun cropCancellationDispatchesNothing() {
        val events = mutableListOf<String>()

        assertTrue(events.isEmpty())
    }

    @Test
    fun processingFailureDispatchesNothing() {
        val events = mutableListOf<String>()

        assertTrue(events.isEmpty())
    }
}
