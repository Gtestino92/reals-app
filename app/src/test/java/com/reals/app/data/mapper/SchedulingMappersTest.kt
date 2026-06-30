package com.reals.app.data.mapper

import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProposalStatus
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingMappersTest {
    @Test
    fun `ConnectionResponseDto maps scheduling phase`() {
        val connection = TestDtos.connection(state = "SCHEDULING_PHASE").toDomain()

        assertEquals("connection-1", connection.id)
        assertEquals("match-1", connection.matchId)
        assertEquals(ConnectionState.SchedulingPhase, connection.state)
        assertEquals("2026-06-19T21:00:00Z", connection.schedulingExpiresAt)
    }

    @Test
    fun `NegotiationResponseDto maps pending confirmed and failed states`() {
        assertEquals(NegotiationStatus.Pending, TestDtos.negotiation("PENDING").toDomain().status)
        assertEquals(NegotiationStatus.Confirmed, TestDtos.negotiation("CONFIRMED").toDomain().status)
        assertEquals(NegotiationStatus.Failed, TestDtos.negotiation("FAILED").toDomain().status)
    }

    @Test
    fun `NegotiationResponseDto maps confirmed slot and chat`() {
        val negotiation = TestDtos.negotiation("CONFIRMED").toDomain()

        assertEquals(TestDtos.now, negotiation.confirmedDateTime)
        assertEquals("chat-2", negotiation.chatId)
        assertEquals("2026-06-19T21:00:00Z", negotiation.schedulingExpiresAt)
    }

    @Test
    fun `ScheduleProposalResponseDto maps slots with offset date times`() {
        val proposal = TestDtos.proposal(status = "ACCEPTED").toDomain()

        assertEquals("proposal-1", proposal.id)
        assertEquals("2026-06-18T21:00:00+00:00", proposal.proposedDateTime)
        assertEquals(ProposalStatus.Accepted, proposal.status)
        assertEquals(1, proposal.preferenceOrder)
    }

    @Test
    fun `scheduling mappers preserve unknown statuses`() {
        assertTrue(TestDtos.connection("NEW").toDomain().state is ConnectionState.Unknown)
        assertTrue(TestDtos.negotiation("WAITING").toDomain().status is NegotiationStatus.Unknown)
        assertTrue(TestDtos.proposal("MAYBE").toDomain().status is ProposalStatus.Unknown)
    }
}
