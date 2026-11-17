package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.ProvisionedSession

class ProvisionAndLoadProfileUseCase(
    private val meRepository: MeRepository,
    private val profileRepository: ProfileRepository,
    private val authenticationSessionId: () -> String = run {
        val identity = java.util.UUID.randomUUID().toString();
        { identity }
    },
) {
    suspend operator fun invoke(): ApiResult<ProvisionedSession> {
        val userResult = meRepository.provisionMe()
        if (userResult is ApiResult.Failure) return userResult

        return loadProfileFor((userResult as ApiResult.Success).value)
    }

    suspend fun loadProfileFor(user: BackendUser): ApiResult<ProvisionedSession> {
        val identity = authenticationSessionId()
        val profileResult = profileRepository.getMyProfileSnapshot()
        if (profileResult is ApiResult.Failure) return profileResult
        val found = (profileResult as ApiResult.Success).value as? com.reals.app.domain.model.ProfileSnapshot.Found
        if (identity != authenticationSessionId() || (found != null && found.profile.userId != user.id)) return ApiResult.Failure(
            com.reals.app.core.network.ApiError.Auth(com.reals.app.core.network.AuthFailureReason.NOT_SIGNED_IN, "La sesión cambió."),
        )

        return ApiResult.Success(
            ProvisionedSession(
                user = user,
                profileSnapshot = (profileResult as ApiResult.Success).value,
                authenticationSessionId = identity,
            ),
        )
    }
}
