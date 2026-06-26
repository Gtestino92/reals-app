package com.reals.app.ui.root

import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.usecase.DismissSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import com.reals.app.ui.matchmaking.HomeUiMapper
import com.reals.app.ui.matchmaking.LocalHiddenInteractions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class HomeCoordinatorCancelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `silent home poll ignores overlapping call`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val firstPollStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetHomeResponse = {
                firstPollStarted.complete(Unit)
                gate.await()
            }
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Searching, inQueue = true),
        )
        val coordinator = coordinator(api, state, this)

        coordinator.pollHomeStateSilently()
        runCurrent()
        firstPollStarted.await()

        coordinator.pollHomeStateSilently()
        runCurrent()

        assertEquals(1, api.calls.count { it == "getHome" })

        gate.complete(Unit)
        runCurrent()

        coordinator.pollHomeStateSilently()
        runCurrent()

        assertEquals(2, api.calls.count { it == "getHome" })
    }

    @Test
    fun `cancel while resolving location returns idle without leaving queue`() = runBlocking {
        val api = FakeRealsApi()
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.ResolvingLocation, inQueue = false),
        )

        coordinator(api, state, this).cancelMatchmakingSearch()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(MatchmakingSearchUiPhase.Idle, ready.home.matchmakingSearchPhase)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `cancel while joining without confirmed queue returns idle without leaving queue`() = runBlocking {
        val api = FakeRealsApi()
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.JoiningQueue, inQueue = false),
        )

        coordinator(api, state, this).cancelMatchmakingSearch()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(MatchmakingSearchUiPhase.Idle, ready.home.matchmakingSearchPhase)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `cancel while searching uses backend leave queue`() = runBlocking {
        val api = FakeRealsApi().apply {
            queueResponse = Response.success(TestDtos.queueStatus(inQueue = false))
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Searching, inQueue = true),
        )

        coordinator(api, state, this).cancelMatchmakingSearch()
        yield()

        assertTrue(api.calls.contains("leaveMatchmakingQueue"))
    }

    @Test
    fun `stale device location result after cancellation does not enqueue`() = runBlocking {
        val api = FakeRealsApi()
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.ResolvingLocation, inQueue = false),
        )
        val coordinator = coordinator(api, state, this)

        coordinator.cancelMatchmakingSearch()
        coordinator.enqueueMatchmakingFromResolvedDeviceLocation(
            SearchLocationInput(latitude = -34.6037, longitude = -58.3816, accuracyMeters = 50),
        )
        yield()

        assertTrue(api.calls.isEmpty())
    }

    private fun coordinator(
        api: FakeRealsApi,
        state: MutableStateFlow<RealsRootUiState>,
        scope: CoroutineScope,
    ): HomeCoordinator {
        val tokenProvider = FakeAuthTokenProvider()
        val meRepository = MeRepository(api, tokenProvider, testApiExecutor())
        val matchmakingRepository = MatchmakingRepository(api, tokenProvider, testApiExecutor())
        val chatRepository = ChatRepository(api, testJson, tokenProvider, testApiExecutor())

        return HomeCoordinator(
            uiState = state,
            dependencies = HomeFeatureDependencies(
                enqueueMatchmaking = EnqueueMatchmakingUseCase(matchmakingRepository),
                getHome = GetHomeUseCase(meRepository),
                leaveQueue = LeaveQueueUseCase(matchmakingRepository),
                dismissSecondChat = DismissSecondChatForConnectionUseCase(chatRepository),
            ),
            scope = scope,
            onOpenFirstChat = { _, _, _ -> },
            onReloadActiveSession = { _ -> },
        )
    }

    private fun ready(
        phase: MatchmakingSearchUiPhase,
        inQueue: Boolean,
    ): RealsRootUiState.Ready {
        val home = homeState(inQueue)
        return RealsRootUiState.Ready(
            session = TestDomain.session(),
            home = HomeUiState(
                homeState = home,
                screenModel = HomeUiMapper().toScreenModel(
                    home = home,
                    localHidden = LocalHiddenInteractions(
                        hiddenFirstChatMatchIds = emptySet(),
                        hiddenVisualMatchIds = emptySet(),
                    ),
                    localMatchmakingBlockedReason = null,
                ),
                matchmakingSearchPhase = phase,
            ),
        )
    }

    private fun homeState(inQueue: Boolean): HomeState = HomeState(
        profileStatus = null,
        matchmaking = HomeMatchmaking(
            inQueue = inQueue,
            canSearch = !inQueue,
            blockedReason = null,
        ),
        activeInteractionsSummary = HomeActiveInteractionsSummary(
            activeInitialCount = 0,
            activeConnectionCount = 0,
            pendingSchedulingConnectionCount = 0,
            actionableConnectionCount = 0,
        ),
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )
}
