package com.reals.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorTest {

    @Test
    fun `active penalty maps to specific user message`() {
        val error = backendError("ACTIVE_PENALTY")

        assertEquals(
            "Por ahora no podes entrar a la busqueda. Intenta nuevamente mas adelante.",
            error.toUserMessage(ErrorContext.Matchmaking),
        )
    }

    @Test
    fun `account deleted maps to specific user message`() {
        val error = backendError("ACCOUNT_DELETED")

        assertEquals(
            "Esta cuenta esta pendiente de eliminacion. Podes recuperarla si todavia esta dentro del plazo.",
            error.toUserMessage(),
        )
    }

    @Test
    fun `second chat expired maps to specific user message`() {
        val error = backendError("SECOND_CHAT_EXPIRED")

        assertEquals(
            "El segundo chat ya vencio.",
            error.toUserMessage(ErrorContext.Chat),
        )
    }

    @Test
    fun `unknown code maps to generic fallback`() {
        val error = backendError("SOME_NEW_BACKEND_CODE", message = "technical backend detail")

        assertEquals(
            "Intenta nuevamente en unos segundos.",
            error.toUserMessage(),
        )
    }

    @Test
    fun `null and empty codes map to generic fallback`() {
        assertEquals(
            "Intenta nuevamente en unos segundos.",
            backendError(null).toUserMessage(),
        )
        assertEquals(
            "Intenta nuevamente en unos segundos.",
            backendError("").toUserMessage(),
        )
    }

    private fun backendError(
        code: String?,
        message: String = "backend error",
    ): ApiError.Backend = ApiError.Backend(
        statusCode = 400,
        code = code,
        error = code,
        message = message,
    )
}
