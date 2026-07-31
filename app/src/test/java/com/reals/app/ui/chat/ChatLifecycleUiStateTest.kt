package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.TestDtos
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLifecycleUiStateTest {
    @Test
    fun `second chat loading keeps short stable title`() {
        val presentation = chatLoadingPresentation("Segundo chat", null)

        assertEquals("Segundo chat", presentation.title)
        assertEquals("Estamos cargando el segundo chat.", presentation.body)
    }

    @Test
    fun `second chat loading keeps partner name out of headline`() {
        val presentation = chatLoadingPresentation("Segundo chat", "Alex")

        assertEquals("Segundo chat", presentation.title)
        assertEquals("Estamos cargando el segundo chat con Alex.", presentation.body)
    }

    @Test
    fun `first chat loading preserves generic loading body`() {
        val presentation = chatLoadingPresentation("Cargando chat", null)

        assertEquals("Cargando chat", presentation.title)
        assertEquals("Estamos preparando la conversación.", presentation.body)
    }

    @Test
    fun `first chat header uses stable title after partner loads`() {
        val title = chatHeaderTitle("Chat", "Juls")

        assertEquals("Chat con Juls", title)
    }

    @Test
    fun `loaded chat header does not show loading when partner is unavailable`() {
        val title = chatHeaderTitle("Chat", null)

        assertEquals("Chat", title)
    }

    @Test
    fun `second chat header keeps second chat title after partner loads`() {
        val title = chatHeaderTitle("Segundo chat", "Juls")

        assertEquals("Segundo chat con Juls", title)
    }

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

    @Test
    fun `first chat header hides permanent expiration deadline`() {
        val lifecycle = FirstChatLifecycleUiState(
            deadline = "2026-06-18T21:05:00Z",
            reason = FirstChatExpiryReason.Inactivity,
            remainingMillis = 30_000L,
            showCountdown = true,
            expired = false,
        )

        assertNull(
            chatHeaderStatusText(
                expiresAt = "2026-06-18T21:10:00Z",
                firstChatLifecycle = lifecycle,
                secondChatReadOnlyUntil = null,
                secondChatUnavailable = false,
                formatDateTime = { "formatted-$it" },
            )
        )
    }

    @Test
    fun `absolute countdown copy remains distinct`() {
        val lifecycle = FirstChatLifecycleUiState(
            deadline = "2026-06-18T21:05:00Z",
            reason = FirstChatExpiryReason.Absolute,
            remainingMillis = 30_000L,
            showCountdown = true,
            expired = false,
        )

        assertEquals("El chat vence en 30s.", lifecycle.warningCopy())
    }

    @Test
    fun `second chat expiration and read only text remains unchanged`() {
        val readOnlyText = chatHeaderStatusText(
            expiresAt = "2026-06-18T21:10:00Z",
            firstChatLifecycle = null,
            secondChatReadOnlyUntil = "2026-06-18T22:10:00Z",
            secondChatUnavailable = false,
            formatDateTime = { value -> "formatted-$value" },
        )
        val unavailableText = chatHeaderStatusText(
            expiresAt = "2026-06-18T21:10:00Z",
            firstChatLifecycle = null,
            secondChatReadOnlyUntil = null,
            secondChatUnavailable = true,
            formatDateTime = { value -> "formatted-$value" },
        )

        assertEquals(
            "Este segundo chat venció. Podés leerlo hasta formatted-2026-06-18T22:10:00Z.",
            readOnlyText,
        )
        assertEquals("Este segundo chat ya no está disponible.", unavailableText)
    }

    @Test
    fun `second chat header hides permanent expiration when available`() {
        val text = chatHeaderStatusText(
            expiresAt = "2026-06-18T21:10:00Z",
            firstChatLifecycle = null,
            secondChatReadOnlyUntil = null,
            secondChatUnavailable = false,
            formatDateTime = { value -> "formatted-$value" },
        )

        assertNull(text)
    }

    @Test
    fun `second chat local expiry dispatch can fire again after corrected active state`() {
        val dispatches = listOf(true, false, true)
            .count(::shouldDispatchSecondChatLocalAbsoluteExpiry)

        assertEquals(2, dispatches)
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
