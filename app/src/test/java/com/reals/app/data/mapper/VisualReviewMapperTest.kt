package com.reals.app.data.mapper

import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.VisualProfileResponseDto
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualReviewMapperTest {
    @Test
    fun visualProfileMapsAndSortsPhotosByPosition() {
        val dto = VisualProfileResponseDto(
            profileId = "profile-1",
            displayName = "Alex",
            age = 29,
            bio = "Bio",
            myPersonalMessageSubmitted = true,
            photos = listOf(
                PhotoResponseDto(
                    id = "photo-2",
                    url = "https://example.com/2.jpg",
                    position = 2,
                    isPersonPhoto = false,
                    isFullBody = true,
                    validationStatus = "VALIDATED",
                ),
                PhotoResponseDto(
                    id = "photo-1",
                    url = "https://example.com/1.jpg",
                    position = 1,
                    isPersonPhoto = true,
                    isFullBody = false,
                    validationStatus = "VALIDATED",
                ),
            ),
        )

        val domain = dto.toDomain()

        assertEquals("profile-1", domain.profileId)
        assertEquals("Alex", domain.displayName)
        assertEquals(29, domain.age)
        assertEquals(true, domain.myPersonalMessageSubmitted)
        assertEquals(listOf("photo-1", "photo-2"), domain.photos.map { it.id })
    }
}
