package com.reals.app.ui.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HomeRouterTest {
    private val router = HomeRouter()

    @Test
    fun `home with FIRST_CHAT pending action routes to first chat`() {
        val screenModel = model(
            pendingActions = listOf(
                HomeActionItem.FirstChat(
                    matchId = "match-1",
                    chatId = "chat-1",
                    partnerDisplayName = "Alex",
                ),
            )
        )

        val autoRoute = router.resolve(
            screenModel = screenModel,
            autoNavigate = true,
        )
        val manualRoute = router.resolve(
            screenModel = screenModel,
            autoNavigate = false,
        )

        assertEquals(HomeRoute.OpenFirstChat("match-1", "chat-1"), autoRoute)
        assertEquals(HomeRoute.OpenFirstChat("match-1", "chat-1"), manualRoute)
    }

    @Test
    fun `without FIRST_CHAT disabled auto navigate keeps user on Home`() {
        val route = router.resolve(
            screenModel = model(
                pendingActions = listOf(
                    HomeActionItem.VisualReview(
                        matchId = "match-visual",
                        partnerDisplayName = "Taylor",
                    ),
                )
            ),
            autoNavigate = false,
        )

        assertEquals(HomeRoute.StayHome, route)
    }

    @Test
    fun `home with visual review does not auto route with current router`() {
        val route = router.resolve(
            screenModel = model(
                pendingActions = listOf(
                    HomeActionItem.VisualReview(
                        matchId = "match-visual",
                        partnerDisplayName = "Taylor",
                    ),
                )
            ),
            autoNavigate = true,
        )

        assertEquals(HomeRoute.StayHome, route)
    }

    @Test
    fun `no pending actions or disabled auto navigate keeps user on Home`() {
        assertEquals(HomeRoute.StayHome, router.resolve(model(), autoNavigate = true))
        assertEquals(HomeRoute.StayHome, router.resolve(model(), autoNavigate = false))
    }

    @Test
    fun `active second chat with pending attendance stays on Home`() {
        val nowMillis = Instant.parse("2026-08-17T22:00:00Z").toEpochMilli()

        val route = router.resolve(
            screenModel = model(
                nextSteps = listOf(
                    HomeNextStepItem.SecondChatAvailable(
                        connectionId = "connection-1",
                        matchId = "match-1",
                        partnerDisplayName = "Alex",
                        chatId = "chat-1",
                        chatStatus = "ACTIVE",
                        availableAt = "2026-08-17T21:00:00Z",
                        expiresAt = "2026-08-17T23:00:00Z",
                        durationMinutes = 120L,
                        entryClosesAt = "2026-08-17T21:20:00Z",
                        myAttendanceStatus = "PENDING",
                    ),
                ),
            ),
            autoNavigate = true,
            nowMillis = nowMillis,
        )

        assertEquals(HomeRoute.StayHome, route)
    }

    @Test
    fun `active second chat with on time attendance auto routes`() {
        val nowMillis = Instant.parse("2026-08-17T22:00:00Z").toEpochMilli()

        val route = router.resolve(
            screenModel = model(
                nextSteps = listOf(
                    HomeNextStepItem.SecondChatAvailable(
                        connectionId = "connection-1",
                        matchId = "match-1",
                        partnerDisplayName = "Alex",
                        chatId = "chat-1",
                        chatStatus = "ACTIVE",
                        availableAt = "2026-08-17T21:00:00Z",
                        expiresAt = "2026-08-17T23:00:00Z",
                        durationMinutes = 120L,
                        entryClosesAt = "2026-08-17T21:20:00Z",
                        myAttendanceStatus = "ON_TIME",
                    ),
                ),
            ),
            autoNavigate = true,
            nowMillis = nowMillis,
        )

        assertEquals(
            HomeRoute.OpenSecondChat(
                connectionId = "connection-1",
                matchId = "match-1",
                partnerName = "Alex",
            ),
            route,
        )
    }

    @Test
    fun `active second chat with late attendance auto routes`() {
        val nowMillis = Instant.parse("2026-08-17T22:00:00Z").toEpochMilli()

        val route = router.resolve(
            screenModel = model(
                nextSteps = listOf(
                    HomeNextStepItem.SecondChatScheduled(
                        connectionId = "connection-2",
                        matchId = "match-2",
                        partnerDisplayName = "Taylor",
                        chatId = "chat-2",
                        chatStatus = "ACTIVE",
                        availableAt = "2026-08-17T21:00:00Z",
                        expiresAt = "2026-08-17T23:00:00Z",
                        durationMinutes = 120L,
                        entryClosesAt = "2026-08-17T21:20:00Z",
                        myAttendanceStatus = "LATE",
                    ),
                ),
            ),
            autoNavigate = true,
            nowMillis = nowMillis,
        )

        assertEquals(
            HomeRoute.OpenSecondChat(
                connectionId = "connection-2",
                matchId = "match-2",
                partnerName = "Taylor",
            ),
            route,
        )
    }

    @Test
    fun `joined active second chat past expiresAt stays on Home`() {
        val nowMillis = Instant.parse("2026-08-17T23:01:00Z").toEpochMilli()

        val route = router.resolve(
            screenModel = model(
                nextSteps = listOf(
                    HomeNextStepItem.SecondChatAvailable(
                        connectionId = "connection-expired",
                        matchId = "match-expired",
                        partnerDisplayName = "Alex",
                        chatId = "chat-expired",
                        chatStatus = "ACTIVE",
                        availableAt = "2026-08-17T20:00:00Z",
                        expiresAt = "2026-08-17T22:00:00Z",
                        durationMinutes = 120L,
                        entryClosesAt = "2026-08-17T20:20:00Z",
                        myAttendanceStatus = "ON_TIME",
                    ),
                ),
            ),
            autoNavigate = true,
            nowMillis = nowMillis,
        )

        assertEquals(HomeRoute.StayHome, route)
    }

    private fun model(
        pendingActions: List<HomeActionItem> = emptyList(),
        nextSteps: List<HomeNextStepItem> = emptyList(),
        passiveNotices: List<HomePassiveNoticeItem> = emptyList(),
    ) = HomeScreenModel(
        pendingActions = pendingActions,
        nextSteps = nextSteps,
        activeInteractionsSummary = null,
        passiveNotices = passiveNotices,
        matchmaking = HomeMatchmakingUiState(
            inQueue = false,
            canSearch = true,
            blockedReason = null,
        ),
    )
}
