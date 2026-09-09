package com.reals.app.domain.usecase

import com.reals.app.data.repository.AuthRepository
import com.reals.app.data.repository.PasswordResetResult

open class RequestPasswordResetUseCase(
    private val authRepository: AuthRepository? = null,
) {
    open suspend operator fun invoke(email: String): PasswordResetResult =
        authRepository?.requestPasswordReset(email) ?: PasswordResetResult.SilentFailure
}
