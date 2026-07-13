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
    fun `partner personal message unread codes map to visual review message`() {
        listOf(
            "PARTNER_PERSONAL_MESSAGE_NOT_READ",
            "VISUAL_REVIEW_PARTNER_MESSAGE_NOT_READ",
        ).forEach { code ->
            assertEquals(
                "Lee el mensaje personal de la otra persona antes de decidir.",
                backendError(code).toUserMessage(ErrorContext.VisualReview),
            )
        }
    }

    @Test
    fun `email not verified maps to profile activation message`() {
        val error = backendError("EMAIL_NOT_VERIFIED")

        assertEquals(BackendErrorCode.EmailNotVerified, error.backendErrorCode)
        assertEquals(
            "Verificá tu email antes de activar el perfil.",
            error.toUserMessage(ErrorContext.ProfileActivation),
        )
    }

    @Test
    fun `invalid profile country maps to deterministic profile message`() {
        val error = backendError("INVALID_PROFILE_COUNTRY", message = "technical detail")

        assertEquals(BackendErrorCode.InvalidProfileCountry, error.backendErrorCode)
        assertEquals(
            "Seleccioná un país válido.",
            error.toUserMessage(ErrorContext.ProfileCreation),
        )
        assertEquals(
            "Seleccioná un país válido.",
            error.toUserMessage(ErrorContext.ProfileUpdate),
        )
    }

    @Test
    fun `authenticity verification backend codes map to deterministic messages`() {
        mapOf(
            "AUTHENTICITY_VERIFICATION_NOT_CONFIGURED" to
                "La verificacion de autenticidad del perfil no esta disponible en este entorno.",
            "AUTHENTICITY_VERIFICATION_PROVIDER_ERROR" to
                "No pudimos completar la verificacion de autenticidad del perfil. Intenta nuevamente mas tarde.",
            "PROFILE_AUTHENTICITY_VERIFICATION_REQUIRED" to
                "Necesitas verificar la autenticidad del perfil antes de activarlo.",
        ).forEach { (code, expected) ->
            assertEquals(
                expected,
                backendError(code).toUserMessage(ErrorContext.ProfileActivation),
            )
        }
    }

    @Test
    fun `fromRaw parses authenticity verification backend codes`() {
        assertEquals(
            BackendErrorCode.AuthenticityVerificationNotConfigured,
            BackendErrorCode.fromRaw("AUTHENTICITY_VERIFICATION_NOT_CONFIGURED"),
        )
        assertEquals(
            BackendErrorCode.AuthenticityVerificationProviderError,
            BackendErrorCode.fromRaw("AUTHENTICITY_VERIFICATION_PROVIDER_ERROR"),
        )
        assertEquals(
            BackendErrorCode.ProfileAuthenticityVerificationRequired,
            BackendErrorCode.fromRaw("PROFILE_AUTHENTICITY_VERIFICATION_REQUIRED"),
        )
    }

    @Test
    fun `fromRaw parses chat backend codes`() {
        mapOf(
            "CHAT_NOT_FOUND" to BackendErrorCode.ChatNotFound,
            "CHAT_NOT_AVAILABLE" to BackendErrorCode.ChatNotAvailable,
            "CHAT_EXPIRED" to BackendErrorCode.ChatExpired,
            "CHAT_ABANDONED" to BackendErrorCode.ChatAbandoned,
            "CHAT_MESSAGE_INVALID" to BackendErrorCode.ChatMessageInvalid,
            "CHAT_DECISION_NOT_AVAILABLE" to BackendErrorCode.ChatDecisionNotAvailable,
            "CHAT_DECISION_ALREADY_SUBMITTED" to BackendErrorCode.ChatDecisionAlreadySubmitted,
            "CHAT_MIN_MESSAGES_REQUIRED" to BackendErrorCode.ChatMinMessagesRequired,
            "CHAT_MUTUAL_CANCELLATION_PENDING" to BackendErrorCode.ChatMutualCancellationPending,
            "FIRST_CHAT_GUIDANCE_PARTICIPATION_REQUIRED" to BackendErrorCode.FirstChatGuidanceParticipationRequired,
            "FIRST_CHAT_GUIDANCE_NEXT_ALREADY_REQUESTED" to BackendErrorCode.FirstChatGuidanceNextAlreadyRequested,
            "FIRST_CHAT_GUIDANCE_COMPLETED" to BackendErrorCode.FirstChatGuidanceCompleted,
            "CHAT_EXIT_REQUEST_NOT_FOUND" to BackendErrorCode.ChatExitRequestNotFound,
            "CHAT_EXIT_REQUEST_NOT_AVAILABLE" to BackendErrorCode.ChatExitRequestNotAvailable,
            "CHAT_EXIT_REQUEST_ALREADY_PENDING" to BackendErrorCode.ChatExitRequestAlreadyPending,
        ).forEach { (raw, expected) ->
            assertEquals(expected, BackendErrorCode.fromRaw(raw))
        }
    }

    @Test
    fun `chat backend codes map to deterministic chat messages`() {
        mapOf(
            "CHAT_NOT_FOUND" to "No encontramos esta conversacion. Actualiza el estado.",
            "CHAT_NOT_AVAILABLE" to "Esta conversacion ya no esta disponible. Actualiza el estado.",
            "CHAT_EXPIRED" to "La conversaci\u00f3n venci\u00f3.",
            "CHAT_ABANDONED" to "La conversaci\u00f3n se cerr\u00f3 por inactividad.",
            "CHAT_MESSAGE_INVALID" to
                "Revisa el mensaje. No puede estar vacio ni superar el limite permitido.",
            "CHAT_DECISION_NOT_AVAILABLE" to
                "La decision sobre esta conversacion ya no esta disponible. Actualiza el estado.",
            "CHAT_DECISION_ALREADY_SUBMITTED" to
                "Ya enviaste tu decision para esta conversacion.",
            "CHAT_MIN_MESSAGES_REQUIRED" to
                "Antes de decidir, envia al menos un poco mas de conversacion.",
            "CHAT_MUTUAL_CANCELLATION_PENDING" to
                "Hay una solicitud de salida pendiente. Resolvela antes de decidir.",
            "FIRST_CHAT_GUIDANCE_PARTICIPATION_REQUIRED" to
                "Particip\u00e1 un poco m\u00e1s antes de pedir otra pregunta.",
            "FIRST_CHAT_GUIDANCE_NEXT_ALREADY_REQUESTED" to
                "Ya pediste cambiar esta pregunta.",
            "FIRST_CHAT_GUIDANCE_COMPLETED" to
                "Ya completaron las preguntas de esta conversaci\u00f3n.",
            "CHAT_EXIT_REQUEST_NOT_FOUND" to
                "No encontramos esa solicitud de salida. Actualiza la conversacion.",
            "CHAT_EXIT_REQUEST_NOT_AVAILABLE" to
                "Esa solicitud de salida ya no esta disponible.",
            "CHAT_EXIT_REQUEST_ALREADY_PENDING" to
                "Ya hay una solicitud de salida pendiente.",
        ).forEach { (code, expected) ->
            assertEquals(
                expected,
                backendError(code, message = "raw backend message").toUserMessage(ErrorContext.Chat),
            )
        }
    }

    @Test
    fun `fromRaw parses scheduling backend codes`() {
        mapOf(
            "SCHEDULING_NOT_AVAILABLE" to BackendErrorCode.SchedulingNotAvailable,
            "SCHEDULING_EXPIRED" to BackendErrorCode.SchedulingExpired,
            "SCHEDULING_NEGOTIATION_NOT_FOUND" to BackendErrorCode.SchedulingNegotiationNotFound,
            "SCHEDULING_INVALID_PROPOSALS" to BackendErrorCode.SchedulingInvalidProposals,
            "SCHEDULING_PROPOSALS_ALREADY_SUBMITTED" to BackendErrorCode.SchedulingProposalsAlreadySubmitted,
            "SCHEDULING_ROUND_CHANGED" to BackendErrorCode.SchedulingRoundChanged,
            "SCHEDULING_PROPOSAL_NOT_AVAILABLE" to BackendErrorCode.SchedulingProposalNotAvailable,
            "SCHEDULING_CANNOT_ACCEPT_OWN_PROPOSAL" to BackendErrorCode.SchedulingCannotAcceptOwnProposal,
            "SCHEDULING_PARTNER_PROPOSALS_NOT_AVAILABLE" to
                BackendErrorCode.SchedulingPartnerProposalsNotAvailable,
        ).forEach { (raw, expected) ->
            assertEquals(expected, BackendErrorCode.fromRaw(raw))
        }
    }

    @Test
    fun `scheduling backend codes map to deterministic scheduling messages`() {
        mapOf(
            "SCHEDULING_NOT_AVAILABLE" to
                "La coordinacion de horarios ya no esta disponible. Actualiza el estado e intenta nuevamente.",
            "SCHEDULING_EXPIRED" to "La coordinacion de horarios vencio.",
            "SCHEDULING_NEGOTIATION_NOT_FOUND" to
                "No encontramos la coordinacion de horarios. Actualiza el estado.",
            "SCHEDULING_INVALID_PROPOSALS" to
                "Revisa los horarios elegidos. Deben ser futuros, unicos y estar alineados cada media hora.",
            "SCHEDULING_PROPOSALS_ALREADY_SUBMITTED" to
                "Ya enviaste tus horarios para esta ronda.",
            "SCHEDULING_ROUND_CHANGED" to
                "La ronda cambio. Actualizamos las opciones; revisalas antes de continuar.",
            "SCHEDULING_PROPOSAL_NOT_AVAILABLE" to
                "Ese horario ya no esta disponible. Actualiza la propuesta.",
            "SCHEDULING_CANNOT_ACCEPT_OWN_PROPOSAL" to
                "No podes aceptar un horario propuesto por vos.",
            "SCHEDULING_PARTNER_PROPOSALS_NOT_AVAILABLE" to
                "Esas opciones ya no estan disponibles. Actualizamos el estado de la coordinacion.",
        ).forEach { (code, expected) ->
            assertEquals(
                expected,
                backendError(code, message = "raw backend message").toUserMessage(ErrorContext.Scheduling),
            )
        }
    }

    @Test
    fun `scheduling domain conflict does not use raw backend message`() {
        val error = backendError("DOMAIN_CONFLICT", message = "raw backend message")

        assertEquals(
            "Esta accion no esta disponible con el estado actual.",
            error.toUserMessage(ErrorContext.Scheduling),
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
