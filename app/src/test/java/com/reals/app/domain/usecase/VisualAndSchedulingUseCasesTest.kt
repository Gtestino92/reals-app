package com.reals.app.domain.usecase

import com.reals.app.data.repository.MatchRepository
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.domain.model.VisualDecision
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualAndSchedulingUseCasesTest {
    private val api = FakeRealsApi()
    private val matchRepository = MatchRepository(api, FakeAuthTokenProvider(), testApiExecutor())
    private val schedulingRepository = SchedulingRepository(api, FakeAuthTokenProvider(), testApiExecutor())

    @Test
    fun `SavePersonalMessageUseCase delegates`() = runBlocking {
        PutMyPersonalMessageUseCase(matchRepository)("match-1", "mensaje").successValue()

        assertEquals("putMyPersonalMessage", api.calls.single())
        assertEquals("mensaje", api.personalMessageBody?.message)
    }

    @Test
    fun `SubmitVisualDecisionUseCase delegates`() = runBlocking {
        SubmitVisualDecisionUseCase(matchRepository)("match-1", VisualDecision.Rejected).successValue()

        assertEquals("submitVisualDecision", api.calls.single())
        assertEquals("REJECTED", api.visualDecisionBody?.decision)
    }

    @Test
    fun `SubmitSchedulingProposalsUseCase delegates`() = runBlocking {
        val slots = listOf("2026-06-18T21:00:00Z")

        SubmitSchedulingProposalsUseCase(schedulingRepository)("connection-1", slots).successValue()

        assertEquals("submitConnectionProposals", api.calls.single())
        assertEquals(slots, api.proposalsBody?.proposedDateTimes)
    }

    @Test
    fun `Accept and Reject scheduling use cases delegate`() = runBlocking {
        AcceptSchedulingProposalUseCase(schedulingRepository)("connection-1", "proposal-1").successValue()
        RejectSchedulingRoundUseCase(schedulingRepository)("connection-1").successValue()

        assertEquals(listOf("acceptConnectionProposal", "rejectConnectionNegotiationRound"), api.calls)
    }
}
