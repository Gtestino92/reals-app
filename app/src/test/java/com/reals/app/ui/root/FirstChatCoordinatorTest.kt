package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.network.toUserMessage
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.domain.model.ChatContinueDecision
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class FirstChatCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = FirstChatCoordinator(firstChatDependencies(api))

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

        assertEquals(FirstChatRefreshResult.ExitResolved, result)
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
            "Revisa el mensaje. No puede estar vacio ni superar el limite permitido.",
            error.toUserMessage(ErrorContext.Chat),
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
        assertEquals("Aprobaste el chat. Te avisaremos si la otra persona tambiÃ©n aprueba.", result.message)
        assertFalse(result.autoNavigateEngagements)
        assertEquals(listOf("submitChatDecision"), api.calls)
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
        assertEquals("El chat paso a revision visual. Actualizamos tu lista.", result.message)
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
            details = "detalle valido",
            onPending = {},
        )

        assertTrue(result is FirstChatActionResult.ReturnHome)
        result as FirstChatActionResult.ReturnHome
        assertEquals("match-1", result.hideFirstChatMatchId)
        assertEquals("Reporte enviado. Cerramos esta conversacion por seguridad.", result.message)
        assertEquals(listOf("safetyCancelChat"), api.calls)
    }

    @Test
    fun `second chat safety cancel rejects invalid details without backend call`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(firstChatDependencies(api))
        val current = secondChatState()

        val result = secondCoordinator.safetyCancel(
            current = current,
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
            details = "detalle valido",
            onPending = {},
        )

        assertTrue(result is SecondChatActionResult.ReturnHome)
        result as SecondChatActionResult.ReturnHome
        assertEquals("Reporte enviado. Cerramos esta conversacion por seguridad.", result.message)
        assertEquals(listOf("safetyCancelChat"), api.calls)
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

    private fun firstChatState(chatStatus: ChatStatus): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = "chat-1",
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = TestDtos.chat(status = chatStatus.rawValue).toDomain(),
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
