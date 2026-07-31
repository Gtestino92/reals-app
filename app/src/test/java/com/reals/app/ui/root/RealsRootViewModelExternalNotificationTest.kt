package com.reals.app.ui.root

import com.reals.app.data.mapper.toDomain
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

    private fun viewModel(api: FakeRealsApi): RealsRootViewModel =
        RealsRootViewModel(rootViewModelTestDependencies(api), autoRefreshSession = false)

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
}
