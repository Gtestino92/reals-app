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
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.testutil.testJson
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappersTest {
    @Test
    fun `Home status DTO maps version dirty and server time`() {
        val status = HomeStatusResponseDto(
            version = 12,
            dirty = true,
            nextRefreshAt = "2026-06-18T21:05:00Z",
            serverTime = "2026-06-18T21:00:00Z",
        ).toDomain()

        assertEquals(12L, status.version)
        assertEquals(true, status.dirty)
        assertEquals("2026-06-18T21:05:00Z", status.nextRefreshAt)
        assertEquals("2026-06-18T21:00:00Z", status.serverTime)
    }

    @Test
    fun `Home status DTO handles null and missing next refresh marker`() {
        val nullStatus = HomeStatusResponseDto(
            version = 12,
            dirty = false,
            nextRefreshAt = null,
            serverTime = "2026-06-18T21:00:00Z",
        ).toDomain()
        val missingStatus = testJson.decodeFromString<HomeStatusResponseDto>(
            """
            {
              "version": 13,
              "dirty": false,
              "serverTime": "2026-06-18T21:00:00Z"
            }
            """.trimIndent()
        ).toDomain()

        assertEquals(null, nullStatus.nextRefreshAt)
        assertEquals(null, missingStatus.nextRefreshAt)
        assertEquals("2026-06-18T21:00:00Z", missingStatus.serverTime)
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
                    visualStartedAt = "2026-06-19T18:00:00Z",
                    visualExpiresAt = "2026-06-20T18:00:00Z",
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
                ),
            ),
        )

        val home = dto.toDomain()

        val firstChat = home.pendingActions[0] as HomePendingAction.FirstChat
        assertEquals("match-first", firstChat.matchId)
        assertEquals("chat-first", firstChat.chatId)

        val visualReview = home.pendingActions[1] as HomePendingAction.VisualReview
        assertEquals("match-visual", visualReview.matchId)
        assertEquals("2026-06-19T18:00:00Z", visualReview.visualStartedAt)
        assertEquals("2026-06-20T18:00:00Z", visualReview.visualExpiresAt)

        val scheduling = home.nextSteps[0] as HomeNextStep.Scheduling
        assertEquals("connection-scheduling", scheduling.connectionId)
        assertEquals("match-scheduling", scheduling.matchId)

        val secondChat = home.nextSteps[1] as HomeNextStep.SecondChatAvailable
        assertEquals(ChatType.SecondChat, secondChat.secondChat?.chatType)
        assertEquals(ChatStatus.Available, secondChat.secondChat?.chatStatus)
        assertEquals("2026-06-20T18:00:00-03:00", secondChat.secondChat?.availableAt)
        assertEquals(120L, secondChat.secondChat?.durationMinutes)

        assertEquals(HomePassiveNotice.SchedulingPreparing, home.passiveNotices.single())
    }

    @Test
    fun `Home DTO preserves backend pending action next step and notice order`() {
        val home = minimalHome(
            pendingActions = listOf(
                HomePendingActionResponseDto(type = "VISUAL_REVIEW", matchId = "match-visual-a"),
                HomePendingActionResponseDto(type = "FIRST_CHAT", matchId = "match-first", chatId = "chat-first"),
                HomePendingActionResponseDto(type = "VISUAL_REVIEW", matchId = "match-visual-b"),
                HomePendingActionResponseDto(
                    type = "VISUAL_REVIEW",
                    matchId = "match-visual-equal-a",
                    visualExpiresAt = "2026-07-31T19:00:00Z",
                ),
                HomePendingActionResponseDto(
                    type = "VISUAL_REVIEW",
                    matchId = "match-visual-equal-b",
                    visualExpiresAt = "2026-07-31T19:00:00Z",
                ),
            ),
            nextSteps = listOf(
                secondChatStep("SECOND_CHAT_AVAILABLE", "connection-active-new", "2026-07-31T21:00:00Z"),
                secondChatStep("SECOND_CHAT_AVAILABLE", "connection-active-old", "2026-07-31T20:00:00Z"),
                secondChatStep("SECOND_CHAT_SCHEDULED", "connection-future-near", "2026-08-01T20:00:00Z"),
                secondChatStep("SECOND_CHAT_SCHEDULED", "connection-future-equal-a", "2026-08-01T20:00:00Z"),
                secondChatStep("SECOND_CHAT_SCHEDULED", "connection-future-equal-b", "2026-08-01T20:00:00Z"),
                secondChatStep("SECOND_CHAT_SCHEDULED", "connection-future-far", "2026-08-02T20:00:00Z"),
                HomeNextStepResponseDto(
                    type = "SCHEDULING",
                    connectionId = "connection-scheduling",
                    matchId = "match-scheduling",
                ),
                secondChatStep("SECOND_CHAT_READ_ONLY", "connection-read-only", "2026-07-30T20:00:00Z"),
            ),
            passiveNotices = listOf(
                HomePassiveNoticeResponseDto(type = "NEW_NOTICE_A"),
                HomePassiveNoticeResponseDto(type = "SCHEDULING_PREPARING"),
                HomePassiveNoticeResponseDto(type = "NEW_NOTICE_B"),
            ),
        ).toDomain()

        assertEquals(
            listOf(
                "match-visual-a",
                "match-first",
                "match-visual-b",
                "match-visual-equal-a",
                "match-visual-equal-b",
            ),
            home.pendingActions.map {
                when (it) {
                    is HomePendingAction.FirstChat -> it.matchId
                    is HomePendingAction.VisualReview -> it.matchId
                    is HomePendingAction.Unknown -> it.rawType
                }
            },
        )
        assertEquals(
            listOf(
                "connection-active-new",
                "connection-active-old",
                "connection-future-near",
                "connection-future-equal-a",
                "connection-future-equal-b",
                "connection-future-far",
                "connection-scheduling",
                "connection-read-only",
            ),
            home.nextSteps.map {
                when (it) {
                    is HomeNextStep.Scheduling -> it.connectionId
                    is HomeNextStep.SecondChatScheduled -> it.connectionId
                    is HomeNextStep.SecondChatAvailable -> it.connectionId
                    is HomeNextStep.SecondChatExpired -> it.connectionId
                    is HomeNextStep.SecondChatReadOnly -> it.connectionId
                    is HomeNextStep.Unknown -> it.connectionId
                }
            },
        )
        assertTrue(home.passiveNotices[0] is HomePassiveNotice.Unknown)
        assertEquals(HomePassiveNotice.SchedulingPreparing, home.passiveNotices[1])
        assertTrue(home.passiveNotices[2] is HomePassiveNotice.Unknown)
    }

    @Test
    fun `Home pending state preserves backend order including malformed second chat metadata last`() {
        val pending = HomePendingStateResponseDto(
            version = 15,
            pendingActions = listOf(
                HomePendingActionLiteResponseDto(
                    type = "VISUAL_REVIEW",
                    matchId = "match-visual",
                    visualExpiresAt = "2026-07-31T19:00:00Z",
                ),
                HomePendingActionLiteResponseDto(
                    type = "FIRST_CHAT",
                    matchId = "match-first",
                    chatId = "chat-first",
                ),
            ),
            nextSteps = listOf(
                secondChatLiteStep("SECOND_CHAT_AVAILABLE", "connection-active-new", "2026-07-31T21:00:00Z"),
                secondChatLiteStep("SECOND_CHAT_AVAILABLE", "connection-active-old", "2026-07-31T20:00:00Z"),
                secondChatLiteStep("SECOND_CHAT_SCHEDULED", "connection-future-near", "2026-08-01T20:00:00Z"),
                secondChatLiteStep("SECOND_CHAT_READ_ONLY", "connection-read-only", "2026-07-30T20:00:00Z"),
                HomeNextStepLiteResponseDto(
                    type = "SECOND_CHAT_AVAILABLE",
                    connectionId = "connection-missing-date",
                    matchId = "match-connection-missing-date",
                    secondChat = HomePendingSecondChatLiteResponseDto(chatId = "chat-missing-date"),
                ),
            ),
            serverTime = "2026-07-31T21:00:00Z",
        ).toDomain()

        assertEquals(
            listOf("match-visual", "match-first"),
            pending.pendingActions.map {
                when (it) {
                    is HomePendingAction.FirstChat -> it.matchId
                    is HomePendingAction.VisualReview -> it.matchId
                    is HomePendingAction.Unknown -> it.rawType
                }
            },
        )
        assertEquals(
            listOf(
                "connection-active-new",
                "connection-active-old",
                "connection-future-near",
                "connection-read-only",
                "connection-missing-date",
            ),
            pending.nextSteps.map {
                when (it) {
                    is HomeNextStep.Scheduling -> it.connectionId
                    is HomeNextStep.SecondChatScheduled -> it.connectionId
                    is HomeNextStep.SecondChatAvailable -> it.connectionId
                    is HomeNextStep.SecondChatExpired -> it.connectionId
                    is HomeNextStep.SecondChatReadOnly -> it.connectionId
                    is HomeNextStep.Unknown -> it.connectionId
                }
            },
        )
        val missingDate = pending.nextSteps.last() as HomeNextStep.SecondChatAvailable
        assertEquals(null, missingDate.secondChat)
    }

    @Test
    fun `Home summary DTO maps pending scheduling boolean`() {
        val summary = HomeActiveInteractionsSummaryResponseDto(
            activeInitialCount = 0,
            activeConnectionCount = 0,
            hasPendingSchedulingConnection = true,
            actionableConnectionCount = 0,
        ).toDomain()

        assertEquals(true, summary.hasPendingSchedulingConnection)
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
                    visualStartedAt = "2026-06-19T18:00:00Z",
                    visualExpiresAt = "2026-06-20T18:00:00Z",
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
            passiveNotices = listOf(HomePassiveNoticeResponseDto(type = "SCHEDULING_PREPARING")),
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
        assertEquals("2026-06-19T18:00:00Z", visualReview.visualStartedAt)
        assertEquals("2026-06-20T18:00:00Z", visualReview.visualExpiresAt)
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
        assertEquals(HomePassiveNotice.SchedulingPreparing, pending.passiveNotices.single())
    }

    @Test
    fun `Home pending state DTO maps count-free scheduling preparing passive notice`() {
        val pending = HomePendingStateResponseDto(
            version = 14,
            passiveNotices = listOf(HomePassiveNoticeResponseDto(type = "SCHEDULING_PREPARING")),
            serverTime = "2026-06-18T21:00:00Z",
        ).toDomain()

        assertEquals(HomePassiveNotice.SchedulingPreparing, pending.passiveNotices.single())
    }

    @Test
    fun `Home contract deserializes count-free passive notices and summary boolean`() {
        val dto = testJson.decodeFromString<HomeContractProbeDto>(
            """
            {
              "activeInteractionsSummary": {
                "activeInitialCount": 0,
                "activeConnectionCount": 0,
                "hasPendingSchedulingConnection": true,
                "actionableConnectionCount": 0
              },
              "passiveNotices": [
                {
                  "type": "SCHEDULING_PREPARING"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(true, dto.activeInteractionsSummary.hasPendingSchedulingConnection)
        assertEquals(HomePassiveNotice.SchedulingPreparing, dto.passiveNotices.single().toDomain())
    }

    @Test
    fun `Home pending action DTO deserializes visual review timestamps and remains additive`() {
        val visual = testJson.decodeFromString<HomePendingActionResponseDto>(
            """
            {
              "type": "VISUAL_REVIEW",
              "matchId": "match-visual",
              "visualStartedAt": "2026-06-19T18:00:00Z",
              "visualExpiresAt": "2026-06-20T18:00:00Z"
            }
            """.trimIndent()
        )
        val legacy = testJson.decodeFromString<HomePendingActionResponseDto>(
            """
            {
              "type": "VISUAL_REVIEW",
              "matchId": "match-legacy"
            }
            """.trimIndent()
        )
        val firstChat = testJson.decodeFromString<HomePendingActionResponseDto>(
            """
            {
              "type": "FIRST_CHAT",
              "matchId": "match-first",
              "chatId": "chat-first",
              "visualStartedAt": "bad",
              "visualExpiresAt": "bad"
            }
            """.trimIndent()
        ).toDomain()

        assertEquals("2026-06-19T18:00:00Z", visual.visualStartedAt)
        assertEquals("2026-06-20T18:00:00Z", visual.visualExpiresAt)
        assertEquals(null, legacy.visualStartedAt)
        assertEquals(null, legacy.visualExpiresAt)
        assertTrue(firstChat is HomePendingAction.FirstChat)
    }

    @Test
    fun `Home pending action lite DTO deserializes visual review timestamps and remains additive`() {
        val visual = testJson.decodeFromString<HomePendingActionLiteResponseDto>(
            """
            {
              "type": "VISUAL_REVIEW",
              "matchId": "match-visual",
              "visualStartedAt": "2026-06-19T18:00:00Z",
              "visualExpiresAt": "2026-06-20T18:00:00Z"
            }
            """.trimIndent()
        )
        val legacy = testJson.decodeFromString<HomePendingActionLiteResponseDto>(
            """
            {
              "type": "VISUAL_REVIEW",
              "matchId": "match-legacy"
            }
            """.trimIndent()
        )
        val firstChat = testJson.decodeFromString<HomePendingActionLiteResponseDto>(
            """
            {
              "type": "FIRST_CHAT",
              "matchId": "match-first",
              "chatId": "chat-first",
              "visualStartedAt": "bad",
              "visualExpiresAt": "bad"
            }
            """.trimIndent()
        ).toDomain()

        assertEquals("2026-06-19T18:00:00Z", visual.visualStartedAt)
        assertEquals("2026-06-20T18:00:00Z", visual.visualExpiresAt)
        assertEquals(null, legacy.visualStartedAt)
        assertEquals(null, legacy.visualExpiresAt)
        assertTrue(firstChat is HomePendingAction.FirstChat)
    }

    @Test
    fun `Home DTO maps full second chat expired metadata`() {
        val home = minimalHome(
            nextSteps = listOf(
                HomeNextStepResponseDto(
                    type = "SECOND_CHAT_EXPIRED",
                    connectionId = "connection-expired",
                    matchId = "match-expired",
                    partner = partnerDto("expired-partner"),
                    secondChat = HomeChatResponseDto(
                        chatId = null,
                        chatType = "SECOND_CHAT",
                        chatStatus = null,
                        availableAt = "2026-06-20T18:00:00-03:00",
                        entryClosesAt = "2026-06-20T18:20:00-03:00",
                        expiresAt = "2026-06-20T20:00:00-03:00",
                        durationMinutes = 120,
                        myAttendanceStatus = "NO_SHOW",
                    ),
                ),
            ),
        ).toDomain()

        val expired = home.nextSteps.single() as HomeNextStep.SecondChatExpired

        assertEquals("connection-expired", expired.connectionId)
        assertEquals("match-expired", expired.matchId)
        assertEquals("expired-partner", expired.partner?.displayName)
        assertEquals("2026-06-20T18:20:00-03:00", expired.secondChat?.entryClosesAt)
        assertEquals(SecondChatAttendanceStatus.NoShow, expired.secondChat?.myAttendanceStatus)
    }

    @Test
    fun `Home pending state DTO maps lite second chat expired metadata`() {
        val pending = HomePendingStateResponseDto(
            version = 16,
            nextSteps = listOf(
                HomeNextStepLiteResponseDto(
                    type = "SECOND_CHAT_EXPIRED",
                    connectionId = "connection-expired",
                    matchId = "match-expired",
                    secondChat = HomePendingSecondChatLiteResponseDto(
                        chatId = null,
                        availableAt = "2026-06-20T18:00:00-03:00",
                        entryClosesAt = "2026-06-20T18:20:00-03:00",
                        expiresAt = "2026-06-20T20:00:00-03:00",
                        durationMinutes = 120,
                        myAttendanceStatus = "PENDING",
                    ),
                ),
            ),
            serverTime = "2026-06-20T18:21:00-03:00",
        ).toDomain()

        val expired = pending.nextSteps.single() as HomeNextStep.SecondChatExpired

        assertEquals("connection-expired", expired.connectionId)
        assertEquals("2026-06-20T18:20:00-03:00", expired.secondChat?.entryClosesAt)
        assertEquals(SecondChatAttendanceStatus.Pending, expired.secondChat?.myAttendanceStatus)
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
            passiveNotices = listOf(HomePassiveNoticeResponseDto(type = "NEW_NOTICE")),
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

    private fun secondChatStep(
        type: String,
        connectionId: String,
        availableAt: String,
    ): HomeNextStepResponseDto = HomeNextStepResponseDto(
        type = type,
        connectionId = connectionId,
        matchId = "match-$connectionId",
        secondChat = HomeChatResponseDto(
            chatId = "chat-$connectionId",
            chatType = "SECOND_CHAT",
            chatStatus = when (type) {
                "SECOND_CHAT_READ_ONLY" -> "EXPIRED"
                else -> "ACTIVE"
            },
            availableAt = availableAt,
            expiresAt = "2026-08-03T20:00:00Z",
            readOnlyUntil = "2026-08-04T20:00:00Z",
            durationMinutes = 120,
        ),
    )

    private fun secondChatLiteStep(
        type: String,
        connectionId: String,
        availableAt: String,
    ): HomeNextStepLiteResponseDto = HomeNextStepLiteResponseDto(
        type = type,
        connectionId = connectionId,
        matchId = "match-$connectionId",
        secondChat = HomePendingSecondChatLiteResponseDto(
            chatId = "chat-$connectionId",
            availableAt = availableAt,
            expiresAt = "2026-08-03T20:00:00Z",
            readOnlyUntil = "2026-08-04T20:00:00Z",
            durationMinutes = 120,
        ),
    )

    private fun partnerDto(name: String): ChatPartnerResponseDto = ChatPartnerResponseDto(
        userId = "user-$name",
        profileId = "profile-$name",
        displayName = name,
    )

    @Serializable
    private data class HomeContractProbeDto(
        val activeInteractionsSummary: HomeActiveInteractionsSummaryResponseDto,
        val passiveNotices: List<HomePassiveNoticeResponseDto>,
    )
}
