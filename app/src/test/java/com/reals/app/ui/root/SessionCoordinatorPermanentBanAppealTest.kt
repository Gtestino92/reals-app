package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.dto.BanAppealResponseDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.BanAppealRepository
import com.reals.app.data.repository.CredentialStateRepository
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.domain.model.PermanentBanAppealStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.usecase.ClearLocalSessionUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.FinalizeAccountDeletionUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.GetPermanentBanAppealUseCase
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerified
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import com.reals.app.domain.usecase.RequestPasswordResetUseCase
import com.reals.app.domain.usecase.SubmitPermanentBanAppealUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorPermanentBanAppealTest {
    @Test
    fun `initial permanent ban loads appeal and does not provision or sign out`() = runTest {
        val harness = harness()
        harness.api.getMeResponse = backendErrorResponse(403, "ACCOUNT_PERMANENTLY_BANNED")
        harness.api.banAppealResponse = Response.success(appeal("AVAILABLE", banActive = true))

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.PermanentBanAppeal
        assertEquals(PermanentBanAppealStatus.Available, state.appeal?.status)
        assertEquals(listOf("getMe", "getMyBanAppeal"), harness.api.calls)
        assertEquals(0, harness.auth.signOutCalls)
        assertEquals(0, harness.readySessions.size)
    }

    @Test
    fun `temporary ban keeps existing suspension flow and skips appeal endpoint`() = runTest {
        val harness = harness()
        harness.api.getMeResponse = backendErrorResponse(
            403,
            "ACCOUNT_TEMPORARILY_BANNED",
            expiresAt = "2026-09-02T01:30:00Z",
        )

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.AccountSuspended
        assertEquals(AccountSuspension.Temporary("2026-09-02T01:30:00Z"), state.suspension)
        assertEquals(listOf("getMe"), harness.api.calls)
    }

    @Test
    fun `submit posts once then installs authoritative pending state from get`() = runTest {
        val harness = harness()
        harness.state.value = RealsRootUiState.PermanentBanAppeal(
            appeal = appeal("AVAILABLE", banActive = true).toDomain(),
        )
        harness.api.submitBanAppealResponse = Response.success(201, Unit)
        harness.api.banAppealResponse = Response.success(appeal("PENDING", banActive = true))

        harness.coordinator.submitPermanentBanAppeal("  Revisen mi caso  ")
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.PermanentBanAppeal
        assertEquals(PermanentBanAppealStatus.Pending, state.appeal?.status)
        assertEquals(listOf("submitMyBanAppeal", "getMyBanAppeal"), harness.api.calls)
        assertEquals("Revisen mi caso", harness.api.banAppealBody?.statement)
    }

    @Test
    fun `double submit while first is active does not create second post`() = runTest {
        val releaseSubmit = CompletableDeferred<Unit>()
        val harness = harness()
        harness.state.value = RealsRootUiState.PermanentBanAppeal(
            appeal = appeal("AVAILABLE", banActive = true).toDomain(),
        )
        harness.api.beforeSubmitMyBanAppealResponse = { releaseSubmit.await() }
        harness.api.banAppealResponse = Response.success(appeal("PENDING", banActive = true))

        harness.coordinator.submitPermanentBanAppeal("Revisen mi caso")
        runCurrent()
        harness.coordinator.submitPermanentBanAppeal("Revisen mi caso otra vez")
        runCurrent()
        releaseSubmit.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, harness.api.calls.count { it == "submitMyBanAppeal" })
    }

    @Test
    fun `already submitted reconciles once with get and does not retry post`() = runTest {
        val harness = harness()
        harness.state.value = RealsRootUiState.PermanentBanAppeal(
            appeal = appeal("AVAILABLE", banActive = true).toDomain(),
        )
        harness.api.submitBanAppealResponse =
            backendErrorResponse(409, "PENALTY_APPEAL_ALREADY_SUBMITTED")
        harness.api.banAppealResponse = Response.success(appeal("PENDING", banActive = true))

        harness.coordinator.submitPermanentBanAppeal("Revisen mi caso")
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.PermanentBanAppeal
        assertEquals(PermanentBanAppealStatus.Pending, state.appeal?.status)
        assertEquals(listOf("submitMyBanAppeal", "getMyBanAppeal"), harness.api.calls)
    }

    @Test
    fun `pending appeal manual refresh temporary ban routes to existing suspension surface`() = runTest {
        val expiresAt = "2026-09-02T01:30:00Z"
        val harness = harness()
        harness.state.value = RealsRootUiState.PermanentBanAppeal(
            appeal = appeal("PENDING", banActive = true).toDomain(),
        )
        harness.api.banAppealResponse = backendErrorResponse(
            403,
            "ACCOUNT_TEMPORARILY_BANNED",
            expiresAt = expiresAt,
        )

        harness.coordinator.refreshPermanentBanAppeal()
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.AccountSuspended
        assertEquals(AccountSuspension.Temporary(expiresAt), state.suspension)
        assertEquals(listOf("getMyBanAppeal"), harness.api.calls)
    }

    @Test
    fun `submit success reconciliation temporary ban routes to existing suspension surface`() = runTest {
        val expiresAt = "2026-09-02T01:30:00Z"
        val harness = harness()
        harness.state.value = RealsRootUiState.PermanentBanAppeal(
            appeal = appeal("AVAILABLE", banActive = true).toDomain(),
        )
        harness.api.submitBanAppealResponse = Response.success(201, Unit)
        harness.api.banAppealResponse = backendErrorResponse(
            403,
            "ACCOUNT_TEMPORARILY_BANNED",
            expiresAt = expiresAt,
        )

        harness.coordinator.submitPermanentBanAppeal("Revisen mi caso")
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.AccountSuspended
        assertEquals(AccountSuspension.Temporary(expiresAt), state.suspension)
        assertEquals(listOf("submitMyBanAppeal", "getMyBanAppeal"), harness.api.calls)
    }

    @Test
    fun `direct submit temporary ban failure routes to existing suspension surface`() = runTest {
        val expiresAt = "2026-09-02T01:30:00Z"
        val harness = harness()
        harness.state.value = RealsRootUiState.PermanentBanAppeal(
            appeal = appeal("AVAILABLE", banActive = true).toDomain(),
        )
        harness.api.submitBanAppealResponse = backendErrorResponse(
            403,
            "ACCOUNT_TEMPORARILY_BANNED",
            expiresAt = expiresAt,
        )

        harness.coordinator.submitPermanentBanAppeal("Revisen mi caso")
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.AccountSuspended
        assertEquals(AccountSuspension.Temporary(expiresAt), state.suspension)
        assertEquals(listOf("submitMyBanAppeal"), harness.api.calls)
    }

    @Test
    fun `approved appeal restarts normal bootstrap through get me and profile load`() = runTest {
        val harness = harness()
        harness.api.getMeResponse = backendErrorResponse(403, "ACCOUNT_PERMANENTLY_BANNED")
        harness.api.beforeGetMyBanAppealResponse = {
            harness.api.getMeResponse = Response.success(TestDtos.user(status = "ACTIVE"))
        }
        harness.api.banAppealResponse = Response.success(appeal("APPROVED", banActive = false))

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        assertEquals(listOf("getMe", "getMyBanAppeal", "getMe", "getMyProfile"), harness.api.calls)
        assertEquals(1, harness.readySessions.size)
    }

    @Test
    fun `approved appeal can still route to temporary suspension after bootstrap`() = runTest {
        val harness = harness()
        harness.api.getMeResponse = backendErrorResponse(403, "ACCOUNT_PERMANENTLY_BANNED")
        harness.api.beforeGetMyBanAppealResponse = {
            harness.api.getMeResponse = backendErrorResponse(
                403,
                "ACCOUNT_TEMPORARILY_BANNED",
                expiresAt = "2026-09-02T01:30:00Z",
            )
        }
        harness.api.banAppealResponse = Response.success(appeal("APPROVED", banActive = false))

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.AccountSuspended
        assertEquals(AccountSuspension.Temporary("2026-09-02T01:30:00Z"), state.suspension)
        assertEquals(0, harness.readySessions.size)
    }

    @Test
    fun `approved appeal bootstrap transient failure keeps approved appeal state`() = runTest {
        val harness = harness()
        harness.api.getMeResponse = backendErrorResponse(403, "ACCOUNT_PERMANENTLY_BANNED")
        harness.api.beforeGetMyBanAppealResponse = {
            harness.api.getMeResponse = backendErrorResponse(503, "SERVICE_UNAVAILABLE")
        }
        harness.api.banAppealResponse = Response.success(appeal("APPROVED", banActive = false))

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.PermanentBanAppeal
        assertEquals(PermanentBanAppealStatus.Approved, state.appeal?.status)
        assertNotNull(state.normalBootstrapError)
        assertEquals(0, harness.readySessions.size)
    }

    @Test
    fun `invalid appeal status and ban combination fails closed`() = runTest {
        val harness = harness()
        harness.api.getMeResponse = backendErrorResponse(403, "ACCOUNT_PERMANENTLY_BANNED")
        harness.api.banAppealResponse = Response.success(appeal("APPROVED", banActive = true))

        harness.coordinator.loadBackendSession()
        advanceUntilIdle()

        val state = harness.state.value as RealsRootUiState.PermanentBanAppeal
        assertEquals(null, state.appeal)
        assertNotNull(state.error)
        assertEquals(0, harness.readySessions.size)
    }

    @Test
    fun `appeal logout clears local session`() = runTest {
        val harness = harness()
        harness.state.value = RealsRootUiState.PermanentBanAppeal(
            appeal = appeal("PENDING", banActive = true).toDomain(),
        )

        harness.coordinator.signOut()
        advanceUntilIdle()

        assertTrue(harness.state.value is RealsRootUiState.Login)
        assertEquals(1, harness.auth.signOutCalls)
    }

    private fun TestScope.harness(): Harness {
        val context = ContextWrapper(null)
        val api = FakeRealsApi()
        val tokenProvider = FakeAuthTokenProvider()
        val meRepository = MeRepository(api, tokenProvider, testApiExecutor())
        val banAppealRepository = BanAppealRepository(api, tokenProvider, testApiExecutor())
        val profileRepository = ProfileRepository(context, api, tokenProvider, testApiExecutor())
        val pushTokenRegistrationService = PushTokenRegistrationService(
            context,
            RegisterPushTokenUseCase(meRepository),
        )
        val auth = FakeAuth()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Login())
        val readySessions = mutableListOf<ProvisionedSession>()
        val coordinator = SessionCoordinator(
            uiState = state,
            dependencies = SessionFeatureDependencies(
                authRepository = auth,
                requestPasswordReset = RequestPasswordResetUseCase(),
                clearLocalSession = ClearLocalSessionUseCase(
                    authRepository = auth,
                    credentialStateRepository = object : CredentialStateRepository(context) {
                        override suspend fun clearCredentialState() = Unit
                    },
                ),
                provisionAndLoadProfile = ProvisionAndLoadProfileUseCase(meRepository, profileRepository),
                getMe = GetMeUseCase(meRepository),
                getPermanentBanAppeal = GetPermanentBanAppealUseCase(banAppealRepository),
                submitPermanentBanAppeal = SubmitPermanentBanAppealUseCase(banAppealRepository),
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
        return Harness(api, auth, state, coordinator, readySessions)
    }

    private data class Harness(
        val api: FakeRealsApi,
        val auth: FakeAuth,
        val state: MutableStateFlow<RealsRootUiState>,
        val coordinator: SessionCoordinator,
        val readySessions: MutableList<ProvisionedSession>,
    )

    private class FakeAuth : FirebaseAuthRepository(ContextWrapper(null)) {
        var signOutCalls = 0
            private set

        override fun isConfigured(): Boolean = true

        override fun hasSignedInUser(): Boolean = true

        override fun currentUserEmail(): String = "user@example.com"

        override suspend fun signIn(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult =
            EmailVerificationCheckResult.Verified

        override fun signOut() {
            signOutCalls++
        }
    }

    private fun appeal(
        status: String,
        banActive: Boolean,
    ) = BanAppealResponseDto(
        status = status,
        banActive = banActive,
        appealedAt = null,
        reviewedAt = null,
    )
}
