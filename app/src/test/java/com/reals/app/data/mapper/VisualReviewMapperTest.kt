package com.reals.app.data.mapper

import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.VisualProfileResponseDto
import com.reals.app.testutil.testJson
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualReviewMapperTest {
    @Test
    fun `VisualProfileResponseDto maps myPersonalMessageSubmitted`() {
        val dto = VisualProfileResponseDto(
            profileId = "profile-1",
            displayName = "Alex",
            age = 29,
            bio = "Bio",
            myPersonalMessageSubmitted = true,
            partnerPersonalMessageSubmitted = true,
            partnerPersonalMessageRead = false,
            decisionRequiresPartnerPersonalMessageRead = true,
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
        assertEquals(true, domain.partnerPersonalMessageSubmitted)
        assertEquals(false, domain.partnerPersonalMessageRead)
        assertEquals(true, domain.decisionRequiresPartnerPersonalMessageRead)
        assertEquals(listOf("photo-1", "photo-2"), domain.photos.map { it.id })
    }

    @Test
    fun `VisualProfileResponseDto defaults optional message metadata`() {
        val dto = testJson.decodeFromString<VisualProfileResponseDto>(
            """
            {
              "profileId": "profile-1",
              "displayName": "Alex",
              "age": 29,
              "bio": null,
              "photos": []
            }
            """.trimIndent()
        )

        val domain = dto.toDomain()

        assertEquals(false, domain.myPersonalMessageSubmitted)
        assertEquals(false, domain.partnerPersonalMessageSubmitted)
        assertEquals(true, domain.partnerPersonalMessageRead)
        assertEquals(false, domain.decisionRequiresPartnerPersonalMessageRead)
    }

    @Test
    fun `VisualProfileResponseDto falls back to legacy approval read requirement`() {
        val dto = VisualProfileResponseDto(
            profileId = "profile-1",
            displayName = "Alex",
            age = 29,
            bio = null,
            photos = emptyList(),
            approvalRequiresPartnerPersonalMessageRead = true,
        )

        val domain = dto.toDomain()

        assertEquals(true, domain.decisionRequiresPartnerPersonalMessageRead)
    }
}
