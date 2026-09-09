package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.Profile

class DeleteProfilePhotoUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(photoId: String): ApiResult<Profile> =
        profileRepository.deleteMyProfilePhoto(photoId)
}
