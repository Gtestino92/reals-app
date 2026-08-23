package com.reals.app.data.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `normal home response decodes with unknown keys and default collections`() {
        val dto = json.decodeFromString<HomeResponseDto>(
            """
            {
              "profileStatus": null,
              "matchmaking": {
                "inQueue": false,
                "canSearch": true,
                "futureBackendField": "ignored"
              },
              "activeInteractionsSummary": {},
              "unexpectedTopLevel": true
            }
            """.trimIndent(),
        )

        assertNull(dto.profileStatus)
        assertEquals(false, dto.matchmaking.inQueue)
        assertEquals(true, dto.matchmaking.canSearch)
        assertEquals(0, dto.activeInteractionsSummary.activeInitialCount)
        assertEquals(0, dto.activeInteractionsSummary.activeConnectionCount)
        assertEquals(emptyList<HomePendingActionResponseDto>(), dto.pendingActions)
        assertEquals(emptyList<HomeNextStepResponseDto>(), dto.nextSteps)
        assertEquals(emptyList<HomePassiveNoticeResponseDto>(), dto.passiveNotices)
    }

    @Test
    fun `backend error response decodes nullable message and ignores unknown keys`() {
        val dto = json.decodeFromString<ErrorResponseDto>(
            """
            {
              "code": "EMAIL_NOT_VERIFIED",
              "error": "Conflict",
              "message": null,
              "traceId": "trace-1"
            }
            """.trimIndent(),
        )

        assertEquals("EMAIL_NOT_VERIFIED", dto.code)
        assertEquals("Conflict", dto.error)
        assertNull(dto.message)
    }

    @Test
    fun `request DTO encoding preserves backend field names`() {
        val body = json.encodeToString(
            CreateProfileRequestDto(
                displayName = "Ada",
                birthDate = "1997-01-02",
                gender = "WOMAN",
                lookingForGenders = setOf("MAN"),
                intention = "LONG_TERM",
                city = "Buenos Aires",
                countryCode = "AR",
                bio = "hello",
                preferredMinAge = 28,
                preferredMaxAge = 40,
                maxDistanceKm = 25,
            ),
        )

        assertTrue(body.contains(""""displayName":"Ada""""))
        assertTrue(body.contains(""""birthDate":"1997-01-02""""))
        assertTrue(body.contains(""""lookingForGenders":["MAN"]"""))
        assertTrue(body.contains(""""countryCode":"AR""""))
        assertTrue(body.contains(""""maxDistanceKm":25"""))
    }

    @Test
    fun `notification preferences request serializes all required booleans`() {
        val body = json.encodeToString(
            NotificationPreferencesRequestDto(
                activityEnabled = true,
                remindersEnabled = false,
                availabilityEnabled = true,
            ),
        )

        val fields = json.parseToJsonElement(body).jsonObject
        assertEquals(true, fields.getValue("activityEnabled").jsonPrimitive.boolean)
        assertEquals(false, fields.getValue("remindersEnabled").jsonPrimitive.boolean)
        assertEquals(true, fields.getValue("availabilityEnabled").jsonPrimitive.boolean)
        assertEquals(3, fields.size)
    }

    @Test
    fun `profile DTOs and models do not contain notification preferences`() {
        listOf(
            ProfileResponseDto::class.java,
            CreateProfileRequestDto::class.java,
            UpdateProfileRequestDto::class.java,
            Profile::class.java,
            ProfileSnapshot::class.java,
        ).forEach { type ->
            assertFalse(
                "${type.simpleName} must not contain notification preferences",
                type.declaredFields.any { field ->
                    field.name.contains("notification", ignoreCase = true) ||
                        field.name.contains("activityEnabled", ignoreCase = true) ||
                        field.name.contains("remindersEnabled", ignoreCase = true) ||
                        field.name.contains("availabilityEnabled", ignoreCase = true)
                },
            )
        }
    }

    @Test
    fun `chat messages response decodes nullable server time and hasMore default`() {
        val dto = json.decodeFromString<ChatMessagesResponseDto>(
            """
            {
              "messages": [
                {
                  "id": "message-1",
                  "chatSessionId": "chat-1",
                  "senderId": "user-1",
                  "content": "hola",
                  "sentAt": "2026-07-21T12:00:00Z"
                }
              ],
              "serverTime": null
            }
            """.trimIndent(),
        )

        assertEquals(1, dto.messages.size)
        assertEquals("message-1", dto.messages.single().id)
        assertEquals(false, dto.hasMore)
        assertNull(dto.serverTime)
    }
}
