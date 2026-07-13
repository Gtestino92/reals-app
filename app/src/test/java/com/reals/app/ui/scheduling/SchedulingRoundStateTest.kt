package com.reals.app.ui.scheduling

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProposalStatus
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingRoundStateTest {
    @Test
    fun `pending current round without my proposals waits for my proposals`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = emptyList(),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.WaitingForMyProposals, state.stage)
        assertTrue(state.myProposals.isEmpty())
        assertTrue(state.partnerProposals.isEmpty())
    }

    @Test
    fun `pending current round with my proposals waits for partner proposals`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(id = "old-partner", userId = "partner", roundNumber = 1),
                proposal(id = "my-current", userId = "me", roundNumber = 2),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.WaitingForPartnerProposals, state.stage)
        assertEquals(listOf("my-current"), state.myProposals.map { it.id })
        assertTrue(state.partnerProposals.isEmpty())
        assertEquals(listOf("my-current"), state.currentRoundProposals.map { it.id })
    }

    @Test
    fun `pending current round with partner proposals reviews received options`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(id = "my-current", userId = "me", roundNumber = 2),
                proposal(id = "partner-current", userId = "partner", roundNumber = 2),
                proposal(id = "old-my", userId = "me", roundNumber = 1),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.ReviewPartnerProposals, state.stage)
        assertEquals(listOf("my-current"), state.myProposals.map { it.id })
        assertEquals(listOf("partner-current"), state.partnerProposals.map { it.id })
        assertEquals(listOf("my-current", "partner-current"), state.currentRoundProposals.map { it.id })
    }

    @Test
    fun `new round after reject ignores proposals from previous rounds`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 3),
            proposals = listOf(
                proposal(id = "old-my", userId = "me", roundNumber = 2),
                proposal(id = "old-partner", userId = "partner", roundNumber = 2),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.WaitingForMyProposals, state.stage)
        assertTrue(state.currentRoundProposals.isEmpty())
        assertTrue(state.myProposals.isEmpty())
        assertTrue(state.partnerProposals.isEmpty())
    }

    @Test
    fun `confirmed negotiation shows scheduled stage`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(status = NegotiationStatus.Confirmed),
            proposals = emptyList(),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.Scheduled, state.stage)
    }

    @Test
    fun `failed negotiation shows failed stage`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(status = NegotiationStatus.Failed),
            proposals = emptyList(),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.Failed, state.stage)
    }

    @Test
    fun `invalid proposal error is shown near proposal selector`() {
        val error = backendError("SCHEDULING_INVALID_PROPOSALS")

        val placement = schedulingErrorPlacement(
            stage = SchedulingStage.WaitingForMyProposals,
            myProposals = emptyList(),
            error = error,
        )

        assertEquals(null, placement.topLevelError)
        assertEquals(
            BackendErrorCode.SchedulingInvalidProposals,
            (placement.proposalError as ApiError.Backend).backendErrorCode,
        )
    }

    @Test
    fun `proposal error stays top level when selector is not visible`() {
        val error = backendError("SCHEDULING_INVALID_PROPOSALS")

        val placement = schedulingErrorPlacement(
            stage = SchedulingStage.WaitingForPartnerProposals,
            myProposals = listOf(proposal(id = "my-current", userId = "me", roundNumber = 2)),
            error = error,
        )

        assertEquals(
            BackendErrorCode.SchedulingInvalidProposals,
            (placement.topLevelError as ApiError.Backend).backendErrorCode,
        )
        assertEquals(null, placement.proposalError)
    }

    @Test
    fun `non proposal scheduling error remains top level`() {
        val error = backendError("SCHEDULING_EXPIRED")

        val placement = schedulingErrorPlacement(
            stage = SchedulingStage.WaitingForMyProposals,
            myProposals = emptyList(),
            error = error,
        )

        assertEquals(
            BackendErrorCode.SchedulingExpired,
            (placement.topLevelError as ApiError.Backend).backendErrorCode,
        )
        assertEquals(null, placement.proposalError)
    }

    private fun negotiation(
        roundNumber: Int = 2,
        status: NegotiationStatus = NegotiationStatus.Pending,
    ) = SchedulingNegotiation(
        id = "negotiation-$roundNumber",
        connectionId = "connection-1",
        roundNumber = roundNumber,
        status = status,
        confirmedDateTime = if (status == NegotiationStatus.Confirmed) "2026-06-18T21:00:00-03:00" else null,
        chatId = if (status == NegotiationStatus.Confirmed) "chat-1" else null,
        schedulingExpiresAt = "2026-06-19T21:00:00-03:00",
        createdAt = "2026-06-18T10:00:00-03:00",
        updatedAt = "2026-06-18T10:00:00-03:00",
    )

    private fun proposal(
        id: String,
        userId: String,
        roundNumber: Int,
        preferenceOrder: Int = 1,
    ) = SchedulingProposal(
        id = id,
        connectionId = "connection-1",
        userId = userId,
        roundNumber = roundNumber,
        preferenceOrder = preferenceOrder,
        proposedDateTime = "2026-06-18T21:00:00-03:00",
        status = ProposalStatus.Pending,
        chatId = null,
        createdAt = "2026-06-18T10:00:00-03:00",
    )

    private fun backendError(code: String): ApiError.Backend =
        ApiError.Backend(
            statusCode = 400,
            code = code,
            error = code,
            message = "backend error",
        )
}
