package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.SubmitBanAppealRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.PermanentBanAppealState

class BanAppealRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getMyBanAppeal(): ApiResult<PermanentBanAppealState> =
        authorizedCall { authorization -> api.getMyBanAppeal(authorization) }
            .map { it.toDomain() }

    suspend fun submitMyBanAppeal(statement: String): ApiResult<Unit> =
        authorizedUnitCall { authorization ->
            api.submitMyBanAppeal(
                authorization = authorization,
                body = SubmitBanAppealRequestDto(statement = statement),
            )
        }
}
