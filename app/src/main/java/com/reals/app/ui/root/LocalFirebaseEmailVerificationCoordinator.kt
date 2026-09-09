package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerified
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalFirebaseEmailVerificationCoordinator(
    val localFirebaseEmailAutoVerificationEnabled: Boolean,
    private val authRepository: FirebaseAuthRepository,
    private val markLocalFirebaseEmailVerified: MarkLocalFirebaseEmailVerified,
) {
    private val mutex = Mutex()

    suspend fun ensureVerifiedForLocalBootstrap(): LocalFirebaseEmailVerificationResult {
        if (!localFirebaseEmailAutoVerificationEnabled) {
            return LocalFirebaseEmailVerificationResult.Verified
        }

        return mutex.withLock { ensureVerifiedLocked() }
    }

    suspend fun checkOrVerifyForUserAction(): EmailVerificationCheckResult {
        if (!localFirebaseEmailAutoVerificationEnabled) {
            return authRepository.reloadAndRefreshEmailVerification()
        }

        return when (ensureVerifiedForLocalBootstrap()) {
            LocalFirebaseEmailVerificationResult.Verified -> EmailVerificationCheckResult.Verified
            LocalFirebaseEmailVerificationResult.NotSignedIn -> EmailVerificationCheckResult.NotSignedIn
            is LocalFirebaseEmailVerificationResult.Failure -> EmailVerificationCheckResult.Failure
        }
    }

    private suspend fun ensureVerifiedLocked(): LocalFirebaseEmailVerificationResult {
        return when (authRepository.reloadAndRefreshEmailVerification()) {
            EmailVerificationCheckResult.Verified -> LocalFirebaseEmailVerificationResult.Verified
            EmailVerificationCheckResult.NotVerified -> verifyThroughLocalBackend()
            EmailVerificationCheckResult.NotSignedIn -> LocalFirebaseEmailVerificationResult.NotSignedIn
            EmailVerificationCheckResult.Failure -> LocalFirebaseEmailVerificationResult.Failure(localFailureError)
        }
    }

    private suspend fun verifyThroughLocalBackend(): LocalFirebaseEmailVerificationResult {
        return when (markLocalFirebaseEmailVerified()) {
            is ApiResult.Success -> when (authRepository.reloadAndRefreshEmailVerification()) {
                EmailVerificationCheckResult.Verified -> LocalFirebaseEmailVerificationResult.Verified
                EmailVerificationCheckResult.NotSignedIn -> LocalFirebaseEmailVerificationResult.NotSignedIn
                EmailVerificationCheckResult.NotVerified,
                EmailVerificationCheckResult.Failure -> LocalFirebaseEmailVerificationResult.Failure(localFailureError)
            }

            is ApiResult.Failure -> LocalFirebaseEmailVerificationResult.Failure(localFailureError)
        }
    }

    companion object {
        fun disabled(authRepository: FirebaseAuthRepository): LocalFirebaseEmailVerificationCoordinator =
            LocalFirebaseEmailVerificationCoordinator(
                localFirebaseEmailAutoVerificationEnabled = false,
                authRepository = authRepository,
                markLocalFirebaseEmailVerified = MarkLocalFirebaseEmailVerified { ApiResult.Success(Unit) },
            )
    }
}

sealed interface LocalFirebaseEmailVerificationResult {
    data object Verified : LocalFirebaseEmailVerificationResult
    data object NotSignedIn : LocalFirebaseEmailVerificationResult
    data class Failure(val error: ApiError) : LocalFirebaseEmailVerificationResult
}

val localFailureError: ApiError = ApiError.LocalFirebaseEmailVerification
