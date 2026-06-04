package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto

class ReplaceMockProfilePhotoUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ): ApiResult<ProfilePhoto> = profileRepository.replaceMyProfilePhoto(
        url = generatedPhotoUrl(profile, position),
        position = position,
        isPersonPhoto = isPersonPhoto,
        isFullBody = isFullBody,
    )

    private fun generatedPhotoUrl(profile: Profile, position: Int): String {
        val userId = profile.userId.replace("-", "")
        val profileId = profile.id.replace("-", "")
        val version = System.currentTimeMillis()
        return "https://static.reals.local/mock-profiles/$userId/$profileId/photo-$position-v$version.jpg"
    }
}
