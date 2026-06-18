package com.reals.app.ui.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRouterTest {
    private val router = HomeRouter()

    @Test
    fun `home with FIRST_CHAT pending action routes to first chat`() {
        val route = router.resolve(
            screenModel = model(
                pendingActions = listOf(
                    HomeActionItem.FirstChat(
                        matchId = "match-1",
                        chatId = "chat-1",
                        partnerDisplayName = "Alex",
                    ),
                )
            ),
            autoNavigate = true,
        )

        assertEquals(HomeRoute.OpenFirstChat("match-1", "chat-1"), route)
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
        assertEquals(
            HomeRoute.StayHome,
            router.resolve(
                model(
                    pendingActions = listOf(
                        HomeActionItem.FirstChat("match-1", "chat-1", null),
                    )
                ),
                autoNavigate = false,
            )
        )
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
