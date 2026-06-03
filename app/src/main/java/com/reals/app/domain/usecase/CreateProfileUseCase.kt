package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile

class CreateProfileUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(input: CreateProfileInput): ApiResult<Profile> =
        profileRepository.createMyProfile(input)
}
