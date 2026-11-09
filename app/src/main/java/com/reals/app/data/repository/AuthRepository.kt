package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.network.ApiResult
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.PasswordResetRequestDto

sealed interface PasswordResetResult {
    data object SentOrHandledGenerically : PasswordResetResult
    data object InvalidEmailFormat : PasswordResetResult
    data object SilentFailure : PasswordResetResult
    data object RateLimited : PasswordResetResult
}

class AuthRepository(
    private val api: RealsApi,
    private val apiExecutor: ApiExecutor,
) {
    suspend fun requestPasswordReset(email: String): PasswordResetResult {
        val cleanEmail = email.trim()
        if (!isLocallyValidEmail(cleanEmail)) return PasswordResetResult.InvalidEmailFormat

        return when (val result =
            apiExecutor.executeUnit {
                api.requestPasswordReset(PasswordResetRequestDto(email = cleanEmail))
            }
        ) {
            is ApiResult.Success -> PasswordResetResult.SentOrHandledGenerically
            is ApiResult.Failure -> {
                val backend = result.error as? ApiError.Backend
                if (backend?.statusCode == 429 && backend.backendErrorCode == BackendErrorCode.RateLimitExceeded) {
                    PasswordResetResult.RateLimited
                } else {
                    PasswordResetResult.SilentFailure
                }
            }
        }
    }
}
