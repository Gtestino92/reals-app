package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.domain.model.CountryReference
import com.reals.app.domain.model.ProfileSnapshot
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ProfileOperationHandlerCountriesTest {
    @Test
    fun `successful country loading stores returned domain list`() = runTest {
        val harness = harness()

        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf("getCountries"), harness.api.calls)
        assertFalse(harness.ready().countriesLoading)
        assertTrue(harness.ready().countriesLoaded)
        assertEquals(listOf("AR", "BR"), harness.ready().countries.map { it.code })
        assertEquals(null, harness.ready().countriesError)
    }

    @Test
    fun `failed country loading exposes ApiError`() = runTest {
        val api = FakeRealsApi().apply {
            countriesResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
        }
        val harness = harness(api)

        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()

        assertFalse(harness.ready().countriesLoading)
        assertTrue(harness.ready().countriesError is ApiError.Backend)
        assertFalse(harness.ready().countriesLoaded)
    }

    @Test
    fun `load countries does not reload when already loaded`() = runTest {
        val harness = harness(
            initialState = ready(
                profileOp = ProfileManagementState(
                    countries = listOf(CountryReference("AR", "Argentina")),
                    countriesLoaded = true,
                ),
            ),
        )

        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()

        assertTrue(harness.api.calls.isEmpty())
    }

    @Test
    fun `load countries does not start another request while loading`() = runTest {
        val harness = harness(
            initialState = ready(
                profileOp = ProfileManagementState(countriesLoading = true),
            ),
        )

        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()

        assertTrue(harness.api.calls.isEmpty())
    }

    @Test
    fun `explicit retry can reload after an error`() = runTest {
        val api = FakeRealsApi().apply {
            countriesResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
        }
        val harness = harness(api)

        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()
        api.countriesResponse = Response.success(listOf(TestDtos.country("UY", "Uruguay")))
        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf("getCountries", "getCountries"), api.calls)
        assertEquals(listOf("UY"), harness.ready().countries.map { it.code })
        assertEquals(null, harness.ready().countriesError)
        assertTrue(harness.ready().countriesLoaded)
    }

    private fun TestScope.harness(
        api: FakeRealsApi = FakeRealsApi(),
        initialState: RealsRootUiState.Ready = ready(),
    ): Harness {
        val profileRepository = ProfileRepository(
            context = ContextWrapper(null),
            api = api,
            tokenProvider = FakeAuthTokenProvider(),
            apiExecutor = testApiExecutor(),
        )
        val getProfilePhotos = GetProfilePhotosUseCase(profileRepository)
        val state = MutableStateFlow<RealsRootUiState>(initialState)
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
            authRepository = FakeFirebaseAuthRepository(),
            getProfilePhotosUseCase = getProfilePhotos,
            scope = this,
            onTerminalAuthFailure = {},
        )
        return Harness(api, state, handler)
    }

    private fun ready(profileOp: ProfileManagementState = ProfileManagementState()): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session().copy(
                profileSnapshot = ProfileSnapshot.Found(TestDtos.profile().toDomain()),
            ),
            profileOp = profileOp,
        )

    private data class Harness(
        val api: FakeRealsApi,
        val state: MutableStateFlow<RealsRootUiState>,
        val handler: ProfileOperationHandler,
    ) {
        fun ready(): RealsRootUiState.Ready = state.value as RealsRootUiState.Ready
    }

    private class FakeFirebaseAuthRepository : FirebaseAuthRepository(ContextWrapper(null))
}
