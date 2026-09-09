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
    fun `match found notification routes through authoritative Home pending first chat`() = runTest(dispatcher) {
        val home = queuedHomeWithPendingFirstChat()
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(home)
            chatResponse = Response.success(
                TestDtos.chat().copy(
                    id = "chat-first",
                    matchId = "match-first",
                )
            )
        }
        val viewModel = viewModel(api)
        viewModel.setState(readyWithHome(TestDtos.home().copy(pendingActions = emptyList())))

        viewModel.handleExternalNotificationOpened(TYPE_MATCH_FOUND)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RealsRootUiState.FirstChat)
        state as RealsRootUiState.FirstChat
        assertEquals("match-first", state.matchId)
        assertEquals("chat-first", state.chatId)
        assertTrue(api.calls.contains("getHome"))
        assertTrue(api.calls.contains("getFirstChatForMatch"))
        assertFalse(api.calls.contains("getSecondChatForConnection"))
        assertFalse(api.calls.contains("joinSecondChat"))
    }

    @Test
    fun `second chat started from Ready with queued first chat routes to mandatory first chat`() =
        runTest(dispatcher) {
            val home = queuedHomeWithPendingFirstChat()
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(home)
                chatResponse = Response.success(
                    TestDtos.chat().copy(
                        id = "chat-first",
                        matchId = "match-first",
                    )
                )
            }
            val viewModel = viewModel(api)
            viewModel.setState(readyWithHome(home))

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
            assertTrue(api.calls.contains("getHome"))
            assertTrue(api.calls.contains("getFirstChatForMatch"))
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
    fun `second chat started is ignored while joined active second chat owns foreground`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState())

        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.SecondChat)
        assertTrue(api.calls.isEmpty())
        assertFalse(api.calls.contains("getSecondChatForConnection"))
        assertFalse(api.calls.contains("joinSecondChat"))
    }

    @Test
    fun `notification open is ignored while account is suspended`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        val suspended = RealsRootUiState.AccountSuspended(
            AccountSuspension.Temporary("2026-09-02T01:30:00Z")
        )
        viewModel.setState(suspended)

        viewModel.handleExternalNotificationOpened(TYPE_MATCH_FOUND)
        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
        advanceUntilIdle()

        assertEquals(suspended, viewModel.uiState.value)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `second chat started in Checking follows mandatory Home first-chat authority after session load`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(queuedHomeWithPendingFirstChat())
            }
            val viewModel = viewModel(api, authRepository = SignedInFirebaseAuthRepository())

            viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
            assertTrue(api.calls.contains("getHome"))
            assertTrue(api.calls.contains("getFirstChatForMatch"))

            viewModel.refreshSession()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
            assertEquals(2, api.calls.count { it == "getFirstChatForMatch" })
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `second chat started in LoadingSession reuses active session load then follows Home first-chat authority`() =
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

            assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
            assertTrue(api.calls.contains("getFirstChatForMatch"))
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `second chat started while legal requirements are visible waits and then follows Home first-chat authority`() =
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

            assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
            assertTrue(api.calls.contains("getHome"))
            assertTrue(api.calls.contains("getFirstChatForMatch"))
            assertFalse(api.calls.contains("joinSecondChat"))
        }

    @Test
    fun `generic notification is ignored while joined active second chat owns foreground`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState())

        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_REMINDER)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.SecondChat)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `notifications are ignored while unresolved first chat owns foreground`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        val firstChat = firstChatState(
            chat = TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "PENDING",
            ).toDomain(),
        )
        viewModel.setState(firstChat)

        viewModel.handleExternalNotificationOpened(TYPE_MATCH_FOUND)
        advanceUntilIdle()
        assertEquals(firstChat, viewModel.uiState.value)

        viewModel.handleExternalNotificationOpened(TYPE_SECOND_CHAT_STARTED)
        advanceUntilIdle()
        assertEquals(firstChat, viewModel.uiState.value)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `already approved first chat does not own notification foreground`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(TestDtos.home().copy(pendingActions = emptyList(), nextSteps = emptyList()))
        }
        val viewModel = viewModel(api)
        viewModel.setState(
            firstChatState(
                chat = TestDtos.chat(
                    myDecision = "APPROVED",
                    partnerDecision = "PENDING",
                ).toDomain(),
            )
        )

        viewModel.handleExternalNotificationOpened(TYPE_MATCH_FOUND)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(listOf("getHome"), api.calls)
    }

    @Test
    fun `non-actionable first chat and pending engagement return Home`() = runTest(dispatcher) {
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

    private fun firstChatState(
        chat: com.reals.app.domain.model.Chat?,
    ): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = chat?.matchId ?: "match-1",
            chatId = chat?.id ?: "chat-1",
            chat = chat,
            match = TestDtos.match().toDomain(),
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
