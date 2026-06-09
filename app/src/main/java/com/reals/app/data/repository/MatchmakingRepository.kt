package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.mapper.toDto
import com.reals.app.domain.model.QueueStatus
import com.reals.app.domain.model.SearchLocationInput

class MatchmakingRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun enqueue(location: SearchLocationInput): ApiResult<QueueStatus> =
        authorizedCall { authorization -> api.enqueueMatchmaking(authorization, location.toDto()) }
            .map { it.toDomain() }

    suspend fun leaveQueue(): ApiResult<QueueStatus> =
        authorizedCall { authorization -> api.leaveMatchmakingQueue(authorization) }
            .map { it.toDomain() }

    suspend fun getQueueStatus(): ApiResult<QueueStatus> =
        authorizedCall { authorization -> api.getMatchmakingQueueStatus(authorization) }
            .map { it.toDomain() }
}
