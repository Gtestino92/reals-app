package com.reals.app.ui.root

import com.reals.app.di.SchedulingFeatureDependencies
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
    fun `accept proposal confirmed updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("CONFIRMED"))

        val state = coordinator.acceptProposal(baseState(), "proposal-1")

        assertEquals(false, state.submitting)
        assertEquals("Aceptamos el horario.", state.message)
        assertEquals(NegotiationStatus.Confirmed, state.negotiation?.status)
    }

    @Test
    fun `reject round failed updates state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("FAILED"))

        val state = coordinator.rejectRound(baseState())

        assertEquals(false, state.submitting)
        assertEquals("Abrimos una nueva ronda de horarios.", state.message)
        assertEquals(NegotiationStatus.Failed, state.negotiation?.status)
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
