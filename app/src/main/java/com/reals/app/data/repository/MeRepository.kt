package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.RegisterPushTokenRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.HomeState

class MeRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun provisionMe(): ApiResult<BackendUser> =
        authorizedCall { authorization -> api.provisionMe(authorization) }
            .map { it.toDomain() }

    suspend fun getMe(): ApiResult<BackendUser> =
        authorizedCall { authorization -> api.getMe(authorization) }
            .map { it.toDomain() }

    suspend fun getHome(): ApiResult<HomeState> =
        authorizedCall { authorization -> api.getHome(authorization) }
            .map { it.toDomain() }

    suspend fun registerPushToken(token: String): ApiResult<Boolean> =
        authorizedCall { authorization ->
            api.registerPushToken(
                authorization = authorization,
                body = RegisterPushTokenRequestDto(token = token, platform = "ANDROID"),
            )
        }.map { it.registered }

    suspend fun deleteMe(): ApiResult<Unit> =
        authorizedUnitCall { authorization -> api.deleteMe(authorization) }

    suspend fun reactivateMe(): ApiResult<BackendUser> =
        authorizedCall { authorization -> api.reactivateMe(authorization) }
            .map { it.toDomain() }
}
