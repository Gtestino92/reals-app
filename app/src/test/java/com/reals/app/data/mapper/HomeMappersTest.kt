package com.reals.app.data.mapper

import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomePassiveNoticeResponseDto
import com.reals.app.data.dto.HomePendingActionLiteResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.dto.HomePendingSecondChatLiteResponseDto
import com.reals.app.data.dto.HomePendingStateResponseDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.data.dto.HomeStatusResponseDto
import com.reals.app.data.dto.HomeNextStepLiteResponseDto
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.HomeNextStep
import com.reals.app.domain.model.HomePassiveNotice
import com.reals.app.domain.model.HomePendingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappersTest {
    @Test
    fun `Home status DTO maps version dirty and server time`() {
        val status = HomeStatusResponseDto(
            version = 12,
            dirty = true,
            serverTime = "2026-06-18T21:00:00Z",
        ).toDomain()

        assertEquals(12L, status.version)
        assertEquals(true, status.dirty)
        assertEquals("2026-06-18T21:00:00Z", status.serverTime)
    }

    @Test
    fun `Home DTO maps pending actions and next steps`() {
        val dto = HomeResponseDto(
            profileStatus = null,
            matchmaking = HomeMatchmakingResponseDto(
                inQueue = false,
                canSearch = true,
            ),
            activeInteractionsSummary = HomeActiveInteractionsSummaryResponseDto(),
            pendingActions = listOf(
                HomePendingActionResponseDto(
                    type = "FIRST_CHAT",
                    matchId = "match-first",
                    chatId = "chat-first",
                    partner = partnerDto("first-partner"),
                ),
                HomePendingActionResponseDto(
                    type = "VISUAL_REVIEW",
                    matchId = "match-visual",
                    partner = partnerDto("visual-partner"),
                ),
            ),
            nextSteps = listOf(
                HomeNextStepResponseDto(
                    type = "SCHEDULING",
                    connectionId = "connection-scheduling",
                    matchId = "match-scheduling",
                    partner = partnerDto("scheduling-partner"),
                ),
                HomeNextStepResponseDto(
                    type = "SECOND_CHAT_AVAILABLE",
                    connectionId = "connection-second",
                    matchId = "match-second",
                    secondChat = HomeChatResponseDto(
                        chatId = "chat-second",
                        chatType = "SECOND_CHAT",
                        chatStatus = "AVAILABLE",
                        availableAt = "2026-06-20T18:00:00-03:00",
                        expiresAt = "2026-06-20T20:00:00-03:00",
                        durationMinutes = 120,
                        partner = partnerDto("second-partner"),
                    ),
                ),
            ),
            passiveNotices = listOf(
                HomePassiveNoticeResponseDto(
                    type = "SCHEDULING_PREPARING",
                    count = 2,
                ),
            ),
        )

        val home = dto.toDomain()

        val firstChat = home.pendingActions[0] as HomePendingAction.FirstChat
        assertEquals("match-first", firstChat.matchId)
        assertEquals("chat-first", firstChat.chatId)

        val visualReview = home.pendingActions[1] as HomePendingAction.VisualReview
        assertEquals("match-visual", visualReview.matchId)

        val scheduling = home.nextSteps[0] as HomeNextStep.Scheduling
        assertEquals("connection-scheduling", scheduling.connectionId)
        assertEquals("match-scheduling", scheduling.matchId)

        val secondChat = home.nextSteps[1] as HomeNextStep.SecondChatAvailable
        assertEquals(ChatType.SecondChat, secondChat.secondChat?.chatType)
        assertEquals(ChatStatus.Available, secondChat.secondChat?.chatStatus)
        assertEquals("2026-06-20T18:00:00-03:00", secondChat.secondChat?.availableAt)
        assertEquals(120L, secondChat.secondChat?.durationMinutes)

        assertEquals(HomePassiveNotice.SchedulingPreparing(2), home.passiveNotices.single())
    }

    @Test
    fun `Home pending state DTO maps lightweight actions and next steps without partners`() {
        val pending = HomePendingStateResponseDto(
            version = 13,
            pendingActions = listOf(
                HomePendingActionLiteResponseDto(
                    type = "FIRST_CHAT",
                    matchId = "match-first",
                    chatId = "chat-first",
                ),
                HomePendingActionLiteResponseDto(
                    type = "VISUAL_REVIEW",
                    matchId = "match-visual",
                ),
                HomePendingActionLiteResponseDto(
                    type = "FIRST_CHAT",
                    matchId = "match-missing-chat",
                ),
                HomePendingActionLiteResponseDto(
                    type = "NEW_ACTION",
                    matchId = "match-unknown",
                ),
            ),
            nextSteps = listOf(
                HomeNextStepLiteResponseDto(
                    type = "SCHEDULING",
                    connectionId = "connection-scheduling",
                    matchId = "match-scheduling",
                ),
                HomeNextStepLiteResponseDto(
                    type = "SECOND_CHAT_SCHEDULED",
                    connectionId = "connection-scheduled",
                    matchId = "match-scheduled",
                    secondChat = HomePendingSecondChatLiteResponseDto(
                        availableAt = "2026-06-20T18:00:00-03:00",
                        expiresAt = "2026-06-20T20:00:00-03:00",
                        durationMinutes = 120,
                    ),
                ),
                HomeNextStepLiteResponseDto(
                    type = "SECOND_CHAT_AVAILABLE",
                    connectionId = "connection-available",
                    matchId = "match-available",
                    secondChat = HomePendingSecondChatLiteResponseDto(
                        chatId = "chat-available",
                        availableAt = "2026-06-20T18:00:00-03:00",
                        expiresAt = "2026-06-20T20:00:00-03:00",
                        durationMinutes = 120,
                    ),
                ),
                HomeNextStepLiteResponseDto(
                    type = "SECOND_CHAT_READ_ONLY",
                    connectionId = "connection-read-only",
                    matchId = "match-read-only",
                    secondChat = HomePendingSecondChatLiteResponseDto(
                        chatId = "chat-read-only",
                        availableAt = "2026-06-20T18:00:00-03:00",
                        expiresAt = "2026-06-20T20:00:00-03:00",
                        readOnlyUntil = "2026-06-21T20:00:00-03:00",
                        durationMinutes = 120,
                    ),
                ),
                HomeNextStepLiteResponseDto(
                    type = "SECOND_CHAT_AVAILABLE",
                    connectionId = "connection-incomplete",
                    matchId = "match-incomplete",
                    secondChat = HomePendingSecondChatLiteResponseDto(chatId = "chat-incomplete"),
                ),
                HomeNextStepLiteResponseDto(
                    type = "NEW_STEP",
                    connectionId = "connection-unknown",
                    matchId = "match-unknown",
                ),
            ),
            passiveNotices = listOf(HomePassiveNoticeResponseDto(type = "SCHEDULING_PREPARING", count = 1)),
            serverTime = "2026-06-18T21:00:00Z",
        ).toDomain()

        assertEquals(13L, pending.version)
        assertEquals("2026-06-18T21:00:00Z", pending.serverTime)

        val firstChat = pending.pendingActions[0] as HomePendingAction.FirstChat
        assertEquals("match-first", firstChat.matchId)
        assertEquals("chat-first", firstChat.chatId)
        assertEquals(null, firstChat.partner)

        val visualReview = pending.pendingActions[1] as HomePendingAction.VisualReview
        assertEquals("match-visual", visualReview.matchId)
        assertEquals(null, visualReview.partner)
        assertTrue(pending.pendingActions[2] is HomePendingAction.Unknown)
        assertTrue(pending.pendingActions[3] is HomePendingAction.Unknown)

        val scheduling = pending.nextSteps[0] as HomeNextStep.Scheduling
        assertEquals("connection-scheduling", scheduling.connectionId)
        assertEquals(null, scheduling.partner)

        val scheduled = pending.nextSteps[1] as HomeNextStep.SecondChatScheduled
        assertEquals(120L, scheduled.secondChat?.durationMinutes)
        assertEquals(null, scheduled.partner)

        val available = pending.nextSteps[2] as HomeNextStep.SecondChatAvailable
        assertEquals("chat-available", available.secondChat?.chatId)

        val readOnly = pending.nextSteps[3] as HomeNextStep.SecondChatReadOnly
        assertEquals("2026-06-21T20:00:00-03:00", readOnly.secondChat?.readOnlyUntil)

        val incomplete = pending.nextSteps[4] as HomeNextStep.SecondChatAvailable
        assertEquals(null, incomplete.secondChat)
        assertTrue(pending.nextSteps[5] is HomeNextStep.Unknown)
        assertEquals(HomePassiveNotice.SchedulingPreparing(1), pending.passiveNotices.single())
    }

    @Test
    fun `Home DTO maps second chat read only metadata`() {
        val home = minimalHome(
            nextSteps = listOf(
                HomeNextStepResponseDto(
                    type = "SECOND_CHAT_READ_ONLY",
                    connectionId = "connection-read-only",
                    matchId = "match-read-only",
                    secondChat = HomeChatResponseDto(
                        chatId = "chat-read-only",
                        chatType = "SECOND_CHAT",
                        chatStatus = "EXPIRED",
                        availableAt = "2026-06-20T18:00:00-03:00",
                        expiresAt = "2026-06-20T20:00:00-03:00",
                        readOnlyUntil = "2026-06-21T20:00:00-03:00",
                        durationMinutes = 120,
                    ),
                ),
            ),
        ).toDomain()

        val readOnly = home.nextSteps.single() as HomeNextStep.SecondChatReadOnly

        assertEquals("connection-read-only", readOnly.connectionId)
        assertEquals(ChatStatus.Expired, readOnly.secondChat?.chatStatus)
        assertEquals("2026-06-21T20:00:00-03:00", readOnly.secondChat?.readOnlyUntil)
    }

    @Test
    fun `Home DTO preserves unknown routing statuses`() {
        val home = minimalHome(
            pendingActions = listOf(HomePendingActionResponseDto(type = "NEW_ACTION", matchId = "match-1")),
            nextSteps = listOf(
                HomeNextStepResponseDto(
                    type = "NEW_STEP",
                    connectionId = "connection-1",
                    matchId = "match-1",
                ),
            ),
            passiveNotices = listOf(HomePassiveNoticeResponseDto(type = "NEW_NOTICE", count = 1)),
        ).toDomain()

        assertTrue(home.pendingActions.single() is HomePendingAction.Unknown)
        assertTrue(home.nextSteps.single() is HomeNextStep.Unknown)
        assertTrue(home.passiveNotices.single() is HomePassiveNotice.Unknown)
    }

    private fun minimalHome(
        pendingActions: List<HomePendingActionResponseDto> = emptyList(),
        nextSteps: List<HomeNextStepResponseDto> = emptyList(),
        passiveNotices: List<HomePassiveNoticeResponseDto> = emptyList(),
    ): HomeResponseDto = HomeResponseDto(
        profileStatus = null,
        matchmaking = HomeMatchmakingResponseDto(inQueue = false, canSearch = true),
        activeInteractionsSummary = HomeActiveInteractionsSummaryResponseDto(),
        pendingActions = pendingActions,
        nextSteps = nextSteps,
        passiveNotices = passiveNotices,
    )

    private fun partnerDto(name: String): ChatPartnerResponseDto = ChatPartnerResponseDto(
        userId = "user-$name",
        profileId = "profile-$name",
        displayName = name,
    )
}
