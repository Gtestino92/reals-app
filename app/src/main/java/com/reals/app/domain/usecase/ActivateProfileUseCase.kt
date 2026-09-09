package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.ProfileActivationResult

class ActivateProfileUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): ApiResult<ProfileActivationResult> =
        profileRepository.activateMyProfile().map { profile ->
            ProfileActivationResult(
                profile = profile,
                addedPhotoCount = 0,
                totalPhotoCount = profile.photoCount,
                generatedUrls = emptyList(),
            )
        }
}
