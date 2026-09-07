package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.CredentialStateRepository
import com.reals.app.data.repository.EmailVerificationCheckResult
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
import com.reals.app.ui.auth.PasswordCredentialResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorPasswordCredentialTest {
    @Test
    fun `manual successful sign in can request password credential save`() = runTest {
        val harness = harness()
        val saveRequests = mutableListOf<Pair<String, String>>()

        harness.coordinator.signIn(" alex@example.com ", "secret-password") { email, password ->
            saveRequests += email to password
        }
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), harness.auth.signInRequests)
        assertEquals(listOf("alex@example.com" to "secret-password"), saveRequests)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `saved password credential sign in uses Firebase without save request`() = runTest {
        val harness = harness()
        val attemptId = harness.coordinator.beginPasswordCredentialSignIn()

        harness.coordinator.completePasswordCredentialSignIn(
            attemptId = attemptId!!,
            result = PasswordCredentialResult.Success("alex@example.com", "secret-password"),
        )
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), harness.auth.signInRequests)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `saved credential cancellation clears loading and does not sign in`() = runTest {
        val harness = harness()
        val attemptId = harness.coordinator.beginPasswordCredentialSignIn()

        harness.coordinator.completePasswordCredentialSignIn(
            attemptId = attemptId!!,
            result = PasswordCredentialResult.Cancelled,
        )
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.Login
        assertEquals(false, state.passwordCredentialLoading)
        assertNull(state.passwordCredentialAttemptId)
        assertNull(state.error)
        assertEquals(emptyList<String>(), harness.auth.signInRequests)
    }

    @Test
    fun `missing saved credentials returns to manual login with non technical message`() = runTest {
        val harness = harness()
        val attemptId = harness.coordinator.beginPasswordCredentialSignIn()

        harness.coordinator.completePasswordCredentialSignIn(
            attemptId = attemptId!!,
            result = PasswordCredentialResult.NotFound,
        )
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.Login
        assertEquals(false, state.passwordCredentialLoading)
        assertEquals("No encontramos credenciales guardadas. Podés ingresar manualmente.", state.credentialMessage)
        assertNull(state.passwordResetMessage)
        assertEquals(emptyList<String>(), harness.auth.signInRequests)
    }

    @Test
    fun `password credential start is ignored during another auth operation`() = runTest {
        val harness = harness()
        harness.state.value = RealsRootUiState.Login(googleLoading = true, googleAttemptId = 7L)

        assertNull(harness.coordinator.beginPasswordCredentialSignIn())
        assertEquals(7L, (harness.state.value as RealsRootUiState.Login).googleAttemptId)
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

    private fun TestScope.harness(): Harness {
        val context = ContextWrapper(null)
        val api = FakeRealsApi()
        val tokenProvider = FakeAuthTokenProvider()
        val meRepository = MeRepository(api, tokenProvider, testApiExecutor())
        val profileRepository = ProfileRepository(context, api, tokenProvider, testApiExecutor())
        val pushTokenRegistrationService = PushTokenRegistrationService(
            context,
            RegisterPushTokenUseCase(meRepository),
        )
        val auth = FakeAuth()
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

    private class FakeAuth : FirebaseAuthRepository(ContextWrapper(null)) {
        val signInRequests = mutableListOf<String>()
        var signOutCalls = 0
            private set

        override fun isConfigured(): Boolean = true

        override fun hasSignedInUser(): Boolean = true

        override fun currentUserEmail(): String = "alex@example.com"

        override suspend fun signIn(email: String, password: String): AuthOperationResult {
            signInRequests += email
            return AuthOperationResult.Success
        }

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
