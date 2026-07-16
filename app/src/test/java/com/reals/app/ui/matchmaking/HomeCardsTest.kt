package com.reals.app.ui.matchmaking

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardsTest {
    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")
    private val locale = Locale.forLanguageTag("es-AR")
    private val nowMillis = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

    @Test
    fun `initial expanded section uses global priority when most urgent has one item`() {
        val visualReview = HomeActionItem.VisualReview(
            matchId = "match-1",
            partnerDisplayName = "Alex",
        )
        val scheduling = HomeNextStepItem.Scheduling(
            connectionId = "connection-1",
            matchId = "match-2",
            partnerDisplayName = "Sam",
        )

        assertEquals(
            HomeSectionKey.VisualReview,
            initiallyExpandedHomeSection(
                actions = listOf(visualReview),
                nextSteps = listOf(scheduling),
            ),
        )
    }

    @Test
    fun `initial expanded section stays collapsed when most urgent section has multiple items`() {
        val visualReviews = listOf(
            HomeActionItem.VisualReview(
                matchId = "match-1",
                partnerDisplayName = "Alex",
            ),
            HomeActionItem.VisualReview(
                matchId = "match-2",
                partnerDisplayName = "Sam",
            ),
        )
        val scheduling = HomeNextStepItem.Scheduling(
            connectionId = "connection-1",
            matchId = "match-3",
            partnerDisplayName = "Taylor",
        )

        assertEquals(
            null,
            initiallyExpandedHomeSection(
                actions = visualReviews,
                nextSteps = listOf(scheduling),
            ),
        )
    }

    @Test
    fun `initial expanded section falls back to coordination then second chats`() {
        val scheduling = HomeNextStepItem.Scheduling(
            connectionId = "connection-1",
            matchId = "match-1",
            partnerDisplayName = "Alex",
        )
        val secondChat = HomeNextStepItem.SecondChatScheduled(
            connectionId = "connection-2",
            matchId = "match-2",
            partnerDisplayName = "Sam",
            chatId = null,
            chatStatus = "SCHEDULED",
            availableAt = "2026-07-16T18:00:00-03:00",
            expiresAt = "2026-07-16T20:00:00-03:00",
            durationMinutes = 120,
        )

        assertEquals(
            HomeSectionKey.Scheduling,
            initiallyExpandedHomeSection(
                actions = emptyList(),
                nextSteps = listOf(scheduling, secondChat),
            ),
        )

        assertEquals(
            HomeSectionKey.SecondChat,
            initiallyExpandedHomeSection(
                actions = emptyList(),
                nextSteps = listOf(secondChat),
            ),
        )
    }

    @Test
    fun `home actions are grouped by section`() {
        val firstChat = HomeActionItem.FirstChat(
            matchId = "match-1",
            chatId = "chat-1",
            partnerDisplayName = "Alex",
        )
        val visualReview = HomeActionItem.VisualReview(
            matchId = "match-2",
            partnerDisplayName = "Sam",
        )

        val sections = homeActionSections(listOf(firstChat, visualReview))

        assertEquals(listOf(firstChat), sections.firstChats)
        assertEquals(listOf(visualReview), sections.visualReviews)
    }

    @Test
    fun `home next steps are grouped by section`() {
        val scheduling = HomeNextStepItem.Scheduling(
            connectionId = "connection-1",
            matchId = "match-1",
            partnerDisplayName = "Alex",
        )
        val scheduledSecondChat = HomeNextStepItem.SecondChatScheduled(
            connectionId = "connection-2",
            matchId = "match-2",
            partnerDisplayName = "Sam",
            chatId = null,
            chatStatus = "SCHEDULED",
            availableAt = "2026-07-16T18:00:00-03:00",
            expiresAt = "2026-07-16T20:00:00-03:00",
            durationMinutes = 120,
        )
        val unknown = HomeNextStepItem.Unknown(
            connectionId = "connection-3",
            matchId = "match-3",
            rawState = "UNKNOWN",
            partnerDisplayName = "Taylor",
        )

        val sections = homeNextStepSections(listOf(scheduling, scheduledSecondChat, unknown))

        assertEquals(listOf(scheduling), sections.schedulingItems)
        assertEquals(listOf(scheduledSecondChat), sections.secondChatItems)
        assertEquals(listOf(unknown), sections.unknownItems)
    }

    @Test
    fun `scheduled second chat body uses contextual tomorrow date`() {
        val item = HomeNextStepItem.SecondChatScheduled(
            connectionId = "connection-1",
            matchId = "match-1",
            partnerDisplayName = "Alex",
            chatId = null,
            chatStatus = "SCHEDULED",
            availableAt = "2026-07-16T18:00:00-03:00",
            expiresAt = "2026-07-16T20:00:00-03:00",
            durationMinutes = 120,
        )

        assertEquals(
            "Programado para Mañana, 18:00. Duración máxima: 2 horas.",
            item.homeNextStepBody(nowMillis, buenosAires, locale),
        )
    }

    @Test
    fun `available second chat body uses contextual today date`() {
        val item = HomeNextStepItem.SecondChatAvailable(
            connectionId = "connection-1",
            matchId = "match-1",
            partnerDisplayName = "Alex",
            chatId = "chat-1",
            chatStatus = "AVAILABLE",
            availableAt = "2026-07-15T20:30:00-03:00",
            expiresAt = "2026-07-15T22:30:00-03:00",
            durationMinutes = 120,
        )

        assertEquals(
            "Disponible desde Hoy, 20:30. Duración máxima: 2 horas.",
            item.homeNextStepBody(nowMillis, buenosAires, locale),
        )
    }

    @Test
    fun `read only second chat body uses contextual full date`() {
        val item = HomeNextStepItem.SecondChatReadOnly(
            connectionId = "connection-1",
            matchId = "match-1",
            partnerDisplayName = "Alex",
            chatId = "chat-1",
            chatStatus = "EXPIRED",
            availableAt = "2026-07-15T20:30:00-03:00",
            expiresAt = "2026-07-15T22:30:00-03:00",
            readOnlyUntil = "2026-07-17T22:30:00-03:00",
            durationMinutes = 120,
        )

        assertEquals(
            "Disponible solo para lectura hasta Viernes 17 de julio, 22:30.",
            item.homeNextStepBody(nowMillis, buenosAires, locale),
        )
    }
}
