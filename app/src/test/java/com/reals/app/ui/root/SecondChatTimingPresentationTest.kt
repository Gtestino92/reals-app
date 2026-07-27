package com.reals.app.ui.root

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.SecondChatStatus
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import java.time.Instant
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecondChatTimingPresentationTest {
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
    fun `joined active second chat before warning window is genuinely active`() {
        val presentation = lifecycle(
            serverTime = "2026-06-18T21:00:00Z",
            absoluteExpiresAt = "2026-06-18T21:11:00Z",
            receivedAtMillis = 1_000L,
        ).timingPresentation(nowMillis = 1_000L)

        assertTrue(presentation.genuinelyActive)
        assertFalse(presentation.locallyExpired)
        assertFalse(presentation.showAbsoluteExpiryWarning)
    }

    @Test
    fun `joined active second chat warns inside final ten minutes`() {
        val presentation = lifecycle(
            serverTime = "2026-06-18T21:00:00Z",
            absoluteExpiresAt = "2026-06-18T21:10:00Z",
            receivedAtMillis = 1_000L,
        ).timingPresentation(nowMillis = 1_000L)

        assertTrue(presentation.genuinelyActive)
        assertTrue(presentation.showAbsoluteExpiryWarning)
    }

    @Test
    fun `joined active second chat at absolute deadline is locally expired`() {
        val lifecycle = lifecycle(
            serverTime = "2026-06-18T21:00:00Z",
            absoluteExpiresAt = "2026-06-18T21:10:00Z",
            receivedAtMillis = 1_000L,
        )

        val presentation = lifecycle.timingPresentation(nowMillis = 601_000L)

        assertTrue(presentation.locallyExpired)
        assertFalse(presentation.genuinelyActive)
    }

    @Test
    fun `server synchronized remaining time decreases with local elapsed time`() {
        val status = status(
            serverTime = "2026-06-18T21:00:00Z",
            absoluteExpiresAt = "2026-06-18T21:30:00Z",
        )

        val initial = status.remainingMillisFromServerSnapshot(
            targetTime = status.absoluteExpiresAt,
            statusReceivedAtMillis = 1_000L,
            nowMillis = 1_000L,
        )
        val later = status.remainingMillisFromServerSnapshot(
            targetTime = status.absoluteExpiresAt,
            statusReceivedAtMillis = 1_000L,
            nowMillis = 61_000L,
        )

        assertEquals(1_800_000L, initial)
        assertEquals(1_740_000L, later)
    }

    @Test
    fun `joined active second chat before deadline blocks fallback navigation`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRealsApi())
        val state = secondChatState(
            lifecycle = lifecycle(
                serverTime = "2026-06-18T21:00:00Z",
                absoluteExpiresAt = "2026-06-18T21:10:00Z",
                receivedAtMillis = System.currentTimeMillis(),
            ),
        )
        viewModel.setState(state)

        assertTrue(state.isJoinedActiveSecondChat())
        assertFalse(state.canHandleSystemBack())

        viewModel.closeSecondChat()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.SecondChat)
    }

    @Test
    fun `same active backend status after local deadline allows fallback navigation`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        val state = secondChatState(
            lifecycle = lifecycle(
                serverTime = "2026-06-18T21:00:00Z",
                absoluteExpiresAt = "2026-06-18T21:01:00Z",
                receivedAtMillis = System.currentTimeMillis() - 61_000L,
            ),
        )
        viewModel.setState(state)

        assertFalse(state.isJoinedActiveSecondChat())
        assertTrue(state.canHandleSystemBack())

        viewModel.closeSecondChat()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(1, api.calls.count { it == "getHome" })
    }

    @Test
    fun `local absolute expiry routes Home only once`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(
            secondChatState(
                lifecycle = lifecycle(
                    serverTime = "2026-06-18T21:00:00Z",
                    absoluteExpiresAt = "2026-06-18T21:01:00Z",
                    receivedAtMillis = System.currentTimeMillis() - 61_000L,
                ),
            )
        )

        viewModel.handleSecondChatLocalAbsoluteExpiry()
        viewModel.handleSecondChatLocalAbsoluteExpiry()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals("El segundo chat venció.", ready.homeMessage)
        assertEquals(1, api.calls.count { it == "getHome" })
        assertFalse(api.calls.any { it == "safetyCancelChat" || it == "enqueueMatchmaking" })
    }

    @Test
    fun `reopened terminal read only second chat allows Home and Back without auto route`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRealsApi())
        val state = secondChatState(
            chatStatus = ChatStatus.Expired,
            lifecycle = lifecycle(
                chatStatus = "EXPIRED",
                serverTime = "2026-06-18T21:00:00Z",
                absoluteExpiresAt = "2026-06-18T21:00:00Z",
                receivedAtMillis = System.currentTimeMillis(),
                readOnlyUntil = "2026-06-18T22:00:00Z",
            ),
        )
        viewModel.setState(state)

        assertFalse(state.isJoinedActiveSecondChat())
        assertTrue(state.canHandleSystemBack())

        viewModel.handleSecondChatLocalAbsoluteExpiry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.SecondChat)
    }

    @Test
    fun `reporting is available before active deadline`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val coordinator = SecondChatCoordinator(rootViewModelTestDependencies(api).secondChat)

        val result = coordinator.safetyCancel(
            current = secondChatState(
                lifecycle = lifecycle(
                    serverTime = "2026-06-18T21:00:00Z",
                    absoluteExpiresAt = "2026-06-18T21:10:00Z",
                    receivedAtMillis = System.currentTimeMillis(),
                ),
            ),
            reason = ChatExitReason.InappropriateBehavior,
            details = "detalle válido",
            onPending = {},
        )

        assertTrue(result is SecondChatActionResult.ReturnHome)
        assertEquals(listOf("safetyCancelChat"), api.calls)
    }

    @Test
    fun `reporting is unavailable after local expiry and terminal read only`() = runTest(dispatcher) {
        val expiredApi = FakeRealsApi()
        val expiredCoordinator = SecondChatCoordinator(rootViewModelTestDependencies(expiredApi).secondChat)
        val expiredResult = expiredCoordinator.safetyCancel(
            current = secondChatState(
                lifecycle = lifecycle(
                    serverTime = "2026-06-18T21:00:00Z",
                    absoluteExpiresAt = "2026-06-18T21:01:00Z",
                    receivedAtMillis = System.currentTimeMillis() - 61_000L,
                ),
            ),
            reason = ChatExitReason.InappropriateBehavior,
            details = "detalle válido",
            onPending = {},
        )

        val terminalApi = FakeRealsApi()
        val terminalCoordinator = SecondChatCoordinator(rootViewModelTestDependencies(terminalApi).secondChat)
        val terminalResult = terminalCoordinator.safetyCancel(
            current = secondChatState(
                chatStatus = ChatStatus.Expired,
                lifecycle = lifecycle(
                    chatStatus = "EXPIRED",
                    serverTime = "2026-06-18T21:00:00Z",
                    absoluteExpiresAt = "2026-06-18T21:00:00Z",
                    receivedAtMillis = System.currentTimeMillis(),
                    readOnlyUntil = "2026-06-18T22:00:00Z",
                ),
            ),
            reason = ChatExitReason.InappropriateBehavior,
            details = "detalle válido",
            onPending = {},
        )

        assertEquals(SecondChatActionResult.Ignore, expiredResult)
        assertEquals(SecondChatActionResult.Ignore, terminalResult)
        assertFalse(expiredApi.calls.contains("safetyCancelChat"))
        assertFalse(terminalApi.calls.contains("safetyCancelChat"))
    }

    @Test
    fun `manual blocking remains available in terminal read only state`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val coordinator = ManualBlockCoordinator(rootViewModelTestDependencies(api).manualBlock.blockMatchParticipant)

        val result = coordinator.block(
            current = secondChatState(
                chatStatus = ChatStatus.Expired,
                lifecycle = lifecycle(
                    chatStatus = "EXPIRED",
                    serverTime = "2026-06-18T21:00:00Z",
                    absoluteExpiresAt = "2026-06-18T21:00:00Z",
                    receivedAtMillis = System.currentTimeMillis(),
                    readOnlyUntil = "2026-06-18T22:00:00Z",
                ),
            ),
            onPending = {},
        )

        assertTrue(result is ManualBlockResult.ReturnHome)
        assertEquals(listOf("blockMatchParticipant"), api.calls)
    }

    @Test
    fun `second chat message send is disabled at local expiry`() {
        val result = ChatMessageActionHandler.prepareSecondChatSend(
            current = secondChatState(
                lifecycle = lifecycle(
                    serverTime = "2026-06-18T21:00:00Z",
                    absoluteExpiresAt = "2026-06-18T21:01:00Z",
                    receivedAtMillis = System.currentTimeMillis() - 61_000L,
                ),
            ),
            content = "hola",
        )

        assertEquals(ChatMessageSendPreparation.Ignored, result)
    }

    @Test
    fun `first chat lifecycle countdown remains unchanged`() {
        val chat = TestDtos.chat().copy(
            expiresAt = "2026-06-18T21:05:00Z",
            inactivityExpiresAt = null,
        ).toDomain()

        val state = com.reals.app.ui.chat.firstChatLifecycleUiState(
            chat,
            Instant.parse("2026-06-18T21:04:30Z").toEpochMilli(),
        )

        assertEquals(30L, state?.remainingSeconds)
        assertTrue(state?.showCountdown == true)
    }

    private fun viewModel(api: FakeRealsApi): RealsRootViewModel =
        RealsRootViewModel(rootViewModelTestDependencies(api), autoRefreshSession = false)

    private fun lifecycle(
        serverTime: String,
        absoluteExpiresAt: String,
        receivedAtMillis: Long,
        chatStatus: String? = "ACTIVE",
        readOnlyUntil: String? = null,
    ): SecondChatLifecycleUiState = SecondChatLifecycleUiState(
        status = status(
            serverTime = serverTime,
            absoluteExpiresAt = absoluteExpiresAt,
            chatStatus = chatStatus,
            readOnlyUntil = readOnlyUntil,
        ),
        statusReceivedAtMillis = receivedAtMillis,
    )

    private fun status(
        serverTime: String,
        absoluteExpiresAt: String,
        chatStatus: String? = "ACTIVE",
        readOnlyUntil: String? = null,
    ): SecondChatStatus = TestDtos.secondChatStatus(
        chatStatus = chatStatus,
        readOnlyUntil = readOnlyUntil,
    ).copy(
        serverTime = serverTime,
        absoluteExpiresAt = absoluteExpiresAt,
    ).toDomain()

    private fun secondChatState(
        lifecycle: SecondChatLifecycleUiState,
        chatStatus: ChatStatus = ChatStatus.Active,
    ): RealsRootUiState.SecondChat = RealsRootUiState.SecondChat(
        session = TestDomain.session(),
        connectionId = "connection-1",
        matchId = "match-1",
        partnerName = "Alex",
        chatId = "chat-1",
        chat = TestDtos.chat(status = chatStatus.rawValue)
            .copy(id = "chat-1", chatType = "SECOND_CHAT")
            .toDomain(),
        lifecycle = lifecycle,
    )

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }
}
