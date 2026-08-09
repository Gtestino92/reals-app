package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.matchmaking.HomeUiMapper
import com.reals.app.ui.matchmaking.LocalHiddenInteractions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelExternalNotificationTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `match found notification open refreshes Home without trusting push state`() = runTest(dispatcher) {
        val home = TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList())
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(home)
        }
        val viewModel = viewModel(api)
        viewModel.setState(readyWithHome(home))

        viewModel.handleExternalNotificationOpened(TYPE_MATCH_FOUND)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(listOf("getHome"), api.calls)
        assertFalse(api.calls.contains("getFirstChatForMatch"))
        assertFalse(api.calls.contains("getSecondChatForConnection"))
        assertFalse(api.calls.contains("joinSecondChat"))
    }

    @Test
    fun `second chat started from Ready with queued first chat refreshes Home without opening first chat`() =
        runTest(dispatcher) {
            val home = queuedHomeWithPendingFirstChat()
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(home)
            }
            val viewModel = viewModel(api)
            viewModel.setState(readyWithHome(home))

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
            assertEquals(listOf("getHome"), api.calls)
            assertFalse(api.calls.contains("getFirstChatForMatch"))
            assertFalse(api.calls.contains("getSecondChatForConnection"))
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `second chat started from Ready with queued active second chat refreshes Home without opening second chat`() =
        runTest(dispatcher) {
            val home = queuedHomeWithActiveSecondChat()
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(home)
            }
            val viewModel = viewModel(api)
            viewModel.setState(readyWithHome(home))

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
            assertEquals(listOf("getHome"), api.calls)
            assertFalse(api.calls.contains("getFirstChatForMatch"))
            assertFalse(api.calls.contains("getSecondChatForConnection"))
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `second chat started returns joined active second chat to Home and refreshes Home only`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState())

        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(listOf("getHome"), api.calls)
        assertFalse(api.calls.contains("getSecondChatForConnection"))
        assertFalse(api.calls.contains("joinSecondChat"))
    }

    @Test
    fun `second chat started in Checking survives session loading and disables Home auto navigation once`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(queuedHomeWithPendingFirstChat())
            }
            val viewModel = viewModel(api, authRepository = SignedInFirebaseAuthRepository())

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
            assertTrue(api.calls.contains("getHome"))
            assertFalse(api.calls.contains("getFirstChatForMatch"))

            viewModel.refreshSession()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
            assertEquals(1, api.calls.count { it == "getFirstChatForMatch" })
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `second chat started in LoadingSession reuses active session load and disables Home auto navigation`() =
        runTest(dispatcher) {
            val homeStarted = CompletableDeferred<Unit>()
            val releaseHome = CompletableDeferred<Unit>()
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(queuedHomeWithPendingFirstChat())
                beforeGetHomeResponse = {
                    homeStarted.complete(Unit)
                    releaseHome.await()
                }
            }
            val viewModel = viewModel(api, authRepository = SignedInFirebaseAuthRepository())
            viewModel.setState(RealsRootUiState.LoadingSession("user@example.com"))

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            runCurrent()
            homeStarted.await()

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            runCurrent()

            assertEquals(1, api.calls.count { it == "getMe" })
            assertEquals(1, api.calls.count { it == "getHome" })

            releaseHome.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
            assertFalse(api.calls.contains("getFirstChatForMatch"))
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `second chat started while legal requirements are visible waits and then loads Home only`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(queuedHomeWithPendingFirstChat())
            }
            val viewModel = viewModel(api)
            val legal = RealsRootUiState.LegalRequirements(
                session = TestDomain.session(),
                resumeContext = LegalResumeContext.PostSession,
                documents = emptyList(),
            )
            viewModel.setState(legal)

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            advanceUntilIdle()

            assertEquals(legal, viewModel.uiState.value)

            viewModel.deferLegalRequirements()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
            assertEquals(listOf("getHome"), api.calls)
            assertFalse(api.calls.contains("getFirstChatForMatch"))
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `second chat reminder preserves existing joined second chat refresh behavior`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            secondChatStatusResponse = Response.success(activeSecondChatStatusDto())
        }
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState())

        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_REMINDER)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.SecondChat)
        assertTrue(api.calls.contains("getSecondChatStatus"))
        assertFalse(api.calls.contains("getHome"))
        assertFalse(api.calls.contains("joinSecondChat"))
    }

    @Test
    fun `second chat started from first chat and pending engagement returns Home`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val viewModel = viewModel(api)

        viewModel.setState(
            RealsRootUiState.FirstChat(
                session = TestDomain.session(),
                matchId = "match-1",
                chatId = "chat-1",
            ),
        )
        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)

        viewModel.setState(
            RealsRootUiState.PendingEngagement(
                session = TestDomain.session(),
                title = "Pendiente",
                body = "Actualizá Home.",
            ),
        )
        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(2, api.calls.count { it == "getHome" })
    }

    @Test
    fun `unknown external notification type is ignored`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        val ready = RealsRootUiState.Ready(TestDomain.session())
        viewModel.setState(ready)

        viewModel.handleExternalNotificationOpened("UNKNOWN")
        advanceUntilIdle()

        assertEquals(ready, viewModel.uiState.value)
        assertTrue(api.calls.isEmpty())
    }

    private fun viewModel(
        api: FakeRealsApi,
        authRepository: FirebaseAuthRepository? = null,
    ): RealsRootViewModel =
        RealsRootViewModel(
            rootViewModelTestDependencies(
                api = api,
                authRepositoryOverride = authRepository,
            ),
            autoRefreshSession = false,
        )

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    private fun secondChatState(): RealsRootUiState.SecondChat =
        RealsRootUiState.SecondChat(
            session = TestDomain.session(),
            connectionId = "connection-1",
            matchId = "match-1",
            partnerName = "Alex",
            chatId = "chat-1",
            chat = TestDtos.chat(status = "ACTIVE").copy(id = "chat-1", chatType = "SECOND_CHAT").toDomain(),
            lifecycle = SecondChatLifecycleUiState(
                status = activeSecondChatStatusDto().toDomain(),
                statusReceivedAtMillis = System.currentTimeMillis(),
            ),
        )

    private fun activeSecondChatStatusDto() = TestDtos.secondChatStatus(
        chatStatus = "ACTIVE",
        chatId = "chat-1",
        serverTime = "2026-07-31T21:00:00Z",
        absoluteExpiresAt = "2026-08-01T21:00:00Z",
    )

    private fun queuedHomeWithPendingFirstChat() = TestDtos.home().copy(
        matchmaking = HomeMatchmakingResponseDto(
            inQueue = true,
            canSearch = false,
            blockedReason = null,
        ),
        pendingActions = listOf(
            HomePendingActionResponseDto(
                type = "FIRST_CHAT",
                matchId = "match-first",
                chatId = "chat-first",
                partner = TestDtos.partner("First"),
            ),
        ),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )

    private fun queuedHomeWithActiveSecondChat() = TestDtos.home().copy(
        matchmaking = HomeMatchmakingResponseDto(
            inQueue = true,
            canSearch = false,
            blockedReason = null,
        ),
        pendingActions = emptyList(),
        nextSteps = listOf(
            HomeNextStepResponseDto(
                type = "SECOND_CHAT_AVAILABLE",
                connectionId = "connection-active",
                matchId = "match-active",
                secondChat = HomeChatResponseDto(
                    chatId = "chat-active",
                    chatType = "SECOND_CHAT",
                    chatStatus = "ACTIVE",
                    availableAt = TestDtos.now,
                    expiresAt = "2026-08-01T21:00:00Z",
                    durationMinutes = 120,
                    partner = TestDtos.partner("Second"),
                ),
            ),
        ),
        passiveNotices = emptyList(),
    )

    private fun readyWithHome(homeDto: com.reals.app.data.dto.HomeResponseDto): RealsRootUiState.Ready {
        val home = homeDto.toDomain()
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
                matchmakingSearchPhase = MatchmakingSearchUiPhase.Searching,
            ),
        )
    }

    private class SignedInFirebaseAuthRepository : FirebaseAuthRepository(ContextWrapper(null)) {
        override fun isConfigured(): Boolean = true

        override fun hasSignedInUser(): Boolean = true

        override fun currentUserEmail(): String = "user@example.com"
    }
}
