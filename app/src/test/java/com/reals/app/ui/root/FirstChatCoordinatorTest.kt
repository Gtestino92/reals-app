package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.network.toUserMessage
import com.reals.app.core.time.ServerClockSnapshot
import com.reals.app.data.preferences.InMemoryFirstChatUnansweredSuggestionDismissalStore
import com.reals.app.data.dto.FirstChatGuidanceResponseDto
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetFirstChatForMatchUseCase
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.PutChatMessageReactionUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.RequestNextFirstChatGuidanceQuestionUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.domain.usecase.SubmitChatDecisionUseCase
import com.reals.app.domain.usecase.TimeoutChatExitRequestUseCase
import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import com.reals.app.ui.chat.firstChatInteractionPolicy
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class FirstChatCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = FirstChatCoordinator(firstChatDependencies(api))

    @Test
    fun `load success returns match chat messages and exit requests`() = runBlocking {
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesArrayPayload(listOf(TestDtos.chatMessage("message-load")))
        )
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "PENDING")))

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.Show)
        val state = (result as FirstChatLoadResult.Show).state
        assertEquals(false, state.loading)
        assertEquals("match-1", state.match?.id)
        assertEquals("chat-1", state.chat?.id)
        assertEquals("message-load", state.messages.single().id)
        assertEquals("exit-1", state.exitRequests.single().id)
        assertEquals(
            java.time.Instant.parse(TestDtos.now).toEpochMilli(),
            state.serverClockSnapshot?.serverTimeEpochMillis,
        )
        assertEquals(null, state.error)
    }

    @Test
    fun `load reads stored unanswered dismissal for user and chat`() = runBlocking {
        val store = InMemoryFirstChatUnansweredSuggestionDismissalStore()
        store.dismissPeriod("user-1", "chat-1", "started:2026-06-18T21:00:00Z")
        val coordinator = FirstChatCoordinator(firstChatDependencies(api, store))

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        val state = (result as FirstChatLoadResult.Show).state
        assertEquals("started:2026-06-18T21:00:00Z", state.dismissedUnansweredPeriodReference)
    }

    @Test
    fun `load match failure preserves error state`() = runBlocking {
        api.matchResponse = backendErrorResponse(
            statusCode = 500,
            code = "INTERNAL_ERROR",
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.Show)
        val state = (result as FirstChatLoadResult.Show).state
        val error = state.error as ApiError.Backend
        assertEquals(false, state.loading)
        assertEquals(null, state.match)
        assertEquals(null, state.chat)
        assertEquals(BackendErrorCode.Unknown, error.backendErrorCode)
    }

    @Test
    fun `load routes home when match is closed`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.RouteHome)
    }

    @Test
    fun `load chat failure preserves loaded match and error state`() = runBlocking {
        api.chatResponse = backendErrorResponse(
            statusCode = 404,
            code = "CHAT_NOT_FOUND",
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.Show)
        val state = (result as FirstChatLoadResult.Show).state
        val error = state.error as ApiError.Backend
        assertEquals(false, state.loading)
        assertEquals("match-1", state.match?.id)
        assertEquals(null, state.chat)
        assertEquals(BackendErrorCode.ChatNotFound, error.backendErrorCode)
    }

    @Test
    fun `load chat abandoned returns route home and not show`() = runBlocking {
        api.chatResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_ABANDONED",
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertEquals(
            FirstChatLoadResult.RouteHome("La conversación se cerró por inactividad."),
            result,
        )
        assertFalse(result is FirstChatLoadResult.Show)
    }

    @Test
    fun `load chat expired returns route home and not show`() = runBlocking {
        api.chatResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_EXPIRED",
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertEquals(
            FirstChatLoadResult.RouteHome("El chat venció."),
            result,
        )
        assertFalse(result is FirstChatLoadResult.Show)
    }

    @Test
    fun `load network chat failure remains recoverable partial state`() = runBlocking {
        api.beforeGetFirstChatForMatchResponse = {
            throw IOException("offline")
        }

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.Show)
        val state = (result as FirstChatLoadResult.Show).state
        assertEquals("match-1", state.match?.id)
        assertEquals(null, state.chat)
        assertTrue(state.error is ApiError.Network)
    }

    @Test
    fun `load generic backend chat failure remains recoverable partial state`() = runBlocking {
        api.chatResponse = backendErrorResponse(
            statusCode = 500,
            code = "SERVER_ERROR",
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.Show)
        val state = (result as FirstChatLoadResult.Show).state
        val error = state.error as ApiError.Backend
        assertEquals("match-1", state.match?.id)
        assertEquals(null, state.chat)
        assertEquals(BackendErrorCode.Unknown, error.backendErrorCode)
    }

    @Test
    fun `load uses messages failure before exit requests failure`() = runBlocking {
        api.chatMessagesResponse = backendErrorResponse(
            statusCode = 400,
            code = "CHAT_MESSAGE_INVALID",
        )
        api.exitRequestsResponse = backendErrorResponse(
            statusCode = 409,
            code = "ACTIVE_PENALTY",
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.Show)
        val state = (result as FirstChatLoadResult.Show).state
        val error = state.error as ApiError.Backend
        assertEquals(false, state.loading)
        assertEquals(BackendErrorCode.ChatMessageInvalid, error.backendErrorCode)
        assertTrue(state.messages.isEmpty())
        assertTrue(state.exitRequests.isEmpty())
    }

    @Test
    fun `load uses exit requests failure when messages succeed`() = runBlocking {
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesArrayPayload(listOf(TestDtos.chatMessage("message-load")))
        )
        api.exitRequestsResponse = backendErrorResponse(
            statusCode = 409,
            code = "ACTIVE_PENALTY",
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = null,
        )

        assertTrue(result is FirstChatLoadResult.Show)
        val state = (result as FirstChatLoadResult.Show).state
        val error = state.error as ApiError.Backend
        assertEquals(false, state.loading)
        assertEquals(BackendErrorCode.ActivePenalty, error.backendErrorCode)
        assertEquals("message-load", state.messages.single().id)
        assertTrue(state.exitRequests.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `load starts match and first chat requests concurrently`() = runTest {
        val matchStarted = CompletableDeferred<Unit>()
        val chatStarted = CompletableDeferred<Unit>()
        val releaseMatch = CompletableDeferred<Unit>()
        val releaseChat = CompletableDeferred<Unit>()
        api.beforeGetMatchResponse = {
            matchStarted.complete(Unit)
            releaseMatch.await()
        }
        api.beforeGetFirstChatForMatchResponse = {
            chatStarted.complete(Unit)
            releaseChat.await()
        }

        val load = async {
            coordinator.load(
                session = TestDomain.session(),
                matchId = "match-1",
                chatId = null,
            )
        }

        try {
            matchStarted.await()
            runCurrent()
            assertTrue(chatStarted.isCompleted)
        } finally {
            releaseMatch.complete(Unit)
            releaseChat.complete(Unit)
        }

        assertTrue(load.await() is FirstChatLoadResult.Show)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `load starts messages and exit requests concurrently`() = runTest {
        val messagesStarted = CompletableDeferred<Unit>()
        val exitRequestsStarted = CompletableDeferred<Unit>()
        val releaseMessages = CompletableDeferred<Unit>()
        val releaseExitRequests = CompletableDeferred<Unit>()
        api.beforeGetChatMessagesResponse = {
            messagesStarted.complete(Unit)
            releaseMessages.await()
        }
        api.beforeGetChatExitRequestsResponse = {
            exitRequestsStarted.complete(Unit)
            releaseExitRequests.await()
        }

        val load = async {
            coordinator.load(
                session = TestDomain.session(),
                matchId = "match-1",
                chatId = null,
            )
        }

        try {
            messagesStarted.await()
            runCurrent()
            assertTrue(exitRequestsStarted.isCompleted)
        } finally {
            releaseMessages.complete(Unit)
            releaseExitRequests.complete(Unit)
        }

        assertTrue(load.await() is FirstChatLoadResult.Show)
    }

    @Test
    fun `refresh with closed chat returns Closed`() = runBlocking {
        api.chatResponse = Response.success(TestDtos.chat(status = "CLOSED"))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.refresh(current, silent = false)

        assertTrue(result is FirstChatRefreshResult.Closed)
    }

    @Test
    fun `refresh success installs chat and server clock together`() = runBlocking {
        val current = firstChatState().copy(
            serverClockSnapshot = ServerClockSnapshot(1_000L, 0L),
        )
        api.chatResponse = Response.success(TestDtos.chat(serverTime = "2026-06-18T21:02:00Z"))

        val result = coordinator.refresh(current, silent = false)

        val state = (result as FirstChatRefreshResult.Show).state
        assertEquals("chat-1", state.chat?.id)
        assertEquals(java.time.Instant.parse("2026-06-18T21:02:00Z").toEpochMilli(), state.serverClockSnapshot?.serverTimeEpochMillis)
    }

    @Test
    fun `failed refresh preserves previous server clock`() = runBlocking {
        val previousClock = ServerClockSnapshot(1_000L, 0L)
        api.chatResponse = backendErrorResponse(500, "INTERNAL_ERROR")

        val result = coordinator.refresh(
            firstChatState().copy(serverClockSnapshot = previousClock),
            silent = false,
        )

        val state = (result as FirstChatRefreshResult.Show).state
        assertEquals(previousClock, state.serverClockSnapshot)
    }

    @Test
    fun `older first chat snapshot does not regress installed clock`() = runBlocking {
        val current = firstChatState().copy(
            serverClockSnapshot = ServerClockSnapshot(
                serverTimeEpochMillis = java.time.Instant.parse("2026-06-18T21:05:00Z").toEpochMilli(),
                receivedAtElapsedRealtimeMillis = 0L,
            ),
        )
        api.chatResponse = Response.success(TestDtos.chat(serverTime = "2026-06-18T21:04:00Z"))

        val result = coordinator.refresh(current, silent = false)

        val state = (result as FirstChatRefreshResult.Show).state
        assertEquals(current.serverClockSnapshot, state.serverClockSnapshot)
    }

    @Test
    fun `refresh chat abandoned returns Closed without stale chat`() = runBlocking {
        api.chatResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_ABANDONED",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.refresh(current, silent = false)

        assertEquals(
            FirstChatRefreshResult.Closed(
                matchState = current.match?.state,
                chatStatus = ChatStatus.Abandoned,
            ),
            result,
        )
        assertFalse(result is FirstChatRefreshResult.Show)
    }

    @Test
    fun `refresh chat expired returns Closed without stale chat`() = runBlocking {
        api.chatResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_EXPIRED",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.refresh(current, silent = false)

        assertEquals(
            FirstChatRefreshResult.Closed(
                matchState = current.match?.state,
                chatStatus = ChatStatus.Expired,
            ),
            result,
        )
        assertFalse(result is FirstChatRefreshResult.Show)
    }

    @Test
    fun `refresh recoverable chat failure keeps current chat and exposes error`() = runBlocking {
        api.chatResponse = backendErrorResponse(
            statusCode = 500,
            code = "SERVER_ERROR",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.refresh(current, silent = false)

        assertTrue(result is FirstChatRefreshResult.Show)
        val state = (result as FirstChatRefreshResult.Show).state
        assertEquals(current.chat, state.chat)
        assertEquals(false, state.refreshing)
        assertTrue(state.error is ApiError.Backend)
    }

    @Test
    fun `silent refresh alternates incremental and reaction reconciliation cursors`() = runBlocking {
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(messages = pollingMessages())
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesPagedPayload(emptyList()))

        coordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        assertEquals("d", api.lastChatMessagesAfter)

        coordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        assertEquals("b", api.lastChatMessagesAfter)

        coordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        assertEquals("d", api.lastChatMessagesAfter)
    }

    @Test
    fun `silent refresh alternation resets after first chat load reopens chat`() = runBlocking {
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(messages = pollingMessages())
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesPagedPayload(emptyList()))

        coordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        coordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        coordinator.load(TestDomain.session(), matchId = "match-1", chatId = "chat-1")
        coordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)

        assertEquals("d", api.lastChatMessagesAfter)
    }

    @Test
    fun `refresh with resolved exit request returns ExitResolved`() = runBlocking {
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "TIMED_OUT")))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.refresh(current, silent = false)

        assertEquals(
            FirstChatRefreshResult.ExitResolved("La solicitud de salida venció."),
            result,
        )
    }

    @Test
    fun `refresh prefers accepted mutual exit message over rejected match state`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match(state = "CHAT_REJECTED"))
        api.chatResponse = Response.success(TestDtos.chat(status = "CANCELLED"))
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "ACCEPTED")))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.refresh(current, silent = false)

        assertEquals(
            FirstChatRefreshResult.ExitResolved("La otra persona aceptó la salida consensuada."),
            result,
        )
    }

    @Test
    fun `active chat with pending exit request remains active`() = runBlocking {
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "PENDING")))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.refresh(current, silent = false)

        assertTrue(result is FirstChatRefreshResult.Show)
        assertEquals(ChatExitRequestStatus.Pending, (result as FirstChatRefreshResult.Show).state.exitRequests.single().status)
    }

    @Test
    fun `sendMessage appends sent message and refreshes messages`() = runBlocking {
        api.chatResponse = Response.success(TestDtos.chat(serverTime = "2026-06-18T21:02:00Z"))
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            dismissedUnansweredPeriodReference = "started:2026-06-18T21:00:00Z",
        )

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")
        assertTrue(result is FirstChatSendResult.Show)
        val state = (result as FirstChatSendResult.Show).state

        assertEquals(false, state.sending)
        assertTrue(state.messages.any { it.id == "message-1" })
        assertEquals("started:2026-06-18T21:00:00Z", state.dismissedUnansweredPeriodReference)
        assertEquals(java.time.Instant.parse("2026-06-18T21:02:00Z").toEpochMilli(), state.serverClockSnapshot?.serverTimeEpochMillis)
        assertEquals("sendChatMessage", api.calls.first())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `sendMessage acknowledges persisted message before post send refresh completes`() = runTest {
        val releaseMessagesRefresh = CompletableDeferred<Unit>()
        val releaseSnapshotRefresh = CompletableDeferred<Unit>()
        val messagesRefreshStarted = CompletableDeferred<Unit>()
        val snapshotRefreshStarted = CompletableDeferred<Unit>()
        val acknowledged = CompletableDeferred<ChatMessage>()
        api.chatMessageResponse = Response.success(TestDtos.chatMessage(id = "message-post"))
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesArrayPayload(emptyList()))
        api.beforeGetChatMessagesResponse = {
            messagesRefreshStarted.complete(Unit)
            releaseMessagesRefresh.await()
        }
        api.beforeGetFirstChatForMatchResponse = {
            snapshotRefreshStarted.complete(Unit)
            releaseSnapshotRefresh.await()
        }
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-1",
            createdAtMillis = 123L,
        )
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            optimisticMessages = listOf(optimistic),
            sending = true,
        )

        val send = async {
            coordinator.sendMessage(
                current = current,
                cleanContent = "hola",
                localId = "local-1",
                onPostAcknowledged = { acknowledged.complete(it) },
            )
        }
        runCurrent()

        val acknowledgedState = acknowledged.await()
        messagesRefreshStarted.await()
        snapshotRefreshStarted.await()
        assertEquals("sendChatMessage", api.calls.first())
        assertTrue(api.calls.contains("getChatMessages"))
        assertTrue(api.calls.contains("getFirstChatForMatch"))
        assertEquals("message-post", acknowledgedState.id)
        assertFalse(send.isCompleted)

        releaseMessagesRefresh.complete(Unit)
        releaseSnapshotRefresh.complete(Unit)
        val final = send.await() as FirstChatSendResult.Show
        assertFalse(final.state.sending)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `sendMessage starts post send reconciliation reads concurrently`() = runTest {
        val messagesRefreshStarted = CompletableDeferred<Unit>()
        val snapshotRefreshStarted = CompletableDeferred<Unit>()
        val releaseMessagesRefresh = CompletableDeferred<Unit>()
        val releaseSnapshotRefresh = CompletableDeferred<Unit>()
        api.beforeGetChatMessagesResponse = {
            messagesRefreshStarted.complete(Unit)
            releaseMessagesRefresh.await()
        }
        api.beforeGetFirstChatForMatchResponse = {
            snapshotRefreshStarted.complete(Unit)
            releaseSnapshotRefresh.await()
        }
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(sending = true)

        val send = async {
            coordinator.sendMessage(current, "hola", localId = "local-1")
        }
        runCurrent()

        messagesRefreshStarted.await()
        snapshotRefreshStarted.await()
        assertFalse(send.isCompleted)

        releaseMessagesRefresh.complete(Unit)
        releaseSnapshotRefresh.complete(Unit)
        val state = (send.await() as FirstChatSendResult.Show).state
        assertFalse(state.sending)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `sendMessage waits for snapshot when messages refresh finishes first`() = runTest {
        val messagesRefreshStarted = CompletableDeferred<Unit>()
        val snapshotRefreshStarted = CompletableDeferred<Unit>()
        val releaseMessagesRefresh = CompletableDeferred<Unit>()
        val releaseSnapshotRefresh = CompletableDeferred<Unit>()
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesArrayPayload(listOf(TestDtos.chatMessage(id = "message-refresh")))
        )
        api.chatResponse = Response.success(TestDtos.chat(serverTime = "2026-06-18T21:02:00Z"))
        api.beforeGetChatMessagesResponse = {
            messagesRefreshStarted.complete(Unit)
            releaseMessagesRefresh.await()
        }
        api.beforeGetFirstChatForMatchResponse = {
            snapshotRefreshStarted.complete(Unit)
            releaseSnapshotRefresh.await()
        }
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(sending = true)

        val send = async {
            coordinator.sendMessage(current, "hola", localId = "local-1")
        }
        runCurrent()
        messagesRefreshStarted.await()
        snapshotRefreshStarted.await()

        releaseMessagesRefresh.complete(Unit)
        runCurrent()
        assertFalse(send.isCompleted)

        releaseSnapshotRefresh.complete(Unit)
        val state = (send.await() as FirstChatSendResult.Show).state
        assertFalse(state.sending)
        assertTrue(state.messages.any { it.id == "message-refresh" })
        assertEquals(java.time.Instant.parse("2026-06-18T21:02:00Z").toEpochMilli(), state.serverClockSnapshot?.serverTimeEpochMillis)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `sendMessage waits for messages when snapshot refresh finishes first`() = runTest {
        val messagesRefreshStarted = CompletableDeferred<Unit>()
        val snapshotRefreshStarted = CompletableDeferred<Unit>()
        val releaseMessagesRefresh = CompletableDeferred<Unit>()
        val releaseSnapshotRefresh = CompletableDeferred<Unit>()
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesArrayPayload(listOf(TestDtos.chatMessage(id = "message-refresh")))
        )
        api.chatResponse = Response.success(TestDtos.chat(serverTime = "2026-06-18T21:02:00Z"))
        api.beforeGetChatMessagesResponse = {
            messagesRefreshStarted.complete(Unit)
            releaseMessagesRefresh.await()
        }
        api.beforeGetFirstChatForMatchResponse = {
            snapshotRefreshStarted.complete(Unit)
            releaseSnapshotRefresh.await()
        }
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(sending = true)

        val send = async {
            coordinator.sendMessage(current, "hola", localId = "local-1")
        }
        runCurrent()
        messagesRefreshStarted.await()
        snapshotRefreshStarted.await()

        releaseSnapshotRefresh.complete(Unit)
        runCurrent()
        assertFalse(send.isCompleted)

        releaseMessagesRefresh.complete(Unit)
        val state = (send.await() as FirstChatSendResult.Show).state
        assertFalse(state.sending)
        assertTrue(state.messages.any { it.id == "message-refresh" })
        assertEquals(java.time.Instant.parse("2026-06-18T21:02:00Z").toEpochMilli(), state.serverClockSnapshot?.serverTimeEpochMillis)
    }

    @Test
    fun `sendMessage post success with messages refresh failure stays acknowledged`() = runBlocking {
        api.chatMessageResponse = Response.success(TestDtos.chatMessage(id = "message-post"))
        api.chatMessagesResponse = backendErrorResponse(
            statusCode = 500,
            code = "INTERNAL_ERROR",
        )
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-1",
            createdAtMillis = 123L,
        )
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            optimisticMessages = listOf(optimistic),
            sending = true,
        )

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")

        val state = (result as FirstChatSendResult.Show).state
        assertEquals(listOf("message-post"), state.messages.map { it.id })
        assertTrue(state.optimisticMessages.isEmpty())
        assertFalse(state.sending)
        assertTrue(state.error is ApiError.Backend)
        assertEquals(listOf("sendChatMessage", "getChatMessages", "getFirstChatForMatch"), api.calls)
    }

    @Test
    fun `sendMessage post success with both refreshes failing keeps messages error precedence`() = runBlocking {
        api.chatMessageResponse = Response.success(TestDtos.chatMessage(id = "message-post"))
        api.chatMessagesResponse = backendErrorResponse(
            statusCode = 500,
            code = "INTERNAL_ERROR",
            message = "messages failed",
        )
        api.chatResponse = backendErrorResponse(
            statusCode = 500,
            code = "INTERNAL_ERROR",
            message = "snapshot failed",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            optimisticMessages = listOf(
                newOptimisticOutgoingMessage(
                    chatId = "chat-1",
                    senderId = "user-1",
                    content = "hola",
                    localId = "local-1",
                    createdAtMillis = 123L,
                )
            ),
            sending = true,
        )

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")

        val state = (result as FirstChatSendResult.Show).state
        val error = state.error as ApiError.Backend
        assertEquals("messages failed", error.message)
        assertEquals(listOf("message-post"), state.messages.map { it.id })
        assertTrue(state.optimisticMessages.isEmpty())
        assertFalse(state.sending)
    }

    @Test
    fun `sendMessage post success with first chat snapshot failure stays acknowledged`() = runBlocking {
        api.chatMessageResponse = Response.success(TestDtos.chatMessage(id = "message-post"))
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesArrayPayload(emptyList()))
        api.chatResponse = backendErrorResponse(
            statusCode = 500,
            code = "INTERNAL_ERROR",
        )
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-1",
            createdAtMillis = 123L,
        )
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            optimisticMessages = listOf(optimistic),
            sending = true,
        )

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")

        val state = (result as FirstChatSendResult.Show).state
        assertEquals(listOf("message-post"), state.messages.map { it.id })
        assertTrue(state.optimisticMessages.isEmpty())
        assertFalse(state.sending)
        assertTrue(state.error is ApiError.Backend)
        assertEquals(listOf("sendChatMessage", "getChatMessages", "getFirstChatForMatch"), api.calls)
    }

    @Test
    fun `sendMessage stale messages refresh does not replace post response for sent message`() = runBlocking {
        api.chatMessageResponse = Response.success(
            TestDtos.chatMessage(id = "message-post").copy(content = "desde post")
        )
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesArrayPayload(
                listOf(TestDtos.chatMessage(id = "message-post").copy(content = "desde refresh viejo"))
            )
        )
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            optimisticMessages = listOf(
                newOptimisticOutgoingMessage(
                    chatId = "chat-1",
                    senderId = "user-1",
                    content = "hola",
                    localId = "local-1",
                    createdAtMillis = 123L,
                )
            ),
            sending = true,
        )

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")

        val state = (result as FirstChatSendResult.Show).state
        assertEquals("desde post", state.messages.single { it.id == "message-post" }.content)
        assertTrue(state.optimisticMessages.isEmpty())
        assertFalse(state.sending)
    }

    @Test
    fun `sendMessage failure keeps chat backend error`() = runBlocking {
        api.chatMessageResponse = backendErrorResponse(
            statusCode = 400,
            code = "CHAT_MESSAGE_INVALID",
            message = "raw backend message",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.sendMessage(current, "", localId = "local-1")
        assertTrue(result is FirstChatSendResult.Show)
        val state = (result as FirstChatSendResult.Show).state
        val error = state.error as ApiError.Backend

        assertEquals(false, state.sending)
        assertEquals(BackendErrorCode.ChatMessageInvalid, error.backendErrorCode)
        assertEquals(
            "Revisá el mensaje. No puede estar vacío ni superar el límite permitido.",
            error.toUserMessage(ErrorContext.Chat),
        )
    }

    @Test
    fun `sendMessage mutual cancellation pending refreshes exits and removes optimistic message`() = runBlocking {
        api.chatMessageResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_MUTUAL_CANCELLATION_PENDING",
        )
        api.chatResponse = Response.success(TestDtos.chat(status = "ACTIVE", serverTime = "2026-06-18T21:02:00Z"))
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "PENDING")))
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-1",
            createdAtMillis = 123L,
        )
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            optimisticMessages = listOf(optimistic),
            sending = true,
        )

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")

        assertTrue(result is FirstChatSendResult.Show)
        val state = (result as FirstChatSendResult.Show).state
        val error = state.error as ApiError.Backend
        assertFalse(state.sending)
        assertTrue(state.optimisticMessages.isEmpty())
        assertEquals(ChatExitRequestStatus.Pending, state.exitRequests.single().status)
        assertEquals(BackendErrorCode.ChatMutualCancellationPending, error.backendErrorCode)
        assertEquals(
            listOf("sendChatMessage", "getFirstChatForMatch", "getChatExitRequests"),
            api.calls,
        )
    }

    @Test
    fun `sendMessage decision-only conflict refreshes snapshot and removes optimistic retry`() = runBlocking {
        api.chatMessageResponse = backendErrorResponse(
            statusCode = 409,
            code = "FIRST_CHAT_DECISION_ONLY",
        )
        api.chatResponse = Response.success(
            TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "APPROVED",
                serverTime = "2026-06-18T21:02:00Z",
            )
        )
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-1",
            createdAtMillis = 123L,
        )
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            optimisticMessages = listOf(optimistic),
            sending = true,
        )

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")

        assertTrue(result is FirstChatSendResult.Show)
        val state = (result as FirstChatSendResult.Show).state
        assertFalse(state.sending)
        assertTrue(state.optimisticMessages.isEmpty())
        assertEquals(ChatDecisionState.Pending, state.chat?.myDecision)
        assertEquals(ChatDecisionState.Approved, state.chat?.partnerDecision)
        assertEquals(null, state.error)
        assertEquals(listOf("sendChatMessage", "getFirstChatForMatch"), api.calls)
    }

    @Test
    fun `sendMessage chat abandoned returns home and hides first chat`() = runBlocking {
        api.chatMessageResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_ABANDONED",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")

        assertTrue(result is FirstChatSendResult.ReturnHome)
        result as FirstChatSendResult.ReturnHome
        assertEquals("match-1", result.hideFirstChatMatchId)
        assertEquals("La conversaci\u00f3n se cerr\u00f3 por inactividad.", result.message)
    }

    @Test
    fun `approved decision with visual phase response reloads home and hides first chat`() = runBlocking {
        var pending: RealsRootUiState.FirstChat? = null
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.submitDecision(
            current = current,
            decision = ChatContinueDecision.Approved,
            onPending = { pending = it },
        )

        assertEquals(true, pending?.actionLoading)
        assertEquals("Aprobando...", pending?.actionLoadingLabel)
        assertTrue(result is FirstChatActionResult.ReloadHome)
        result as FirstChatActionResult.ReloadHome
        assertEquals("match-1", result.hideFirstChatMatchId)
        assertEquals("El chat cambió de estado. Actualizamos tu Home.", result.message)
        assertFalse(result.autoNavigateEngagements)
        assertEquals(listOf("submitChatDecision"), api.calls)
    }

    @Test
    fun `decision is ignored while mutual cancellation is pending`() = runBlocking {
        val current = firstChatState(chatStatus = ChatStatus.Active).copy(
            exitRequests = listOf(TestDtos.exitRequest(status = "PENDING").toDomain()),
        )

        val result = coordinator.submitDecision(
            current = current,
            decision = ChatContinueDecision.Approved,
            onPending = {},
        )

        assertEquals(FirstChatActionResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `decision mutual cancellation pending refreshes exits`() = runBlocking {
        api.matchResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_MUTUAL_CANCELLATION_PENDING",
        )
        api.chatResponse = Response.success(TestDtos.chat(status = "ACTIVE", serverTime = "2026-06-18T21:02:00Z"))
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "PENDING")))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.submitDecision(
            current = current,
            decision = ChatContinueDecision.Approved,
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        val error = state.error as ApiError.Backend
        assertFalse(state.actionLoading)
        assertEquals(null, state.actionLoadingLabel)
        assertEquals(ChatExitRequestStatus.Pending, state.exitRequests.single().status)
        assertEquals(BackendErrorCode.ChatMutualCancellationPending, error.backendErrorCode)
        assertEquals(
            listOf("submitChatDecision", "getFirstChatForMatch", "getChatExitRequests"),
            api.calls,
        )
    }

    @Test
    fun `rejected decision on terminal state returns home with exit message`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.submitDecision(
            current = current,
            decision = ChatContinueDecision.Rejected,
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.ReturnHome)
        result as FirstChatActionResult.ReturnHome
        assertEquals("match-1", result.hideFirstChatMatchId)
        assertEquals("El chat cambió de estado. Actualizamos tu Home.", result.message)
    }

    @Test
    fun `decision failure keeps first chat and exposes error`() = runBlocking {
        api.matchResponse = backendErrorResponse(
            statusCode = 500,
            code = "INTERNAL_ERROR",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.submitDecision(
            current = current,
            decision = ChatContinueDecision.Approved,
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertFalse(state.actionLoading)
        assertTrue(state.error is ApiError.Backend)
    }

    @Test
    fun `approval eligibility failure keeps first chat state pending and retryable`() = runBlocking {
        api.matchResponse = backendErrorResponse(
            statusCode = 409,
            code = "FIRST_CHAT_APPROVAL_TOO_EARLY",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.submitDecision(
            current = current,
            decision = ChatContinueDecision.Approved,
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        val error = state.error as ApiError.Backend
        assertFalse(state.actionLoading)
        assertEquals(null, state.actionLoadingLabel)
        assertEquals(ChatDecisionState.Pending, state.chat?.myDecision)
        assertEquals(ChatDecisionState.Pending, state.chat?.partnerDecision)
        assertEquals("CHAT_ACTIVE", state.match?.state?.rawValue)
        assertEquals(BackendErrorCode.FirstChatApprovalTooEarly, error.backendErrorCode)
        assertEquals(
            "Todavía es muy pronto para avanzar. Conversen un poco más antes de decidir.",
            error.toUserMessage(ErrorContext.Chat),
        )
        assertTrue(
            firstChatInteractionPolicy(
                chat = state.chat,
                canChat = true,
                exitFlowLocked = false,
                showDecisionActions = true,
                matchIsChatActive = state.match?.state?.rawValue == "CHAT_ACTIVE",
                firstChatLocallyExpired = false,
                audioInteractionBusy = false,
            ).canDecide
        )
        assertEquals(listOf("submitChatDecision"), api.calls)
    }

    @Test
    fun `decision chat abandoned returns home and hides first chat`() = runBlocking {
        api.matchResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_ABANDONED",
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.submitDecision(
            current = current,
            decision = ChatContinueDecision.Approved,
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.ReturnHome)
        result as FirstChatActionResult.ReturnHome
        assertEquals("match-1", result.hideFirstChatMatchId)
        assertEquals("La conversaci\u00f3n se cerr\u00f3 por inactividad.", result.message)
    }

    @Test
    fun `guidance request is ignored when guidance is null`() = runBlocking {
        val current = firstChatState(chatStatus = ChatStatus.Active, guidance = null)

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertEquals(FirstChatActionResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `guidance request is not sent when completed`() = runBlocking {
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(completed = true, canRequestNext = false),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertEquals(FirstChatActionResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `guidance request is not sent when already requested`() = runBlocking {
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(myNextRequested = true),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertEquals(FirstChatActionResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `guidance request is not sent when user cannot request next`() = runBlocking {
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(canRequestNext = false),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertEquals(FirstChatActionResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `valid guidance request calls dependency with current chat id and stores same question response`() = runBlocking {
        var pending: RealsRootUiState.FirstChat? = null
        api.firstChatGuidanceResponse = Response.success(
            TestDtos.firstChatGuidance(
                questionId = "Q027",
                questionText = "Pregunta inicial",
                myNextRequested = true,
            )
        )
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(questionId = "Q027", questionText = "Pregunta inicial"),
        )

        val result = coordinator.requestNextGuidanceQuestion(
            current = current,
            onPending = { pending = it },
        )

        assertEquals(true, pending?.guidanceActionLoading)
        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertEquals(listOf("requestNextFirstChatGuidanceQuestion"), api.calls)
        assertEquals("chat-1", api.lastPathId)
        assertEquals(false, state.guidanceActionLoading)
        assertEquals("Q027", state.chat?.guidance?.question?.id)
        assertEquals(true, state.chat?.guidance?.myNextRequested)
    }

    @Test
    fun `advanced guidance success replaces question and returned request state`() = runBlocking {
        api.firstChatGuidanceResponse = Response.success(
            TestDtos.firstChatGuidance(
                questionId = "Q028",
                questionText = "Pregunta siguiente",
                questionOrdinal = 2,
                myNextRequested = false,
            )
        )
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(questionId = "Q027", questionText = "Pregunta inicial"),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertTrue(result is FirstChatActionResult.Show)
        val guidance = (result as FirstChatActionResult.Show).state.chat?.guidance
        assertEquals("Q028", guidance?.question?.id)
        assertEquals("Pregunta siguiente", guidance?.question?.text)
        assertEquals(2, guidance?.questionOrdinal)
        assertEquals(false, guidance?.myNextRequested)
    }

    @Test
    fun `completed guidance response preserves Q3 and clears loading`() = runBlocking {
        api.firstChatGuidanceResponse = Response.success(
            TestDtos.firstChatGuidance(
                questionId = "Q003",
                questionText = "Pregunta final",
                questionOrdinal = 3,
                canRequestNext = false,
                myNextRequested = false,
                completed = true,
            )
        )
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(questionId = "Q003", questionOrdinal = 3),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertEquals(false, state.guidanceActionLoading)
        assertEquals("Q003", state.chat?.guidance?.question?.id)
        assertEquals("Pregunta final", state.chat?.guidance?.question?.text)
        assertEquals(true, state.chat?.guidance?.completed)
        assertEquals(false, state.chat?.guidance?.myNextRequested)
    }

    @Test
    fun `guidance request failure clears loading and exposes chat error`() = runBlocking {
        api.firstChatGuidanceResponse = backendErrorResponse(
            statusCode = 409,
            code = "FIRST_CHAT_GUIDANCE_PARTICIPATION_REQUIRED",
        )
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        val error = state.error as ApiError.Backend
        assertEquals(false, state.guidanceActionLoading)
        assertEquals(BackendErrorCode.FirstChatGuidanceParticipationRequired, error.backendErrorCode)
        assertEquals(
            "Particip\u00e1 un poco m\u00e1s antes de pedir otra pregunta.",
            error.toUserMessage(ErrorContext.Chat),
        )
    }

    @Test
    fun `guidance request is ignored while mutual cancellation is pending`() = runBlocking {
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(),
        ).copy(
            exitRequests = listOf(TestDtos.exitRequest(status = "PENDING").toDomain()),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertEquals(FirstChatActionResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `guidance request mutual cancellation pending refreshes exits`() = runBlocking {
        api.firstChatGuidanceResponse = backendErrorResponse(
            statusCode = 409,
            code = "CHAT_MUTUAL_CANCELLATION_PENDING",
        )
        api.chatResponse = Response.success(
            TestDtos.chat(status = "ACTIVE", serverTime = "2026-06-18T21:02:00Z")
        )
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "PENDING")))
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        val error = state.error as ApiError.Backend
        assertFalse(state.guidanceActionLoading)
        assertEquals(ChatExitRequestStatus.Pending, state.exitRequests.single().status)
        assertEquals(BackendErrorCode.ChatMutualCancellationPending, error.backendErrorCode)
        assertEquals(
            listOf(
                "requestNextFirstChatGuidanceQuestion",
                "getFirstChatForMatch",
                "getChatExitRequests",
            ),
            api.calls,
        )
    }

    @Test
    fun `guidance request decision-only conflict refreshes snapshot and clears loading`() = runBlocking {
        api.firstChatGuidanceResponse = backendErrorResponse(
            statusCode = 409,
            code = "FIRST_CHAT_DECISION_ONLY",
        )
        api.chatResponse = Response.success(
            TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "APPROVED",
                serverTime = "2026-06-18T21:02:00Z",
            )
        )
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertFalse(state.guidanceActionLoading)
        assertEquals(ChatDecisionState.Approved, state.chat?.partnerDecision)
        assertEquals(null, state.error)
        assertEquals(listOf("requestNextFirstChatGuidanceQuestion", "getFirstChatForMatch"), api.calls)
    }

    @Test
    fun `guidance request preserves unrelated first chat state`() = runBlocking {
        val message = TestDtos.chatMessage("message-old").toDomain()
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-old",
            createdAtMillis = 1L,
        )
        val exitRequest = TestDtos.exitRequest(status = "REJECTED").toDomain()
        api.firstChatGuidanceResponse = Response.success(TestDtos.firstChatGuidance(myNextRequested = true))
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(),
        ).copy(
            messages = listOf(message),
            optimisticMessages = listOf(optimistic),
            exitRequests = listOf(exitRequest),
        )

        val result = coordinator.requestNextGuidanceQuestion(current, onPending = {})

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertEquals(listOf(message), state.messages)
        assertEquals(listOf(optimistic), state.optimisticMessages)
        assertEquals(listOf(exitRequest), state.exitRequests)
        assertEquals(current.match, state.match)
    }

    @Test
    fun `refresh replaces stale guidance from first chat response`() = runBlocking {
        api.chatResponse = Response.success(
            TestDtos.chat(
                status = "ACTIVE",
                guidance = TestDtos.firstChatGuidance(
                    questionId = "Q028",
                    questionText = "Pregunta actualizada",
                    questionOrdinal = 2,
                    myNextRequested = false,
                )
            )
        )
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(
                questionId = "Q027",
                questionText = "Pregunta anterior",
                myNextRequested = true,
            ),
        )

        val result = coordinator.refresh(current, silent = false)

        assertTrue(result is FirstChatRefreshResult.Show)
        val guidance = (result as FirstChatRefreshResult.Show).state.chat?.guidance
        assertEquals("Q028", guidance?.question?.id)
        assertEquals("Pregunta actualizada", guidance?.question?.text)
        assertEquals(2, guidance?.questionOrdinal)
        assertEquals(false, guidance?.myNextRequested)
    }

    @Test
    fun `mutual exit refreshes chat and exit requests when chat remains open`() = runBlocking {
        api.exitRequestResponse = Response.success(TestDtos.exitRequest(status = "PENDING"))
        api.chatResponse = Response.success(TestDtos.chat(status = "ACTIVE"))
        api.exitRequestsResponse = Response.success(listOf(TestDtos.exitRequest(status = "PENDING")))
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.requestMutualExit(
            current = current,
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertFalse(state.actionLoading)
        assertEquals("Enviamos tu solicitud de salida consensuada.", state.message)
        assertEquals(ChatExitRequestStatus.Pending, state.exitRequests.single().status)
        assertEquals(
            listOf("requestChatExit", "getFirstChatForMatch", "getChatExitRequests"),
            api.calls,
        )
    }

    @Test
    fun `mutual exit decision-only conflict refreshes snapshot and clears loading`() = runBlocking {
        api.exitRequestResponse = backendErrorResponse(
            statusCode = 409,
            code = "FIRST_CHAT_DECISION_ONLY",
        )
        api.chatResponse = Response.success(
            TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "APPROVED",
                serverTime = "2026-06-18T21:02:00Z",
            )
        )
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.requestMutualExit(current = current, onPending = {})

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertFalse(state.actionLoading)
        assertEquals(null, state.actionLoadingLabel)
        assertEquals(ChatDecisionState.Approved, state.chat?.partnerDecision)
        assertEquals(null, state.error)
        assertEquals(listOf("requestChatExit", "getFirstChatForMatch"), api.calls)
    }

    @Test
    fun `decision-only snapshot blocks new guidance and ordinary exit locally`() = runBlocking {
        val current = firstChatState(
            chatStatus = ChatStatus.Active,
            guidance = TestDtos.firstChatGuidance(),
            myDecision = "PENDING",
            partnerDecision = "APPROVED",
        )

        val guidanceResult = coordinator.requestNextGuidanceQuestion(current, onPending = {})
        val exitResult = coordinator.requestMutualExit(current, onPending = {})
        val cancelResult = coordinator.cancelUnilaterally(current, onPending = {})

        assertEquals(FirstChatActionResult.Ignore, guidanceResult)
        assertEquals(FirstChatActionResult.Ignore, exitResult)
        assertEquals(FirstChatActionResult.Ignore, cancelResult)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `exit action returns home when outcome closes chat`() = runBlocking {
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.cancelUnilaterally(
            current = current,
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.ReturnHome)
        result as FirstChatActionResult.ReturnHome
        assertEquals("match-1", result.hideFirstChatMatchId)
        assertEquals("Cerraste el chat.", result.message)
        assertEquals(listOf("cancelChat"), api.calls)
    }

    @Test
    fun `first chat safety cancel rejects invalid details without backend call`() = runBlocking {
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.safetyCancel(
            current = current,
            reason = ChatExitReason.InappropriateBehavior,
            details = "<b>unsafe</b>",
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.Show)
        val state = (result as FirstChatActionResult.Show).state
        assertTrue(state.error is ApiError.Unexpected)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `first chat safety cancel success returns home with report message`() = runBlocking {
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.safetyCancel(
            current = current,
            reason = ChatExitReason.ChildSafetyConcern,
            details = "detalle válido",
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.ReturnHome)
        result as FirstChatActionResult.ReturnHome
        assertEquals("match-1", result.hideFirstChatMatchId)
        assertEquals(
            "Reporte enviado. Cerramos ésta conversación por seguridad y no volveremos a cruzarte con ésta persona.",
            result.message,
        )
        assertEquals(listOf("safetyCancelChat"), api.calls)
        assertEquals("CHILD_SAFETY_CONCERN", api.exitBody?.reason)
    }

    @Test
    fun `first chat audio success removes optimistic audio message`() = runBlocking {
        api.chatAudioMessageResponse = Response.success(
            TestDtos.audioChatMessage(clientMessageId = "client-1")
        )
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesArrayPayload(emptyList()))
        api.chatResponse = Response.success(
            TestDtos.chat(status = "ACTIVE", serverTime = "2026-06-18T21:02:00Z")
        )
        val file = tempAudioFile()
        val optimistic = newOptimisticOutgoingAudioMessage(
            chatId = "chat-1",
            senderId = "user-1",
            clientMessageId = "client-1",
            durationMillis = 2_000L,
        )

        val result = coordinator.sendAudioMessage(
            current = firstChatState(ChatStatus.Active).copy(
                audioDraft = audioDraft(file),
                audioUpload = ChatAudioUploadUiState(uploading = true),
                optimisticMessages = listOf(optimistic),
                dismissedUnansweredPeriodReference = "started:2026-06-18T21:00:00Z",
            ),
            file = file,
            clientMessageId = "client-1",
        )

        result as FirstChatSendResult.Show
        assertTrue(result.state.optimisticMessages.isEmpty())
        assertEquals("client-1", result.state.audioUpload.completedClientMessageId)
        assertEquals("started:2026-06-18T21:00:00Z", result.state.dismissedUnansweredPeriodReference)
        assertEquals(java.time.Instant.parse("2026-06-18T21:02:00Z").toEpochMilli(), result.state.serverClockSnapshot?.serverTimeEpochMillis)
    }

    @Test
    fun `first chat audio decision-only conflict refreshes snapshot and clears upload retry`() = runBlocking {
        api.chatAudioMessageResponse = backendErrorResponse(
            statusCode = 409,
            code = "FIRST_CHAT_DECISION_ONLY",
        )
        api.chatResponse = Response.success(
            TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "APPROVED",
                serverTime = "2026-06-18T21:02:00Z",
            )
        )
        val file = tempAudioFile()
        val optimistic = newOptimisticOutgoingAudioMessage(
            chatId = "chat-1",
            senderId = "user-1",
            clientMessageId = "client-1",
            durationMillis = 2_000L,
        )

        val result = coordinator.sendAudioMessage(
            current = firstChatState(ChatStatus.Active).copy(
                audioDraft = audioDraft(file),
                audioUpload = ChatAudioUploadUiState(uploading = true),
                optimisticMessages = listOf(optimistic),
            ),
            file = file,
            clientMessageId = "client-1",
        )

        result as FirstChatSendResult.Show
        assertTrue(result.state.optimisticMessages.isEmpty())
        assertEquals(ChatAudioUploadUiState(), result.state.audioUpload)
        assertEquals(ChatDecisionState.Approved, result.state.chat?.partnerDecision)
        assertEquals(null, result.state.error)
        assertEquals(listOf("sendChatAudioMessage", "getFirstChatForMatch"), api.calls)
    }

    @Test
    fun `optimistic message helper preserves sending state shape`() {
        val message = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-123",
            createdAtMillis = 123L,
        )

        assertEquals("local-123", message.localId)
        assertEquals("chat-1", message.chatId)
        assertEquals("user-1", message.senderId)
        assertEquals("hola", message.content)
        assertEquals(OutgoingMessageDeliveryState.Sending, message.deliveryState)
        assertTrue(listOf(message).withoutOptimisticMessage("local-123").isEmpty())
    }

    @Test
    fun `optimistic audio helper reuses client message id as local id`() {
        val message = newOptimisticOutgoingAudioMessage(
            chatId = "chat-1",
            senderId = "user-1",
            clientMessageId = "client-123",
            durationMillis = 2_000L,
            createdAtMillis = 123L,
        )

        assertEquals("client-123", message.localId)
        assertEquals(OptimisticOutgoingMessageType.Audio, message.messageType)
        assertEquals(2_000L, message.audioDurationMillis)
        assertEquals(OutgoingMessageDeliveryState.Sending, message.deliveryState)
        assertTrue(listOf(message).withoutOptimisticMessage("client-123").isEmpty())
    }

    private fun firstChatState(
        chatStatus: ChatStatus = ChatStatus.Active,
        guidance: FirstChatGuidanceResponseDto? = null,
        myDecision: String? = "PENDING",
        partnerDecision: String? = "PENDING",
    ): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = "chat-1",
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = TestDtos.chat(
                status = chatStatus.rawValue,
                guidance = guidance,
                myDecision = myDecision,
                partnerDecision = partnerDecision,
            ).toDomain(),
        )

    private fun pollingMessages(): List<ChatMessage> =
        listOf(
            TestDtos.chatMessage("a").copy(senderId = "other", sentAt = "2026-06-18T21:00:00Z").toDomain(),
            TestDtos.chatMessage("b").copy(senderId = "me", sentAt = "2026-06-18T21:01:00Z").toDomain(),
            TestDtos.chatMessage("c").copy(senderId = "other", sentAt = "2026-06-18T21:02:00Z").toDomain(),
            TestDtos.chatMessage("d").copy(senderId = "me", sentAt = "2026-06-18T21:03:00Z").toDomain(),
        )

    private fun audioDraft(file: File): ChatAudioDraftUiState =
        ChatAudioDraftUiState(
            filePath = file.absolutePath,
            clientMessageId = "client-1",
            durationMillis = 2_000L,
            sizeBytes = file.length(),
        )

    private fun tempAudioFile(): File =
        kotlin.io.path.createTempFile(suffix = ".m4a").toFile().apply {
            writeBytes(byteArrayOf(1))
        }

    private fun firstChatDependencies(
        api: FakeRealsApi,
        dismissalStore: InMemoryFirstChatUnansweredSuggestionDismissalStore =
            InMemoryFirstChatUnansweredSuggestionDismissalStore(),
    ): FirstChatFeatureDependencies {
        val tokenProvider = FakeAuthTokenProvider()
        val matchRepository = MatchRepository(api, { 0L }, tokenProvider, testApiExecutor())
        val chatRepository = ChatRepository(api, testJson, tokenProvider, testApiExecutor())
        return FirstChatFeatureDependencies(
            getMatch = GetMatchUseCase(matchRepository),
            getFirstChatForMatch = GetFirstChatForMatchUseCase(matchRepository),
            submitChatDecision = SubmitChatDecisionUseCase(matchRepository),
            getChatMessages = GetChatMessagesUseCase(chatRepository),
            sendChatMessage = SendChatMessageUseCase(chatRepository),
            sendChatAudioMessage = com.reals.app.domain.usecase.SendChatAudioMessageUseCase(chatRepository),
            putMessageReaction = PutChatMessageReactionUseCase(chatRepository),
            requestNextFirstChatGuidanceQuestion = RequestNextFirstChatGuidanceQuestionUseCase(chatRepository),
            getChatExitRequests = GetChatExitRequestsUseCase(chatRepository),
            requestMutualChatExit = RequestMutualChatExitUseCase(chatRepository),
            acceptChatExitRequest = AcceptChatExitRequestUseCase(chatRepository),
            rejectChatExitRequest = RejectChatExitRequestUseCase(chatRepository),
            timeoutChatExitRequest = TimeoutChatExitRequestUseCase(chatRepository),
            cancelChat = CancelChatUseCase(chatRepository),
            safetyCancelChat = SafetyCancelChatUseCase(chatRepository),
            unansweredSuggestionDismissalStore = dismissalStore,
        )
    }
}
