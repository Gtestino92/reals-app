package com.reals.app.ui.root

import com.reals.app.core.time.ServerClockSnapshot
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.preferences.InMemoryFirstChatUnansweredSuggestionDismissalStore
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.chat.firstChatUnansweredSuggestionState
import java.time.Instant
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
class FirstChatAsyncResultReconciliationTest {
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
    fun `silent refresh cannot erase dismissed unanswered suggestion period`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val store = InMemoryFirstChatUnansweredSuggestionDismissalStore()
        val api = FakeRealsApi().apply {
            exitRequestsResponse = Response.success(emptyList())
            chatMessagesResponse = Response.success(TestDtos.chatMessagesArrayPayload(listOf(ownMessageDto())))
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api, store)
        viewModel.setState(eligibleFirstChatState())

        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        refreshStarted.await()

        val period = "started:2026-06-18T21:00:00Z"
        viewModel.dismissFirstChatUnansweredSuggestion(period)
        runCurrent()

        assertEquals(period, store.dismissedPeriod("user-1", "chat-1"))
        assertEquals(period, (viewModel.uiState.value as RealsRootUiState.FirstChat).dismissedUnansweredPeriodReference)

        gate.complete(Unit)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals(period, finalState.dismissedUnansweredPeriodReference)
        assertFalse(
            firstChatUnansweredSuggestionState(
                chat = finalState.chat,
                currentUserId = "user-1",
                confirmedMessages = finalState.messages,
                pendingExitRequest = finalState.exitRequests.firstOrNull {
                    it.status == ChatExitRequestStatus.Pending
                },
                estimatedServerNowMillis = millis("2026-06-18T21:03:30Z"),
                dismissedPeriodReference = finalState.dismissedUnansweredPeriodReference,
                mutualExitActionAvailable = true,
            ).visible
        )
    }

    @Test
    fun `text send completion cannot erase dismissed unanswered suggestion period`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val sendStarted = CompletableDeferred<Unit>()
        val store = InMemoryFirstChatUnansweredSuggestionDismissalStore()
        val api = FakeRealsApi().apply {
            exitRequestsResponse = Response.success(emptyList())
            chatMessageResponse = Response.success(ownMessageDto(id = "sent-1", sentAt = "2026-06-18T21:04:00Z"))
            chatMessagesResponse = Response.success(TestDtos.chatMessagesArrayPayload(emptyList()))
            beforeSendChatMessageResponse = {
                sendStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api, store)
        viewModel.setState(eligibleFirstChatState())

        assertTrue(viewModel.sendFirstChatMessage("hola"))
        runCurrent()
        sendStarted.await()
        assertTrue((viewModel.uiState.value as RealsRootUiState.FirstChat).sending)

        val period = "started:2026-06-18T21:00:00Z"
        viewModel.dismissFirstChatUnansweredSuggestion(period)
        runCurrent()

        gate.complete(Unit)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals(period, store.dismissedPeriod("user-1", "chat-1"))
        assertEquals(period, finalState.dismissedUnansweredPeriodReference)
        assertFalse(finalState.sending)
        assertTrue(finalState.optimisticMessages.isEmpty())
        assertTrue(finalState.messages.any { it.id == "sent-1" })
    }

    @Test
    fun `older refresh result cannot regress newer displayed chat and server clock`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            chatResponse = Response.success(chatDto(serverTime = S2, lastMessageAt = S2))
            exitRequestsResponse = Response.success(emptyList())
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(serverTime = S1, lastMessageAt = S1))

        viewModel.refreshFirstChat(silent = false)
        runCurrent()
        refreshStarted.await()

        viewModel.setState(firstChatState(serverTime = S3, lastMessageAt = S3).copy(refreshing = true))

        gate.complete(Unit)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertFalse(finalState.refreshing)
        assertEquals(S3, finalState.chat?.lastMessageAt)
        assertEquals(millis(S3), finalState.serverClockSnapshot?.serverTimeEpochMillis)
    }

    @Test
    fun `newer refresh result installs returned chat and server clock while preserving dismissal`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            chatResponse = Response.success(chatDto(serverTime = S3, lastMessageAt = S3))
            exitRequestsResponse = Response.success(emptyList())
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(serverTime = S1, lastMessageAt = S1))

        viewModel.refreshFirstChat(silent = false)
        runCurrent()
        refreshStarted.await()

        val period = "started:2026-06-18T21:00:00Z"
        viewModel.setState(firstChatState(serverTime = S1, lastMessageAt = S1).copy(
            refreshing = true,
            dismissedUnansweredPeriodReference = period,
        ))

        gate.complete(Unit)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertFalse(finalState.refreshing)
        assertEquals(S3, finalState.chat?.lastMessageAt)
        assertEquals(millis(S3), finalState.serverClockSnapshot?.serverTimeEpochMillis)
        assertEquals(period, finalState.dismissedUnansweredPeriodReference)
    }

    private fun viewModel(
        api: FakeRealsApi,
        store: InMemoryFirstChatUnansweredSuggestionDismissalStore =
            InMemoryFirstChatUnansweredSuggestionDismissalStore(),
    ): RealsRootViewModel =
        RealsRootViewModel(rootViewModelTestDependencies(api, store), autoRefreshSession = false)

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    private fun eligibleFirstChatState(): RealsRootUiState.FirstChat =
        firstChatState(serverTime = "2026-06-18T21:03:30Z", lastMessageAt = "2026-06-18T21:01:00Z").copy(
            messages = listOf(ownMessageDto(sentAt = "2026-06-18T21:01:00Z").toDomain()),
        )

    private fun firstChatState(
        serverTime: String,
        lastMessageAt: String,
    ): RealsRootUiState.FirstChat {
        val chat = chatDto(serverTime = serverTime, lastMessageAt = lastMessageAt).toDomain()
        return RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = chat.id,
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = chat,
            serverClockSnapshot = ServerClockSnapshot(
                serverTimeEpochMillis = millis(serverTime),
                receivedAtElapsedRealtimeMillis = 0L,
            ),
        )
    }

    private fun chatDto(
        serverTime: String,
        lastMessageAt: String,
    ) = TestDtos.chat(serverTime = serverTime).copy(lastMessageAt = lastMessageAt)

    private fun ownMessageDto(
        id: String = "own-1",
        sentAt: String = "2026-06-18T21:01:00Z",
    ) = TestDtos.chatMessage(id).copy(sentAt = sentAt)

    private fun millis(value: String): Long = Instant.parse(value).toEpochMilli()

    private companion object {
        const val S1 = "2026-06-18T21:01:00Z"
        const val S2 = "2026-06-18T21:02:00Z"
        const val S3 = "2026-06-18T21:03:00Z"
    }
}
