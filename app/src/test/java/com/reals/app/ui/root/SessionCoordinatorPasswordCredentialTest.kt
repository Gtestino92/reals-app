package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.CredentialStateRepository
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.EmailVerificationSendResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.usecase.ClearLocalSessionUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.FinalizeAccountDeletionUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerified
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import com.reals.app.domain.usecase.RequestPasswordResetUseCase
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.notifications.registration.PushTokenRegistrationService
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorPasswordCredentialTest {
    @Test
    fun `manual successful sign in with remember false does not request password save`() = runTest {
        val harness = harness()
        val saveRequests = mutableListOf<Pair<String, String>>()

        harness.coordinator.signIn(" alex@example.com ", "secret-password", rememberCredentials = false) { email, password ->
            saveRequests += email to password
        }
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), harness.auth.signInRequests)
        assertEquals(emptyList<Pair<String, String>>(), saveRequests)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `manual successful sign in with remember true requests password save`() = runTest {
        val harness = harness()
        val saveRequests = mutableListOf<Pair<String, String>>()

        harness.coordinator.signIn(" alex@example.com ", "secret-password", rememberCredentials = true) { email, password ->
            saveRequests += email to password
        }
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), harness.auth.signInRequests)
        assertEquals(listOf("alex@example.com" to "secret-password"), saveRequests)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `manual failed sign in with remember true does not request password save`() = runTest {
        val harness = harness(signInResult = AuthOperationResult.Failure("Credenciales inválidas."))
        val saveRequests = mutableListOf<Pair<String, String>>()

        harness.coordinator.signIn("alex@example.com", "bad-password", rememberCredentials = true) { email, password ->
            saveRequests += email to password
        }
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.Login
        assertEquals("Credenciales inválidas.", state.error)
        assertEquals(emptyList<Pair<String, String>>(), saveRequests)
        assertEquals(0, harness.readySessions.size)
    }

    @Test
    fun `password save failure after successful sign in does not block session load`() = runTest {
        val harness = harness()

        harness.coordinator.signIn("alex@example.com", "secret-password", rememberCredentials = true) { _, _ ->
            error("Credential Manager rejected the save request.")
        }
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), harness.auth.signInRequests)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `manual successful sign up with remember true requests password save`() = runTest {
        val harness = harness()
        val saveRequests = mutableListOf<Pair<String, String>>()

        harness.coordinator.signUp(" alex@example.com ", "secret-password", rememberCredentials = true) { email, password ->
            saveRequests += email to password
        }
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), harness.auth.signUpRequests)
        assertEquals(listOf("alex@example.com" to "secret-password"), saveRequests)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `logout clears Firebase session and credential active state`() = runTest {
        val harness = harness()

        harness.coordinator.signOut()
        advanceUntilIdle()

        assertTrue(harness.state.value is RealsRootUiState.Login)
        assertEquals(1, harness.auth.signOutCalls)
        assertEquals(1, harness.credentialState.clearCalls)
    }

    private fun TestScope.harness(
        signInResult: AuthOperationResult = AuthOperationResult.Success,
        signUpResult: AuthOperationResult = AuthOperationResult.Success,
    ): Harness {
        val context = ContextWrapper(null)
        val api = FakeRealsApi()
        val tokenProvider = FakeAuthTokenProvider()
        val meRepository = MeRepository(api, tokenProvider, testApiExecutor())
        val profileRepository = ProfileRepository(context, api, tokenProvider, testApiExecutor())
        val pushTokenRegistrationService = PushTokenRegistrationService(
            context,
            RegisterPushTokenUseCase(meRepository),
        )
        val auth = FakeAuth(signInResult, signUpResult)
        val credentialState = FakeCredentialState(context)
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Login())
        val readySessions = mutableListOf<ProvisionedSession>()
        val coordinator = SessionCoordinator(
            uiState = state,
            dependencies = SessionFeatureDependencies(
                authRepository = auth,
                requestPasswordReset = RequestPasswordResetUseCase(),
                clearLocalSession = ClearLocalSessionUseCase(auth, credentialState),
                provisionAndLoadProfile = ProvisionAndLoadProfileUseCase(meRepository, profileRepository),
                getMe = GetMeUseCase(meRepository),
                pushTokenRegistrationService = pushTokenRegistrationService,
                markLocalFirebaseEmailVerified = MarkLocalFirebaseEmailVerified { ApiResult.Success(Unit) },
            ),
            accountDependencies = AccountFeatureDependencies(
                reactivateAccount = ReactivateAccountUseCase(meRepository),
                deleteAccount = DeleteAccountUseCase(meRepository),
                finalizeAccountDeletion = FinalizeAccountDeletionUseCase(meRepository),
            ),
            scope = this,
            onActiveSessionLoaded = { readySessions += it },
            onReactivatedSessionLoaded = {},
        )
        return Harness(auth, credentialState, state, coordinator, readySessions)
    }

    private data class Harness(
        val auth: FakeAuth,
        val credentialState: FakeCredentialState,
        val state: MutableStateFlow<RealsRootUiState>,
        val coordinator: SessionCoordinator,
        val readySessions: MutableList<ProvisionedSession>,
    )

    private class FakeAuth(
        private val signInResult: AuthOperationResult,
        private val signUpResult: AuthOperationResult,
    ) : FirebaseAuthRepository(ContextWrapper(null)) {
        val signInRequests = mutableListOf<String>()
        val signUpRequests = mutableListOf<String>()
        var signOutCalls = 0
            private set

        override fun isConfigured(): Boolean = true

        override fun hasSignedInUser(): Boolean = true

        override fun currentUserEmail(): String = "alex@example.com"

        override suspend fun signIn(email: String, password: String): AuthOperationResult {
            signInRequests += email
            return signInResult
        }

        override suspend fun signUp(email: String, password: String): AuthOperationResult {
            signUpRequests += email
            return signUpResult
        }

        override suspend fun sendEmailVerificationEmail(): EmailVerificationSendResult =
            EmailVerificationSendResult.Sent

        override suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult =
            EmailVerificationCheckResult.Verified

        override fun signOut() {
            signOutCalls++
        }
    }

    private class FakeCredentialState(context: ContextWrapper) : CredentialStateRepository(context) {
        var clearCalls = 0
            private set

        override suspend fun clearCredentialState() {
            clearCalls++
        }
    }
}
