package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testJson
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelReactionTest {
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
    fun `first chat valid tap marks pending immediately without global loading`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        runCurrent()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals(setOf("incoming"), state.reaction.pendingMessageIds)
        assertFalse(state.sending)
        assertFalse(state.actionLoading)
    }

    @Test
    fun `second chat valid tap marks pending immediately without global loading`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToSecondChatMessage("incoming"))
        runCurrent()

        val state = viewModel.uiState.value as RealsRootUiState.SecondChat
        assertEquals(setOf("incoming"), state.reaction.pendingMessageIds)
        assertFalse(state.sending)
        assertFalse(state.actionLoading)
    }

    @Test
    fun `two different first chat reactions complete independently in reverse order`() = runTest(dispatcher) {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePutChatMessageReactionResponse = {
                if (lastPathId == "chat-1/a") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
            }
            chatMessageReactionResponse = Response.success(TestDtos.chatMessage("b", reactionType = "HEART"))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(
            message("a", senderId = "other"),
            message("b", "2026-06-18T21:01:00Z", senderId = "other"),
        )))

        assertTrue(viewModel.reactToFirstChatMessage("a"))
        firstStarted.await()
        assertTrue(viewModel.reactToFirstChatMessage("b"))
        advanceUntilIdle()
        api.chatMessageReactionResponse = Response.success(TestDtos.chatMessage("a", reactionType = "HEART"))
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertTrue(state.reaction.pendingMessageIds.isEmpty())
        assertEquals(ChatMessageReactionType.Heart, state.messages.single { it.id == "a" }.reactionType)
        assertEquals(ChatMessageReactionType.Heart, state.messages.single { it.id == "b" }.reactionType)
        assertEquals(2, api.calls.count { it == "putChatMessageReaction" })
    }

    @Test
    fun `duplicate tap on same pending message is ignored`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePutChatMessageReactionResponse = { release.await() }
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        runCurrent()
        assertFalse(viewModel.reactToFirstChatMessage("incoming"))
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, api.calls.count { it == "putChatMessageReaction" })
    }

    @Test
    fun `canonical success merges heart and clears only that pending id`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            chatMessageReactionResponse = Response.success(TestDtos.chatMessage("a", reactionType = "HEART"))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(
            message("a", senderId = "other"),
            message("b", "2026-06-18T21:01:00Z", senderId = "other"),
        )).copy(reaction = ChatReactionUiState(setOf("b"))))

        assertTrue(viewModel.reactToFirstChatMessage("a"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals(setOf("b"), state.reaction.pendingMessageIds)
        assertEquals(ChatMessageReactionType.Heart, state.messages.single { it.id == "a" }.reactionType)
    }

    @Test
    fun `failure clears pending silently without chat error or message`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            chatMessageReactionResponse = backendErrorResponse(409, "DOMAIN_CONFLICT")
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertTrue(state.reaction.pendingMessageIds.isEmpty())
        assertEquals(null, state.error)
        assertEquals(null, state.message)
    }

    @Test
    fun `reaction not available clears pending and runs reaction reconciliation`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            chatMessageReactionResponse = backendErrorResponse(409, "CHAT_MESSAGE_REACTION_NOT_AVAILABLE")
            chatMessagesResponse = chatMessagesPayload(listOf(
                TestDtos.chatMessage("incoming", reactionType = "HEART"),
            ))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertTrue(state.reaction.pendingMessageIds.isEmpty())
        assertEquals(ChatMessageReactionType.Heart, state.messages.single { it.id == "incoming" }.reactionType)
        assertTrue(api.calls.count { it == "getChatMessages" } >= 1)
    }

    @Test
    fun `pending heart survives stale first chat silent poll`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            beforePutChatMessageReactionResponse = { CompletableDeferred<Unit>().await() }
            chatMessagesResponse = chatMessagesPayload(listOf(TestDtos.chatMessage("incoming")))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        runCurrent()
        viewModel.refreshFirstChat(silent = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals(setOf("incoming"), state.reaction.pendingMessageIds)
        assertEquals(null, state.messages.single { it.id == "incoming" }.reactionType)
    }

    @Test
    fun `stale poll null after reaction success cannot erase heart`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            chatMessageReactionResponse = Response.success(TestDtos.chatMessage("incoming", reactionType = "HEART"))
            chatMessagesResponse = chatMessagesPayload(listOf(TestDtos.chatMessage("incoming")))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        advanceUntilIdle()
        viewModel.refreshFirstChat(silent = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals(ChatMessageReactionType.Heart, state.messages.single { it.id == "incoming" }.reactionType)
    }

    @Test
    fun `poll heart before put failure leaves canonical heart visible`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePutChatMessageReactionResponse = { release.await() }
            chatMessageReactionResponse = backendErrorResponse(500, "SERVER_ERROR")
            chatMessagesResponse = chatMessagesPayload(listOf(TestDtos.chatMessage("incoming", reactionType = "HEART")))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        runCurrent()
        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertTrue(state.reaction.pendingMessageIds.isEmpty())
        assertEquals(ChatMessageReactionType.Heart, state.messages.single { it.id == "incoming" }.reactionType)
    }

    @Test
    fun `late first chat reaction result is ignored after navigation away`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePutChatMessageReactionResponse = { release.await() }
            chatMessageReactionResponse = Response.success(TestDtos.chatMessage("incoming", reactionType = "HEART"))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToFirstChatMessage("incoming"))
        runCurrent()
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
    }

    @Test
    fun `second chat stale refresh cannot erase successful heart`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            chatMessageReactionResponse = Response.success(TestDtos.chatMessage("incoming", reactionType = "HEART"))
            chatMessagesResponse = chatMessagesPayload(listOf(TestDtos.chatMessage("incoming")))
        }
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState(messages = listOf(message("incoming", senderId = "other"))))

        assertTrue(viewModel.reactToSecondChatMessage("incoming"))
        advanceUntilIdle()
        viewModel.refreshSecondChat(silent = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.SecondChat
        assertEquals(ChatMessageReactionType.Heart, state.messages.single { it.id == "incoming" }.reactionType)
    }

    @Test
    fun `own outgoing message after partner block does not prevent reaction`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState(messages = listOf(
            message("a", senderId = "other"),
            message("b", "2026-06-18T21:01:00Z", senderId = "other"),
            message("c", "2026-06-18T21:02:00Z", senderId = "user-1"),
        )))

        assertTrue(viewModel.reactToFirstChatMessage("a"))
    }

    @Test
    fun `new partner block makes old unreacted message ignored`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRealsApi())
        viewModel.setState(firstChatState(messages = listOf(
            message("a", senderId = "other"),
            message("b", "2026-06-18T21:01:00Z", senderId = "user-1"),
            message("c", "2026-06-18T21:02:00Z", senderId = "other"),
        )))

        assertFalse(viewModel.reactToFirstChatMessage("a"))
        assertTrue(viewModel.reactToFirstChatMessage("c"))
    }

    private fun viewModel(api: FakeRealsApi): RealsRootViewModel =
        RealsRootViewModel(rootViewModelTestDependencies(api), autoRefreshSession = false)

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    private fun firstChatState(messages: List<ChatMessage>): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = "chat-1",
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = TestDtos.chat(status = "ACTIVE").copy(id = "chat-1").toDomain(),
            messages = messages,
        )

    private fun secondChatState(messages: List<ChatMessage>): RealsRootUiState.SecondChat =
        RealsRootUiState.SecondChat(
            session = TestDomain.session(),
            connectionId = "connection-1",
            matchId = "match-1",
            partnerName = "Alex",
            chatId = "chat-1",
            chat = TestDtos.chat(status = "ACTIVE").copy(id = "chat-1", chatType = "SECOND_CHAT").toDomain(),
            messages = messages,
            lifecycle = SecondChatLifecycleUiState(
                status = TestDtos.secondChatStatus(serverTime = "2026-06-18T21:00:00Z").toDomain(),
                statusReceivedAtMillis = 0L,
            ),
        )

    private fun message(
        id: String,
        sentAt: String = "2026-06-18T21:00:00Z",
        senderId: String,
    ): ChatMessage = ChatMessage(
        id = id,
        chatSessionId = "chat-1",
        senderId = senderId,
        content = "hola",
        sentAt = sentAt,
    )

    private fun chatMessagesPayload(messages: List<com.reals.app.data.dto.ChatMessageResponseDto>): Response<JsonElement> =
        Response.success(testJson.encodeToJsonElement(messages))
}
