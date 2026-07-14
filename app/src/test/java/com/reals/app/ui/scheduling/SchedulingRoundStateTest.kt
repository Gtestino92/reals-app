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
    fun `partner pending proposals with no own proposals reviews received options`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(id = "partner-current", userId = "partner", roundNumber = 2),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.ReviewPartnerProposals, state.stage)
        assertTrue(state.myProposals.isEmpty())
        assertEquals(listOf("partner-current"), state.partnerPendingProposals.map { it.id })
    }

    @Test
    fun `partner pending proposals remain review priority when own proposals also exist`() {
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
        assertEquals(listOf("partner-current"), state.partnerPendingProposals.map { it.id })
        assertEquals(listOf("my-current", "partner-current"), state.currentRoundProposals.map { it.id })
    }

    @Test
    fun `partner rejected proposals allow selector when user has not submitted`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(
                    id = "partner-rejected",
                    userId = "partner",
                    roundNumber = 2,
                    status = ProposalStatus.Rejected,
                ),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.WaitingForMyProposals, state.stage)
        assertTrue(state.partnerPendingProposals.isEmpty())
        assertEquals(listOf("partner-rejected"), state.partnerProposals.map { it.id })
    }

    @Test
    fun `partner pending expired proposal still requires review`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(
                    id = "partner-expired",
                    userId = "partner",
                    roundNumber = 2,
                    status = ProposalStatus.Pending,
                    proposedDateTime = "2026-06-18T09:00:00-03:00",
                ),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.ReviewPartnerProposals, state.stage)
        assertEquals(listOf("partner-expired"), state.partnerPendingProposals.map { it.id })
    }

    @Test
    fun `own pending proposals wait when no partner pending proposals exist`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(id = "my-current", userId = "me", roundNumber = 2),
                proposal(
                    id = "partner-rejected",
                    userId = "partner",
                    roundNumber = 2,
                    status = ProposalStatus.Rejected,
                ),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.WaitingForPartnerProposals, state.stage)
        assertEquals(listOf("my-current"), state.myPendingProposals.map { it.id })
        assertTrue(state.partnerPendingProposals.isEmpty())
    }

    @Test
    fun `own rejected proposals still review partner pending proposals`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(
                    id = "my-rejected",
                    userId = "me",
                    roundNumber = 2,
                    status = ProposalStatus.Rejected,
                ),
                proposal(id = "partner-current", userId = "partner", roundNumber = 2),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.ReviewPartnerProposals, state.stage)
        assertTrue(state.myPendingProposals.isEmpty())
        assertEquals(listOf("partner-current"), state.partnerPendingProposals.map { it.id })
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
            error = error,
        )

        assertEquals(null, placement.topLevelError)
        assertEquals(
            BackendErrorCode.SchedulingInvalidProposals,
            (placement.proposalError as ApiError.Backend).backendErrorCode,
        )
        assertEquals(null, placement.reviewError)
    }

    @Test
    fun `proposal error stays top level when selector is not visible`() {
        val error = backendError("SCHEDULING_INVALID_PROPOSALS")

        val placement = schedulingErrorPlacement(
            stage = SchedulingStage.WaitingForPartnerProposals,
            error = error,
        )

        assertEquals(
            BackendErrorCode.SchedulingInvalidProposals,
            (placement.topLevelError as ApiError.Backend).backendErrorCode,
        )
        assertEquals(null, placement.proposalError)
        assertEquals(null, placement.reviewError)
    }

    @Test
    fun `received proposal review errors are shown near review card`() {
        val error = backendError("SCHEDULING_PARTNER_PROPOSALS_NOT_AVAILABLE")

        val placement = schedulingErrorPlacement(
            stage = SchedulingStage.ReviewPartnerProposals,
            error = error,
        )

        assertEquals(null, placement.topLevelError)
        assertEquals(null, placement.proposalError)
        assertEquals(
            BackendErrorCode.SchedulingPartnerProposalsNotAvailable,
            (placement.reviewError as ApiError.Backend).backendErrorCode,
        )
    }

    @Test
    fun `proposal not available review error is not duplicated at top level`() {
        val error = backendError("SCHEDULING_PROPOSAL_NOT_AVAILABLE")

        val placement = schedulingErrorPlacement(
            stage = SchedulingStage.ReviewPartnerProposals,
            error = error,
        )

        assertEquals(null, placement.topLevelError)
        assertEquals(null, placement.proposalError)
        assertEquals(
            BackendErrorCode.SchedulingProposalNotAvailable,
            (placement.reviewError as ApiError.Backend).backendErrorCode,
        )
    }

    @Test
    fun `non proposal scheduling error remains top level`() {
        val error = backendError("SCHEDULING_EXPIRED")

        val placement = schedulingErrorPlacement(
            stage = SchedulingStage.WaitingForMyProposals,
            error = error,
        )

        assertEquals(
            BackendErrorCode.SchedulingExpired,
            (placement.topLevelError as ApiError.Backend).backendErrorCode,
        )
        assertEquals(null, placement.proposalError)
        assertEquals(null, placement.reviewError)
    }

    @Test
    fun `proposal draft scope is preserved for same connection and round`() {
        val first = schedulingProposalDraftScope("connection-1", 2)
        val second = schedulingProposalDraftScope("connection-1", 2)

        assertEquals(first, second)
    }

    @Test
    fun `proposal draft scope changes when round changes`() {
        val first = schedulingProposalDraftScope("connection-1", 2)
        val second = schedulingProposalDraftScope("connection-1", 3)

        assertTrue(first != second)
    }

    @Test
    fun `rejected received proposals make selector eligible when user has not submitted`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(roundNumber = 2),
            proposals = listOf(
                proposal(
                    id = "partner-rejected",
                    userId = "partner",
                    roundNumber = 2,
                    status = ProposalStatus.Rejected,
                ),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.WaitingForMyProposals, state.stage)
        assertTrue(state.myProposals.isEmpty())
        assertTrue(state.partnerPendingProposals.isEmpty())
    }

    @Test
    fun `proposal time availability is future only when proposal instant is after now`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        assertEquals(
            SchedulingProposalTimeAvailability.Future,
            schedulingProposalTimeAvailability("2026-07-15T19:31:00-03:00", nowMillis),
        )
    }

    @Test
    fun `proposal time availability is expired when proposal instant equals now`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        assertEquals(
            SchedulingProposalTimeAvailability.Expired,
            schedulingProposalTimeAvailability("2026-07-15T19:30:00-03:00", nowMillis),
        )
    }

    @Test
    fun `proposal time availability is expired when proposal instant is before now`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        assertEquals(
            SchedulingProposalTimeAvailability.Expired,
            schedulingProposalTimeAvailability("2026-07-15T19:29:00-03:00", nowMillis),
        )
    }

    @Test
    fun `proposal time availability compares equivalent instants across offsets`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        assertEquals(
            SchedulingProposalTimeAvailability.Expired,
            schedulingProposalTimeAvailability("2026-07-16T00:30:00+02:00", nowMillis),
        )
    }

    @Test
    fun `proposal time availability treats malformed timestamp as invalid`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        assertEquals(
            SchedulingProposalTimeAvailability.Invalid,
            schedulingProposalTimeAvailability("not-a-date", nowMillis),
        )
    }

    @Test
    fun `proposal time availability does not depend on display time zone`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        assertEquals(
            SchedulingProposalTimeAvailability.Future,
            schedulingProposalTimeAvailability("2026-07-16T08:00:00+09:00", nowMillis),
        )
    }

    @Test
    fun `review state marks future pending proposal acceptable`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        val state = schedulingReceivedProposalReviewState(
            partnerProposals = listOf(proposal("future", "partner", 2, proposedDateTime = "2026-07-15T20:00:00-03:00")),
            nowMillis = nowMillis,
        )

        assertEquals(listOf("future"), state.items.map { it.proposal.id })
        assertEquals(true, state.items.single().acceptanceAvailable)
        assertEquals(false, state.items.single().expired)
    }

    @Test
    fun `review state marks expired pending proposal non acceptable`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        val state = schedulingReceivedProposalReviewState(
            partnerProposals = listOf(proposal("expired", "partner", 2, proposedDateTime = "2026-07-15T19:30:00-03:00")),
            nowMillis = nowMillis,
        )

        assertEquals(false, state.items.single().acceptanceAvailable)
        assertEquals(true, state.items.single().expired)
        assertEquals(true, state.allExpired)
        assertEquals(true, state.resolutionByRejectionAvailable)
    }

    @Test
    fun `review state preserves preference order and accepts only future proposals`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        val state = schedulingReceivedProposalReviewState(
            partnerProposals = listOf(
                proposal("expired", "partner", 2, preferenceOrder = 1, proposedDateTime = "2026-07-15T19:30:00-03:00"),
                proposal("future", "partner", 2, preferenceOrder = 2, proposedDateTime = "2026-07-15T20:00:00-03:00"),
            ),
            nowMillis = nowMillis,
        )

        assertEquals(listOf("expired", "future"), state.items.map { it.proposal.id })
        assertEquals(listOf(false, true), state.items.map { it.acceptanceAvailable })
    }

    @Test
    fun `review state marks invalid timestamp unavailable and non acceptable`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        val state = schedulingReceivedProposalReviewState(
            partnerProposals = listOf(proposal("invalid", "partner", 2, proposedDateTime = "not-a-date")),
            nowMillis = nowMillis,
        )

        assertEquals(SchedulingProposalTimeAvailability.Invalid, state.items.single().timeAvailability)
        assertEquals(false, state.items.single().acceptanceAvailable)
        assertEquals(true, state.noneAcceptable)
        assertEquals(false, state.allExpired)
    }

    @Test
    fun `review state ignores rejected proposals`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()

        val state = schedulingReceivedProposalReviewState(
            partnerProposals = listOf(
                proposal(
                    id = "rejected",
                    userId = "partner",
                    roundNumber = 2,
                    status = ProposalStatus.Rejected,
                    proposedDateTime = "2026-07-15T20:00:00-03:00",
                ),
            ),
            nowMillis = nowMillis,
        )

        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `pending proposal changes from future to expired without backend status change`() {
        val timeA = java.time.Instant.parse("2026-07-15T22:29:59Z").toEpochMilli()
        val timeB = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()
        val value = "2026-07-15T19:30:00-03:00"

        assertEquals(SchedulingProposalTimeAvailability.Future, schedulingProposalTimeAvailability(value, timeA))
        assertEquals(SchedulingProposalTimeAvailability.Expired, schedulingProposalTimeAvailability(value, timeB))
    }

    @Test
    fun `pending proposal presentation filters rejected rows and numbers contiguously`() {
        val items = schedulingPendingProposalPresentationItems(
            listOf(
                proposal("rejected", "me", 2, preferenceOrder = 1, status = ProposalStatus.Rejected),
                proposal("pending-two", "me", 2, preferenceOrder = 2),
                proposal("pending-three", "me", 2, preferenceOrder = 3),
            )
        )

        assertEquals(listOf(1, 2), items.map { it.number })
        assertEquals(listOf("pending-two", "pending-three"), items.map { it.item.id })
    }

    @Test
    fun `received proposal presentation numbers visible pending rows after rejected rows are filtered`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T22:30:00Z").toEpochMilli()
        val reviewState = schedulingReceivedProposalReviewState(
            partnerProposals = listOf(
                proposal(
                    id = "rejected",
                    userId = "partner",
                    roundNumber = 2,
                    preferenceOrder = 1,
                    status = ProposalStatus.Rejected,
                ),
                proposal(
                    id = "pending",
                    userId = "partner",
                    roundNumber = 2,
                    preferenceOrder = 3,
                    proposedDateTime = "2026-07-15T20:00:00-03:00",
                ),
            ),
            nowMillis = nowMillis,
        )

        val items = schedulingReceivedProposalPresentationItems(reviewState)

        assertEquals(listOf(1), items.map { it.number })
        assertEquals(listOf("pending"), items.map { it.item.proposal.id })
    }

    @Test
    fun `confirmed stage ignores proposal history presentation needs`() {
        val state = deriveSchedulingRoundState(
            loading = false,
            negotiation = negotiation(status = NegotiationStatus.Confirmed),
            proposals = listOf(
                proposal("my-rejected", "me", 2, status = ProposalStatus.Rejected),
                proposal("partner-rejected", "partner", 2, status = ProposalStatus.Rejected),
            ),
            currentUserId = "me",
        )

        assertEquals(SchedulingStage.Scheduled, state.stage)
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
        status: ProposalStatus = ProposalStatus.Pending,
        proposedDateTime: String = "2026-06-18T21:00:00-03:00",
    ) = SchedulingProposal(
        id = id,
        connectionId = "connection-1",
        userId = userId,
        roundNumber = roundNumber,
        preferenceOrder = preferenceOrder,
        proposedDateTime = proposedDateTime,
        status = status,
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
