package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal

class GetSchedulingNegotiationUseCase(
    private val schedulingRepository: SchedulingRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<SchedulingNegotiation> =
        schedulingRepository.getNegotiation(connectionId)
}

class GetSchedulingProposalsUseCase(
    private val schedulingRepository: SchedulingRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<List<SchedulingProposal>> =
        schedulingRepository.getProposals(connectionId)
}

class SubmitSchedulingProposalsUseCase(
    private val schedulingRepository: SchedulingRepository,
) {
    suspend operator fun invoke(
        connectionId: String,
        expectedRoundNumber: Int,
        proposedDateTimes: List<String>,
    ): ApiResult<List<SchedulingProposal>> =
        schedulingRepository.submitProposals(connectionId, expectedRoundNumber, proposedDateTimes)
}

class AcceptSchedulingProposalUseCase(
    private val schedulingRepository: SchedulingRepository,
) {
    suspend operator fun invoke(
        connectionId: String,
        proposalId: String,
    ): ApiResult<SchedulingNegotiation> =
        schedulingRepository.acceptProposal(connectionId, proposalId)
}

class RejectPartnerSchedulingProposalsUseCase(
    private val schedulingRepository: SchedulingRepository,
) {
    suspend operator fun invoke(
        connectionId: String,
        expectedRoundNumber: Int,
    ): ApiResult<SchedulingNegotiation> =
        schedulingRepository.rejectPartnerProposals(connectionId, expectedRoundNumber)
}
