package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.EmailVerificationSendResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.GetCountriesUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerified
import com.reals.app.domain.usecase.ReorderProfilePhotosUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileOperationHandlerLocalEmailVerificationTest {
    @Test
    fun `check email verification in local calls shared helper and marks verified after refresh`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.NotVerified, EmailVerificationCheckResult.Verified)
        val mark = FakeMark()
        val harness = harness(auth = auth, mark = mark, localEnabled = true)

        harness.handler.checkEmailVerification()
        advanceUntilIdle()

        val state = harness.ready()
        assertEquals(1, mark.calls)
        assertEquals(2, auth.checkCalls)
        assertFalse(state.emailVerificationRequired)
        assertTrue(state.emailVerificationLocallyVerified)
    }

    @Test
    fun `check email verification disabled preserves firebase-only check`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.Verified)
        val mark = FakeMark()
        val harness = harness(auth = auth, mark = mark, localEnabled = false)

        harness.handler.checkEmailVerification()
        advanceUntilIdle()

        assertEquals(0, mark.calls)
        assertEquals(1, auth.checkCalls)
        assertTrue(harness.ready().emailVerificationLocallyVerified)
    }

    @Test
    fun `local helper failure preserves unverified state and safe error`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.NotVerified)
        val mark = FakeMark(ApiResult.Failure(ApiError.Network("down")))
        val harness = harness(auth = auth, mark = mark, localEnabled = true)

        harness.handler.checkEmailVerification()
        advanceUntilIdle()

        val state = harness.ready()
        assertTrue(state.emailVerificationRequired)
        assertFalse(state.emailVerificationLocallyVerified)
        assertEquals("No pudimos comprobar la verificación. Intentá nuevamente.", state.emailVerificationError)
        assertNull(state.profileActivationError)
    }

    @Test
    fun `upload email not verified failure keeps photo error and sets remediation state`() {
        val error = ApiError.Backend(403, "EMAIL_NOT_VERIFIED", "EMAIL_NOT_VERIFIED", "verify")
        val state = photoActionFailureState(
            previous = ready(
                profileOp = ProfileManagementState(emailVerificationLocallyVerified = true),
                addingPhoto = true,
            ),
            error = error,
        )

        assertEquals(error, state.photoActionError)
        assertFalse(state.addingPhoto)
        assertTrue(state.emailVerificationRequired)
        assertFalse(state.emailVerificationLocallyVerified)
    }

    @Test
    fun `replacement email not verified failure keeps photo error and sets remediation state`() {
        val error = ApiError.Backend(403, "EMAIL_NOT_VERIFIED", "EMAIL_NOT_VERIFIED", "verify")
        val state = photoActionFailureState(previous = ready(addingPhoto = true), error = error)

        assertEquals(error, state.photoActionError)
        assertTrue(state.emailVerificationRequired)
        assertFalse(state.emailVerificationLocallyVerified)
    }

    @Test
    fun `other photo errors do not set email verification required`() {
        val error = ApiError.Backend(500, "SERVER_ERROR", "SERVER_ERROR", "boom")
        val state = photoActionFailureState(
            previous = ready(profileOp = ProfileManagementState(), addingPhoto = true),
            error = error,
        )

        assertEquals(error, state.photoActionError)
        assertFalse(state.emailVerificationRequired)
    }

    private fun TestScope.harness(
        auth: FakeAuth,
        mark: FakeMark,
        localEnabled: Boolean,
    ): Harness {
        val context = ContextWrapper(null)
        val api = FakeRealsApi()
        val profileRepository = ProfileRepository(context, api, FakeAuthTokenProvider(), testApiExecutor())
        val getProfilePhotos = GetProfilePhotosUseCase(profileRepository)
        val coordinator = LocalFirebaseEmailVerificationCoordinator(localEnabled, auth, mark)
        val state = MutableStateFlow<RealsRootUiState>(ready())
        val handler = ProfileOperationHandler(
            uiState = state,
            dependencies = ProfileFeatureDependencies(
                createProfile = CreateProfileUseCase(profileRepository),
                updateProfile = UpdateProfileUseCase(profileRepository),
                getCountries = GetCountriesUseCase(profileRepository),
                updateMatchFilters = UpdateMatchFiltersUseCase(profileRepository),
                getProfilePhotos = getProfilePhotos,
                addProfilePhotoFile = AddProfilePhotoFileUseCase(profileRepository),
                replaceProfilePhotoFile = ReplaceProfilePhotoFileUseCase(profileRepository),
                deleteProfilePhoto = DeleteProfilePhotoUseCase(profileRepository),
                reorderProfilePhotos = ReorderProfilePhotosUseCase(profileRepository),
                activateProfile = ActivateProfileUseCase(profileRepository),
            ),
            authRepository = auth,
            localFirebaseEmailVerificationCoordinator = coordinator,
            getProfilePhotosUseCase = getProfilePhotos,
            scope = this,
            onTerminalAuthFailure = {},
        )
        return Harness(state, handler)
    }

    private fun ready(
        profileOp: ProfileManagementState = ProfileManagementState(emailVerificationRequired = true),
        addingPhoto: Boolean = false,
    ): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session().copy(
                profileSnapshot = ProfileSnapshot.Found(TestDtos.profile(status = "DRAFT").toDomain()),
            ),
            profileOp = profileOp,
            photos = PhotoManagementUiState(addingPhoto = addingPhoto),
        )

    private data class Harness(
        val state: MutableStateFlow<RealsRootUiState>,
        val handler: ProfileOperationHandler,
    ) {
        fun ready(): RealsRootUiState.Ready = state.value as RealsRootUiState.Ready
    }

    private class FakeAuth(
        vararg results: EmailVerificationCheckResult,
    ) : FirebaseAuthRepository(ContextWrapper(null)) {
        private val queue = ArrayDeque(results.toList())
        var checkCalls = 0
            private set

        override suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult {
            checkCalls++
            return if (queue.isEmpty()) EmailVerificationCheckResult.Verified else queue.removeFirst()
        }

        override suspend fun sendEmailVerificationEmail(): EmailVerificationSendResult =
            EmailVerificationSendResult.Sent
    }

    private class FakeMark(
        private val result: ApiResult<Unit> = ApiResult.Success(Unit),
    ) : MarkLocalFirebaseEmailVerified {
        var calls = 0
            private set

        override suspend fun invoke(): ApiResult<Unit> {
            calls++
            return result
        }
    }
}
