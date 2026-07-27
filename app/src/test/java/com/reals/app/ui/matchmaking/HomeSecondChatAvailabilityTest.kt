package com.reals.app.ui.matchmaking

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSecondChatAvailabilityTest {
    @Test
    fun `scheduled second chat opens when usable chat reference is exposed`() {
        val item = scheduledSecondChat(chatId = "chat-second", chatStatus = "AVAILABLE")

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
    fun `available second chat can open without materialized chat inside active window`() {
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

        val presentation = item.secondChatHomePresentation(millis("2026-06-20T18:30:00-03:00"))

        assertTrue(item.canOpenSecondChatNow(millis("2026-06-20T18:30:00-03:00")))
        assertEquals(SecondChatHomeState.Open, presentation?.state)
        assertEquals("Entrar al segundo chat", presentation?.primaryCtaLabel)
        assertTrue(presentation?.canOpenPartnerProfile == true)
    }

    @Test
    fun `scheduled second chat keeps profile visible before available without chat reference`() {
        val presentation = scheduledSecondChat()
            .secondChatHomePresentation(millis("2026-06-20T17:59:59-03:00"))

        assertEquals(SecondChatHomeState.Waiting, presentation?.state)
        assertTrue(presentation?.canOpenPartnerProfile == true)
    }

    @Test
    fun `scheduled second chat keeps profile visible exactly at available without chat reference`() {
        val presentation = scheduledSecondChat()
            .secondChatHomePresentation(millis("2026-06-20T18:00:00-03:00"))

        assertEquals(SecondChatHomeState.Open, presentation?.state)
        assertTrue(presentation?.canOpenPartnerProfile == true)
    }

    @Test
    fun `scheduled second chat keeps profile visible after available without chat reference`() {
        val presentation = scheduledSecondChat()
            .secondChatHomePresentation(millis("2026-06-20T18:30:00-03:00"))

        assertEquals(SecondChatHomeState.Open, presentation?.state)
        assertTrue(presentation?.canOpenPartnerProfile == true)
    }

    @Test
    fun `scheduled second chat keeps profile visible after active chat reference appears`() {
        val presentation = scheduledSecondChat(chatId = "chat-second", chatStatus = "ACTIVE")
            .secondChatHomePresentation(millis("2026-06-20T18:30:00-03:00"))

        assertEquals(SecondChatHomeState.Open, presentation?.state)
        assertTrue(presentation?.canOpenPartnerProfile == true)
    }

    @Test
    fun `scheduled second chat hides profile at expiration`() {
        val presentation = scheduledSecondChat()
            .secondChatHomePresentation(millis("2026-06-20T20:00:00-03:00"))

        assertEquals(SecondChatHomeState.Expired, presentation?.state)
        assertFalse(presentation?.canOpenPartnerProfile == true)
    }

    @Test
    fun `read only second chat closes after read only window`() {
        val item = HomeNextStepItem.SecondChatReadOnly(
            connectionId = "connection-second",
            matchId = "match-second",
            partnerDisplayName = "Partner",
            chatId = "chat-second",
            chatStatus = "EXPIRED",
            availableAt = "2026-06-20T18:00:00-03:00",
            expiresAt = "2026-06-20T20:00:00-03:00",
            readOnlyUntil = "2026-06-21T20:00:00-03:00",
            durationMinutes = 120,
        )

        assertTrue(item.canOpenSecondChatNow(millis("2026-06-21T19:59:59-03:00")))
        assertFalse(item.canOpenSecondChatNow(millis("2026-06-21T20:00:00-03:00")))
    }

    @Test
    fun `read only second chat omits primary cta after read only window`() {
        val item = HomeNextStepItem.SecondChatReadOnly(
            connectionId = "connection-second",
            matchId = "match-second",
            partnerDisplayName = "Partner",
            chatId = "chat-second",
            chatStatus = "EXPIRED",
            availableAt = "2026-06-20T18:00:00-03:00",
            expiresAt = "2026-06-20T20:00:00-03:00",
            readOnlyUntil = "2026-06-21T20:00:00-03:00",
            durationMinutes = 120,
        )

        val presentation = item.secondChatHomePresentation(millis("2026-06-21T20:00:00-03:00"))

        assertEquals(SecondChatHomeState.ReadOnlyEnded, presentation?.state)
        assertFalse(presentation?.canOpenChat == true)
        assertTrue(presentation?.canDismiss == true)
        assertNull(presentation?.primaryCtaLabel)
    }

    private fun scheduledSecondChat(
        chatId: String? = null,
        chatStatus: String? = null,
    ): HomeNextStepItem.SecondChatScheduled =
        HomeNextStepItem.SecondChatScheduled(
            connectionId = "connection-second",
            matchId = "match-second",
            partnerDisplayName = "Partner",
            chatId = chatId,
            chatStatus = chatStatus,
            availableAt = "2026-06-20T18:00:00-03:00",
            expiresAt = "2026-06-20T20:00:00-03:00",
            durationMinutes = 120,
        )

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
