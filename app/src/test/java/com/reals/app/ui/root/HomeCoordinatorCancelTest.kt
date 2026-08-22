package com.reals.app.ui.root

import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomeMatchmakingBlockedReasonResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.HomeStatus
import com.reals.app.domain.model.SearchLocationInput
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
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import com.reals.app.ui.matchmaking.HomeUiMapper
import com.reals.app.ui.matchmaking.LocalHiddenInteractions
import com.reals.app.ui.matchmaking.HomeNextStepItem
import com.reals.app.ui.matchmaking.toHomeMatchmakingBlockedReasonUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            beforeGetHomeStatusResponse = {
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

        assertEquals(1, api.calls.count { it == "getHomeStatus" })

        gate.complete(Unit)
        runCurrent()

        coordinator.pollHomeStateSilently()
        runCurrent()

        assertEquals(2, api.calls.count { it == "getHomeStatus" })
        assertEquals(0, api.calls.count { it == "getHome" })
    }

    @Test
    fun `silent home poll with unchanged clean version does not load full home`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(TestDtos.homeStatus(version = 4, dirty = false))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 4),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(4L, ready.home.homeStatusVersion)
    }

    @Test
    fun `silent home poll with future next refresh marker does not load full home`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(
                TestDtos.homeStatus(
                    version = 4,
                    dirty = false,
                    nextRefreshAt = "2026-06-18T21:05:00Z",
                    serverTime = "2026-06-18T21:00:00Z",
                )
            )
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 4),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(4L, ready.home.homeStatusVersion)
    }

    @Test
    fun `silent home poll loads full home when next refresh marker equals server time`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(
                TestDtos.homeStatus(
                    version = 4,
                    dirty = false,
                    nextRefreshAt = "2026-06-18T21:00:00Z",
                    serverTime = "2026-06-18T21:00:00Z",
                )
            )
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 4),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus", "getHome"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(4L, ready.home.homeStatusVersion)
        assertEquals(1, ready.home.homeState?.activeInteractionsSummary?.activeInitialCount)
    }

    @Test
    fun `silent home poll loads full home when next refresh marker is before server time`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(
                TestDtos.homeStatus(
                    version = 4,
                    dirty = false,
                    nextRefreshAt = "2026-06-18T20:59:59Z",
                    serverTime = "2026-06-18T21:00:00Z",
                )
            )
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 4),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus", "getHome"), api.calls)
    }

    @Test
    fun `silent home poll stores first clean version without loading full home`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(TestDtos.homeStatus(version = 5, dirty = false))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = null),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(5L, ready.home.homeStatusVersion)
    }

    @Test
    fun `silent home poll loads full home when version changes`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(TestDtos.homeStatus(version = 6, dirty = false))
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 5),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus", "getHome"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(6L, ready.home.homeStatusVersion)
        assertEquals(1, ready.home.homeState?.activeInteractionsSummary?.activeInitialCount)
    }

    @Test
    fun `silent home poll with unchanged dirty version loads full home`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(TestDtos.homeStatus(version = 7, dirty = true))
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 7),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus", "getHome"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(7L, ready.home.homeStatusVersion)
    }

    @Test
    fun `silent home poll ignores malformed wakeup timestamps without device time fallback`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(
                TestDtos.homeStatus(
                    version = 3,
                    dirty = false,
                    nextRefreshAt = "not-a-time",
                    serverTime = null,
                )
            )
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 3),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus"), api.calls)
        assertEquals(false, HomeStatus(3, false, "not-a-time", null).isHomeWakeUpDue())
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(3L, ready.home.homeStatusVersion)
    }

    @Test
    fun `silent home poll with no known version and dirty status loads full home once`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(TestDtos.homeStatus(version = 8, dirty = true))
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = null),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus", "getHome"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(8L, ready.home.homeStatusVersion)
    }

    @Test
    fun `silent home poll status failure does not overwrite ui with error`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 3),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(null, ready.home.homeError)
        assertEquals(3L, ready.home.homeStatusVersion)
    }

    @Test
    fun `silent home poll full home failure after new version remains silent`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(TestDtos.homeStatus(version = 19, dirty = true))
            homeResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 18),
        )

        coordinator(api, state, this).pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus", "getHome"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(null, ready.home.homeError)
        assertEquals(18L, ready.home.homeStatusVersion)
        assertEquals(false, ready.home.homeLoading)
        assertEquals(false, ready.home.homeState?.matchmaking?.inQueue)
    }

    @Test
    fun `silent home poll retries due wakeup after full home failure`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeStatusResponse = Response.success(
                TestDtos.homeStatus(
                    version = 18,
                    dirty = false,
                    nextRefreshAt = "2026-06-18T20:59:59Z",
                    serverTime = "2026-06-18T21:00:00Z",
                )
            )
            homeResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 18),
        )
        val coordinator = coordinator(api, state, this)

        coordinator.pollHomeStateSilently()
        yield()
        api.homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        coordinator.pollHomeStateSilently()
        yield()

        assertEquals(listOf("getHomeStatus", "getHome", "getHomeStatus", "getHome"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(null, ready.home.homeError)
        assertEquals(18L, ready.home.homeStatusVersion)
        assertEquals(1, ready.home.homeState?.activeInteractionsSummary?.activeInitialCount)
    }

    @Test
    fun `explicit refresh still loads full home without status precheck`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 8),
        )

        coordinator(api, state, this).refreshHomeState()
        yield()

        assertEquals(listOf("getHome"), api.calls)
        val ready = state.value as RealsRootUiState.Ready
        assertEquals(8L, ready.home.homeStatusVersion)
    }

    @Test
    fun `explicit refresh updates next step positions to latest backend order`() = runBlocking {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(
                TestDtos.home().copy(
                    pendingActions = emptyList(),
                    nextSteps = listOf(
                        HomeNextStepResponseDto(
                            type = "SCHEDULING",
                            connectionId = "connection-new-first",
                            matchId = "match-new-first",
                        ),
                        HomeNextStepResponseDto(
                            type = "SCHEDULING",
                            connectionId = "connection-new-second",
                            matchId = "match-new-second",
                        ),
                    ),
                ),
            )
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false, homeStatusVersion = 8),
        )

        coordinator(api, state, this).refreshHomeState()
        yield()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(
            listOf("connection-new-first", "connection-new-second"),
            ready.home.screenModel?.nextSteps?.map { (it as HomeNextStepItem.Scheduling).connectionId },
        )
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

    @Test
    fun `visual advancement cap enqueue failure refreshes Home without generic cap error`() = runBlocking {
        val api = FakeRealsApi().apply {
            queueResponse = backendErrorResponse(409, "VISUAL_ADVANCEMENT_LIMIT_REACHED")
            homeResponse = Response.success(
                TestDtos.home().copy(
                    matchmaking = HomeMatchmakingResponseDto(
                        inQueue = false,
                        canSearch = false,
                        blockedReason = HomeMatchmakingBlockedReasonResponseDto(
                            code = "VISUAL_ADVANCEMENT_LIMIT_REACHED",
                            message = "Wait",
                            nextAvailableAt = "2026-08-21T15:20:00Z",
                        ),
                    ),
                    pendingActions = emptyList(),
                    nextSteps = emptyList(),
                )
            )
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false),
        )

        coordinator(api, state, this).enqueueMatchmaking(
            SearchLocationInput(latitude = -34.6037, longitude = -58.3816, accuracyMeters = 50),
        )
        yield()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(listOf("enqueueMatchmaking", "getHome"), api.calls)
        assertEquals(false, ready.home.homeLoading)
        assertNull(ready.home.homeError)
        assertEquals(MatchmakingSearchUiPhase.Idle, ready.home.matchmakingSearchPhase)
        assertEquals("VISUAL_ADVANCEMENT_LIMIT_REACHED", ready.home.screenModel?.matchmaking?.blockedReason?.code)
        assertEquals("2026-08-21T15:20:00Z", ready.home.screenModel?.matchmaking?.blockedReason?.nextAvailableAt)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `active match limit enqueue failure blocks locally and refreshes Home without generic error`() = runTest {
        val getHomeStarted = CompletableDeferred<Unit>()
        val releaseGetHome = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            queueResponse = backendErrorResponse(409, "ACTIVE_MATCH_LIMIT_REACHED")
            homeResponse = Response.success(
                TestDtos.home().copy(
                    matchmaking = HomeMatchmakingResponseDto(
                        inQueue = false,
                        canSearch = true,
                        blockedReason = null,
                    ),
                    pendingActions = emptyList(),
                    nextSteps = emptyList(),
                )
            )
            beforeGetHomeResponse = {
                getHomeStarted.complete(Unit)
                releaseGetHome.await()
            }
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false),
        )

        coordinator(api, state, this).enqueueMatchmaking(
            SearchLocationInput(latitude = -34.6037, longitude = -58.3816, accuracyMeters = 50),
        )
        runCurrent()
        getHomeStarted.await()

        val blocked = state.value as RealsRootUiState.Ready
        assertEquals(listOf("enqueueMatchmaking", "getHome"), api.calls.takeLast(2))
        assertEquals(false, blocked.home.homeLoading)
        assertNull(blocked.home.homeError)
        assertEquals(MatchmakingSearchUiPhase.Failed, blocked.home.matchmakingSearchPhase)
        assertEquals("ACTIVE_MATCH_LIMIT_REACHED", blocked.home.screenModel?.matchmaking?.blockedReason?.code)
        assertEquals(null, blocked.home.screenModel?.matchmaking?.blockedReason?.nextAvailableAt)

        releaseGetHome.complete(Unit)
        runCurrent()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(true, ready.home.screenModel?.matchmaking?.canSearch)
        assertNull(ready.home.screenModel?.matchmaking?.blockedReason)
        assertNull(ready.home.matchmakingBlockedReason)
        assertNull(ready.home.homeError)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `active connection limit enqueue failure blocks locally and keeps authoritative refreshed blocker`() = runTest {
        val getHomeStarted = CompletableDeferred<Unit>()
        val releaseGetHome = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            queueResponse = backendErrorResponse(409, "ACTIVE_CONNECTION_LIMIT_REACHED")
            homeResponse = Response.success(
                TestDtos.home().copy(
                    matchmaking = HomeMatchmakingResponseDto(
                        inQueue = false,
                        canSearch = false,
                        blockedReason = HomeMatchmakingBlockedReasonResponseDto(
                            code = "ACTIVE_CONNECTION_LIMIT_REACHED",
                            message = "Still blocked",
                            nextAvailableAt = null,
                        ),
                    ),
                    pendingActions = emptyList(),
                    nextSteps = emptyList(),
                )
            )
            beforeGetHomeResponse = {
                getHomeStarted.complete(Unit)
                releaseGetHome.await()
            }
        }
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false),
        )

        coordinator(api, state, this).enqueueMatchmaking(
            SearchLocationInput(latitude = -34.6037, longitude = -58.3816, accuracyMeters = 50),
        )
        runCurrent()
        getHomeStarted.await()

        val blocked = state.value as RealsRootUiState.Ready
        assertEquals(listOf("enqueueMatchmaking", "getHome"), api.calls.takeLast(2))
        assertNull(blocked.home.homeError)
        assertEquals("ACTIVE_CONNECTION_LIMIT_REACHED", blocked.home.screenModel?.matchmaking?.blockedReason?.code)
        assertEquals(null, blocked.home.screenModel?.matchmaking?.blockedReason?.nextAvailableAt)

        releaseGetHome.complete(Unit)
        runCurrent()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(false, ready.home.screenModel?.matchmaking?.canSearch)
        assertEquals("ACTIVE_CONNECTION_LIMIT_REACHED", ready.home.screenModel?.matchmaking?.blockedReason?.code)
        assertNull(ready.home.matchmakingBlockedReason)
        assertNull(ready.home.homeError)
    }

    @Test
    fun `later Home can search clears visual advancement local blocker`() = runBlocking {
        val capError = com.reals.app.core.network.ApiError.Backend(
            statusCode = 409,
            code = "VISUAL_ADVANCEMENT_LIMIT_REACHED",
            error = "VISUAL_ADVANCEMENT_LIMIT_REACHED",
            message = "Wait",
        )
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(
                TestDtos.home().copy(
                    matchmaking = HomeMatchmakingResponseDto(
                        inQueue = false,
                        canSearch = true,
                        blockedReason = null,
                    ),
                    pendingActions = emptyList(),
                    nextSteps = emptyList(),
                )
            )
        }
        val home = homeState(inQueue = false)
        val state = MutableStateFlow<RealsRootUiState>(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                home = HomeUiState(
                    homeState = home,
                    screenModel = HomeUiMapper().toScreenModel(
                        home = home,
                        localHidden = LocalHiddenInteractions(
                            hiddenFirstChatMatchIds = emptySet(),
                            hiddenVisualMatchIds = emptySet(),
                        ),
                        localMatchmakingBlockedReason = capError.toHomeMatchmakingBlockedReasonUiState(),
                    ),
                    matchmakingBlockedReason = capError,
                    matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
                ),
            )
        )

        coordinator(api, state, this).refreshHomeState()
        yield()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(true, ready.home.screenModel?.matchmaking?.canSearch)
        assertNull(ready.home.screenModel?.matchmaking?.blockedReason)
        assertNull(ready.home.matchmakingBlockedReason)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reenter active match limit keeps no stale message while refreshing Home`() = runTest {
        val getHomeStarted = CompletableDeferred<Unit>()
        val releaseGetHome = CompletableDeferred<Unit>()
        val api = FakeRealsApi()
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false),
        )
        val coordinator = coordinator(api, state, this)
        primeLastSearchLocation(coordinator, api)
        api.queueResponse = backendErrorResponse(409, "ACTIVE_MATCH_LIMIT_REACHED")
        api.homeResponse = Response.success(
            TestDtos.home().copy(
                matchmaking = HomeMatchmakingResponseDto(
                    inQueue = false,
                    canSearch = true,
                    blockedReason = null,
                ),
                pendingActions = emptyList(),
                nextSteps = emptyList(),
            )
        )
        api.beforeGetHomeResponse = {
            getHomeStarted.complete(Unit)
            releaseGetHome.await()
        }

        val job = launch { coordinator.reenterMatchmakingOrLoadHome(TestDomain.session()) }
        runCurrent()
        getHomeStarted.await()

        val blocked = state.value as RealsRootUiState.Ready
        assertEquals(listOf("enqueueMatchmaking", "getHome"), api.calls.takeLast(2))
        assertNull(blocked.home.homeMessage)
        assertEquals("ACTIVE_MATCH_LIMIT_REACHED", blocked.home.matchmakingBlockedReason?.backendCode())
        assertEquals("ACTIVE_MATCH_LIMIT_REACHED", blocked.home.screenModel?.matchmaking?.blockedReason?.code)

        releaseGetHome.complete(Unit)
        job.join()
        runCurrent()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(true, ready.home.screenModel?.matchmaking?.canSearch)
        assertNull(ready.home.screenModel?.matchmaking?.blockedReason)
        assertNull(ready.home.matchmakingBlockedReason)
        assertNull(ready.home.homeMessage)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reenter active connection limit keeps no stale message and authoritative blocker wins`() = runTest {
        val getHomeStarted = CompletableDeferred<Unit>()
        val releaseGetHome = CompletableDeferred<Unit>()
        val api = FakeRealsApi()
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false),
        )
        val coordinator = coordinator(api, state, this)
        primeLastSearchLocation(coordinator, api)
        api.queueResponse = backendErrorResponse(409, "ACTIVE_CONNECTION_LIMIT_REACHED")
        api.homeResponse = Response.success(
            TestDtos.home().copy(
                matchmaking = HomeMatchmakingResponseDto(
                    inQueue = false,
                    canSearch = false,
                    blockedReason = HomeMatchmakingBlockedReasonResponseDto(
                        code = "ACTIVE_CONNECTION_LIMIT_REACHED",
                        message = "Still blocked",
                        nextAvailableAt = null,
                    ),
                ),
                pendingActions = emptyList(),
                nextSteps = emptyList(),
            )
        )
        api.beforeGetHomeResponse = {
            getHomeStarted.complete(Unit)
            releaseGetHome.await()
        }

        val job = launch { coordinator.reenterMatchmakingOrLoadHome(TestDomain.session()) }
        runCurrent()
        getHomeStarted.await()

        val blocked = state.value as RealsRootUiState.Ready
        assertEquals(listOf("enqueueMatchmaking", "getHome"), api.calls.takeLast(2))
        assertNull(blocked.home.homeMessage)
        assertEquals("ACTIVE_CONNECTION_LIMIT_REACHED", blocked.home.matchmakingBlockedReason?.backendCode())
        assertEquals("ACTIVE_CONNECTION_LIMIT_REACHED", blocked.home.screenModel?.matchmaking?.blockedReason?.code)

        releaseGetHome.complete(Unit)
        job.join()
        runCurrent()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(false, ready.home.screenModel?.matchmaking?.canSearch)
        assertEquals("ACTIVE_CONNECTION_LIMIT_REACHED", ready.home.screenModel?.matchmaking?.blockedReason?.code)
        assertNull(ready.home.matchmakingBlockedReason)
        assertNull(ready.home.homeMessage)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reenter visual advancement limit keeps no generic message and authoritative blocker wins`() = runTest {
        val getHomeStarted = CompletableDeferred<Unit>()
        val releaseGetHome = CompletableDeferred<Unit>()
        val api = FakeRealsApi()
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false),
        )
        val coordinator = coordinator(api, state, this)
        primeLastSearchLocation(coordinator, api)
        api.queueResponse = backendErrorResponse(409, "VISUAL_ADVANCEMENT_LIMIT_REACHED")
        api.homeResponse = Response.success(
            TestDtos.home().copy(
                matchmaking = HomeMatchmakingResponseDto(
                    inQueue = false,
                    canSearch = false,
                    blockedReason = HomeMatchmakingBlockedReasonResponseDto(
                        code = "VISUAL_ADVANCEMENT_LIMIT_REACHED",
                        message = "Wait",
                        nextAvailableAt = "2026-08-21T15:20:00Z",
                    ),
                ),
                pendingActions = emptyList(),
                nextSteps = emptyList(),
            )
        )
        api.beforeGetHomeResponse = {
            getHomeStarted.complete(Unit)
            releaseGetHome.await()
        }

        val job = launch { coordinator.reenterMatchmakingOrLoadHome(TestDomain.session()) }
        runCurrent()
        getHomeStarted.await()

        val blocked = state.value as RealsRootUiState.Ready
        assertEquals(listOf("enqueueMatchmaking", "getHome"), api.calls.takeLast(2))
        assertNull(blocked.home.homeMessage)
        assertEquals("VISUAL_ADVANCEMENT_LIMIT_REACHED", blocked.home.matchmakingBlockedReason?.backendCode())
        assertEquals("VISUAL_ADVANCEMENT_LIMIT_REACHED", blocked.home.screenModel?.matchmaking?.blockedReason?.code)

        releaseGetHome.complete(Unit)
        job.join()
        runCurrent()

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(false, ready.home.screenModel?.matchmaking?.canSearch)
        assertEquals("VISUAL_ADVANCEMENT_LIMIT_REACHED", ready.home.screenModel?.matchmaking?.blockedReason?.code)
        assertEquals("2026-08-21T15:20:00Z", ready.home.screenModel?.matchmaking?.blockedReason?.nextAvailableAt)
        assertNull(ready.home.matchmakingBlockedReason)
        assertNull(ready.home.homeMessage)
    }

    @Test
    fun `reenter unexpected enqueue failure keeps automatic reenqueue failure message`() = runBlocking {
        val api = FakeRealsApi()
        val state = MutableStateFlow<RealsRootUiState>(
            ready(phase = MatchmakingSearchUiPhase.Idle, inQueue = false),
        )
        val coordinator = coordinator(api, state, this)
        primeLastSearchLocation(coordinator, api)
        api.queueResponse = backendErrorResponse(500, "UNKNOWN_REENTER_FAILURE")
        api.homeResponse = Response.success(
            TestDtos.home().copy(
                matchmaking = HomeMatchmakingResponseDto(
                    inQueue = false,
                    canSearch = true,
                    blockedReason = null,
                ),
                pendingActions = emptyList(),
                nextSteps = emptyList(),
            )
        )

        coordinator.reenterMatchmakingOrLoadHome(TestDomain.session())

        val ready = state.value as RealsRootUiState.Ready
        assertEquals(listOf("enqueueMatchmaking", "getHome"), api.calls.takeLast(2))
        assertEquals(
            "Aprobaste el chat. No pudimos volver a iniciar la búsqueda automáticamente.",
            ready.home.homeMessage,
        )
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
                getHomeStatus = GetHomeStatusUseCase(meRepository),
                getHomePending = GetHomePendingUseCase(meRepository),
                leaveQueue = LeaveQueueUseCase(matchmakingRepository),
                dismissSecondChat = DismissSecondChatForConnectionUseCase(chatRepository),
            ),
            scope = scope,
            onOpenFirstChat = { _, _, _ -> },
            onOpenSecondChat = { _, _, _, _ -> },
            onReloadActiveSession = { _ -> },
        )
    }

    private suspend fun primeLastSearchLocation(
        coordinator: HomeCoordinator,
        api: FakeRealsApi,
    ) {
        api.queueResponse = Response.success(TestDtos.queueStatus(inQueue = true))
        api.homeResponse = Response.success(
            TestDtos.home().copy(
                matchmaking = HomeMatchmakingResponseDto(
                    inQueue = true,
                    canSearch = false,
                    blockedReason = null,
                ),
                pendingActions = emptyList(),
                nextSteps = emptyList(),
            )
        )
        coordinator.enqueueMatchmaking(searchLocation())
        yield()
        api.beforeGetHomeResponse = {}
    }

    private fun com.reals.app.core.network.ApiError.backendCode(): String? =
        (this as? com.reals.app.core.network.ApiError.Backend)?.code

    private fun searchLocation(): SearchLocationInput =
        SearchLocationInput(latitude = -34.6037, longitude = -58.3816, accuracyMeters = 50)

    private fun ready(
        phase: MatchmakingSearchUiPhase,
        inQueue: Boolean,
        homeStatusVersion: Long? = null,
    ): RealsRootUiState.Ready {
        val home = homeState(inQueue)
        return RealsRootUiState.Ready(
            session = TestDomain.session(),
            home = HomeUiState(
                homeState = home,
                homeStatusVersion = homeStatusVersion,
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
            hasPendingSchedulingConnection = false,
            actionableConnectionCount = 0,
        ),
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )
}
