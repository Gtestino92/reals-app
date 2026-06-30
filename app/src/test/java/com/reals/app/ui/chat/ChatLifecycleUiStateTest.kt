package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.TestDtos
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLifecycleUiStateTest {
    @Test
    fun `first chat does not warn before final minute`() {
        val chat = TestDtos.chat().copy(
            expiresAt = "2026-06-18T21:05:00Z",
            inactivityExpiresAt = null,
        ).toDomain()

        val state = firstChatLifecycleUiState(chat, millis("2026-06-18T21:03:59Z"))

        assertEquals(FirstChatExpiryReason.Absolute, state?.reason)
        assertFalse(state?.showCountdown == true)
        assertFalse(state?.expired == true)
    }

    @Test
    fun `first chat warns in final minute`() {
        val chat = TestDtos.chat().copy(
            expiresAt = "2026-06-18T21:05:00Z",
            inactivityExpiresAt = null,
        ).toDomain()

        val state = firstChatLifecycleUiState(chat, millis("2026-06-18T21:04:30Z"))

        assertTrue(state?.showCountdown == true)
        assertEquals(30L, state?.remainingSeconds)
    }

    @Test
    fun `first chat expires at zero`() {
        val chat = TestDtos.chat().copy(
            expiresAt = "2026-06-18T21:05:00Z",
            inactivityExpiresAt = null,
        ).toDomain()

        val state = firstChatLifecycleUiState(chat, millis("2026-06-18T21:05:00Z"))

        assertTrue(state?.expired == true)
    }

    @Test
    fun `inactivity reason wins when earlier than absolute deadline`() {
        val chat = TestDtos.chat().copy(
            expiresAt = "2026-06-18T21:10:00Z",
            inactivityExpiresAt = "2026-06-18T21:05:00Z",
        ).toDomain()

        val state = firstChatLifecycleUiState(chat, millis("2026-06-18T21:04:30Z"))

        assertEquals(FirstChatExpiryReason.Inactivity, state?.reason)
        assertEquals("El chat se cierra por inactividad en 30s.", state?.warningCopy())
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
