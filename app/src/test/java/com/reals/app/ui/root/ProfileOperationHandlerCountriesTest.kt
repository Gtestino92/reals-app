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
class ProfileOperationHandlerCountriesTest {
    @Test fun `personal save preserves photo and preference feedback and concurrent countries`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply { beforeUpdateProfileResponse = { gate.await() } }
        val initial = ready(ProfileManagementState(matchFiltersMessage = "Preferencias guardadas"))
            .copy(photos = PhotoManagementUiState(photoActionMessage = "Foto guardada"))
        val harness = harness(api, initial)
        harness.handler.updateProfile(com.reals.app.domain.model.UpdateProfileInput("Ana", null, "Rosario", "AR"))
        runCurrent()
        assertTrue(harness.ready().updatingProfile)
        assertEquals("Foto guardada", harness.ready().photoActionMessage)
        assertEquals("Preferencias guardadas", harness.ready().matchFiltersMessage)
        harness.handler.loadCountriesIfNeeded()
        runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertTrue(harness.ready().countriesLoaded)
        assertEquals("Foto guardada", harness.ready().photoActionMessage)
        assertEquals("Preferencias guardadas", harness.ready().matchFiltersMessage)
        assertEquals("Perfil actualizado.", harness.ready().profileUpdateMessage)
    }

    @Test fun `old personal response cannot replace a new session`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply { beforeUpdateProfileResponse = { gate.await() } }
        val harness = harness(api)
        harness.handler.updateProfile(com.reals.app.domain.model.UpdateProfileInput("Ana", null, "Rosario", "AR"))
        runCurrent()
        val replacement = ready().copy(session = TestDomain.session().copy(user = TestDomain.session().user.copy(id = "other")))
        harness.state.value = replacement
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(replacement, harness.state.value)
    }

    @Test fun `preference save preserves personal and photo feedback`() = runTest {
        val harness = harness(initialState = ready(ProfileManagementState(profileUpdateMessage = "Datos guardados"))
            .copy(photos = PhotoManagementUiState(photoActionMessage = "Foto guardada")))
        harness.handler.updateMatchFilters(com.reals.app.domain.model.UpdateMatchFiltersInput("DATE", setOf("MALE"), 20, 40, 50))
        advanceUntilIdle()
        assertEquals("Datos guardados", harness.ready().profileUpdateMessage)
        assertEquals("Foto guardada", harness.ready().photoActionMessage)
        assertEquals("Filtros actualizados.", harness.ready().matchFiltersMessage)
    }
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

    @Test
    fun `photo load success preserves countries loaded concurrently`() = runTest {
        val photoRequestStarted = CompletableDeferred<Unit>()
        val releasePhotoResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetProfilePhotosResponse = {
                photoRequestStarted.complete(Unit)
                releasePhotoResponse.await()
            }
            photosResponse = Response.success(
                listOf(
                    TestDtos.photo(id = "photo-2", position = 2),
                    TestDtos.photo(id = "photo-1", position = 1),
                ),
            )
        }
        val harness = harness(api)

        harness.handler.loadProfilePhotos()
        runCurrent()
        photoRequestStarted.await()
        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf("AR", "BR"), harness.ready().countries.map { it.code })
        assertTrue(harness.ready().countriesLoaded)
        assertFalse(harness.ready().countriesLoading)
        assertEquals(null, harness.ready().countriesError)

        releasePhotoResponse.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("AR", "BR"), harness.ready().countries.map { it.code })
        assertTrue(harness.ready().countriesLoaded)
        assertFalse(harness.ready().countriesLoading)
        assertEquals(null, harness.ready().countriesError)
        assertEquals(listOf("photo-1", "photo-2"), harness.ready().profilePhotos.map { it.id })
        assertFalse(harness.ready().loadingPhotos)
    }

    @Test
    fun `photo load failure preserves countries loaded concurrently`() = runTest {
        val photoRequestStarted = CompletableDeferred<Unit>()
        val releasePhotoResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetProfilePhotosResponse = {
                photoRequestStarted.complete(Unit)
                releasePhotoResponse.await()
            }
            photosResponse = backendErrorResponse(500, "SERVER_ERROR", "photos failed")
        }
        val harness = harness(api)

        harness.handler.loadProfilePhotos()
        runCurrent()
        photoRequestStarted.await()
        harness.handler.loadCountriesIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf("AR", "BR"), harness.ready().countries.map { it.code })
        assertTrue(harness.ready().countriesLoaded)

        releasePhotoResponse.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("AR", "BR"), harness.ready().countries.map { it.code })
        assertTrue(harness.ready().countriesLoaded)
        assertFalse(harness.ready().countriesLoading)
        assertEquals(null, harness.ready().countriesError)
        assertTrue(harness.ready().profilePhotosError is ApiError.Backend)
        assertFalse(harness.ready().loadingPhotos)
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
