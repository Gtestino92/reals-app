package com.reals.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendStatusParserTest {
    @Test
    fun `ProfileStatus parses backend statuses`() {
        assertEquals(ProfileStatus.Active, ProfileStatus.fromBackend("ACTIVE"))
        assertEquals(ProfileStatus.Draft, ProfileStatus.fromBackend("DRAFT"))
        assertEquals(ProfileStatus.Inactive, ProfileStatus.fromBackend("INACTIVE"))

        val unknown = ProfileStatus.fromBackend("SUSPENDED")
        assertTrue(unknown is ProfileStatus.Unknown)
        assertEquals("SUSPENDED", unknown.rawValue)
    }

    @Test
    fun `BackendUserStatus parses backend statuses`() {
        assertEquals(BackendUserStatus.Active, BackendUserStatus.fromBackend("ACTIVE"))
        assertEquals(BackendUserStatus.Deleted, BackendUserStatus.fromBackend("DELETED"))

        val unknown = BackendUserStatus.fromBackend("LOCKED")
        assertTrue(unknown is BackendUserStatus.Unknown)
        assertEquals("LOCKED", unknown.rawValue)
    }

    @Test
    fun `ChatExitRequestStatus parses TIMED_OUT`() {
        assertEquals(ChatExitRequestStatus.TimedOut, ChatExitRequestStatus.fromBackend("TIMED_OUT"))
    }

    @Test
    fun `ChatExitRequestStatus preserves unknown statuses`() {
        val status = ChatExitRequestStatus.fromBackend("WAITING_FOR_PARTNER")

        assertTrue(status is ChatExitRequestStatus.Unknown)
        assertEquals("WAITING_FOR_PARTNER", status.rawValue)
    }

    @Test
    fun `ChatStatus parses backend statuses`() {
        assertEquals(ChatStatus.Available, ChatStatus.fromBackend("AVAILABLE"))
        assertEquals(ChatStatus.Active, ChatStatus.fromBackend("ACTIVE"))
        assertEquals(ChatStatus.Cancelled, ChatStatus.fromBackend("CANCELLED"))
        assertEquals(ChatStatus.Expired, ChatStatus.fromBackend("EXPIRED"))
        assertEquals(ChatStatus.Abandoned, ChatStatus.fromBackend("ABANDONED"))
        assertEquals(ChatStatus.Closed, ChatStatus.fromBackend("CLOSED"))
        assertEquals(ChatStatus.Finished, ChatStatus.fromBackend("FINISHED"))
    }

    @Test
    fun `ChatStatus preserves unknown statuses`() {
        val status = ChatStatus.fromBackend("PAUSED")

        assertTrue(status is ChatStatus.Unknown)
        assertEquals("PAUSED", status.rawValue)
    }

    @Test
    fun `MatchState parses backend statuses`() {
        assertEquals(MatchState.ChatActive, MatchState.fromBackend("CHAT_ACTIVE"))
        assertEquals(MatchState.VisualPhase, MatchState.fromBackend("VISUAL_PHASE"))
        assertEquals(MatchState.VisualApproved, MatchState.fromBackend("VISUAL_APPROVED"))
        assertEquals(MatchState.ChatRejected, MatchState.fromBackend("CHAT_REJECTED"))
        assertEquals(MatchState.VisualRejected, MatchState.fromBackend("VISUAL_REJECTED"))
        assertEquals(MatchState.Expired, MatchState.fromBackend("EXPIRED"))
    }

    @Test
    fun `MatchState preserves unknown statuses`() {
        val state = MatchState.fromBackend("REOPENED")

        assertTrue(state is MatchState.Unknown)
        assertEquals("REOPENED", state.rawValue)
    }

    @Test
    fun `ChatDecisionState parses backend statuses`() {
        assertEquals(ChatDecisionState.Pending, ChatDecisionState.fromBackend("PENDING"))
        assertEquals(ChatDecisionState.Approved, ChatDecisionState.fromBackend("APPROVED"))
        assertEquals(ChatDecisionState.Rejected, ChatDecisionState.fromBackend("REJECTED"))
        assertEquals(ChatDecisionState.Abandoned, ChatDecisionState.fromBackend("ABANDONED"))
        assertEquals(ChatDecisionState.Pending, ChatDecisionState.fromBackend(null))

        val unknown = ChatDecisionState.fromBackend("POSTPONED")
        assertTrue(unknown is ChatDecisionState.Unknown)
        assertEquals("POSTPONED", unknown.rawValue)
    }

    @Test
    fun `ChatExitRequestStatus parses backend statuses`() {
        assertEquals(ChatExitRequestStatus.Pending, ChatExitRequestStatus.fromBackend("PENDING"))
        assertEquals(ChatExitRequestStatus.Accepted, ChatExitRequestStatus.fromBackend("ACCEPTED"))
        assertEquals(ChatExitRequestStatus.Rejected, ChatExitRequestStatus.fromBackend("REJECTED"))
        assertEquals(ChatExitRequestStatus.TimedOut, ChatExitRequestStatus.fromBackend("TIMED_OUT"))
    }

    @Test
    fun `ChatExitRequestType parses backend statuses`() {
        assertEquals(ChatExitRequestType.MutualCancel, ChatExitRequestType.fromBackend("MUTUAL_CANCEL"))
        assertEquals(ChatExitRequestType.UnilateralCancel, ChatExitRequestType.fromBackend("UNILATERAL_CANCEL"))
        assertEquals(ChatExitRequestType.SafetyReport, ChatExitRequestType.fromBackend("SAFETY_REPORT"))

        val unknown = ChatExitRequestType.fromBackend("OTHER_FLOW")
        assertTrue(unknown is ChatExitRequestType.Unknown)
        assertEquals("OTHER_FLOW", unknown.rawValue)
    }

    @Test
    fun `ChatExitReason parses backend statuses`() {
        assertEquals(ChatExitReason.NoLongerInterested, ChatExitReason.fromBackend("NO_LONGER_INTERESTED"))
        assertEquals(ChatExitReason.InappropriateBehavior, ChatExitReason.fromBackend("INAPPROPRIATE_BEHAVIOR"))
        assertEquals(ChatExitReason.Harassment, ChatExitReason.fromBackend("HARASSMENT"))
        assertEquals(ChatExitReason.Other, ChatExitReason.fromBackend("OTHER"))

        val unknown = ChatExitReason.fromBackend("SOMETHING_ELSE")
        assertTrue(unknown is ChatExitReason.Unknown)
        assertEquals("SOMETHING_ELSE", unknown.rawValue)
    }

    @Test
    fun `Scheduling statuses parse backend values and preserve unknowns`() {
        assertEquals(ConnectionState.SchedulingPending, ConnectionState.fromBackend("SCHEDULING_PENDING"))
        assertEquals(ConnectionState.SchedulingPhase, ConnectionState.fromBackend("SCHEDULING_PHASE"))
        assertEquals(ConnectionState.SecondChatScheduled, ConnectionState.fromBackend("SECOND_CHAT_SCHEDULED"))
        assertEquals(ConnectionState.SecondChatAvailable, ConnectionState.fromBackend("SECOND_CHAT_AVAILABLE"))
        assertEquals(ConnectionState.SecondChat, ConnectionState.fromBackend("SECOND_CHAT"))
        assertEquals(ConnectionState.Closed, ConnectionState.fromBackend("CLOSED"))
        assertEquals(NegotiationStatus.Pending, NegotiationStatus.fromBackend("PENDING"))
        assertEquals(NegotiationStatus.Confirmed, NegotiationStatus.fromBackend("CONFIRMED"))
        assertEquals(NegotiationStatus.Failed, NegotiationStatus.fromBackend("FAILED"))
        assertEquals(ProposalStatus.Pending, ProposalStatus.fromBackend("PENDING"))
        assertEquals(ProposalStatus.Accepted, ProposalStatus.fromBackend("ACCEPTED"))
        assertEquals(ProposalStatus.Rejected, ProposalStatus.fromBackend("REJECTED"))

        assertEquals("NEW_CONNECTION", (ConnectionState.fromBackend("NEW_CONNECTION") as ConnectionState.Unknown).rawValue)
        assertEquals("WAITING", (NegotiationStatus.fromBackend("WAITING") as NegotiationStatus.Unknown).rawValue)
        assertEquals("MAYBE", (ProposalStatus.fromBackend("MAYBE") as ProposalStatus.Unknown).rawValue)
    }
}
