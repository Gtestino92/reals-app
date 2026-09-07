package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
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
import com.reals.app.testutil.testApiExecutor
import com.reals.app.ui.profile.ProfilePhotoPrefetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileOperationHandlerPhotoPrefetchTest {
    @Test
    fun loadProfilePhotosWithEntryPrefetchTriggersAfterSuccessfulMetadataLoad() = runTest {
        val api = FakeRealsApi().apply {
            photosResponse = Response.success(
                listOf(
                    TestDtos.photo(id = "photo-2", position = 2),
                    TestDtos.photo(id = "photo-1", position = 1),
                )
            )
        }
        val prefetcher = RecordingProfilePhotoPrefetcher()
        val harness = harness(api = api, prefetcher = prefetcher)

        harness.handler.loadProfilePhotos(prefetchAfterSuccess = true)
        advanceUntilIdle()

        assertEquals(listOf("photo-1", "photo-2"), harness.ready().profilePhotos.map { it.id })
        assertEquals(listOf("profile-1" to listOf("photo-1", "photo-2")), prefetcher.calls)
    }

    @Test
    fun regularPhotoReloadDoesNotTriggerEntryPrefetch() = runTest {
        val api = FakeRealsApi().apply {
            photosResponse = Response.success(listOf(TestDtos.photo(id = "photo-1", position = 1)))
        }
        val prefetcher = RecordingProfilePhotoPrefetcher()
        val harness = harness(api = api, prefetcher = prefetcher)

        harness.handler.loadProfilePhotos()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, List<String>>>(), prefetcher.calls)
    }

    @Test
    fun emptyPhotoListDoesNotTriggerEntryPrefetch() = runTest {
        val api = FakeRealsApi().apply {
            photosResponse = Response.success(emptyList())
        }
        val prefetcher = RecordingProfilePhotoPrefetcher()
        val harness = harness(api = api, prefetcher = prefetcher)

        harness.handler.loadProfilePhotos(prefetchAfterSuccess = true)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, List<String>>>(), prefetcher.calls)
    }

    @Test
    fun completedLoadForPreviousProfileDoesNotUpdateStateOrPrefetch() = runTest {
        val releasePhotoResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetProfilePhotosResponse = { releasePhotoResponse.await() }
            photosResponse = Response.success(listOf(TestDtos.photo(id = "photo-1", position = 1)))
        }
        val prefetcher = RecordingProfilePhotoPrefetcher()
        val harness = harness(api = api, prefetcher = prefetcher)

        harness.handler.loadProfilePhotos(prefetchAfterSuccess = true)
        runCurrent()
        harness.state.value = ready(profile = profile(id = "profile-2"))
        releasePhotoResponse.complete(Unit)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), harness.ready().profilePhotos.map { it.id })
        assertEquals(emptyList<Pair<String, List<String>>>(), prefetcher.calls)
    }

    private fun TestScope.harness(
        api: FakeRealsApi,
        prefetcher: RecordingProfilePhotoPrefetcher,
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
            authRepository = FakeFirebaseAuthRepository(),
            getProfilePhotosUseCase = getProfilePhotos,
            profilePhotoPrefetcher = prefetcher,
            scope = this,
            onTerminalAuthFailure = {},
        )
        return Harness(state, handler)
    }

    private fun ready(profile: Profile = profile()): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session().copy(profileSnapshot = ProfileSnapshot.Found(profile)),
        )

    private fun profile(id: String = "profile-1"): Profile =
        TestDtos.profile().toDomain().copy(id = id)

    private data class Harness(
        val state: MutableStateFlow<RealsRootUiState>,
        val handler: ProfileOperationHandler,
    ) {
        fun ready(): RealsRootUiState.Ready = state.value as RealsRootUiState.Ready
    }

    private class RecordingProfilePhotoPrefetcher : ProfilePhotoPrefetcher {
        val calls = mutableListOf<Pair<String, List<String>>>()

        override fun prefetchOwnProfilePhotos(
            scope: CoroutineScope,
            profileId: String,
            photos: List<ProfilePhoto>,
        ) {
            calls += profileId to photos.map { it.id }
        }

        override fun cancel() = Unit
    }

    private class FakeFirebaseAuthRepository : FirebaseAuthRepository(ContextWrapper(null))
}
