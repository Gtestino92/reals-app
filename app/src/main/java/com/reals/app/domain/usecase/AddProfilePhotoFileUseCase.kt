package com.reals.app.domain.usecase

import android.net.Uri
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.ProfilePhoto

class AddProfilePhotoFileUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(
        fileUri: Uri,
        position: Int,
    ): ApiResult<ProfilePhoto> =
        profileRepository.addMyProfilePhotoFile(fileUri = fileUri, position = position)
}
