package com.reals.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedExitRequestCardStateTest {
    @Test
    fun `timeout request waits until countdown expires`() {
        assertFalse(shouldRequestExitTimeout(remainingSeconds = 1L, actionsDisabled = false))
    }

    @Test
    fun `timeout request waits while action is already in progress`() {
        assertFalse(shouldRequestExitTimeout(remainingSeconds = 0L, actionsDisabled = true))
    }

    @Test
    fun `timeout request runs when expired and no action is in progress`() {
        assertTrue(shouldRequestExitTimeout(remainingSeconds = 0L, actionsDisabled = false))
    }

    @Test
    fun `requester copy before expiry waits for other user`() {
        assertEquals(
            "Esperando respuesta. Si no contesta, el chat se cierra en 12s.",
            timedExitRequestBodyText(requestedByMe = true, remainingSeconds = 12L),
        )
    }

    @Test
    fun `responder copy before expiry prompts response`() {
        assertEquals(
            "Te propusieron cerrar el chat. Responde en 12s.",
            timedExitRequestBodyText(requestedByMe = false, remainingSeconds = 12L),
        )
    }

    @Test
    fun `expired copy is shared by requester and responder`() {
        val expected = "La solicitud vencio. Estamos cerrando el chat."

        assertEquals(expected, timedExitRequestBodyText(requestedByMe = true, remainingSeconds = 0L))
        assertEquals(expected, timedExitRequestBodyText(requestedByMe = false, remainingSeconds = 0L))
    }
}
