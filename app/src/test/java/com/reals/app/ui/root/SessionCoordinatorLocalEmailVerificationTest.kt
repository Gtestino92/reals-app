package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.EmailVerificationSendResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.data.mapper.toDomain
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerified
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import com.reals.app.notifications.registration.PushTokenRegistrationService
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorLocalEmailVerificationTest {
    @Test
    fun `restored local session verifies after backend active user load before ready`() = runTest {
        val harness = harness(
            auth = FakeAuth(EmailVerificationCheckResult.NotVerified, EmailVerificationCheckResult.Verified),
            localEnabled = true,
        )

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        assertEquals(listOf("getMe", "getMyProfile"), harness.api.calls)
        assertEquals(1, harness.mark.calls)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `existing local login verifies before ready routing`() = runTest {
        val harness = harness(
            auth = FakeAuth(EmailVerificationCheckResult.NotVerified, EmailVerificationCheckResult.Verified),
            localEnabled = true,
        )

        harness.coordinator.signIn("user@example.com", "password")
        advanceUntilIdle()

        assertEquals(listOf("user@example.com"), harness.auth.signInRequests)
        assertEquals(1, harness.mark.calls)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `local signup suppresses email verification email and verifies after provisioning`() = runTest {
        val harness = harness(
            auth = FakeAuth(EmailVerificationCheckResult.NotVerified, EmailVerificationCheckResult.Verified),
            localEnabled = true,
        )
        harness.api.getMeResponse = backendErrorResponse(404, "NOT_FOUND", "missing")

        harness.coordinator.signUp("new@example.com", "password")
        advanceUntilIdle()

        assertEquals(0, harness.auth.emailVerificationSendCalls)
        assertEquals(listOf("getMe", "provisionMe", "getMyProfile"), harness.api.calls)
        assertEquals(1, harness.mark.calls)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `disabled flag signup sends verification email and never calls local endpoint`() = runTest {
        val harness = harness(auth = FakeAuth(EmailVerificationCheckResult.NotVerified), localEnabled = false)

        harness.coordinator.signUp("new@example.com", "password")
        advanceUntilIdle()

        assertEquals(1, harness.auth.emailVerificationSendCalls)
        assertEquals(0, harness.mark.calls)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `local helper failure blocks ready session with controlled error`() = runTest {
        val harness = harness(
            auth = FakeAuth(EmailVerificationCheckResult.NotVerified),
            mark = FakeMark(ApiResult.Failure(ApiError.Network("down"))),
            localEnabled = true,
        )

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        assertTrue(harness.state.value is RealsRootUiState.Failure)
        assertEquals(0, harness.readySessions.size)
    }

    @Test
    fun `already verified local user skips backend local verification`() = runTest {
        val harness = harness(auth = FakeAuth(EmailVerificationCheckResult.Verified), localEnabled = true)

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        assertEquals(0, harness.mark.calls)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `deleted backend account does not call local verification`() = runTest {
        val harness = harness(auth = FakeAuth(EmailVerificationCheckResult.NotVerified), localEnabled = true)
        harness.api.userResponse = retrofit2.Response.success(TestDtos.user(status = "DELETED"))

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        assertEquals(0, harness.mark.calls)
        assertTrue(harness.state.value is RealsRootUiState.AccountDeletionPending)
    }

    @Test
    fun `reactivation verifies only after successful backend reactivation`() = runTest {
        val harness = harness(
            auth = FakeAuth(EmailVerificationCheckResult.NotVerified, EmailVerificationCheckResult.Verified),
            localEnabled = true,
        )
        harness.state.value = RealsRootUiState.AccountDeletionPending(TestDtos.user(status = "DELETED").let {
            it.toDomain()
        })

        harness.coordinator.reactivateAccount()
        advanceUntilIdle()

        assertEquals(listOf("reactivateMe", "getMyProfile"), harness.api.calls)
        assertEquals(1, harness.mark.calls)
        assertEquals(1, harness.reactivatedSessions.size)
    }

    @Test
    fun `duplicate refresh attempts do not duplicate concurrent backend verification calls`() = runTest {
        val releaseMark = CompletableDeferred<Unit>()
        val harness = harness(
            auth = FakeAuth(
                EmailVerificationCheckResult.NotVerified,
                EmailVerificationCheckResult.Verified,
                EmailVerificationCheckResult.Verified,
            ),
            mark = FakeMark(beforeReturn = { releaseMark.await() }),
            localEnabled = true,
        )

        harness.coordinator.loadBackendSession()
        harness.coordinator.loadBackendSession()
        runCurrent()

        assertEquals(1, harness.mark.calls)
        releaseMark.complete(Unit)
        advanceUntilIdle()
    }

    private fun TestScope.harness(
        auth: FakeAuth,
        mark: FakeMark = FakeMark(),
        localEnabled: Boolean,
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
        val localCoordinator = LocalFirebaseEmailVerificationCoordinator(localEnabled, auth, mark)
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Checking)
        val readySessions = mutableListOf<ProvisionedSession>()
        val reactivatedSessions = mutableListOf<ProvisionedSession>()
        val coordinator = SessionCoordinator(
            uiState = state,
            dependencies = SessionFeatureDependencies(
                authRepository = auth,
                provisionAndLoadProfile = ProvisionAndLoadProfileUseCase(meRepository, profileRepository),
                getMe = GetMeUseCase(meRepository),
                pushTokenRegistrationService = pushTokenRegistrationService,
                markLocalFirebaseEmailVerified = mark,
                localFirebaseEmailAutoVerificationEnabled = localEnabled,
                localFirebaseEmailVerificationCoordinator = localCoordinator,
            ),
            accountDependencies = AccountFeatureDependencies(
                reactivateAccount = ReactivateAccountUseCase(meRepository),
                deleteAccount = DeleteAccountUseCase(meRepository, auth),
            ),
            scope = this,
            onActiveSessionLoaded = { readySessions += it },
            onReactivatedSessionLoaded = { reactivatedSessions += it },
        )
        return Harness(api, auth, mark, state, coordinator, readySessions, reactivatedSessions)
    }

    private data class Harness(
        val api: FakeRealsApi,
        val auth: FakeAuth,
        val mark: FakeMark,
        val state: MutableStateFlow<RealsRootUiState>,
        val coordinator: SessionCoordinator,
        val readySessions: MutableList<ProvisionedSession>,
        val reactivatedSessions: MutableList<ProvisionedSession>,
    )

    private class FakeAuth(
        vararg verificationResults: EmailVerificationCheckResult,
    ) : FirebaseAuthRepository(ContextWrapper(null)) {
        private val queue = ArrayDeque(verificationResults.toList())
        val signInRequests = mutableListOf<String>()
        var emailVerificationSendCalls = 0
            private set

        override fun isConfigured(): Boolean = true

        override fun hasSignedInUser(): Boolean = true

        override fun currentUserEmail(): String = "user@example.com"

        override suspend fun signIn(email: String, password: String): AuthOperationResult {
            signInRequests += email
            return AuthOperationResult.Success
        }

        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun sendEmailVerificationEmail(): EmailVerificationSendResult {
            emailVerificationSendCalls++
            return EmailVerificationSendResult.Sent
        }

        override suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult =
            if (queue.isEmpty()) EmailVerificationCheckResult.Verified else queue.removeFirst()
    }

    private class FakeMark(
        private val result: ApiResult<Unit> = ApiResult.Success(Unit),
        private val beforeReturn: suspend () -> Unit = {},
    ) : MarkLocalFirebaseEmailVerified {
        var calls = 0
            private set

        override suspend fun invoke(): ApiResult<Unit> {
            calls++
            beforeReturn()
            return result
        }
    }
}
