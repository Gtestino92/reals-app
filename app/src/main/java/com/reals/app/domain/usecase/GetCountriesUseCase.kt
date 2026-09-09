package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.CountryReference

class GetCountriesUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): ApiResult<List<CountryReference>> =
        profileRepository.getCountries()
}
