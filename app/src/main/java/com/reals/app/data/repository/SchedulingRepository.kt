package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.AddProposalRequestDto
import com.reals.app.data.dto.RejectPartnerProposalsRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.SchedulingAvailability
import com.reals.app.domain.model.SchedulingConnection
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal

class SchedulingRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getConnection(connectionId: String): ApiResult<SchedulingConnection> =
        authorizedCall { authorization -> api.getConnection(authorization, connectionId) }
            .map { it.toDomain() }

    suspend fun getNegotiation(connectionId: String): ApiResult<SchedulingNegotiation> =
        authorizedCall { authorization -> api.getConnectionNegotiation(authorization, connectionId) }
            .map { it.toDomain() }

    suspend fun getProposals(connectionId: String): ApiResult<List<SchedulingProposal>> =
        authorizedCall { authorization -> api.getConnectionProposals(authorization, connectionId) }
            .map { proposals -> proposals.map { it.toDomain() } }

    suspend fun getAvailability(connectionId: String): ApiResult<SchedulingAvailability> =
        authorizedCall { authorization -> api.getConnectionSchedulingAvailability(authorization, connectionId) }
            .map { it.toDomain() }

    suspend fun submitProposals(
        connectionId: String,
        expectedRoundNumber: Int,
        proposedDateTimes: List<String>,
    ): ApiResult<List<SchedulingProposal>> =
        authorizedCall { authorization ->
            api.submitConnectionProposals(
                authorization = authorization,
                connectionId = connectionId,
                body = AddProposalRequestDto(
                    expectedRoundNumber = expectedRoundNumber,
                    proposedDateTimes = proposedDateTimes,
                ),
            )
        }.map { proposals -> proposals.map { it.toDomain() } }

    suspend fun acceptProposal(
        connectionId: String,
        proposalId: String,
    ): ApiResult<SchedulingNegotiation> =
        authorizedCall { authorization ->
            api.acceptConnectionProposal(
                authorization = authorization,
                connectionId = connectionId,
                proposalId = proposalId,
            )
        }.map { it.toDomain() }

    suspend fun rejectPartnerProposals(
        connectionId: String,
        expectedRoundNumber: Int,
    ): ApiResult<SchedulingNegotiation> =
        authorizedCall { authorization ->
            api.rejectConnectionPartnerProposals(
                authorization = authorization,
                connectionId = connectionId,
                body = RejectPartnerProposalsRequestDto(expectedRoundNumber),
            )
        }.map { it.toDomain() }
}
