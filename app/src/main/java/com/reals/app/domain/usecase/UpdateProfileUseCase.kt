package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.UpdateProfileInput

class UpdateProfileUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(input: UpdateProfileInput): ApiResult<Profile> =
        profileRepository.updateMyProfile(input)
}
