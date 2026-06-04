package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.Profile

class DeleteProfilePhotoUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(position: Int): ApiResult<Profile> =
        profileRepository.deleteMyProfilePhoto(position)
}
