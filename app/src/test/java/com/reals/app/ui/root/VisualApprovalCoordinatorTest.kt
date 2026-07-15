package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.di.VisualApprovalFeatureDependencies
import com.reals.app.domain.model.VisualDecision
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.SubmitVisualDecisionUseCase
import com.reals.app.data.repository.MatchRepository
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

class VisualApprovalCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = VisualApprovalCoordinator(visualDependencies(api))

    @Test
    fun `open with blank match id does nothing`() = runBlocking {
        val result = coordinator.open(
            session = TestDomain.session(),
            matchId = "   ",
            locallyHidden = false,
            onPending = {},
        )

        assertEquals(VisualApprovalFlowResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `open with locally hidden visual review returns home`() = runBlocking {
        val result = coordinator.open(
            session = TestDomain.session(),
            matchId = "match-1",
            locallyHidden = true,
            onPending = {},
        )

        assertTrue(result is VisualApprovalFlowResult.ReturnHome)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `refresh ignores while busy`() = runBlocking {
        val result = coordinator.refresh(
            current = visualState(loading = true),
            locallyHidden = false,
            onPending = {},
        )

        assertEquals(VisualApprovalFlowResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `load copies myPersonalMessageSubmitted from profile`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))
        api.visualProfileResponse = Response.success(TestDtos.visualProfile(myPersonalMessageSubmitted = true))

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            initialMatch = null,
            previous = null,
            locallyHidden = false,
        )

        val state = (result as VisualApprovalLoadResult.Show).state
        assertEquals(true, state.myPersonalMessageSubmitted)
        assertEquals(null, state.partnerMessage)
        assertEquals(false, state.partnerMessageLoaded)
        assertFalse(api.calls.contains("getPartnerPersonalMessage"))
    }

    @Test
    fun `load does not fetch unread partner message automatically`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = true,
                partnerPersonalMessageRead = false,
                decisionRequiresPartnerPersonalMessageRead = true,
            )
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            initialMatch = null,
            previous = null,
            locallyHidden = false,
        )

        val state = (result as VisualApprovalLoadResult.Show).state
        val profile = requireNotNull(state.profile)
        assertFalse(profile.partnerPersonalMessageRead)
        assertTrue(profile.decisionRequiresPartnerPersonalMessageRead)
        assertEquals(null, state.partnerMessage)
        assertFalse(state.partnerMessageLoaded)
        assertFalse(api.calls.contains("getPartnerPersonalMessage"))
    }

    @Test
    fun `readPartnerPersonalMessage success marks partner message read locally`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = true,
                partnerPersonalMessageRead = false,
                decisionRequiresPartnerPersonalMessageRead = true,
            )
        )
        val loadResult = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            initialMatch = null,
            previous = null,
            locallyHidden = false,
        )
        val unreadState = (loadResult as VisualApprovalLoadResult.Show).state

        val state = coordinator.readPartnerPersonalMessage(unreadState)

        val profile = requireNotNull(state.profile)
        assertTrue(profile.partnerPersonalMessageRead)
        assertFalse(profile.decisionRequiresPartnerPersonalMessageRead)
        assertEquals("hola", state.partnerMessage)
        assertTrue(state.partnerMessageLoaded)
        assertFalse(state.readingPartnerMessage)
        assertEquals(1, api.calls.count { it == "getPartnerPersonalMessage" })
    }

    @Test
    fun `readPartnerPersonalMessage failure keeps unread metadata`() = runBlocking {
        api.partnerMessageResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
        val current = RealsRootUiState.VisualApproval(
            session = TestDomain.session(),
            matchId = "match-1",
            profile = TestDtos.visualProfile(
                    partnerPersonalMessageSubmitted = true,
                    partnerPersonalMessageRead = false,
                    decisionRequiresPartnerPersonalMessageRead = true,
                ).toDomain(),
        )

        val state = coordinator.readPartnerPersonalMessage(current)

        val profile = requireNotNull(state.profile)
        assertFalse(profile.partnerPersonalMessageRead)
        assertTrue(profile.decisionRequiresPartnerPersonalMessageRead)
        assertFalse(state.partnerMessageLoaded)
        assertFalse(state.readingPartnerMessage)
        assertTrue(state.partnerMessageError is ApiError.Backend)
        assertTrue(state.error is ApiError.Backend)
        assertEquals(1, api.calls.count { it == "getPartnerPersonalMessage" })
    }

    @Test
    fun `load fetches partner message automatically when already read`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = true,
                partnerPersonalMessageRead = true,
                decisionRequiresPartnerPersonalMessageRead = false,
            )
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            initialMatch = null,
            previous = null,
            locallyHidden = false,
        )

        val state = (result as VisualApprovalLoadResult.Show).state
        val profile = requireNotNull(state.profile)
        assertTrue(profile.partnerPersonalMessageRead)
        assertFalse(profile.decisionRequiresPartnerPersonalMessageRead)
        assertEquals("hola", state.partnerMessage)
        assertTrue(state.partnerMessageLoaded)
        assertEquals(1, api.calls.count { it == "getPartnerPersonalMessage" })
    }

    @Test
    fun `load does not fetch partner message when none submitted`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))
        api.visualProfileResponse = Response.success(
            TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = false,
                partnerPersonalMessageRead = true,
                decisionRequiresPartnerPersonalMessageRead = false,
            )
        )

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            initialMatch = null,
            previous = null,
            locallyHidden = false,
        )

        val state = (result as VisualApprovalLoadResult.Show).state
        val profile = requireNotNull(state.profile)
        assertFalse(profile.partnerPersonalMessageSubmitted)
        assertFalse(profile.decisionRequiresPartnerPersonalMessageRead)
        assertEquals(null, state.partnerMessage)
        assertFalse(state.partnerMessageLoaded)
        assertFalse(api.calls.contains("getPartnerPersonalMessage"))
    }

    @Test
    fun `savePersonalMessage success sets myPersonalMessageSubmitted true`() = runBlocking {
        val current = RealsRootUiState.VisualApproval(
            session = TestDomain.session(),
            matchId = "match-1",
        )

        val state = coordinator.savePersonalMessage(current, "mensaje")

        assertEquals(true, state.myPersonalMessageSubmitted)
        assertEquals("Guardamos tu mensaje personal.", state.message)
        assertEquals("mensaje", api.personalMessageBody?.message)
    }

    @Test
    fun `savePersonalMessage conflict marks already submitted`() = runBlocking {
        api.unitResponse = backendErrorResponse(409, "DOMAIN_CONFLICT", "already submitted")
        val current = RealsRootUiState.VisualApproval(
            session = TestDomain.session(),
            matchId = "match-1",
        )

        val state = coordinator.savePersonalMessage(current, "mensaje")

        assertEquals(true, state.myPersonalMessageSubmitted)
        assertEquals("Ya habias guardado tu mensaje personal.", state.message)
        assertEquals(null, state.error)
    }

    @Test
    fun `save personal message action rejects blank without backend call`() = runBlocking {
        val result = coordinator.savePersonalMessageAction(
            current = visualState(),
            message = "   ",
            onPending = {},
        )

        assertTrue(result is VisualApprovalFlowResult.Show)
        val state = (result as VisualApprovalFlowResult.Show).state
        assertTrue(state.error is ApiError.Unexpected)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `save personal message action rejects markup without backend call`() = runBlocking {
        val result = coordinator.savePersonalMessageAction(
            current = visualState(),
            message = "<b>hola</b>",
            onPending = {},
        )

        assertTrue(result is VisualApprovalFlowResult.Show)
        val state = (result as VisualApprovalFlowResult.Show).state
        assertTrue(state.error is ApiError.Unexpected)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `save personal message action returns already saved message`() = runBlocking {
        val result = coordinator.savePersonalMessageAction(
            current = visualState(myPersonalMessageSubmitted = true),
            message = "mensaje",
            onPending = {},
        )

        assertTrue(result is VisualApprovalFlowResult.Show)
        val state = (result as VisualApprovalFlowResult.Show).state
        assertEquals("Ya habias guardado tu mensaje personal.", state.message)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `read partner personal message action ignores when already loaded`() = runBlocking {
        val result = coordinator.readPartnerPersonalMessageAction(
            current = visualState(
                partnerMessageLoaded = true,
                profile = TestDtos.visualProfile(
                    partnerPersonalMessageSubmitted = true,
                ).toDomain(),
            ),
            onPending = {},
        )

        assertEquals(VisualApprovalFlowResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `load routes home when visual state changed`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_APPROVED"))

        val result = coordinator.load(
            session = TestDomain.session(),
            matchId = "match-1",
            initialMatch = null,
            previous = null,
            locallyHidden = false,
        )

        assertTrue(result is VisualApprovalLoadResult.RouteHome)
    }

    @Test
    fun `savePersonalMessage non conflict keeps error`() = runBlocking {
        api.unitResponse = backendErrorResponse(403, "ACCESS_DENIED", "forbidden")
        val current = RealsRootUiState.VisualApproval(
            session = TestDomain.session(),
            matchId = "match-1",
        )

        val state = coordinator.savePersonalMessage(current, "mensaje")

        assertEquals(false, state.myPersonalMessageSubmitted)
        assertTrue(state.error is ApiError.Backend)
    }

    @Test
    fun `submit visual decision is blocked when partner message read is required`() = runBlocking {
        val result = coordinator.submitDecision(
            current = visualState(
                profile = TestDtos.visualProfile(
                    partnerPersonalMessageSubmitted = true,
                    partnerPersonalMessageRead = false,
                    decisionRequiresPartnerPersonalMessageRead = true,
                ).toDomain(),
            ),
            decision = VisualDecision.Approved,
            onPending = {},
        )

        assertEquals(VisualApprovalFlowResult.Ignore, result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `submit visual decision terminal success hides visual review and reloads home`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_APPROVED"))
        var pending: RealsRootUiState.VisualApproval? = null

        val result = coordinator.submitDecision(
            current = visualState(),
            decision = VisualDecision.Approved,
            onPending = { pending = it },
        )

        assertEquals(true, pending?.deciding)
        assertEquals("Aprobando...", pending?.decidingLabel)
        assertTrue(result is VisualApprovalFlowResult.ReloadHome)
        result as VisualApprovalFlowResult.ReloadHome
        assertEquals("match-1", result.hideVisualMatchId)
        assertFalse(result.autoNavigateEngagements)
        assertEquals(listOf("submitVisualDecision"), api.calls)
    }

    @Test
    fun `submit visual decision active success stays in visual approval`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("CHAT_ACTIVE"))

        val result = coordinator.submitDecision(
            current = visualState(),
            decision = VisualDecision.Rejected,
            onPending = {},
        )

        assertTrue(result is VisualApprovalFlowResult.Show)
        result as VisualApprovalFlowResult.Show
        assertEquals("match-1", result.hideVisualMatchId)
        assertEquals("Guardamos tu decisión.", result.state.message)
        assertFalse(result.state.deciding)
    }

    @Test
    fun `submit visual decision failure keeps visual approval and exposes error`() = runBlocking {
        api.matchResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")

        val result = coordinator.submitDecision(
            current = visualState(),
            decision = VisualDecision.Approved,
            onPending = {},
        )

        assertTrue(result is VisualApprovalFlowResult.Show)
        val state = (result as VisualApprovalFlowResult.Show).state
        assertFalse(state.deciding)
        assertEquals(null, state.decidingLabel)
        assertTrue(state.error is ApiError.Backend)
    }

    private fun visualState(
        loading: Boolean = false,
        myPersonalMessageSubmitted: Boolean = false,
        partnerMessageLoaded: Boolean = false,
        profile: com.reals.app.domain.model.VisualProfile? = null,
    ): RealsRootUiState.VisualApproval = RealsRootUiState.VisualApproval(
        session = TestDomain.session(),
        matchId = "match-1",
        profile = profile,
        myPersonalMessageSubmitted = myPersonalMessageSubmitted,
        partnerMessageLoaded = partnerMessageLoaded,
        loading = loading,
    )

    private fun visualDependencies(api: FakeRealsApi): VisualApprovalFeatureDependencies {
        val repository = MatchRepository(api, FakeAuthTokenProvider(), testApiExecutor())
        return VisualApprovalFeatureDependencies(
            getMatch = GetMatchUseCase(repository),
            getVisualProfile = GetVisualProfileUseCase(repository),
            submitVisualDecision = SubmitVisualDecisionUseCase(repository),
            putMyPersonalMessage = PutMyPersonalMessageUseCase(repository),
            getPartnerPersonalMessage = GetPartnerPersonalMessageUseCase(repository),
        )
    }
}
