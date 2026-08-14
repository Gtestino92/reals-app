package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.network.toUserMessage
import com.reals.app.di.SchedulingFeatureDependencies
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.usecase.AcceptSchedulingProposalUseCase
import com.reals.app.domain.usecase.GetSchedulingAvailabilityUseCase
import com.reals.app.domain.usecase.GetSchedulingNegotiationUseCase
import com.reals.app.domain.usecase.GetSchedulingProposalsUseCase
import com.reals.app.domain.usecase.RejectPartnerSchedulingProposalsUseCase
import com.reals.app.domain.usecase.SubmitSchedulingProposalsUseCase
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulingCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = SchedulingCoordinator(schedulingDependencies(api))

    @Test
    fun `initial scheduling refresh success updates state`() = runBlocking {
        val state = coordinator.refresh(
            current = baseState().copy(
                returnHomeSurface = HomeSurface.Pending,
                loading = true,
                negotiation = null,
            ),
            silent = false,
        )

        assertEquals(false, state.loading)
        assertEquals(HomeSurface.Pending, state.returnHomeSurface)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(1, state.proposals.size)
        assertEquals(60L, state.availability?.conflictWindowMinutes)
    }

    @Test
    fun `initial scheduling refresh negotiation failure does not request proposals`() = runBlocking {
        api.negotiationResponse = backendErrorResponse(
            statusCode = 404,
            code = "SCHEDULING_NEGOTIATION_NOT_FOUND",
        )

        val state = coordinator.refresh(
            current = baseState().copy(
                loading = true,
                negotiation = null,
            ),
            silent = false,
        )

        assertEquals(false, state.loading)
        assertEquals(false, state.refreshing)
        assertEquals(null, state.negotiation)
        assertEquals(listOf("getConnectionNegotiation"), api.calls)
        assertEquals(ApiError.Backend::class, state.error!!::class)
    }

    @Test
    fun `initial scheduling refresh proposal failure preserves loaded negotiation`() = runBlocking {
        api.proposalsResponse = backendErrorResponse(
            statusCode = 500,
            code = "SCHEDULING_PROPOSALS_UNAVAILABLE",
        )

        val state = coordinator.refresh(
            current = baseState().copy(
                loading = true,
                negotiation = null,
            ),
            silent = false,
        )

        assertEquals(false, state.loading)
        assertEquals(false, state.refreshing)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(emptyList<Any>(), state.proposals)
        assertEquals(60L, state.availability?.conflictWindowMinutes)
        assertEquals(ApiError.Backend::class, state.error!!::class)
    }

    @Test
    fun `refresh availability failure preserves prior successful snapshot`() = runBlocking {
        val priorAvailability = TestDtos.schedulingAvailability(
            conflictWindowMinutes = 45,
            unavailableWindows = listOf(TestDtos.unavailableWindow()),
        ).toDomain()
        api.schedulingAvailabilityResponse = backendErrorResponse(
            statusCode = 503,
            code = "SCHEDULING_AVAILABILITY_UNAVAILABLE",
        )

        val state = coordinator.refresh(
            current = baseState().copy(availability = priorAvailability),
            silent = false,
        )

        assertEquals(false, state.loading)
        assertEquals(false, state.refreshing)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(1, state.proposals.size)
        assertEquals(priorAvailability, state.availability)
        assertEquals(ApiError.Backend::class, state.error!!::class)
    }

    @Test
    fun `refreshed availability replaces prior successful snapshot`() = runBlocking {
        val priorAvailability = TestDtos.schedulingAvailability(
            conflictWindowMinutes = 45,
        ).toDomain()
        api.schedulingAvailabilityResponse = Response.success(
            TestDtos.schedulingAvailability(
                conflictWindowMinutes = 75,
                unavailableWindows = listOf(TestDtos.unavailableWindow()),
            ),
        )

        val state = coordinator.refresh(
            current = baseState().copy(availability = priorAvailability),
            silent = false,
        )

        assertEquals(75L, state.availability?.conflictWindowMinutes)
        assertEquals(1, state.availability?.unavailableWindows?.size)
        assertEquals(null, state.error)
    }

    @Test
    fun `submit proposals success updates state`() = runBlocking {
        val current = baseState()
        val slots = listOf("2026-06-18T21:00:00Z")

        val state = coordinator.submitProposals(current, slots, onPending = {})

        assertEquals(false, state.submitting)
        assertEquals(null, state.submittingLabel)
        assertEquals("Enviamos tus horarios.", state.message)
        assertEquals(2, api.proposalsBody?.expectedRoundNumber)
        assertEquals(slots, api.proposalsBody?.proposedDateTimes)
    }

    @Test
    fun `submit proposals without negotiation does not call api`() = runBlocking {
        val state = coordinator.submitProposals(
            current = baseState().copy(negotiation = null),
            proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
            onPending = {},
        )

        assertEquals(emptyList<String>(), api.calls)
        assertEquals(false, state.submitting)
        assertEquals(null, state.message)
        assertEquals(ApiError.Unexpected::class, state.error!!::class)
    }

    @Test
    fun `submit proposals failure keeps scheduling backend error`() = runBlocking {
        api.submitProposalsResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_PROPOSALS_ALREADY_SUBMITTED",
            message = "raw backend message",
        )

        val state = coordinator.submitProposals(
            current = baseState(),
            proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
            onPending = {},
        )
        val error = state.error as ApiError.Backend

        assertEquals(false, state.submitting)
        assertEquals(null, state.submittingLabel)
        assertEquals(BackendErrorCode.SchedulingProposalsAlreadySubmitted, error.backendErrorCode)
        assertEquals(
            "Ya enviaste tus horarios para ésta ronda.",
            error.toUserMessage(ErrorContext.Scheduling),
        )
        assertNull(state.message)
    }

    @Test
    fun `submit slot conflict remains primary and refreshes scheduling snapshot`() = runBlocking {
        api.submitProposalsResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_SLOT_CONFLICT",
            message = "raw backend message",
        )
        api.schedulingAvailabilityResponse = Response.success(
            TestDtos.schedulingAvailability(
                unavailableWindows = listOf(TestDtos.unavailableWindow()),
            ),
        )

        val state = coordinator.submitProposals(
            current = baseState(),
            proposedDateTimes = listOf("2026-07-30T19:00:00-03:00"),
            onPending = {},
        )
        val error = state.error as ApiError.Backend

        assertEquals(BackendErrorCode.SchedulingSlotConflict, error.backendErrorCode)
        assertNull(state.message)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(1, state.proposals.size)
        assertEquals(1, state.availability?.unavailableWindows?.size)
        assertEquals(
            listOf(
                "submitConnectionProposals",
                "getConnectionNegotiation",
                "getConnectionProposals",
                "getConnectionSchedulingAvailability",
            ),
            api.calls,
        )
    }

    @Test
    fun `submit proposals publishes pending before request completes and clears feedback`() = runTest {
        val submitStarted = CompletableDeferred<Unit>()
        val releaseSubmit = CompletableDeferred<Unit>()
        api.beforeSubmitConnectionProposalsResponse = {
            submitStarted.complete(Unit)
            releaseSubmit.await()
        }
        val previousError = ApiError.Unexpected("old")
        val current = baseState().copy(
            returnHomeSurface = HomeSurface.Pending,
            submittingLabel = "Enviando horarios...",
            error = previousError,
            message = "mensaje anterior",
        )
        var pending: RealsRootUiState.Scheduling? = null

        val result = async {
            coordinator.submitProposals(
                current = current,
                proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
                onPending = { pending = it },
            )
        }

        submitStarted.await()
        runCurrent()

        assertEquals(true, pending?.submitting)
        assertEquals(HomeSurface.Pending, pending?.returnHomeSurface)
        assertEquals("Enviando horarios...", pending?.submittingLabel)
        assertNull(pending?.error)
        assertNull(pending?.message)

        releaseSubmit.complete(Unit)
        val final = result.await()
        assertEquals(false, final.submitting)
        assertEquals(HomeSurface.Pending, final.returnHomeSurface)
    }

    @Test
    fun `invalid proposals error remains primary after successful refresh`() = runBlocking {
        api.submitProposalsResponse = backendErrorResponse(
            statusCode = 400,
            code = "SCHEDULING_INVALID_PROPOSALS",
            message = "raw backend message",
        )
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING"))
        api.proposalsResponse = Response.success(listOf(TestDtos.proposal()))

        val state = coordinator.submitProposals(
            current = baseState(),
            proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
            onPending = {},
        )
        val error = state.error as ApiError.Backend

        assertEquals(BackendErrorCode.SchedulingInvalidProposals, error.backendErrorCode)
        assertNull(state.message)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(1, state.proposals.size)
    }

    @Test
    fun `round changed error remains primary after successful refresh`() = runBlocking {
        api.submitProposalsResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_ROUND_CHANGED",
            message = "raw backend message",
        )
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING"))
        api.proposalsResponse = Response.success(listOf(TestDtos.proposal()))

        val state = coordinator.submitProposals(
            current = baseState(),
            proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
            onPending = {},
        )
        val error = state.error as ApiError.Backend

        assertEquals(BackendErrorCode.SchedulingRoundChanged, error.backendErrorCode)
        assertNull(state.message)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
    }

    @Test
    fun `submit refresh failure does not replace submit failure`() = runBlocking {
        api.submitProposalsResponse = backendErrorResponse(
            statusCode = 400,
            code = "SCHEDULING_INVALID_PROPOSALS",
            message = "raw backend message",
        )
        api.negotiationResponse = backendErrorResponse(
            statusCode = 404,
            code = "SCHEDULING_NEGOTIATION_NOT_FOUND",
        )

        val state = coordinator.submitProposals(
            current = baseState(),
            proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
            onPending = {},
        )
        val error = state.error as ApiError.Backend

        assertEquals(BackendErrorCode.SchedulingInvalidProposals, error.backendErrorCode)
        assertNull(state.message)
    }

    @Test
    fun `accept proposal confirmed updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("CONFIRMED"))

        val state = coordinator.acceptProposal(baseState(), "proposal-1", onPending = {})

        assertEquals(false, state.submitting)
        assertEquals("Horario confirmado.", state.message)
        assertEquals(NegotiationStatus.Confirmed, state.negotiation?.status)
    }

    @Test
    fun `proposal not available after acceptance remains primary after refresh`() = runBlocking {
        api.acceptProposalResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_PROPOSAL_NOT_AVAILABLE",
        )
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING"))
        api.proposalsResponse = Response.success(
            listOf(TestDtos.proposal("PENDING").copy(userId = "partner")),
        )

        val state = coordinator.acceptProposal(baseState(), "proposal-1", onPending = {})
        val error = state.error as ApiError.Backend

        assertEquals(BackendErrorCode.SchedulingProposalNotAvailable, error.backendErrorCode)
        assertNull(state.message)
        assertEquals(1, state.proposals.size)
        assertEquals("partner", state.proposals.single().userId)
    }

    @Test
    fun `accept slot conflict remains primary and refreshes scheduling snapshot`() = runBlocking {
        api.acceptProposalResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_SLOT_CONFLICT",
        )
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING"))
        api.proposalsResponse = Response.success(
            listOf(TestDtos.proposal("PENDING").copy(userId = "partner")),
        )
        api.schedulingAvailabilityResponse = Response.success(
            TestDtos.schedulingAvailability(
                unavailableWindows = listOf(TestDtos.unavailableWindow()),
            ),
        )

        val state = coordinator.acceptProposal(baseState(), "proposal-1", onPending = {})
        val error = state.error as ApiError.Backend

        assertEquals(BackendErrorCode.SchedulingSlotConflict, error.backendErrorCode)
        assertNull(state.message)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals("partner", state.proposals.single().userId)
        assertEquals(1, state.availability?.unavailableWindows?.size)
    }

    @Test
    fun `accept proposal publishes pending before request completes`() = runTest {
        val acceptStarted = CompletableDeferred<Unit>()
        val releaseAccept = CompletableDeferred<Unit>()
        api.beforeAcceptConnectionProposalResponse = {
            acceptStarted.complete(Unit)
            releaseAccept.await()
        }
        val current = baseState().copy(error = ApiError.Unexpected("old"), message = "old")
        var pending: RealsRootUiState.Scheduling? = null

        val result = async {
            coordinator.acceptProposal(current, "proposal-1", onPending = { pending = it })
        }

        acceptStarted.await()
        runCurrent()

        assertEquals(true, pending?.submitting)
        assertNull(pending?.error)
        assertNull(pending?.message)

        releaseAccept.complete(Unit)
        assertEquals(false, result.await().submitting)
    }

    @Test
    fun `reject partner proposals failed updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("FAILED"))

        val state = coordinator.rejectPartnerProposals(baseState(), onPending = {})

        assertEquals(false, state.submitting)
        assertEquals("No hubo acuerdo.", state.message)
        assertEquals(NegotiationStatus.Failed, state.negotiation?.status)
        assertEquals(2, api.rejectPartnerProposalsBody?.expectedRoundNumber)
    }

    @Test
    fun `reject partner proposals same round updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING").copy(roundNumber = 2))

        val state = coordinator.rejectPartnerProposals(baseState(), onPending = {})

        assertEquals(false, state.submitting)
        assertEquals("Rechazaste las opciones recibidas.", state.message)
        assertEquals(2, state.negotiation?.roundNumber)
    }

    @Test
    fun `reject partner proposals pending next round updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING").copy(roundNumber = 3))
        val current = baseState().copy(
            negotiation = TestDtos.negotiation("PENDING").copy(roundNumber = 2).toDomain(),
        )

        val state = coordinator.rejectPartnerProposals(current, onPending = {})

        assertEquals(false, state.submitting)
        assertEquals("Ambas listas fueron rechazadas. Se abrio una nueva ronda.", state.message)
        assertEquals(3, state.negotiation?.roundNumber)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(
            listOf(
                "rejectConnectionPartnerProposals",
                "getConnectionNegotiation",
                "getConnectionProposals",
                "getConnectionSchedulingAvailability",
            ),
            api.calls,
        )
    }

    @Test
    fun `partner proposals unavailable remains primary after refresh`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING"))
        api.proposalsResponse = Response.success(listOf(TestDtos.proposal()))
        api.rejectPartnerProposalsResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_PARTNER_PROPOSALS_NOT_AVAILABLE",
        )

        val state = coordinator.rejectPartnerProposals(baseState(), onPending = {})
        val error = state.error as ApiError.Backend

        assertEquals(BackendErrorCode.SchedulingPartnerProposalsNotAvailable, error.backendErrorCode)
        assertNull(state.message)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
    }

    @Test
    fun `reject partner proposals publishes pending before request completes`() = runTest {
        val rejectStarted = CompletableDeferred<Unit>()
        val releaseReject = CompletableDeferred<Unit>()
        api.beforeRejectConnectionPartnerProposalsResponse = {
            rejectStarted.complete(Unit)
            releaseReject.await()
        }
        val current = baseState().copy(error = ApiError.Unexpected("old"), message = "old")
        var pending: RealsRootUiState.Scheduling? = null

        val result = async {
            coordinator.rejectPartnerProposals(current, onPending = { pending = it })
        }

        rejectStarted.await()
        runCurrent()

        assertEquals(true, pending?.submitting)
        assertNull(pending?.error)
        assertNull(pending?.message)

        releaseReject.complete(Unit)
        assertEquals(false, result.await().submitting)
    }

    @Test
    fun `reject partner proposals without negotiation does not call api`() = runBlocking {
        val state = coordinator.rejectPartnerProposals(
            current = baseState().copy(negotiation = null),
            onPending = {},
        )

        assertEquals(emptyList<String>(), api.calls)
        assertEquals(false, state.submitting)
        assertEquals(null, state.message)
        assertEquals(ApiError.Unexpected::class, state.error!!::class)
    }

    private fun baseState() = RealsRootUiState.Scheduling(
        session = TestDomain.session(),
        connectionId = "connection-1",
        matchId = "match-1",
        partnerName = "Alex",
        negotiation = TestDtos.negotiation("PENDING").copy(roundNumber = 2).toDomain(),
    )

    private fun schedulingDependencies(api: FakeRealsApi): SchedulingFeatureDependencies {
        val repository = SchedulingRepository(api, FakeAuthTokenProvider(), testApiExecutor())
        return SchedulingFeatureDependencies(
            getNegotiation = GetSchedulingNegotiationUseCase(repository),
            getProposals = GetSchedulingProposalsUseCase(repository),
            getAvailability = GetSchedulingAvailabilityUseCase(repository),
            submitProposals = SubmitSchedulingProposalsUseCase(repository),
            acceptProposal = AcceptSchedulingProposalUseCase(repository),
            rejectPartnerProposals = RejectPartnerSchedulingProposalsUseCase(repository),
        )
    }
}
