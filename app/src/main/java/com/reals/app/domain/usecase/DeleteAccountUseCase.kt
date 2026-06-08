package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.MeRepository

class DeleteAccountUseCase(
    private val meRepository: MeRepository,
    private val authRepository: FirebaseAuthRepository,
) {
    suspend operator fun invoke(): ApiResult<Unit> {
        return when (val backendResult = meRepository.deleteMe()) {
            is ApiResult.Failure -> {
                backendResult
            }

            is ApiResult.Success -> {
                authRepository.signOut()
                ApiResult.Success(Unit)
            }
        }
    }
}
