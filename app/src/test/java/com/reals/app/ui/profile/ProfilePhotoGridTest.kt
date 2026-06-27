package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun testPhoto(id: String, position: Int): ProfilePhoto =
        ProfilePhoto(
            id = id,
            url = "https://static.reals.local/$id.jpg",
            position = position,
            isPersonPhoto = true,
            isFullBody = false,
            validationStatus = "APPROVED",
        )
}
