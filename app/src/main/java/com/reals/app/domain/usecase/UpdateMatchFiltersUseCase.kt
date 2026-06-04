package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.UpdateMatchFiltersInput

class UpdateMatchFiltersUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(input: UpdateMatchFiltersInput): ApiResult<Profile> =
        profileRepository.updateMyMatchFilters(input)
}
