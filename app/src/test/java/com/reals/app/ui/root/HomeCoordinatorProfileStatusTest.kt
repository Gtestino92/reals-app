package com.reals.app.ui.root

import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomePassiveNoticeResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.usecase.DismissSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetHomePendingUseCase
import com.reals.app.domain.usecase.GetHomeStatusUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class HomeCoordinatorProfileStatusTest {
    @Test
    fun `active profile Home behavior remains unchanged`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(emptyHome(profileStatus = "ACTIVE"))
        }
        val state = MutableStateFlow<RealsRootUiState>(ready())
        var reloadCalls = 0

        coordinator(api, state, this, onReload = { reloadCalls += 1 })
            .loadHomeForReady(state.value as RealsRootUiState.Ready)
        yield()

        assertEquals(0, reloadCalls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(ProfileStatus.Active, ready.currentProfileStatus())
        assertEquals(ProfileStatus.Active, ready.home.homeState?.profileStatus)
    }

    @Test
    fun `draft profile with pending visual review stays in Home without session reroute`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(
                emptyHome(profileStatus = "DRAFT").copy(
                    pendingActions = listOf(
                        HomePendingActionResponseDto(
                            type = "VISUAL_REVIEW",
                            matchId = "match-visual",
                            partner = TestDtos.partner("Visual"),
                        ),
                    ),
                )
            )
        }
        val state = MutableStateFlow<RealsRootUiState>(ready())
        var reloadCalls = 0

        coordinator(api, state, this, onReload = { reloadCalls += 1 })
            .loadHomeForReady(state.value as RealsRootUiState.Ready)
        yield()

        assertEquals(0, reloadCalls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(ProfileStatus.Draft, ready.currentProfileStatus())
        assertTrue(ready.home.screenModel?.pendingActions?.isNotEmpty() == true)
    }

    @Test
    fun `draft profile with scheduling next step stays in Home without session reroute`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(
                emptyHome(profileStatus = "DRAFT").copy(
                    nextSteps = listOf(
                        HomeNextStepResponseDto(
                            type = "SCHEDULING",
                            connectionId = "connection-1",
                            matchId = "match-1",
                            partner = TestDtos.partner("Scheduling"),
                        ),
                    ),
                )
            )
        }
        val state = MutableStateFlow<RealsRootUiState>(ready())
        var reloadCalls = 0

        coordinator(api, state, this, onReload = { reloadCalls += 1 })
            .loadHomeForReady(state.value as RealsRootUiState.Ready)
        yield()

        assertEquals(0, reloadCalls)
        assertTrue((state.value as RealsRootUiState.Ready).home.screenModel?.nextSteps?.isNotEmpty() == true)
    }

    @Test
    fun `draft profile with only pending scheduling passive notice stays in Home`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(
                emptyHome(profileStatus = "DRAFT").copy(
                    passiveNotices = listOf(HomePassiveNoticeResponseDto("SCHEDULING_PREPARING")),
                )
            )
        }
        val state = MutableStateFlow<RealsRootUiState>(ready())
        var reloadCalls = 0

        coordinator(api, state, this, onReload = { reloadCalls += 1 })
            .loadHomeForReady(state.value as RealsRootUiState.Ready)
        yield()

        assertEquals(0, reloadCalls)
        assertTrue((state.value as RealsRootUiState.Ready).home.screenModel?.passiveNotices?.isNotEmpty() == true)
    }

    @Test
    fun `draft profile with no existing interaction uses profile session reroute`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(emptyHome(profileStatus = "DRAFT"))
        }
        val state = MutableStateFlow<RealsRootUiState>(ready())
        var reloadCalls = 0

        coordinator(api, state, this, onReload = { reloadCalls += 1 })
            .loadHomeForReady(state.value as RealsRootUiState.Ready)
        yield()

        assertEquals(1, reloadCalls)
    }

    private fun coordinator(
        api: FakeRealsApi,
        state: MutableStateFlow<RealsRootUiState>,
        scope: CoroutineScope,
        onReload: suspend () -> Unit,
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
                getHomeStatus = GetHomeStatusUseCase(meRepository),
                getHomePending = GetHomePendingUseCase(meRepository),
                leaveQueue = LeaveQueueUseCase(matchmakingRepository),
                dismissSecondChat = DismissSecondChatForConnectionUseCase(chatRepository),
            ),
            scope = scope,
            onOpenFirstChat = { _, _, _ -> },
            onReloadActiveSession = { onReload() },
        )
    }

    private fun ready(): RealsRootUiState.Ready =
        RealsRootUiState.Ready(session = TestDomain.session())

    private fun emptyHome(profileStatus: String) = TestDtos.home().copy(
        profileStatus = profileStatus,
        matchmaking = HomeMatchmakingResponseDto(
            inQueue = false,
            canSearch = profileStatus == "ACTIVE",
            blockedReason = null,
        ),
        activeInteractionsSummary = HomeActiveInteractionsSummaryResponseDto(
            activeInitialCount = 0,
            activeConnectionCount = 0,
            hasPendingSchedulingConnection = false,
            actionableConnectionCount = 0,
        ),
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )

    private fun RealsRootUiState.Ready.currentProfileStatus(): ProfileStatus =
        ((session.profileSnapshot as ProfileSnapshot.Found).profile.status)
}
