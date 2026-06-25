package com.reals.app.ui.root

import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.di.FirstChatFeatureDependencies
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
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

        val state = coordinator.sendMessage(current, "hola", localId = "local-1")

        assertEquals(false, state.sending)
        assertTrue(state.messages.any { it.id == "message-1" })
        assertEquals("sendChatMessage", api.calls.first())
    }

    private fun firstChatState(chatStatus: ChatStatus): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = "chat-1",
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = TestDtos.chat(status = chatStatus.rawValue).toDomain(),
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
