package com.reals.app.data.mapper

import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomePassiveNoticeResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.dto.HomeResponseDto
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

        assertEquals(HomePassiveNotice.SchedulingPreparing(2), home.passiveNotices.single())
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
