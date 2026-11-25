package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatMessage
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.testJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.After
import org.junit.Before
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ChatHistoryViewModelFixture {
    protected val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun viewModel(api: FakeRealsApi): RealsRootViewModel =
        RealsRootViewModel(rootViewModelTestDependencies(api), autoRefreshSession = false)

    protected fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    protected fun firstChatState(
        messages: List<ChatMessage>,
        matchId: String = "match-1",
        chatId: String = "chat-1",
        error: ApiError? = null,
        message: String? = null,
    ): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = matchId,
            chatId = chatId,
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = TestDtos.chat(status = "ACTIVE").copy(
                id = chatId,
                matchId = matchId,
                expiresAt = "2099-06-20T21:00:00Z",
                inactivityExpiresAt = "2099-06-18T21:05:00Z",
            ).toDomain(),
            messages = messages,
            error = error,
            message = message,
        )

    protected fun secondChatState(
        messages: List<ChatMessage>,
        connectionId: String = "connection-1",
        chatId: String = "chat-1",
        lifecycle: SecondChatLifecycleUiState = SecondChatLifecycleUiState(
            status = activeSecondChatStatus().toDomain(),
            statusReceivedAtMillis = System.currentTimeMillis(),
        ),
    ): RealsRootUiState.SecondChat =
        RealsRootUiState.SecondChat(
            session = TestDomain.session(),
            connectionId = connectionId,
            matchId = "match-1",
            partnerName = "Alex",
            chatId = chatId,
            chat = TestDtos.chat(status = "ACTIVE").copy(
                id = chatId,
                connectionId = connectionId,
                chatType = "SECOND_CHAT",
            ).toDomain(),
            messages = messages,
            lifecycle = lifecycle,
        )

    protected fun activeSecondChatStatus(
        myAttendanceStatus: String = "ON_TIME",
    ): com.reals.app.data.dto.SecondChatAttendanceResponseDto =
        TestDtos.secondChatStatus(
            myAttendanceStatus = myAttendanceStatus,
            scheduledAt = "2099-06-18T21:00:00Z",
            entryClosesAt = "2099-06-18T21:20:00Z",
            absoluteExpiresAt = "2099-06-18T23:00:00Z",
            serverTime = "2099-06-18T21:00:00Z",
            mutualCompletionEligibleAt = "2099-06-18T21:10:00Z",
            inactivityClaimableAt = "2099-06-18T21:05:00Z",
            inactivityClosesAt = "2099-06-18T21:10:00Z",
        )

    protected fun chatMessagesPayload(messages: List<com.reals.app.data.dto.ChatMessageResponseDto>): Response<JsonElement> =
        Response.success(testJson.encodeToJsonElement(messages))
}
