package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.EmailVerificationSendResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.GetCountriesUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.ReorderProfilePhotosUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileOperationHandlerPhotoReorderTest {
    @Test
    fun `move photo optimistically updates photos and calls reorder once`() = runTest {
        val api = FakeRealsApi()
        val releaseResponse = CompletableDeferred<Unit>()
        api.beforeReorderPhotosResponse = { releaseResponse.await() }
        api.reorderPhotosResponse = Response.success(
            listOf(TestDtos.photo("photo-2", 1), TestDtos.photo("photo-1", 4)),
        )
        val harness = harness(api = api)
        harness.state.value = ready(photos = listOf(photo("photo-1", 1), photo("photo-2", 4)))

        harness.handler.moveProfilePhoto("photo-1", 4)

        val optimistic = harness.readyState()
        assertEquals(listOf("photo-2", "photo-1"), optimistic.profilePhotos.map { it.id })
        assertTrue(optimistic.reorderingPhotos)
        runCurrent()
        assertEquals(listOf("reorderMyProfilePhotos"), api.calls)

        releaseResponse.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `move photo success uses backend response and clears reorder error`() = runTest {
        val api = FakeRealsApi()
        api.reorderPhotosResponse = Response.success(
            listOf(TestDtos.photo("photo-2", 1), TestDtos.photo("photo-1", 4)),
        )
        val harness = harness(api = api)
        harness.state.value = ready(
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                photoReorderError = ApiError.Network("old"),
            ),
        )

        harness.handler.moveProfilePhoto("photo-1", 4)
        advanceUntilIdle()

        val state = harness.readyState()
        assertEquals(listOf("photo-2", "photo-1"), state.profilePhotos.map { it.id })
        assertFalse(state.reorderingPhotos)
        assertEquals(null, state.photoReorderError)
        assertEquals(null, state.photoReorderMessage)
        assertEquals(listOf("reorderMyProfilePhotos"), api.calls)
    }

    @Test
    fun `move photo failure rolls back previous order and sets error`() = runTest {
        val api = FakeRealsApi()
        api.reorderPhotosResponse = backendErrorResponse(500, "SERVER_ERROR", "boom")
        val previousPhotos = listOf(photo("photo-1", 1), photo("photo-2", 4))
        val harness = harness(api = api)
        harness.state.value = ready(photos = previousPhotos)

        harness.handler.moveProfilePhoto("photo-1", 4)
        advanceUntilIdle()

        val state = harness.readyState()
        assertEquals(previousPhotos.map { it.id to it.position }, state.profilePhotos.map { it.id to it.position })
        assertFalse(state.reorderingPhotos)
        assertTrue(state.photoReorderError is ApiError.Backend)
        assertEquals(null, state.photoReorderMessage)
    }

    @Test
    fun `second move while reorder in flight is ignored`() = runTest {
        val api = FakeRealsApi()
        val releaseResponse = CompletableDeferred<Unit>()
        api.beforeReorderPhotosResponse = { releaseResponse.await() }
        api.reorderPhotosResponse = Response.success(
            listOf(TestDtos.photo("photo-2", 1), TestDtos.photo("photo-1", 4)),
        )
        val harness = harness(api = api)
        harness.state.value = ready(photos = listOf(photo("photo-1", 1), photo("photo-2", 4)))

        harness.handler.moveProfilePhoto("photo-1", 4)
        harness.handler.moveProfilePhoto("photo-2", 7)

        runCurrent()
        assertEquals(listOf("reorderMyProfilePhotos"), api.calls)
        releaseResponse.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `activation does not autosave reorder`() = runTest {
        val api = FakeRealsApi()
        val harness = harness(api = api)
        harness.state.value = ready(
            profile = draftProfile(),
            photos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
            profileOp = ProfileManagementState(emailVerificationLocallyVerified = true),
        )

        harness.handler.activateProfile()
        advanceUntilIdle()

        assertEquals(listOf("activateMyProfile"), api.calls)
        assertTrue(harness.state.value is RealsRootUiState.ActivationComplete)
    }

    @Test
    fun `activation is blocked while reorder is in flight`() = runTest {
        val api = FakeRealsApi()
        val harness = harness(api = api)
        harness.state.value = ready(
            profile = draftProfile(),
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                reorderingPhotos = true,
            ),
            profileOp = ProfileManagementState(emailVerificationLocallyVerified = true),
        )

        harness.handler.activateProfile()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), api.calls)
        assertTrue(harness.state.value is RealsRootUiState.Ready)
    }

    @Test
    fun `photo load add replace and delete are blocked while reorder is in flight`() = runTest {
        val api = FakeRealsApi()
        val harness = harness(api = api)
        harness.state.value = ready(
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                reorderingPhotos = true,
            ),
        )
        harness.handler.loadProfilePhotos()
        harness.handler.addProfilePhotoFile(3, null)
        harness.handler.replaceProfilePhotoFile("photo-1", 1, null)
        harness.handler.deleteProfilePhoto("photo-1", 1)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), api.calls)
    }

    private fun TestScope.harness(
        api: FakeRealsApi = FakeRealsApi(),
        authRepository: FirebaseAuthRepository = FakeFirebaseAuthRepository(),
    ): Harness {
        val profileRepository = ProfileRepository(
            context = ContextWrapper(null),
            api = api,
            tokenProvider = FakeAuthTokenProvider(),
            apiExecutor = testApiExecutor(),
        )
        val getProfilePhotos = GetProfilePhotosUseCase(profileRepository)
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
            authRepository = authRepository,
            getProfilePhotosUseCase = getProfilePhotos,
            scope = this,
            onTerminalAuthFailure = {},
        )
        return Harness(state, handler)
    }

    private fun ready(
        profile: Profile = activeProfile(),
        photos: List<ProfilePhoto> = emptyList(),
        photosState: PhotoManagementUiState = PhotoManagementUiState(profilePhotos = photos),
        profileOp: ProfileManagementState = ProfileManagementState(),
        editingActiveProfile: Boolean = false,
    ): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session().copy(profileSnapshot = ProfileSnapshot.Found(profile)),
            profileOp = profileOp,
            photos = photosState,
            editingActiveProfile = editingActiveProfile,
        )

    private fun activeProfile(): Profile =
        TestDtos.profile(status = "ACTIVE").toDomain().copy(photoCount = 2)

    private fun draftProfile(): Profile =
        TestDtos.profile(status = "DRAFT").toDomain().copy(photoCount = 2)

    private fun photo(id: String, position: Int): ProfilePhoto =
        TestDtos.photo(id = id, position = position).toDomain()

    private data class Harness(
        val state: MutableStateFlow<RealsRootUiState>,
        val handler: ProfileOperationHandler,
    ) {
        fun readyState(): RealsRootUiState.Ready = state.value as RealsRootUiState.Ready
    }

    private class FakeFirebaseAuthRepository(
        private val emailVerificationCheckResult: EmailVerificationCheckResult =
            EmailVerificationCheckResult.Verified,
    ) : FirebaseAuthRepository(ContextWrapper(null)) {
        override suspend fun sendEmailVerificationEmail(): EmailVerificationSendResult =
            EmailVerificationSendResult.Sent

        override suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult =
            emailVerificationCheckResult
    }
}
