package com.reals.app.ui.matchmaking

import java.time.OffsetDateTime
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
        org.junit.Assert.assertEquals(5_000L, HOME_POLL_INTERVAL_MILLIS)
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
