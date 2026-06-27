package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.MatchRepository
import com.reals.app.domain.usecase.GetVisualProfileUseCase
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

class PartnerProfileCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = PartnerProfileCoordinator(getVisualProfile(api))

    @Test
    fun `load returns profile and clears loading on success`() = runBlocking {
        var pending: RealsRootUiState.PartnerProfile? = null

        val state = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            onPending = { pending = it },
        )

        assertEquals(true, pending?.loading)
        assertEquals("match-1", pending?.matchId)
        assertEquals("visual-profile-1", state.profile?.profileId)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertEquals(null, state.error)
        assertEquals(listOf("getVisualProfile"), api.calls)
    }

    @Test
    fun `load returns error and clears loading on failure`() = runBlocking {
        api.visualProfileResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")

        val state = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            onPending = {},
        )

        assertEquals(null, state.profile)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertTrue(state.error is ApiError.Backend)
    }

    @Test
    fun `refresh preserves session and match id and clears refreshing on success`() = runBlocking {
        val current = partnerProfileState()
        var pending: RealsRootUiState.PartnerProfile? = null

        val state = coordinator.refresh(
            current = current,
            onPending = { pending = it },
        )

        assertEquals(current.session, state.session)
        assertEquals(current.matchId, state.matchId)
        assertEquals(true, pending?.refreshing)
        assertEquals(null, pending?.error)
        assertEquals("visual-profile-1", state.profile?.profileId)
        assertFalse(state.refreshing)
        assertEquals(null, state.error)
    }

    @Test
    fun `refresh preserves existing profile and exposes error on failure`() = runBlocking {
        val existingProfile = TestDtos.visualProfile().toDomain()
        api.visualProfileResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
        val current = partnerProfileState(profile = existingProfile)

        val state = coordinator.refresh(
            current = current,
            onPending = {},
        )

        assertEquals(existingProfile, state.profile)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertTrue(state.error is ApiError.Backend)
    }

    private fun partnerProfileState(
        profile: com.reals.app.domain.model.VisualProfile? = null,
    ): RealsRootUiState.PartnerProfile = RealsRootUiState.PartnerProfile(
        session = TestDomain.session(),
        matchId = "match-1",
        profile = profile,
        error = ApiError.Unexpected("previous"),
    )

    private fun getVisualProfile(api: FakeRealsApi): GetVisualProfileUseCase {
        api.visualProfileResponse = Response.success(TestDtos.visualProfile())
        val repository = MatchRepository(api, FakeAuthTokenProvider(), testApiExecutor())
        return GetVisualProfileUseCase(repository)
    }
}
