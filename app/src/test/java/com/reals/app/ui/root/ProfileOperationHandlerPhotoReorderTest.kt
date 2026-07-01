package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.EmailVerificationSendResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.domain.model.PhotoPlacementInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileOperationHandlerPhotoReorderTest {
    @Test
    fun `local move sets pending photo order`() = runTest {
        val harness = harness()
        harness.state.value = ready(photos = listOf(photo("photo-1", 1), photo("photo-2", 4)))

        harness.handler.moveProfilePhotoLocally("photo-1", 4)

        val state = harness.readyState()
        assertEquals(4, state.pendingPhotoOrder?.first { it.photoId == "photo-1" }?.position)
        assertEquals(1, state.pendingPhotoOrder?.first { it.photoId == "photo-2" }?.position)
    }

    @Test
    fun `local move clears previous reorder feedback`() = runTest {
        val harness = harness()
        harness.state.value = ready(
            photos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                photoReorderError = ApiError.Network("failed"),
                photoReorderMessage = "old",
            ),
        )

        harness.handler.moveProfilePhotoLocally("photo-1", 4)

        val state = harness.readyState()
        assertEquals(null, state.photoReorderError)
        assertEquals(null, state.photoReorderMessage)
    }

    @Test
    fun `close with pending reorder success calls use case once and closes`() = runTest {
        val api = FakeRealsApi()
        api.reorderPhotosResponse = Response.success(listOf(TestDtos.photo("photo-1", 4), TestDtos.photo("photo-2", 1)))
        val harness = harness(api = api)
        harness.state.value = ready(
            profile = activeProfile(),
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                pendingPhotoOrder = listOf(
                    PhotoPlacementInput("photo-1", 4),
                    PhotoPlacementInput("photo-2", 1),
                ),
            ),
            editingActiveProfile = true,
        )

        harness.handler.closeProfileManagementSavingPendingChanges { ready ->
            harness.state.value = ready.copy(editingActiveProfile = false)
        }
        advanceUntilIdle()

        val state = harness.readyState()
        assertEquals(listOf("reorderMyProfilePhotos"), api.calls)
        assertEquals(listOf(1, 4), state.profilePhotos.map { it.position })
        assertEquals(null, state.pendingPhotoOrder)
        assertFalse(state.editingActiveProfile)
    }

    @Test
    fun `close with pending reorder failure keeps pending order and does not close`() = runTest {
        val api = FakeRealsApi()
        api.reorderPhotosResponse = backendErrorResponse(500, "SERVER_ERROR", "boom")
        val pendingOrder = listOf(PhotoPlacementInput("photo-1", 4), PhotoPlacementInput("photo-2", 1))
        val harness = harness(api = api)
        harness.state.value = ready(
            profile = activeProfile(),
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                pendingPhotoOrder = pendingOrder,
            ),
            editingActiveProfile = true,
        )

        harness.handler.closeProfileManagementSavingPendingChanges { ready ->
            harness.state.value = ready.copy(editingActiveProfile = false)
        }
        advanceUntilIdle()

        val state = harness.readyState()
        assertEquals(listOf("reorderMyProfilePhotos"), api.calls)
        assertEquals(pendingOrder, state.pendingPhotoOrder)
        assertTrue(state.photoReorderError is ApiError.Backend)
        assertTrue(state.editingActiveProfile)
    }

    @Test
    fun `activation with pending reorder success proceeds to activation`() = runTest {
        val api = FakeRealsApi()
        api.reorderPhotosResponse = Response.success(listOf(TestDtos.photo("photo-1", 4), TestDtos.photo("photo-2", 1)))
        val harness = harness(api = api)
        harness.state.value = ready(
            profile = draftProfile(),
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                pendingPhotoOrder = listOf(
                    PhotoPlacementInput("photo-1", 4),
                    PhotoPlacementInput("photo-2", 1),
                ),
            ),
            profileOp = ProfileManagementState(emailVerificationLocallyVerified = true),
        )

        harness.handler.activateProfile()
        advanceUntilIdle()

        assertEquals(listOf("reorderMyProfilePhotos", "activateMyProfile"), api.calls)
        assertTrue(harness.state.value is RealsRootUiState.ActivationComplete)
    }

    @Test
    fun `activation with pending reorder failure does not activate`() = runTest {
        val api = FakeRealsApi()
        api.reorderPhotosResponse = backendErrorResponse(500, "SERVER_ERROR", "boom")
        val harness = harness(api = api)
        harness.state.value = ready(
            profile = draftProfile(),
            photosState = PhotoManagementUiState(
                profilePhotos = listOf(photo("photo-1", 1), photo("photo-2", 4)),
                pendingPhotoOrder = listOf(
                    PhotoPlacementInput("photo-1", 4),
                    PhotoPlacementInput("photo-2", 1),
                ),
            ),
            profileOp = ProfileManagementState(emailVerificationLocallyVerified = true),
        )

        harness.handler.activateProfile()
        advanceUntilIdle()

        val state = harness.readyState()
        assertEquals(listOf("reorderMyProfilePhotos"), api.calls)
        assertTrue(state.photoReorderError is ApiError.Backend)
        assertEquals(2, state.pendingPhotoOrder?.size)
    }

    @Test
    fun `activation without pending reorder behaves as before`() = runTest {
        val api = FakeRealsApi()
        val harness = harness(api = api)
        harness.state.value = ready(
            profile = draftProfile(),
            profileOp = ProfileManagementState(emailVerificationLocallyVerified = true),
        )

        harness.handler.activateProfile()
        advanceUntilIdle()

        assertEquals(listOf("activateMyProfile"), api.calls)
        assertTrue(harness.state.value is RealsRootUiState.ActivationComplete)
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
