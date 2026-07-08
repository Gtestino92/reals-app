package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
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

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelManualBlockTest {
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
    fun `FirstChat block success loads fresh Home without report or matchmaking`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(firstChat())

        viewModel.blockCurrentMatchParticipant()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(
            "Bloqueaste a esta persona. Cerramos la interacción y no volverán a ser emparejados.",
            state.homeMessage,
        )
        assertEquals(1, api.calls.count { it == "blockMatchParticipant" })
        assertEquals(listOf("match-manual"), api.blockMatchIds)
        assertEquals(1, api.calls.count { it == "getHome" })
        assertFalse(api.calls.any { it == "safetyCancelChat" || it == "enqueueMatchmaking" })
    }

    @Test
    fun `block failure remains FirstChat and can clear error without loading Home`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            userBlockResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChat())

        viewModel.blockCurrentMatchParticipant()
        advanceUntilIdle()

        val failed = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertFalse(failed.manualBlock.loading)
        assertTrue(failed.manualBlock.error is ApiError.Backend)
        assertFalse(api.calls.contains("getHome"))

        viewModel.clearManualBlockError()

        val cleared = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals(null, cleared.manualBlock.error)
        assertEquals("match-manual", cleared.matchId)
    }

    @Test
    fun `busy FirstChat does not call block endpoint`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(firstChat().copy(sending = true))

        viewModel.blockCurrentMatchParticipant()
        advanceUntilIdle()

        assertFalse(api.calls.contains("blockMatchParticipant"))
        assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
    }

    @Test
    fun `Scheduling manual block loading ignores silent refresh`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(
            scheduling().copy(manualBlock = ManualBlockUiState(loading = true)),
        )

        viewModel.refreshScheduling(silent = true)
        advanceUntilIdle()

        assertFalse(api.calls.contains("getConnectionNegotiation"))
        assertFalse(api.calls.contains("getConnectionProposals"))
    }

    @Test
    fun `FirstChat USER_PAIR_BLOCKED reroutes to fresh Home without retry`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(
            firstChat().copy(error = pairBlockedError()),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(
            "Esta interacción ya no está disponible. Actualizamos tu Home.",
            state.homeMessage,
        )
        assertEquals(1, api.calls.count { it == "getHome" })
        assertFalse(api.calls.contains("submitChatDecision"))
    }

    @Test
    fun `Scheduling USER_PAIR_BLOCKED reroutes while unrelated 409 stays`() = runTest(dispatcher) {
        val blockedApi = FakeRealsApi()
        val blockedViewModel = viewModel(blockedApi)
        blockedViewModel.setState(scheduling().copy(error = pairBlockedError()))

        advanceUntilIdle()

        assertTrue(blockedViewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(1, blockedApi.calls.count { it == "getHome" })

        val unrelatedApi = FakeRealsApi()
        val unrelatedViewModel = viewModel(unrelatedApi)
        unrelatedViewModel.setState(
            scheduling().copy(
                error = ApiError.Backend(
                    statusCode = 409,
                    code = "DOMAIN_CONFLICT",
                    error = "Conflict",
                    message = "unrelated",
                ),
            ),
        )

        advanceUntilIdle()

        assertTrue(unrelatedViewModel.uiState.value is RealsRootUiState.Scheduling)
        assertFalse(unrelatedApi.calls.contains("getHome"))
    }

    private fun viewModel(api: FakeRealsApi) =
        RealsRootViewModel(rootViewModelTestDependencies(api), autoRefreshSession = false)

    private fun firstChat() = RealsRootUiState.FirstChat(
        session = TestDomain.session(),
        matchId = "match-manual",
        chatId = "chat-manual",
        match = TestDtos.match("CHAT_ACTIVE").toDomain(),
        chat = TestDtos.chat("ACTIVE").copy(id = "chat-manual").toDomain(),
    )

    private fun scheduling() = RealsRootUiState.Scheduling(
        session = TestDomain.session(),
        connectionId = "connection-manual",
        matchId = "match-manual",
        negotiation = TestDtos.negotiation("PENDING").toDomain(),
    )

    private fun pairBlockedError() = ApiError.Backend(
        statusCode = 409,
        code = "USER_PAIR_BLOCKED",
        error = "Conflict",
        message = "pair blocked",
    )

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }
}
