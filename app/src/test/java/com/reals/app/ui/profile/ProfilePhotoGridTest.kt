package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfilePhotoModerationStatusNeedsReview
import com.reals.app.domain.model.ProfilePhotoModerationStatusRejected
import com.reals.app.domain.model.PhotoPlacementInput
import com.reals.app.domain.model.isPendingModerationReview
import com.reals.app.domain.model.isRejectedByModeration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoGridTest {
    @Test
    fun gridPositionsAreOneThroughNine() {
        assertEquals((1..9).toList(), ProfilePhotoGridPositions.toList())
    }

    @Test
    fun profilePhotosByGridPositionMapsOnlyGridSlots() {
        val firstSlot = testPhoto(id = "photo-1", position = 1)
        val ninthSlot = testPhoto(id = "photo-9", position = 9)
        val outOfRange = testPhoto(id = "photo-10", position = 10)

        val photosByPosition = listOf(firstSlot, ninthSlot, outOfRange).profilePhotosByGridPosition()

        assertEquals(firstSlot, photosByPosition[1])
        assertEquals(ninthSlot, photosByPosition[9])
        assertFalse(photosByPosition.containsKey(10))
    }

    @Test
    fun profilePhotosByGridPositionKeepsPendingReviewPhotosVisibleToOwner() {
        val pendingReview = testPhoto(
            id = "photo-1",
            position = 1,
            moderationStatus = ProfilePhotoModerationStatusNeedsReview,
        )

        val photosByPosition = listOf(pendingReview).profilePhotosByGridPosition()

        assertEquals(pendingReview, photosByPosition[1])
        assertTrue(photosByPosition.getValue(1).isPendingModerationReview())
    }

    @Test
    fun profilePhotosByGridPositionKeepsRejectedPhotosVisibleToOwner() {
        val rejected = testPhoto(
            id = "photo-1",
            position = 1,
            moderationStatus = ProfilePhotoModerationStatusRejected,
        )

        val photosByPosition = listOf(rejected).profilePhotosByGridPosition()

        assertEquals(rejected, photosByPosition[1])
        assertTrue(photosByPosition.getValue(1).isRejectedByModeration())
    }

    @Test
    fun ownerModerationStatusPresentationExplainsRejectedReplacement() {
        val rejected = testPhoto(
            id = "photo-1",
            position = 1,
            moderationStatus = ProfilePhotoModerationStatusRejected,
        )

        val presentation = rejected.ownerModerationStatusPresentation()

        assertEquals("Rechazada", presentation?.label)
        assertEquals("Foto 1 rechazada. Debe ser reemplazada.", presentation?.contentDescription)
    }

    @Test
    fun movePhotoLocallyMovesToEmptyPositionAndPreservesHoles() {
        val photos = listOf(
            testPhoto(id = "photo-1", position = 1),
            testPhoto(id = "photo-2", position = 4),
        )

        val placements = movePhotoLocally(
            photos = photos,
            pendingOrder = null,
            photoId = "photo-1",
            targetPosition = 7,
        )

        assertEquals(listOf(4, 7), placements.map { it.position }.sorted())
        assertEquals(7, placements.first { it.photoId == "photo-1" }.position)
        assertEquals(4, placements.first { it.photoId == "photo-2" }.position)
    }

    @Test
    fun movePhotoLocallySwapsWithOccupiedPosition() {
        val photos = listOf(
            testPhoto(id = "photo-1", position = 1),
            testPhoto(id = "photo-2", position = 4),
        )

        val placements = movePhotoLocally(
            photos = photos,
            pendingOrder = null,
            photoId = "photo-1",
            targetPosition = 4,
        )

        assertEquals(4, placements.first { it.photoId == "photo-1" }.position)
        assertEquals(1, placements.first { it.photoId == "photo-2" }.position)
    }

    @Test
    fun movePhotoLocallySamePositionIsNoOp() {
        val photos = listOf(
            testPhoto(id = "photo-1", position = 1),
            testPhoto(id = "photo-2", position = 4),
        )

        val placements = movePhotoLocally(
            photos = photos,
            pendingOrder = null,
            photoId = "photo-1",
            targetPosition = 1,
        )

        assertEquals(buildCompletePhotoPlacements(photos), placements)
    }

    @Test
    fun movePhotoLocallyDoesNotDuplicatePositionsOrDropPhotoIds() {
        val photos = listOf(
            testPhoto(id = "photo-1", position = 1),
            testPhoto(id = "photo-2", position = 2),
            testPhoto(id = "photo-3", position = 9),
        )

        val placements = movePhotoLocally(
            photos = photos,
            pendingOrder = null,
            photoId = "photo-3",
            targetPosition = 2,
        )

        assertEquals(placements.size, placements.map { it.position }.toSet().size)
        assertEquals(
            photos.map { it.id }.sorted(),
            placements.map { it.photoId }.sorted(),
        )
    }

    @Test
    fun photosWithPendingOrderOverridesDisplayPositions() {
        val photos = listOf(
            testPhoto(id = "photo-1", position = 1),
            testPhoto(id = "photo-2", position = 4),
        )
        val pending = listOf(
            PhotoPlacementInput(photoId = "photo-1", position = 4),
            PhotoPlacementInput(photoId = "photo-2", position = 1),
        )

        val display = photosWithPendingOrder(photos, pending)

        assertEquals(listOf("photo-2", "photo-1"), display.map { it.id })
        assertEquals(listOf(1, 4), display.map { it.position })
        assertEquals(photos.first().url, display.first { it.id == "photo-1" }.url)
    }

    private fun testPhoto(
        id: String,
        position: Int,
        moderationStatus: String = "APPROVED",
    ): ProfilePhoto =
        ProfilePhoto(
            id = id,
            url = "https://static.reals.local/$id.jpg",
            position = position,
            isPersonPhoto = true,
            isFullBody = false,
            validationStatus = "APPROVED",
            moderationStatus = moderationStatus,
        )
}
