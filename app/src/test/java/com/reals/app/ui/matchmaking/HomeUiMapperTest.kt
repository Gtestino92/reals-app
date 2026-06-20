package com.reals.app.ui.matchmaking

import com.reals.app.domain.model.ChatPartner
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.HomeChat
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomeNextStep
import com.reals.app.domain.model.HomePassiveNotice
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiMapperTest {
    private val mapper = HomeUiMapper()

    @Test
    fun `first chat pending action generates actionable first chat item`() {
        val model = mapper.toScreenModel(
            home = homeState(
                pendingActions = listOf(
                    HomePendingAction.FirstChat(
                        matchId = "match-1",
                        chatId = "chat-1",
                        partner = partner("Alex"),
                    ),
                ),
            ),
            localHidden = noHiddenInteractions(),
            localMatchmakingBlockedReason = null,
        )

        val item = model.pendingActions.single() as HomeActionItem.FirstChat
        assertEquals("match-1", item.matchId)
        assertEquals("chat-1", item.chatId)
        assertEquals("Alex", item.partnerDisplayName)
    }

    @Test
    fun `visual review pending action generates actionable visual item`() {
        val model = mapper.toScreenModel(
            home = homeState(
                pendingActions = listOf(
                    HomePendingAction.VisualReview(
                        matchId = "match-visual",
                        partner = partner("Riley"),
                    ),
                ),
            ),
            localHidden = noHiddenInteractions(),
            localMatchmakingBlockedReason = null,
        )

        val item = model.pendingActions.single() as HomeActionItem.VisualReview
        assertEquals("match-visual", item.matchId)
        assertEquals("Riley", item.partnerDisplayName)
    }

    @Test
    fun `scheduling pending passive notice does not generate actionable button`() {
        val model = mapper.toScreenModel(
            home = homeState(
                passiveNotices = listOf(HomePassiveNotice.SchedulingPreparing(count = 1)),
            ),
            localHidden = noHiddenInteractions(),
            localMatchmakingBlockedReason = null,
        )

        assertTrue(model.pendingActions.isEmpty())
        assertEquals(HomePassiveNoticeItem.SchedulingPreparing(1), model.passiveNotices.single())
    }

    @Test
    fun `scheduling phase generates actionable next step`() {
        val model = mapper.toScreenModel(
            home = homeState(
                nextSteps = listOf(
                    HomeNextStep.Scheduling(
                        connectionId = "connection-1",
                        matchId = "match-1",
                        partner = partner("Sam"),
                    ),
                ),
            ),
            localHidden = noHiddenInteractions(),
            localMatchmakingBlockedReason = null,
        )

        val item = model.nextSteps.single() as HomeNextStepItem.Scheduling
        assertEquals("connection-1", item.connectionId)
        assertEquals("match-1", item.matchId)
        assertEquals("Sam", item.partnerDisplayName)
    }

    @Test
    fun `second chat scheduled and available generate next steps`() {
        val model = mapper.toScreenModel(
            home = homeState(
                nextSteps = listOf(
                    HomeNextStep.SecondChatScheduled(
                        connectionId = "connection-scheduled",
                        matchId = "match-scheduled",
                        partner = null,
                        secondChat = homeChat("scheduled-chat", ChatStatus.Active, "Taylor"),
                    ),
                    HomeNextStep.SecondChatAvailable(
                        connectionId = "connection-available",
                        matchId = "match-available",
                        partner = null,
                        secondChat = homeChat("available-chat", ChatStatus.Available, "Jordan"),
                    ),
                    HomeNextStep.SecondChatReadOnly(
                        connectionId = "connection-read-only",
                        matchId = "match-read-only",
                        partner = null,
                        secondChat = homeChat("read-only-chat", ChatStatus.Expired, "Riley"),
                    ),
                ),
            ),
            localHidden = noHiddenInteractions(),
            localMatchmakingBlockedReason = null,
        )

        assertTrue(model.nextSteps[0] is HomeNextStepItem.SecondChatScheduled)
        assertEquals("Taylor", (model.nextSteps[0] as HomeNextStepItem.SecondChatScheduled).partnerDisplayName)
        assertTrue(model.nextSteps[1] is HomeNextStepItem.SecondChatAvailable)
        assertEquals("Jordan", (model.nextSteps[1] as HomeNextStepItem.SecondChatAvailable).partnerDisplayName)
        assertTrue(model.nextSteps[2] is HomeNextStepItem.SecondChatReadOnly)
        assertEquals("Riley", (model.nextSteps[2] as HomeNextStepItem.SecondChatReadOnly).partnerDisplayName)
    }

    @Test
    fun `closed and cancelled interactions do not appear as action`() {
        val model = mapper.toScreenModel(
            home = homeState(
                nextSteps = listOf(
                    HomeNextStep.SecondChatScheduled(
                        connectionId = "connection-closed",
                        matchId = "match-closed",
                        partner = null,
                        secondChat = homeChat("closed-chat", ChatStatus.Closed, "Casey"),
                    ),
                    HomeNextStep.SecondChatAvailable(
                        connectionId = "connection-cancelled",
                        matchId = "match-cancelled",
                        partner = null,
                        secondChat = homeChat("cancelled-chat", ChatStatus.Cancelled, "Morgan"),
                    ),
                ),
            ),
            localHidden = noHiddenInteractions(),
            localMatchmakingBlockedReason = null,
        )

        assertTrue(model.pendingActions.isEmpty())
        assertTrue(model.nextSteps.isEmpty())
    }

    private fun homeState(
        pendingActions: List<HomePendingAction> = emptyList(),
        nextSteps: List<HomeNextStep> = emptyList(),
        passiveNotices: List<HomePassiveNotice> = emptyList(),
    ): HomeState = HomeState(
        profileStatus = null,
        matchmaking = HomeMatchmaking(
            inQueue = false,
            canSearch = true,
            blockedReason = null,
        ),
        activeInteractionsSummary = HomeActiveInteractionsSummary(
            activeInitialCount = 0,
            activeConnectionCount = 0,
            pendingSchedulingConnectionCount = 0,
            actionableConnectionCount = 0,
        ),
        pendingActions = pendingActions,
        nextSteps = nextSteps,
        passiveNotices = passiveNotices,
    )

    private fun noHiddenInteractions(): LocalHiddenInteractions = LocalHiddenInteractions(
        hiddenFirstChatMatchIds = emptySet(),
        hiddenVisualMatchIds = emptySet(),
    )

    private fun homeChat(chatId: String, status: ChatStatus, partnerName: String): HomeChat = HomeChat(
        chatId = chatId,
        chatType = ChatType.SecondChat,
        chatStatus = status,
        availableAt = "2026-06-20T18:00:00-03:00",
        expiresAt = "2026-06-20T20:00:00-03:00",
        readOnlyUntil = "2026-06-21T20:00:00-03:00",
        durationMinutes = 120,
        partner = partner(partnerName),
    )

    private fun partner(displayName: String): ChatPartner = ChatPartner(
        userId = "user-$displayName",
        profileId = "profile-$displayName",
        displayName = displayName,
    )
}
