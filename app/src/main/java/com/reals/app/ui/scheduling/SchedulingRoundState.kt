package com.reals.app.ui.scheduling

import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProposalStatus
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal

internal data class SchedulingRoundState(
    val stage: SchedulingStage,
    val currentRoundProposals: List<SchedulingProposal>,
    val myProposals: List<SchedulingProposal>,
    val partnerProposals: List<SchedulingProposal>,
    val myPendingProposals: List<SchedulingProposal>,
    val partnerPendingProposals: List<SchedulingProposal>,
)

internal enum class SchedulingStage {
    Loading,
    WaitingForMyProposals,
    WaitingForPartnerProposals,
    ReviewPartnerProposals,
    Scheduled,
    Failed,
    Unknown,
}

internal fun deriveSchedulingRoundState(
    loading: Boolean,
    negotiation: SchedulingNegotiation?,
    proposals: List<SchedulingProposal>,
    currentUserId: String,
): SchedulingRoundState {
    val currentRound = negotiation?.roundNumber
    val currentRoundProposals = proposals
        .filter { currentRound != null && it.roundNumber == currentRound }
        .sortedWith(compareBy<SchedulingProposal> { it.userId != currentUserId }.thenBy { it.preferenceOrder })
    val myProposals = currentRoundProposals.filter { it.userId == currentUserId }
    val partnerProposals = currentRoundProposals.filter { it.userId != currentUserId }
    val myPendingProposals = myProposals.filter { it.status == ProposalStatus.Pending }
    val partnerPendingProposals = partnerProposals.filter { it.status == ProposalStatus.Pending }

    return SchedulingRoundState(
        stage = deriveSchedulingStage(
            loading = loading,
            negotiation = negotiation,
            myProposals = myProposals,
            partnerPendingProposals = partnerPendingProposals,
        ),
        currentRoundProposals = currentRoundProposals,
        myProposals = myProposals,
        partnerProposals = partnerProposals,
        myPendingProposals = myPendingProposals,
        partnerPendingProposals = partnerPendingProposals,
    )
}

private fun deriveSchedulingStage(
    loading: Boolean,
    negotiation: SchedulingNegotiation?,
    myProposals: List<SchedulingProposal>,
    partnerPendingProposals: List<SchedulingProposal>,
): SchedulingStage = when {
    loading -> SchedulingStage.Loading
    negotiation == null -> SchedulingStage.Unknown
    negotiation.status == NegotiationStatus.Confirmed -> SchedulingStage.Scheduled
    negotiation.status == NegotiationStatus.Failed -> SchedulingStage.Failed
    negotiation.status is NegotiationStatus.Unknown -> SchedulingStage.Unknown
    negotiation.status == NegotiationStatus.Pending && partnerPendingProposals.isNotEmpty() ->
        SchedulingStage.ReviewPartnerProposals
    negotiation.status == NegotiationStatus.Pending && myProposals.isEmpty() ->
        SchedulingStage.WaitingForMyProposals
    negotiation.status == NegotiationStatus.Pending -> SchedulingStage.WaitingForPartnerProposals
    else -> SchedulingStage.Unknown
}
