package com.reals.app.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorResponseDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun decodesNullableMessageFromBackendError() {
        val dto = json.decodeFromString<ErrorResponseDto>(
            """
            {
              "code": "DOMAIN_CONFLICT",
              "error": "Conflict",
              "message": null
            }
            """.trimIndent(),
        )

        assertEquals("DOMAIN_CONFLICT", dto.code)
        assertEquals("Conflict", dto.error)
        assertNull(dto.message)
    }
}
