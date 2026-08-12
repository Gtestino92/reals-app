package com.reals.app.domain.usecase

import com.reals.app.data.repository.CredentialStateRepository
import com.reals.app.data.repository.FirebaseAuthRepository
import kotlinx.coroutines.CancellationException

open class ClearLocalSessionUseCase(
    private val authRepository: FirebaseAuthRepository,
    private val credentialStateRepository: CredentialStateRepository,
) {
    open suspend operator fun invoke() {
        authRepository.signOut()
        try {
            credentialStateRepository.clearCredentialState()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
        }
    }
}
