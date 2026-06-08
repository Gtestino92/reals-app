package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository
import com.reals.app.domain.model.BackendUser

class GetMeUseCase(
    private val meRepository: MeRepository,
) {
    suspend operator fun invoke(): ApiResult<BackendUser> =
        meRepository.getMe()
}
