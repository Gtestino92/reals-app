package com.reals.app.ui.matchmaking

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePendingPresentationTest {
    private val nowMillis = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

    @Test
    fun `first chat stays off Pendientes and remains directly exposed`() {
        val firstChat = HomeActionItem.FirstChat("match-first", "chat-first", "Alex")
        val presentation = presentation(actions = listOf(firstChat))

        assertEquals(listOf(firstChat), presentation.firstChats)
        assertFalse(presentation.hasHubItems)
        assertEquals(null, presentation.summaryText)
    }

    @Test
    fun `hub items are classified by user meaning`() {
        val visualReview = visualReview()
        val scheduling = scheduling("connection-scheduling")
        val waiting = scheduled("connection-waiting", availableAt = "2026-07-15T12:30:00Z")
        val open = available("connection-open")
        val readOnly = readOnly("connection-read-only", readOnlyUntil = "2026-07-15T13:00:00Z")
        val readOnlyEnded = readOnly("connection-read-only-ended", readOnlyUntil = "2026-07-15T11:00:00Z")
        val expired = scheduled(
            "connection-expired",
            availableAt = "2026-07-15T09:00:00Z",
            expiresAt = "2026-07-15T10:00:00Z",
        )
        val unknown = HomeNextStepItem.Unknown("connection-unknown", "match-unknown", "NEW", "Taylor")

        val presentation = presentation(
            actions = listOf(
                HomeActionItem.FirstChat("match-first", "chat-first", "Alex"),
                visualReview,
            ),
            nextSteps = listOf(scheduling, waiting, open, readOnly, readOnlyEnded, expired, unknown),
        )

        assertSection(
            presentation,
            HomePendingSectionType.ActionRequired,
            listOf("visual:match-visual", "next:connection-scheduling"),
        )
        assertSection(presentation, HomePendingSectionType.InProgress, listOf("next:connection-open"))
        assertSection(presentation, HomePendingSectionType.Upcoming, listOf("next:connection-waiting"))
        assertSection(
            presentation,
            HomePendingSectionType.Recent,
            listOf("next:connection-expired", "next:connection-read-only", "next:connection-read-only-ended"),
        )
        assertSection(presentation, HomePendingSectionType.Other, listOf("next:connection-unknown"))
    }

    @Test
    fun `secondary kind mapping identifies known pending interaction types`() {
        assertEquals(HomePendingItemKind.VisualReview, visualReview().asHubItem().pendingItemKind())
        assertEquals(HomePendingItemKind.Scheduling, scheduling("connection-scheduling").pendingItemKind())
        assertEquals(HomePendingItemKind.SecondChat, scheduled("connection-scheduled").pendingItemKind())
        assertEquals(HomePendingItemKind.SecondChat, available("connection-available").pendingItemKind())
        assertEquals(
            HomePendingItemKind.SecondChat,
            readOnly("connection-read-only", readOnlyUntil = "2026-07-15T13:00:00Z").pendingItemKind(),
        )
        assertEquals(
            null,
            HomeNextStepItem.Unknown("connection-unknown", "match-unknown", "NEW", "Taylor").pendingItemKind(),
        )
    }

    @Test
    fun `action required keeps one primary section with visual review and scheduling subgroups`() {
        val presentation = presentation(
            actions = listOf(visualReview(matchId = "match-visual")),
            nextSteps = listOf(scheduling("connection-scheduling")),
        )

        val section = presentation.hubSections.single()

        assertEquals(HomePendingSectionType.ActionRequired, section.type)
        assertEquals(
            listOf(HomePendingItemKind.VisualReview, HomePendingItemKind.Scheduling),
            section.secondaryGroups.map { it.kind },
        )
        assertEquals(listOf("Perfiles por descubrir", "Coordinación"), section.secondaryGroups.map { it.title })
        assertEquals(listOf("visual:match-visual"), section.secondaryGroups[0].items.map { it.idForTest() })
        assertEquals(listOf("next:connection-scheduling"), section.secondaryGroups[1].items.map { it.idForTest() })
    }

    @Test
    fun `unknown pending state stays under other without semantic subgroup title`() {
        val presentation = presentation(
            nextSteps = listOf(HomeNextStepItem.Unknown("connection-unknown", "match-unknown", "NEW", "Taylor")),
        )

        val section = presentation.hubSections.single()

        assertEquals(HomePendingSectionType.Other, section.type)
        assertEquals(listOf(null), section.secondaryGroups.map { it.kind })
        assertEquals(listOf(null), section.secondaryGroups.map { it.title })
        assertEquals(listOf("next:connection-unknown"), section.secondaryGroups.single().items.map { it.idForTest() })
    }

    @Test
    fun `primary classification remains unchanged after secondary grouping`() {
        val presentation = presentation(
            actions = listOf(HomeActionItem.FirstChat("match-first", "chat-first", "Alex"), visualReview()),
            nextSteps = listOf(
                scheduling("connection-scheduling"),
                scheduled("connection-waiting", availableAt = "2026-07-15T12:30:00Z"),
                available("connection-open"),
                readOnly("connection-read-only", readOnlyUntil = "2026-07-15T13:00:00Z"),
                HomeNextStepItem.Unknown("connection-unknown", "match-unknown", "NEW", "Taylor"),
            ),
        )

        assertEquals(
            listOf(
                HomePendingSectionType.InProgress,
                HomePendingSectionType.ActionRequired,
                HomePendingSectionType.Upcoming,
                HomePendingSectionType.Recent,
                HomePendingSectionType.Other,
            ),
            presentation.hubSections.map { it.type },
        )
        assertEquals(1, presentation.firstChats.size)
    }

    @Test
    fun `secondary grouping preserves deterministic order inside each type`() {
        val laterVisualReview = visualReview(
            matchId = "match-later",
            visualExpiresAt = "2026-07-15T14:00:00Z",
        )
        val earlierVisualReview = visualReview(
            matchId = "match-earlier",
            visualExpiresAt = "2026-07-15T13:00:00Z",
        )

        val presentation = presentation(
            actions = listOf(laterVisualReview, earlierVisualReview),
            nextSteps = listOf(scheduling("connection-a"), scheduling("connection-b")),
        )

        val groups = presentation.hubSections.single { it.type == HomePendingSectionType.ActionRequired }
            .secondaryGroups

        assertEquals(listOf("visual:match-earlier", "visual:match-later"), groups[0].items.map { it.idForTest() })
        assertEquals(listOf("next:connection-a", "next:connection-b"), groups[1].items.map { it.idForTest() })
    }

    @Test
    fun `pending row titles identify interaction type`() {
        assertEquals(
            "Descubrí el perfil de Ana",
            visualReview(partnerDisplayName = "Ana").pendingVisualReviewTitle(),
        )
        assertEquals("Descubrí el perfil", visualReview(partnerDisplayName = null).pendingVisualReviewTitle())
        assertEquals(
            "Coordinación con Ana",
            scheduling("connection-scheduling", partnerDisplayName = "Ana").pendingNextStepTitle(),
        )
        assertEquals(
            "Segundo chat con Ana",
            scheduled("connection-scheduled", partnerDisplayName = "Ana").pendingNextStepTitle(),
        )
    }

    @Test
    fun `pending scheduling body is specific to second chat coordination`() {
        assertEquals(
            "Elegí horarios para el segundo chat.",
            scheduling("connection-scheduling").pendingNextStepBody(nowMillis),
        )
    }

    @Test
    fun `summary copy uses singular plural and priority order`() {
        assertEquals(
            "1 requiere tu acción",
            presentation(actions = listOf(visualReview())).summaryText,
        )
        assertEquals(
            "2 requieren tu acción · 1 próximo",
            presentation(
                actions = listOf(visualReview()),
                nextSteps = listOf(scheduling("connection-scheduling"), scheduled("connection-waiting")),
            ).summaryText,
        )
        assertEquals(
            "1 en curso · 1 requiere tu acción",
            presentation(
                actions = listOf(visualReview()),
                nextSteps = listOf(available("connection-open")),
            ).summaryText,
        )
        assertEquals(
            "1 reciente",
            presentation(nextSteps = listOf(readOnly("connection-read-only", readOnlyUntil = "2026-07-15T13:00:00Z")))
                .summaryText,
        )
        assertEquals(
            "1 pendiente",
            presentation(nextSteps = listOf(HomeNextStepItem.Unknown("connection-unknown", null, "NEW", null)))
                .summaryText,
        )
        assertEquals(null, presentation().summaryText)
    }

    @Test
    fun `priority promotes only urgent post first chat items`() {
        val open = available("connection-open")
        val startingSoon = scheduled(
            "connection-soon",
            availableAt = Instant.ofEpochMilli(nowMillis + SECOND_CHAT_NEAR_WINDOW_MILLIS).toString(),
            expiresAt = Instant.ofEpochMilli(nowMillis + SECOND_CHAT_NEAR_WINDOW_MILLIS + 120 * 60 * 1000L).toString(),
        )
        val outsideNearWindow = scheduled(
            "connection-later",
            availableAt = Instant.ofEpochMilli(nowMillis + SECOND_CHAT_NEAR_WINDOW_MILLIS + 1).toString(),
            expiresAt = Instant.ofEpochMilli(nowMillis + SECOND_CHAT_NEAR_WINDOW_MILLIS + 120 * 60 * 1000L).toString(),
        )
        val criticalVisualReview = visualReview(
            matchId = "match-critical",
            visualStartedAt = "2026-07-15T10:00:00Z",
            visualExpiresAt = "2026-07-15T12:05:00Z",
        )
        val warningVisualReview = visualReview(
            matchId = "match-warning",
            visualStartedAt = "2026-07-15T10:00:00Z",
            visualExpiresAt = "2026-07-15T12:40:00Z",
        )
        val expiredVisualReview = visualReview(
            matchId = "match-expired",
            visualStartedAt = "2026-07-15T10:00:00Z",
            visualExpiresAt = "2026-07-15T11:00:00Z",
        )
        val readOnly = readOnly("connection-read-only", readOnlyUntil = "2026-07-15T13:00:00Z")
        val expiredSecondChat = scheduled(
            "connection-expired",
            availableAt = "2026-07-15T09:00:00Z",
            expiresAt = "2026-07-15T10:00:00Z",
        )

        val presentation = presentation(
            actions = listOf(warningVisualReview, expiredVisualReview, criticalVisualReview),
            nextSteps = listOf(open, startingSoon, outsideNearWindow, readOnly, expiredSecondChat),
        )

        assertEquals(
            listOf("visual:match-critical", "next:connection-soon"),
            presentation.priorityItems.map { it.idForTest() },
        )
        assertEquals(1, presentation.priorityOverflowCount)
    }

    @Test
    fun `priority ordering uses event timestamps and exposes at most two`() {
        val laterCritical = visualReview(
            matchId = "match-later-critical",
            visualStartedAt = "2026-07-15T10:00:00Z",
            visualExpiresAt = "2026-07-15T12:08:00Z",
        )
        val earliestOpen = available(
            "connection-open-earliest",
            availableAt = "2026-07-15T10:00:00Z",
            expiresAt = "2026-07-15T12:03:00Z",
        )
        val soon = scheduled(
            "connection-soon",
            availableAt = "2026-07-15T12:04:00Z",
            expiresAt = "2026-07-15T14:04:00Z",
        )
        val invalidOpen = available("connection-open-invalid", availableAt = "2026-07-15T11:00:00Z", expiresAt = "bad")

        val presentation = presentation(
            actions = listOf(laterCritical),
            nextSteps = listOf(invalidOpen, soon, earliestOpen),
        )

        assertEquals(
            listOf("next:connection-open-earliest", "next:connection-soon"),
            presentation.priorityItems.map { it.idForTest() },
        )
        assertEquals(2, presentation.priorityOverflowCount)
    }

    @Test
    fun `upcoming second chats sort by valid availableAt and malformed values remain stable`() {
        val invalidA = scheduled("connection-invalid-a", availableAt = "bad", expiresAt = null)
        val later = scheduled("connection-later", availableAt = "2026-07-15T13:00:00Z")
        val earlier = scheduled("connection-earlier", availableAt = "2026-07-15T12:30:00Z")
        val invalidB = scheduled("connection-invalid-b", availableAt = null, expiresAt = null)

        val presentation = presentation(nextSteps = listOf(invalidA, later, earlier, invalidB))

        assertSection(
            presentation,
            HomePendingSectionType.Upcoming,
            listOf(
                "next:connection-earlier",
                "next:connection-later",
                "next:connection-invalid-a",
                "next:connection-invalid-b",
            ),
        )
    }

    private fun presentation(
        actions: List<HomeActionItem> = emptyList(),
        nextSteps: List<HomeNextStepItem> = emptyList(),
    ): HomePendingPresentation = homePendingPresentation(
        model = HomeScreenModel(
            pendingActions = actions,
            nextSteps = nextSteps,
            activeInteractionsSummary = null,
            passiveNotices = emptyList(),
            matchmaking = HomeMatchmakingUiState(inQueue = false, canSearch = true, blockedReason = null),
        ),
        nowMillis = nowMillis,
    )

    private fun visualReview(
        matchId: String = "match-visual",
        partnerDisplayName: String? = "Sam",
        visualStartedAt: String? = "2026-07-15T10:00:00Z",
        visualExpiresAt: String? = "2026-07-15T14:00:00Z",
    ): HomeActionItem.VisualReview = HomeActionItem.VisualReview(
        matchId = matchId,
        partnerDisplayName = partnerDisplayName,
        visualStartedAt = visualStartedAt,
        visualExpiresAt = visualExpiresAt,
    )

    private fun scheduling(
        connectionId: String,
        partnerDisplayName: String? = "Alex",
    ): HomeNextStepItem.Scheduling =
        HomeNextStepItem.Scheduling(
            connectionId = connectionId,
            matchId = "match-$connectionId",
            partnerDisplayName = partnerDisplayName,
        )

    private fun scheduled(
        connectionId: String = "connection-scheduled",
        partnerDisplayName: String? = "Alex",
        availableAt: String? = "2026-07-15T13:00:00Z",
        expiresAt: String? = "2026-07-15T15:00:00Z",
    ): HomeNextStepItem.SecondChatScheduled =
        HomeNextStepItem.SecondChatScheduled(
            connectionId = connectionId,
            matchId = "match-$connectionId",
            partnerDisplayName = partnerDisplayName,
            chatId = null,
            chatStatus = "SCHEDULED",
            availableAt = availableAt,
            expiresAt = expiresAt,
            durationMinutes = 120,
        )

    private fun available(
        connectionId: String,
        availableAt: String? = "2026-07-15T11:00:00Z",
        expiresAt: String? = "2026-07-15T13:00:00Z",
    ): HomeNextStepItem.SecondChatAvailable =
        HomeNextStepItem.SecondChatAvailable(
            connectionId = connectionId,
            matchId = "match-$connectionId",
            partnerDisplayName = "Alex",
            chatId = "chat-$connectionId",
            chatStatus = "ACTIVE",
            availableAt = availableAt,
            expiresAt = expiresAt,
            durationMinutes = 120,
        )

    private fun readOnly(
        connectionId: String,
        readOnlyUntil: String?,
    ): HomeNextStepItem.SecondChatReadOnly =
        HomeNextStepItem.SecondChatReadOnly(
            connectionId = connectionId,
            matchId = "match-$connectionId",
            partnerDisplayName = "Alex",
            chatId = "chat-$connectionId",
            chatStatus = "EXPIRED",
            availableAt = "2026-07-15T09:00:00Z",
            expiresAt = "2026-07-15T11:00:00Z",
            readOnlyUntil = readOnlyUntil,
            durationMinutes = 120,
        )

    private fun assertSection(
        presentation: HomePendingPresentation,
        type: HomePendingSectionType,
        expectedIds: List<String>,
    ) {
        assertEquals(expectedIds, presentation.hubSections.single { it.type == type }.items.map { it.idForTest() })
    }

    private fun HomePendingHubItem.idForTest(): String =
        when (this) {
            is HomePendingHubItem.VisualReview -> "visual:${action.matchId}"
            is HomePendingHubItem.NextStep -> "next:${item.connectionIdForTest()}"
        }

    private fun HomeActionItem.VisualReview.asHubItem(): HomePendingHubItem.VisualReview =
        HomePendingHubItem.VisualReview(
            action = this,
            sourceIndex = 0,
            expiresAtMillis = visualExpiresAt.toInstantOrNull()?.toEpochMilli(),
        )

    private fun HomePriorityItem.idForTest(): String =
        when (this) {
            is HomePriorityItem.VisualReview -> "visual:${action.matchId}"
            is HomePriorityItem.SecondChatOpen -> "next:${item.connectionIdForTest()}"
            is HomePriorityItem.SecondChatStartingSoon -> "next:${item.connectionIdForTest()}"
        }

    private fun HomeNextStepItem.connectionIdForTest(): String =
        when (this) {
            is HomeNextStepItem.Scheduling -> connectionId
            is HomeNextStepItem.SecondChatScheduled -> connectionId
            is HomeNextStepItem.SecondChatAvailable -> connectionId
            is HomeNextStepItem.SecondChatReadOnly -> connectionId
            is HomeNextStepItem.Unknown -> connectionId
        }
}
