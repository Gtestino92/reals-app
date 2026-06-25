package com.reals.app.data.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTokenDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `register push token request encodes required platform`() {
        val body = json.encodeToString(
            RegisterPushTokenRequestDto(
                token = "fcm-token",
                platform = "ANDROID",
            ),
        )

        assertTrue(body.contains(""""platform":"ANDROID""""))
        assertEquals("""{"token":"fcm-token","platform":"ANDROID"}""", body)
    }
}
