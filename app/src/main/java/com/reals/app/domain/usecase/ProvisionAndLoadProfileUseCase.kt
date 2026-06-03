package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.ProvisionedSession

class ProvisionAndLoadProfileUseCase(
    private val meRepository: MeRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): ApiResult<ProvisionedSession> {
        val userResult = meRepository.provisionMe()
        if (userResult is ApiResult.Failure) return userResult

        val profileResult = profileRepository.getMyProfileSnapshot()
        if (profileResult is ApiResult.Failure) return profileResult

        return ApiResult.Success(
            ProvisionedSession(
                user = (userResult as ApiResult.Success).value,
                profileSnapshot = (profileResult as ApiResult.Success).value,
            ),
        )
    }
}
