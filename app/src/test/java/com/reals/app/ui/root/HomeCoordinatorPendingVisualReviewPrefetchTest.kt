package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.usecase.DismissSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetHomePendingUseCase
import com.reals.app.domain.usecase.GetHomeStatusUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import com.reals.app.ui.matchmaking.HomeUiMapper
import com.reals.app.ui.matchmaking.LocalHiddenInteractions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCoordinatorPendingVisualReviewPrefetchTest {
    @Test
    fun enteringPendingSurfaceStartsPendingVisualReviewPrefetch() = runTest {
        val home = homeState(
            pendingActions = listOf(
                HomePendingAction.FirstChat("match-first", "chat-1", partner = null),
                HomePendingAction.VisualReview("match-visual-a", partner = null),
                HomePendingAction.VisualReview("match-visual-b", partner = null),
            )
        )
        val state = readyState(home = home, surface = HomeSurface.Overview)
        val prefetcher = RecordingPendingVisualReviewPhotoPrefetcher()
        val coordinator = coordinator(state, this, prefetcher)

        coordinator.showHomeSurface(HomeSurface.Pending)

        assertEquals(listOf(listOf("match-visual-a", "match-visual-b")), prefetcher.matchIdCalls)
        assertEquals(0, prefetcher.cancelCalls)
    }

    @Test
    fun leavingPendingSurfaceCancelsPendingVisualReviewPrefetch() = runTest {
        val home = homeState(
            pendingActions = listOf(HomePendingAction.VisualReview("match-visual", partner = null))
        )
        val state = readyState(home = home, surface = HomeSurface.Pending)
        val prefetcher = RecordingPendingVisualReviewPhotoPrefetcher()
        val coordinator = coordinator(state, this, prefetcher)

        coordinator.showHomeSurface(HomeSurface.Overview)

        assertEquals(1, prefetcher.cancelCalls)
    }

    private fun coordinator(
        state: MutableStateFlow<RealsRootUiState>,
        scope: CoroutineScope,
        prefetcher: RecordingPendingVisualReviewPhotoPrefetcher,
    ): HomeCoordinator {
        val api = FakeRealsApi()
        val tokenProvider = FakeAuthTokenProvider()
        val meRepository = MeRepository(api, tokenProvider, testApiExecutor())
        val matchmakingRepository = MatchmakingRepository(api, tokenProvider, testApiExecutor())
        val chatRepository = ChatRepository(api, testJson, tokenProvider, testApiExecutor())

        return HomeCoordinator(
            uiState = state,
            dependencies = HomeFeatureDependencies(
                enqueueMatchmaking = EnqueueMatchmakingUseCase(matchmakingRepository),
                getHome = GetHomeUseCase(meRepository),
                getHomeStatus = GetHomeStatusUseCase(meRepository),
                getHomePending = GetHomePendingUseCase(meRepository),
                leaveQueue = LeaveQueueUseCase(matchmakingRepository),
                dismissSecondChat = DismissSecondChatForConnectionUseCase(chatRepository),
            ),
            scope = scope,
            onOpenFirstChat = { _, _, _ -> },
            onOpenSecondChat = { _, _, _, _ -> },
            onReloadActiveSession = { _ -> },
            pendingVisualReviewPhotoPrefetcher = prefetcher,
            getVisualProfile = { ApiResult.Success(visualProfile(it)) },
        )
    }

    private fun readyState(
        home: HomeState,
        surface: HomeSurface,
    ): MutableStateFlow<RealsRootUiState> {
        val screenModel = HomeUiMapper().toScreenModel(
            home = home,
            localHidden = LocalHiddenInteractions(
                hiddenFirstChatMatchIds = emptySet(),
                hiddenVisualMatchIds = emptySet(),
            ),
            localMatchmakingBlockedReason = null,
        )
        return MutableStateFlow(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                home = HomeUiState(
                    homeState = home,
                    screenModel = screenModel,
                    surface = surface,
                ),
            )
        )
    }

    private fun homeState(pendingActions: List<HomePendingAction>): HomeState = HomeState(
        profileStatus = null,
        matchmaking = HomeMatchmaking(
            inQueue = false,
            canSearch = true,
            blockedReason = null,
        ),
        activeInteractionsSummary = HomeActiveInteractionsSummary(
            activeInitialCount = 0,
            activeConnectionCount = 0,
            hasPendingSchedulingConnection = false,
            actionableConnectionCount = 0,
        ),
        pendingActions = pendingActions,
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )

    private fun visualProfile(matchId: String): VisualProfile = VisualProfile(
        profileId = "profile-$matchId",
        displayName = "Profile $matchId",
        age = 30,
        bio = null,
        photos = emptyList(),
        visualExpiresAt = null,
        myPersonalMessageSubmitted = false,
        partnerPersonalMessageSubmitted = false,
        partnerPersonalMessageRead = false,
        decisionRequiresPartnerPersonalMessageRead = false,
    )

    private class RecordingPendingVisualReviewPhotoPrefetcher : PendingVisualReviewPhotoPrefetcher {
        val matchIdCalls = mutableListOf<List<String>>()
        var cancelCalls = 0

        override fun prefetchPendingVisualReviews(
            scope: CoroutineScope,
            sessionScopeKey: String,
            pendingActions: List<HomePendingAction>,
            getVisualProfile: suspend (String) -> ApiResult<VisualProfile>,
        ) {
            matchIdCalls += pendingVisualReviewMatchIds(pendingActions)
        }

        override fun cancel() {
            cancelCalls += 1
        }
    }
}
