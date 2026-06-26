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
import com.reals.app.domain.usecase.GetSchedulingNegotiationUseCase
import com.reals.app.domain.usecase.GetSchedulingProposalsUseCase
import com.reals.app.domain.usecase.RejectSchedulingRoundUseCase
import com.reals.app.domain.usecase.SubmitSchedulingProposalsUseCase
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class SchedulingCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = SchedulingCoordinator(schedulingDependencies(api))

    @Test
    fun `load scheduling success updates state`() = runBlocking {
        val state = coordinator.load(
            session = TestDomain.session(),
            connectionId = "connection-1",
            matchId = "match-1",
            partnerName = "Alex",
        )

        assertEquals(false, state.loading)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(1, state.proposals.size)
    }

    @Test
    fun `submit proposals success updates state`() = runBlocking {
        val current = baseState()
        val slots = listOf("2026-06-18T21:00:00Z")

        val state = coordinator.submitProposals(current, slots)

        assertEquals(false, state.submitting)
        assertEquals("Enviamos tus horarios.", state.message)
        assertEquals(slots, api.proposalsBody?.proposedDateTimes)
    }

    @Test
    fun `submit proposals failure keeps scheduling backend error`() = runBlocking {
        api.proposalsResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_PROPOSALS_ALREADY_SUBMITTED",
            message = "raw backend message",
        )

        val state = coordinator.submitProposals(
            current = baseState(),
            proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
        )
        val error = state.error as ApiError.Backend

        assertEquals(false, state.submitting)
        assertEquals(BackendErrorCode.SchedulingProposalsAlreadySubmitted, error.backendErrorCode)
        assertEquals(
            "Ya enviaste tus horarios para esta ronda.",
            error.toUserMessage(ErrorContext.Scheduling),
        )
    }

    @Test
    fun `accept proposal confirmed updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("CONFIRMED"))

        val state = coordinator.acceptProposal(baseState(), "proposal-1")

        assertEquals(false, state.submitting)
        assertEquals("Horario confirmado.", state.message)
        assertEquals(NegotiationStatus.Confirmed, state.negotiation?.status)
    }

    @Test
    fun `reject round failed updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("FAILED"))

        val state = coordinator.rejectRound(baseState())

        assertEquals(false, state.submitting)
        assertEquals("No hubo acuerdo.", state.message)
        assertEquals(NegotiationStatus.Failed, state.negotiation?.status)
    }

    @Test
    fun `reject round pending next round updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING").copy(roundNumber = 3))
        val current = baseState().copy(
            negotiation = TestDtos.negotiation("PENDING").copy(roundNumber = 2).toDomain(),
        )

        val state = coordinator.rejectRound(current)

        assertEquals(false, state.submitting)
        assertEquals("Ronda rechazada, se abrio una nueva ronda.", state.message)
        assertEquals(3, state.negotiation?.roundNumber)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(listOf("rejectConnectionNegotiationRound", "getConnectionNegotiation", "getConnectionProposals"), api.calls)
    }

    private fun baseState() = RealsRootUiState.Scheduling(
        session = TestDomain.session(),
        connectionId = "connection-1",
        matchId = "match-1",
        partnerName = "Alex",
    )

    private fun schedulingDependencies(api: FakeRealsApi): SchedulingFeatureDependencies {
        val repository = SchedulingRepository(api, FakeAuthTokenProvider(), testApiExecutor())
        return SchedulingFeatureDependencies(
            getNegotiation = GetSchedulingNegotiationUseCase(repository),
            getProposals = GetSchedulingProposalsUseCase(repository),
            submitProposals = SubmitSchedulingProposalsUseCase(repository),
            acceptProposal = AcceptSchedulingProposalUseCase(repository),
            rejectRound = RejectSchedulingRoundUseCase(repository),
        )
    }
}
