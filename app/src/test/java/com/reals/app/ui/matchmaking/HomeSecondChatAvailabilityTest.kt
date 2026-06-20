package com.reals.app.ui.matchmaking

import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSecondChatAvailabilityTest {
    @Test
    fun `scheduled second chat opens when available time arrives`() {
        val item = scheduledSecondChat()

        assertTrue(item.canOpenSecondChatNow(millis("2026-06-20T18:00:00-03:00")))
    }

    @Test
    fun `scheduled second chat stays blocked before available time`() {
        val item = scheduledSecondChat()

        assertFalse(item.canOpenSecondChatNow(millis("2026-06-20T17:59:59-03:00")))
    }

    @Test
    fun `scheduled second chat closes after expiration`() {
        val item = scheduledSecondChat()

        assertFalse(item.canOpenSecondChatNow(millis("2026-06-20T20:00:00-03:00")))
    }

    @Test
    fun `available second chat opens without chat id while inside active window`() {
        val item = HomeNextStepItem.SecondChatAvailable(
            connectionId = "connection-second",
            matchId = "match-second",
            partnerDisplayName = "Partner",
            chatId = null,
            chatStatus = null,
            availableAt = "2026-06-20T18:00:00-03:00",
            expiresAt = "2026-06-20T20:00:00-03:00",
            durationMinutes = 120,
        )

        assertTrue(item.canOpenSecondChatNow(millis("2026-06-20T18:30:00-03:00")))
    }

    private fun scheduledSecondChat(): HomeNextStepItem.SecondChatScheduled =
        HomeNextStepItem.SecondChatScheduled(
            connectionId = "connection-second",
            matchId = "match-second",
            partnerDisplayName = "Partner",
            chatId = null,
            chatStatus = null,
            availableAt = "2026-06-20T18:00:00-03:00",
            expiresAt = "2026-06-20T20:00:00-03:00",
            durationMinutes = 120,
        )

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
