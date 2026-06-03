package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileActivationResult

class CompleteAndActivateProfileUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(profile: Profile): ApiResult<ProfileActivationResult> {
        val photosResult = profileRepository.getMyProfilePhotos()
        if (photosResult is ApiResult.Failure) return photosResult

        val existingPhotos = (photosResult as ApiResult.Success).value
        val existingPositions = existingPhotos.map { it.position }.toSet()
        val generatedUrls = mutableListOf<String>()
        var addedPhotoCount = 0

        for (position in 1..REQUIRED_SEED_PHOTO_COUNT) {
            if (position in existingPositions) continue

            val url = generatedPhotoUrl(profile, position)
            when (
                val addResult = profileRepository.addMyProfilePhoto(
                    url = url,
                    position = position,
                    isPersonPhoto = position <= MIN_PERSON_PHOTO_COUNT,
                    isFullBody = position == FULL_BODY_PHOTO_POSITION,
                )
            ) {
                is ApiResult.Success -> {
                    addedPhotoCount += 1
                    generatedUrls += addResult.value.url
                }

                is ApiResult.Failure -> {
                    if (!addResult.isOccupiedPositionConflict()) return addResult
                }
            }
        }

        return when (val activationResult = profileRepository.activateMyProfile()) {
            is ApiResult.Success -> ApiResult.Success(
                ProfileActivationResult(
                    profile = activationResult.value,
                    addedPhotoCount = addedPhotoCount,
                    totalPhotoCount = activationResult.value.photoCount,
                    generatedUrls = generatedUrls,
                ),
            )

            is ApiResult.Failure -> activationResult
        }
    }

    private fun generatedPhotoUrl(profile: Profile, position: Int): String {
        val userId = profile.userId.replace("-", "")
        val profileId = profile.id.replace("-", "")
        return "https://static.reals.local/mock-profiles/$userId/$profileId/photo-$position.jpg"
    }

    private fun ApiResult.Failure.isOccupiedPositionConflict(): Boolean {
        val backend = error as? ApiError.Backend ?: return false
        return backend.statusCode == 409 && backend.code == "PHOTO_POSITION_OCCUPIED"
    }

    companion object {
        private const val REQUIRED_SEED_PHOTO_COUNT = 9
        private const val MIN_PERSON_PHOTO_COUNT = 3
        private const val FULL_BODY_PHOTO_POSITION = 1
    }
}
