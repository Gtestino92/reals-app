package com.reals.app.data.repository

import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProposalStatus
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class SchedulingRepositoryTest {
    private val api = FakeRealsApi()
    private val repository = SchedulingRepository(api, FakeAuthTokenProvider(), testApiExecutor())

    @Test
    fun `get scheduling maps negotiation and proposals`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING"))
        api.proposalsResponse = Response.success(listOf(TestDtos.proposal("PENDING")))

        val negotiation = repository.getNegotiation("connection-1").successValue()
        val proposals = repository.getProposals("connection-1").successValue()

        assertEquals(NegotiationStatus.Pending, negotiation.status)
        assertEquals(ProposalStatus.Pending, proposals.single().status)
        assertEquals(listOf("getConnectionNegotiation", "getConnectionProposals"), api.calls)
    }

    @Test
    fun `submit proposals sends expected slot list`() = runBlocking {
        val slots = listOf("2026-06-18T21:00:00Z", "2026-06-19T21:00:00Z")

        repository.submitProposals("connection-1", slots).successValue()

        assertEquals("submitConnectionProposals", api.calls.single())
        assertEquals(slots, api.proposalsBody?.proposedDateTimes)
    }

    @Test
    fun `accept proposal maps confirmed negotiation`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("CONFIRMED"))

        val negotiation = repository.acceptProposal("connection-1", "proposal-1").successValue()

        assertEquals("acceptConnectionProposal", api.calls.single())
        assertEquals("connection-1/proposal-1", api.lastPathId)
        assertEquals(NegotiationStatus.Confirmed, negotiation.status)
    }

    @Test
    fun `reject round maps failed or next round state`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("FAILED"))

        val negotiation = repository.rejectRound("connection-1").successValue()

        assertEquals("rejectConnectionNegotiationRound", api.calls.single())
        assertEquals(NegotiationStatus.Failed, negotiation.status)
    }
}
