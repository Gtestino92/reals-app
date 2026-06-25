package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository

class RegisterPushTokenUseCase(
    private val meRepository: MeRepository,
) {
    suspend operator fun invoke(token: String): ApiResult<Boolean> =
        meRepository.registerPushToken(token)
}
