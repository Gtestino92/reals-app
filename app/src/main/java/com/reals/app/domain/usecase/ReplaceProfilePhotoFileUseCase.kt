package com.reals.app.domain.usecase

import android.net.Uri
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.ProfilePhoto

class ReplaceProfilePhotoFileUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(
        photoId: String,
        fileUri: Uri,
    ): ApiResult<ProfilePhoto> =
        profileRepository.replaceMyProfilePhotoFile(photoId = photoId, fileUri = fileUri)
}
