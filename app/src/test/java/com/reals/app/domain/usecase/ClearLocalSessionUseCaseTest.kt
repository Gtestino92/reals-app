package com.reals.app.domain.usecase

import android.content.ContextWrapper
import com.reals.app.data.repository.CredentialStateRepository
import com.reals.app.data.repository.FirebaseAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ClearLocalSessionUseCaseTest {
    @Test
    fun `clears Firebase sign out and credential state`() = runBlocking {
        val authRepository = FakeAuthRepository()
        val credentialStateRepository = FakeCredentialStateRepository()
        val useCase = ClearLocalSessionUseCase(authRepository, credentialStateRepository)

        useCase()

        assertEquals(1, authRepository.signOutCalls)
        assertEquals(1, credentialStateRepository.clearCalls)
    }

    @Test
    fun `credential clear failure does not prevent Firebase sign out`() = runBlocking {
        val authRepository = FakeAuthRepository()
        val credentialStateRepository = FakeCredentialStateRepository(fail = true)
        val useCase = ClearLocalSessionUseCase(authRepository, credentialStateRepository)

        useCase()

        assertEquals(1, authRepository.signOutCalls)
        assertEquals(1, credentialStateRepository.clearCalls)
    }

    private class FakeAuthRepository : FirebaseAuthRepository(ContextWrapper(null)) {
        var signOutCalls = 0
            private set

        override fun signOut() {
            signOutCalls++
        }
    }

    private class FakeCredentialStateRepository(
        private val fail: Boolean = false,
    ) : CredentialStateRepository(ContextWrapper(null)) {
        var clearCalls = 0
            private set

        override suspend fun clearCredentialState() {
            clearCalls++
            if (fail) {
                throw IllegalStateException("clear failed")
            }
        }
    }
}
