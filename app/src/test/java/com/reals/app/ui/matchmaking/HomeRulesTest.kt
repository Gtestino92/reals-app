package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRulesTest {
    @Test
    fun `home status polling remains enabled for idle empty home`() {
        val model = emptyHomeScreenModel()

        assertTrue(model.shouldPollHome())
    }

    @Test
    fun `home polling interval is five seconds`() {
        assertEquals(5_000L, HOME_POLL_INTERVAL_MILLIS)
    }

    @Test
    fun `profile not active matchmaking block uses Spanish local copy`() {
        val error = ApiError.Backend(
            statusCode = 409,
            code = "PROFILE_NOT_ACTIVE",
            error = "PROFILE_NOT_ACTIVE",
            message = "Profile must be active before matchmaking.",
        )

        assertEquals(
            "Tu perfil está en borrador. Reactivalo para buscar nuevas personas.",
            error.matchmakingBlockedMessage(),
        )
    }

    @Test
    fun `unknown matchmaking block does not expose backend raw English message`() {
        val error = ApiError.Backend(
            statusCode = 409,
            code = "UNKNOWN_BLOCK",
            error = "UNKNOWN_BLOCK",
            message = "This backend message is not localized.",
        )

        assertEquals(null, error.matchmakingBlockedMessage())
    }

    @Test
    fun `location matchmaking copy is hidden for draft Home`() {
        val active = emptyHomeScreenModel()
        val draft = active.copy(
            draftProfileWarning = DraftProfileHomeWarning(
                title = "Tu perfil está en borrador",
                message = "Completá y reactivá tu perfil para buscar nuevas personas.",
                actionLabel = "Completar perfil",
            ),
        )

        assertTrue(active.shouldShowMatchmakingLocationCopy())
        assertFalse(draft.shouldShowMatchmakingLocationCopy())
    }

    @Test
    fun `second chat availability polling continues after availability until chat reference appears`() {
        val model = emptyHomeScreenModel().copy(
            nextSteps = listOf(
                HomeNextStepItem.SecondChatAvailable(
                    connectionId = "connection-second",
                    matchId = "match-second",
                    partnerDisplayName = "Partner",
                    chatId = null,
                    chatStatus = null,
                    availableAt = "2026-06-20T18:00:00-03:00",
                    expiresAt = "2026-06-20T20:00:00-03:00",
                    durationMinutes = 120,
                )
            )
        )

        assertTrue(model.shouldPollSecondChatAvailability(millis("2026-06-20T18:01:00-03:00")))
    }

    @Test
    fun `second chat availability polling stops after expiration`() {
        val model = emptyHomeScreenModel().copy(
            nextSteps = listOf(
                HomeNextStepItem.SecondChatAvailable(
                    connectionId = "connection-second",
                    matchId = "match-second",
                    partnerDisplayName = "Partner",
                    chatId = null,
                    chatStatus = null,
                    availableAt = "2026-06-20T18:00:00-03:00",
                    expiresAt = "2026-06-20T20:00:00-03:00",
                    durationMinutes = 120,
                )
            )
        )

        assertFalse(model.shouldPollSecondChatAvailability(millis("2026-06-20T20:00:00-03:00")))
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
