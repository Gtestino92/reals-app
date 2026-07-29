package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.repository.MatchRepository
import com.reals.app.domain.usecase.BlockMatchParticipantUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ManualBlockCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = ManualBlockCoordinator(
        BlockMatchParticipantUseCase(
            MatchRepository(api, { 0L }, FakeAuthTokenProvider(), testApiExecutor()),
        ),
    )

    @Test
    fun `201 success publishes loading and returns Home route for exact match`() = runBlocking {
        api.userBlockResponse = Response.success(201, TestDtos.userBlock())
        var pending: RealsRootUiState? = null

        val result = coordinator.block(firstChat(), onPending = { pending = it })

        assertTrue((pending as RealsRootUiState.FirstChat).manualBlock.loading)
        assertTrue(result is ManualBlockResult.ReturnHome)
        assertEquals(listOf("blockMatchParticipant"), api.calls)
        assertEquals("match-first", api.lastPathId)
    }

    @Test
    fun `200 idempotent replay returns the same Home route`() = runBlocking {
        api.userBlockResponse = Response.success(200, TestDtos.userBlock())

        val result = coordinator.block(firstChat(), onPending = {})

        assertTrue(result is ManualBlockResult.ReturnHome)
    }

    @Test
    fun `failure stays on destination clears loading and stores error`() = runBlocking {
        api.userBlockResponse = backendErrorResponse(500, "SERVER_ERROR")

        val result = coordinator.block(firstChat(), onPending = {})
        val state = (result as ManualBlockResult.Show).state as RealsRootUiState.FirstChat

        assertFalse(state.manualBlock.loading)
        assertTrue(state.manualBlock.error is ApiError.Backend)
        assertEquals("match-first", state.matchId)
    }

    @Test
    fun `already blocking state is ignored`() = runBlocking {
        val result = coordinator.block(
            firstChat().copy(manualBlock = ManualBlockUiState(loading = true)),
            onPending = {},
        )

        assertEquals(ManualBlockResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `another interaction transition in flight is ignored`() = runBlocking {
        val result = coordinator.block(firstChat().copy(actionLoading = true), onPending = {})

        assertEquals(ManualBlockResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `Scheduling uses its own current match id`() = runBlocking {
        val state = RealsRootUiState.Scheduling(
            session = TestDomain.session(),
            connectionId = "connection-scheduling",
            matchId = "match-scheduling",
        )

        val result = coordinator.block(state, onPending = {})

        assertTrue(result is ManualBlockResult.ReturnHome)
        assertEquals("match-scheduling", api.lastPathId)
    }

    private fun firstChat() = RealsRootUiState.FirstChat(
        session = TestDomain.session(),
        matchId = "match-first",
    )
}
