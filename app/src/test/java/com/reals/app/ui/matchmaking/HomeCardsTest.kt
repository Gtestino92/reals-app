package com.reals.app.ui.matchmaking

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.root.AffinityHomeSummaryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCardsTest {
    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")
    private val locale = Locale.forLanguageTag("es-AR")
    private val nowMillis = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

    @Test
    fun `initial expanded section uses pending actions before next steps when single`() {
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
            HomeSectionKey.PendingActions,
            initiallyExpandedHomeSection(
                actions = listOf(visualReview),
                nextSteps = listOf(scheduling),
            ),
        )
    }

    @Test
    fun `initial expanded section stays collapsed when pending actions have multiple items`() {
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
            null,
            initiallyExpandedHomeSection(
                actions = emptyList(),
                nextSteps = listOf(scheduling, secondChat),
            ),
        )

        assertEquals(
            HomeSectionKey.NextSteps,
            initiallyExpandedHomeSection(
                actions = emptyList(),
                nextSteps = listOf(secondChat),
            ),
        )
    }

    @Test
    fun `collapsible state keys use order-preserving top level sections`() {
        assertEquals(
            "home-section:Próximos pasos",
            homeCollapsibleSectionStateKey("Próximos pasos"),
        )
        assertEquals(
            "home-section:Acciones pendientes",
            homeCollapsibleSectionStateKey("Acciones pendientes"),
        )
    }

    @Test
    fun `pending actions top level section preserves caller order`() {
        val firstChat = HomeActionItem.FirstChat(
            matchId = "match-1",
            chatId = "chat-1",
            partnerDisplayName = "Alex",
        )
        val visualReview = HomeActionItem.VisualReview(
            matchId = "match-2",
            partnerDisplayName = "Sam",
        )

        assertEquals(listOf("match-1", "match-2"), listOf(firstChat, visualReview).map { it.matchIdForTest() })
    }

    @Test
    fun `next steps top level section preserves caller order across categories`() {
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

        assertEquals(
            listOf("connection-2", "connection-1", "connection-3"),
            listOf(scheduledSecondChat, scheduling, unknown).map { it.connectionIdForTest() },
        )
    }

    @Test
    fun `collapsible section state key is stable across count changes`() {
        assertEquals(
            "home-section:Próximos pasos",
            homeCollapsibleSectionStateKey("Próximos pasos"),
        )
        assertEquals(
            homeCollapsibleSectionStateKey("Próximos pasos"),
            homeCollapsibleSectionStateKey("Próximos pasos"),
        )
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
    fun `scheduling next step body stays concise`() {
        val item = HomeNextStepItem.Scheduling(
            connectionId = "connection-1",
            matchId = "match-1",
            partnerDisplayName = "Alex",
        )

        assertEquals("Proponé opciones para el segundo chat.", item.homeNextStepBody(nowMillis))
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

    @Test
    fun `elapsed read only second chat body is terminal`() {
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
            "El período de solo lectura terminó.",
            item.homeNextStepBody(
                Instant.parse("2026-07-18T02:00:00Z").toEpochMilli(),
                buenosAires,
                locale,
            ),
        )
    }

    @Test
    fun `affinity card remains usable before catalog loads`() {
        val presentation = homeAffinityCardPresentation(
            AffinityHomeSummaryUiState(loading = true),
        )

        assertEquals("Descubrí tus afinidades", presentation.title)
        assertEquals(null, presentation.progressText)
        assertEquals("Empezar", presentation.actionLabel)
        assertTrue(presentation.loading)
    }

    @Test
    fun `affinity card presents empty partial and complete progress`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val empty = homeAffinityCardPresentation(
            AffinityHomeSummaryUiState(catalog = catalog),
        )
        val partial = homeAffinityCardPresentation(
            AffinityHomeSummaryUiState(
                catalog = catalog,
                answers = listOf(TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain()),
            ),
        )
        val complete = homeAffinityCardPresentation(
            AffinityHomeSummaryUiState(
                catalog = catalog,
                answers = listOf(
                    TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain(),
                    TestDtos.affinityAnswer("PLANS_WEEKEND_001", 1, "LOW").toDomain(),
                ),
            ),
        )

        assertEquals("Descubrí tus afinidades", empty.title)
        assertEquals("0 de 2 respondidas", empty.progressText)
        assertEquals("Empezar", empty.actionLabel)
        assertEquals("Seguí construyendo tus afinidades", partial.title)
        assertEquals("1 de 2 respondidas", partial.progressText)
        assertEquals("Continuar", partial.actionLabel)
        assertEquals("Tus afinidades", complete.title)
        assertEquals("2 de 2 respondidas", complete.progressText)
        assertEquals("Revisar respuestas", complete.actionLabel)
    }

    @Test
    fun `second chat dismiss visibility follows existing can dismiss policy`() {
        val active = HomeNextStepItem.SecondChatAvailable(
            connectionId = "connection-1",
            matchId = "match-1",
            partnerDisplayName = "Alex",
            chatId = "chat-1",
            chatStatus = "AVAILABLE",
            availableAt = "2026-07-15T11:00:00Z",
            expiresAt = "2026-07-15T13:00:00Z",
            durationMinutes = 120,
        )
        val expired = active.copy(
            chatStatus = "EXPIRED",
            expiresAt = "2026-07-15T11:30:00Z",
        )
        val readOnly = HomeNextStepItem.SecondChatReadOnly(
            connectionId = "connection-2",
            matchId = "match-2",
            partnerDisplayName = "Sam",
            chatId = "chat-2",
            chatStatus = "EXPIRED",
            availableAt = "2026-07-15T09:00:00Z",
            expiresAt = "2026-07-15T11:00:00Z",
            readOnlyUntil = "2026-07-16T11:00:00Z",
            durationMinutes = 120,
        )

        assertFalse(active.canShowSecondChatDismissAction(nowMillis))
        assertTrue(expired.canShowSecondChatDismissAction(nowMillis))
        assertTrue(readOnly.canShowSecondChatDismissAction(nowMillis))
    }

    private fun HomeActionItem.matchIdForTest(): String = when (this) {
        is HomeActionItem.FirstChat -> matchId
        is HomeActionItem.VisualReview -> matchId
    }

    private fun HomeNextStepItem.connectionIdForTest(): String = when (this) {
        is HomeNextStepItem.Scheduling -> connectionId
        is HomeNextStepItem.SecondChatScheduled -> connectionId
        is HomeNextStepItem.SecondChatAvailable -> connectionId
        is HomeNextStepItem.SecondChatExpired -> connectionId
        is HomeNextStepItem.SecondChatReadOnly -> connectionId
        is HomeNextStepItem.Unknown -> connectionId
    }
}
