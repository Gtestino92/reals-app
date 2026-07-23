package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.dto.PartnerPersonalMessageResponseDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.MatchRepository
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
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
    private val coordinator = PartnerProfileCoordinator(
        getVisualProfile(api),
        getPartnerPersonalMessage(api),
    )

    @Test
    fun `load returns profile and clears loading on success`() = runBlocking {
        val pendingStates = mutableListOf<RealsRootUiState.PartnerProfile>()

        val state = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            onPending = { pendingStates += it },
        )

        assertEquals(true, pendingStates.first().loading)
        assertEquals("match-1", pendingStates.first().matchId)
        assertEquals("visual-profile-1", pendingStates.last().profile?.profileId)
        assertFalse(pendingStates.last().loadingPartnerMessage)
        assertEquals("visual-profile-1", state.profile?.profileId)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertFalse(state.loadingPartnerMessage)
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
        val pendingStates = mutableListOf<RealsRootUiState.PartnerProfile>()

        val state = coordinator.refresh(
            current = current,
            onPending = { pendingStates += it },
        )

        assertEquals(current.session, state.session)
        assertEquals(current.matchId, state.matchId)
        assertEquals(true, pendingStates.first().refreshing)
        assertEquals(null, pendingStates.first().error)
        assertEquals("visual-profile-1", pendingStates.last().profile?.profileId)
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

    @Test
    fun `load fetches submitted partner personal message`() = runBlocking {
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(partnerPersonalMessageSubmitted = true)
        )
        val pendingStates = mutableListOf<RealsRootUiState.PartnerProfile>()

        val state = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            onPending = { pendingStates += it },
        )

        val profilePending = pendingStates.last()
        assertEquals("visual-profile-1", profilePending.profile?.profileId)
        assertTrue(profilePending.loadingPartnerMessage)
        assertFalse(profilePending.partnerMessageLoaded)
        assertEquals(null, profilePending.partnerMessageError)
        assertEquals("hola", state.partnerMessage)
        assertTrue(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
        assertEquals(null, state.partnerMessageError)
        assertEquals(listOf("getVisualProfile", "getPartnerPersonalMessage"), api.calls)
    }

    @Test
    fun `load skips partner personal message when not submitted`() = runBlocking {
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(partnerPersonalMessageSubmitted = false)
        )

        val state = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            onPending = {},
        )

        assertEquals(null, state.partnerMessage)
        assertFalse(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
        assertEquals(listOf("getVisualProfile"), api.calls)
    }

    @Test
    fun `load keeps profile with message specific failure`() = runBlocking {
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(partnerPersonalMessageSubmitted = true)
        )
        api.partnerMessageResponse = backendErrorResponse(409, "VISUAL_CONTENT_NOT_AVAILABLE", "unavailable")

        val state = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            onPending = {},
        )

        assertEquals("visual-profile-1", state.profile?.profileId)
        assertEquals(null, state.partnerMessage)
        assertFalse(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
        assertTrue(state.partnerMessageError is ApiError.Backend)
        assertEquals(null, state.error)
    }

    @Test
    fun `load publishes profile before waiting for partner personal message`() = runBlocking {
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(partnerPersonalMessageSubmitted = true)
        )
        val pendingStates = mutableListOf<RealsRootUiState.PartnerProfile>()
        api.beforeGetPartnerPersonalMessageResponse = {
            val profilePending = pendingStates.last()
            assertEquals("visual-profile-1", profilePending.profile?.profileId)
            assertFalse(profilePending.loading)
            assertFalse(profilePending.refreshing)
            assertTrue(profilePending.loadingPartnerMessage)
            assertFalse(profilePending.partnerMessageLoaded)
        }

        val state = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            onPending = { pendingStates += it },
        )

        assertEquals("hola", state.partnerMessage)
        assertEquals(listOf("getVisualProfile", "getPartnerPersonalMessage"), api.calls)
    }

    @Test
    fun `retry partner message success keeps profile and skips visual profile request`() = runBlocking {
        val existingProfile = TestDtos.visualProfile(partnerPersonalMessageSubmitted = true).toDomain()
        val current = partnerProfileState(profile = existingProfile).copy(
            partnerMessageError = ApiError.Unexpected("previous message failure"),
        )
        val pendingStates = mutableListOf<RealsRootUiState.PartnerProfile>()

        val state = coordinator.retryPartnerMessage(
            current = current,
            onPending = { pendingStates += it },
        )

        assertEquals(existingProfile, pendingStates.single().profile)
        assertTrue(pendingStates.single().loadingPartnerMessage)
        assertEquals(null, pendingStates.single().partnerMessageError)
        assertEquals(existingProfile, state.profile)
        assertEquals("hola", state.partnerMessage)
        assertTrue(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
        assertEquals(listOf("getPartnerPersonalMessage"), api.calls)
    }

    @Test
    fun `retry partner message failure preserves profile and leaves retry possible`() = runBlocking {
        val existingProfile = TestDtos.visualProfile(partnerPersonalMessageSubmitted = true).toDomain()
        api.partnerMessageResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
        val current = partnerProfileState(profile = existingProfile).copy(
            partnerMessageError = ApiError.Unexpected("previous message failure"),
        )

        val state = coordinator.retryPartnerMessage(
            current = current,
            onPending = {},
        )

        assertEquals(existingProfile, state.profile)
        assertEquals(null, state.partnerMessage)
        assertFalse(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
        assertTrue(state.partnerMessageError is ApiError.Backend)
        assertEquals(null, state.error)
        assertEquals(listOf("getPartnerPersonalMessage"), api.calls)
    }

    @Test
    fun `refresh preserves loaded message while replacing it on success`() = runBlocking {
        val existingProfile = TestDtos.visualProfile(partnerPersonalMessageSubmitted = true).toDomain()
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(partnerPersonalMessageSubmitted = true)
        )
        api.partnerMessageResponse = Response.success(PartnerPersonalMessageResponseDto("nuevo mensaje"))
        val current = partnerProfileState(profile = existingProfile).copy(
            partnerMessage = "mensaje anterior",
            partnerMessageLoaded = true,
        )
        val pendingStates = mutableListOf<RealsRootUiState.PartnerProfile>()

        val state = coordinator.refresh(
            current = current,
            onPending = { pendingStates += it },
        )

        assertEquals("mensaje anterior", pendingStates.first().partnerMessage)
        assertTrue(pendingStates.first().partnerMessageLoaded)
        val messagePending = pendingStates.last()
        assertEquals("mensaje anterior", messagePending.partnerMessage)
        assertTrue(messagePending.partnerMessageLoaded)
        assertTrue(messagePending.loadingPartnerMessage)
        assertEquals("nuevo mensaje", state.partnerMessage)
        assertTrue(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
    }

    @Test
    fun `refresh preserves loaded message when message refresh fails`() = runBlocking {
        val existingProfile = TestDtos.visualProfile(partnerPersonalMessageSubmitted = true).toDomain()
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(partnerPersonalMessageSubmitted = true)
        )
        api.partnerMessageResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
        val current = partnerProfileState(profile = existingProfile).copy(
            partnerMessage = "mensaje anterior",
            partnerMessageLoaded = true,
        )

        val state = coordinator.refresh(
            current = current,
            onPending = {},
        )

        assertEquals("mensaje anterior", state.partnerMessage)
        assertTrue(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
        assertTrue(state.partnerMessageError is ApiError.Backend)
        assertEquals(null, state.error)
    }

    @Test
    fun `refresh clears stale message when profile reports no submitted message`() = runBlocking {
        val existingProfile = TestDtos.visualProfile(partnerPersonalMessageSubmitted = true).toDomain()
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(partnerPersonalMessageSubmitted = false)
        )
        val current = partnerProfileState(profile = existingProfile).copy(
            partnerMessage = "mensaje anterior",
            partnerMessageLoaded = true,
            partnerMessageError = ApiError.Unexpected("previous"),
        )

        val state = coordinator.refresh(
            current = current,
            onPending = {},
        )

        assertEquals(null, state.partnerMessage)
        assertFalse(state.partnerMessageLoaded)
        assertFalse(state.loadingPartnerMessage)
        assertEquals(null, state.partnerMessageError)
        assertEquals(listOf("getVisualProfile"), api.calls)
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

    private fun getPartnerPersonalMessage(api: FakeRealsApi): GetPartnerPersonalMessageUseCase {
        val repository = MatchRepository(api, FakeAuthTokenProvider(), testApiExecutor())
        return GetPartnerPersonalMessageUseCase(repository)
    }
}
