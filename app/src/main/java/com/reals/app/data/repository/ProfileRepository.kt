package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileSnapshot

class ProfileRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getMyProfileSnapshot(): ApiResult<ProfileSnapshot> {
        return when (val result = authorizedCall { authorization -> api.getMyProfile(authorization) }) {
            is ApiResult.Success -> ApiResult.Success(ProfileSnapshot.Found(result.value.toDomain()))
            is ApiResult.Failure -> {
                val backend = result.error as? ApiError.Backend
                if (backend?.statusCode == 404) {
                    ApiResult.Success(ProfileSnapshot.Missing)
                } else {
                    result
                }
            }
        }
    }
}
