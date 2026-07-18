package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.network.toUserMessage
import com.reals.app.data.dto.FirstChatGuidanceResponseDto
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetChatUseCase
import com.reals.app.domain.usecase.GetFirstChatForMatchUseCase
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.GetSecondChatForConnectionUseCase
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
        assertEquals(null, state.error)
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
        val current = firstChatState(chatStatus = ChatStatus.Active)

        val result = coordinator.sendMessage(current, "hola", localId = "local-1")
        assertTrue(result is FirstChatSendResult.Show)
        val state = (result as FirstChatSendResult.Show).state

        assertEquals(false, state.sending)
        assertTrue(state.messages.any { it.id == "message-1" })
        assertEquals("sendChatMessage", api.calls.first())
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
        api.chatResponse = Response.success(TestDtos.chat(status = "ACTIVE"))
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
    fun `approved decision with active chat reloads home and hides first chat`() = runBlocking {
        var pending: RealsRootUiState.FirstChat? = null
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
        assertEquals("Aprobaste el chat. Te avisaremos si la otra persona también aprueba.", result.message)
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
        api.chatResponse = Response.success(TestDtos.chat(status = "ACTIVE"))
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
        assertEquals("El chat pasó a revisión visual. Actualizamos tu lista.", result.message)
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
        api.chatResponse = Response.success(TestDtos.chat(status = "ACTIVE"))
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
            "Reporte enviado. Cerramos esta conversación por seguridad y no volveremos a cruzarte con esta persona.",
            result.message,
        )
        assertEquals(listOf("safetyCancelChat"), api.calls)
        assertEquals("CHILD_SAFETY_CONCERN", api.exitBody?.reason)
    }

    @Test
    fun `second chat safety cancel rejects invalid details without backend call`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(firstChatDependencies(api))
        val current = secondChatState()

        val result = secondCoordinator.safetyCancel(
            current = current,
            reason = ChatExitReason.InappropriateBehavior,
            details = "<script>alert(1)</script>",
            onPending = {},
        )

        assertTrue(result is SecondChatActionResult.Show)
        val state = (result as SecondChatActionResult.Show).state
        assertTrue(state.error is ApiError.Unexpected)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `second chat safety cancel success returns home with report message`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(firstChatDependencies(api))
        val current = secondChatState()

        val result = secondCoordinator.safetyCancel(
            current = current,
            reason = ChatExitReason.ChildSafetyConcern,
            details = "detalle válido",
            onPending = {},
        )

        assertTrue(result is SecondChatActionResult.ReturnHome)
        result as SecondChatActionResult.ReturnHome
        assertEquals(
            "Reporte enviado. Cerramos esta conversación por seguridad y no volveremos a cruzarte con esta persona.",
            result.message,
        )
        assertEquals(listOf("safetyCancelChat"), api.calls)
        assertEquals("CHILD_SAFETY_CONCERN", api.exitBody?.reason)
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

    private fun firstChatState(
        chatStatus: ChatStatus,
        guidance: FirstChatGuidanceResponseDto? = null,
    ): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = "chat-1",
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = TestDtos.chat(status = chatStatus.rawValue, guidance = guidance).toDomain(),
        )

    private fun secondChatState(): RealsRootUiState.SecondChat =
        RealsRootUiState.SecondChat(
            session = TestDomain.session(),
            connectionId = "connection-1",
            matchId = "match-1",
            chatId = "chat-1",
            chat = TestDtos.chat(status = "ACTIVE").toDomain(),
        )

    private fun firstChatDependencies(api: FakeRealsApi): FirstChatFeatureDependencies {
        val tokenProvider = FakeAuthTokenProvider()
        val matchRepository = MatchRepository(api, tokenProvider, testApiExecutor())
        val chatRepository = ChatRepository(api, testJson, tokenProvider, testApiExecutor())
        return FirstChatFeatureDependencies(
            getMatch = GetMatchUseCase(matchRepository),
            getChat = GetChatUseCase(chatRepository),
            getFirstChatForMatch = GetFirstChatForMatchUseCase(matchRepository),
            getSecondChatForConnection = GetSecondChatForConnectionUseCase(chatRepository),
            submitChatDecision = SubmitChatDecisionUseCase(matchRepository),
            getChatMessages = GetChatMessagesUseCase(chatRepository),
            sendChatMessage = SendChatMessageUseCase(chatRepository),
            requestNextFirstChatGuidanceQuestion = RequestNextFirstChatGuidanceQuestionUseCase(chatRepository),
            getChatExitRequests = GetChatExitRequestsUseCase(chatRepository),
            requestMutualChatExit = RequestMutualChatExitUseCase(chatRepository),
            acceptChatExitRequest = AcceptChatExitRequestUseCase(chatRepository),
            rejectChatExitRequest = RejectChatExitRequestUseCase(chatRepository),
            timeoutChatExitRequest = TimeoutChatExitRequestUseCase(chatRepository),
            cancelChat = CancelChatUseCase(chatRepository),
            safetyCancelChat = SafetyCancelChatUseCase(chatRepository),
        )
    }
}
