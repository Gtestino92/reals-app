package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.PhotoPlacementInput
import com.reals.app.domain.model.ProfilePhoto

class ReorderProfilePhotosUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(
        placements: List<PhotoPlacementInput>,
    ): ApiResult<List<ProfilePhoto>> =
        profileRepository.reorderMyProfilePhotos(placements)
}
