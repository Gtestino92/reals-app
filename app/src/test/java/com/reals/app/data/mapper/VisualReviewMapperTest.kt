package com.reals.app.data.mapper

import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.VisualAffinityIndicatorResponseDto
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
            visualExpiresAt = "2026-06-19T21:00:00Z",
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
                    moderationStatus = "APPROVED",
                ),
                PhotoResponseDto(
                    id = "photo-1",
                    url = "https://example.com/1.jpg",
                    position = 1,
                    isPersonPhoto = true,
                    isFullBody = false,
                    validationStatus = "VALIDATED",
                    moderationStatus = "APPROVED",
                ),
            ),
        )

        val domain = dto.toDomain()

        assertEquals("profile-1", domain.profileId)
        assertEquals("Alex", domain.displayName)
        assertEquals(29, domain.age)
        assertEquals("2026-06-19T21:00:00Z", domain.visualExpiresAt)
        assertEquals(true, domain.myPersonalMessageSubmitted)
        assertEquals(true, domain.partnerPersonalMessageSubmitted)
        assertEquals(false, domain.partnerPersonalMessageRead)
        assertEquals(true, domain.decisionRequiresPartnerPersonalMessageRead)
        assertEquals(listOf("photo-1", "photo-2"), domain.photos.map { it.id })
    }

    @Test
    fun `VisualProfileResponseDto maps affinity indicators in backend order`() {
        val dto = VisualProfileResponseDto(
            profileId = "profile-1",
            displayName = "Alex",
            age = 29,
            bio = null,
            photos = emptyList(),
            affinityIndicators = listOf(
                VisualAffinityIndicatorResponseDto(
                    categoryId = "MUSIC",
                    title = "Música",
                ),
                VisualAffinityIndicatorResponseDto(
                    categoryId = "CINEMA_SERIES_AND_STORIES",
                    title = "Cine, series y relatos",
                ),
            ),
        )

        val domain = dto.toDomain()

        assertEquals(
            listOf(
                "MUSIC" to "Música",
                "CINEMA_SERIES_AND_STORIES" to "Cine, series y relatos",
            ),
            domain.affinityIndicators.map { it.categoryId to it.title },
        )
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
        assertEquals(0, domain.affinityIndicators.size)
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

    @Test
    fun `VisualProfileResponseDto decodes affinity indicators from payload`() {
        val dto = testJson.decodeFromString<VisualProfileResponseDto>(
            """
            {
              "profileId": "profile-1",
              "displayName": "Alex",
              "age": 29,
              "bio": null,
              "photos": [],
              "affinityIndicators": [
                {
                  "categoryId": "MUSIC",
                  "title": "Música"
                },
                {
                  "categoryId": "SPORTS_AND_MOVEMENT",
                  "title": "Deportes y movimiento"
                }
              ]
            }
            """.trimIndent()
        )

        val domain = dto.toDomain()

        assertEquals(
            listOf(
                "MUSIC" to "Música",
                "SPORTS_AND_MOVEMENT" to "Deportes y movimiento",
            ),
            domain.affinityIndicators.map { it.categoryId to it.title },
        )
    }
}
