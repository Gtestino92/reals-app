package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.domain.model.DeleteAccountResult

class DeleteAccountUseCase(
    private val meRepository: MeRepository,
    private val authRepository: FirebaseAuthRepository,
) {
    suspend operator fun invoke(): ApiResult<DeleteAccountResult> {
        return when (val backendResult = meRepository.deleteMe()) {
            is ApiResult.Failure -> {
                backendResult
            }

            is ApiResult.Success -> {
                val deletedUser = meRepository.getMe() as? ApiResult.Success
                authRepository.signOut()
                ApiResult.Success(
                    DeleteAccountResult(
                        deletionFinalizesAt = deletedUser?.value?.deletionFinalizesAt,
                    )
                )
            }
        }
    }
}
