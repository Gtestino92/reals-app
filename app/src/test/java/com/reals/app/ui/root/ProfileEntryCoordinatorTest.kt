package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ProfileEntryCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = ProfileEntryCoordinator(getProfilePhotos(api))

    @Test
    fun `active profile returns LoadHome with auto navigation`() = runBlocking {
        val result = coordinator.enter(
            session = TestDomain.session(),
            onPending = {},
        )

        assertTrue(result is ProfileEntryResult.LoadHome)
        result as ProfileEntryResult.LoadHome
        assertTrue(result.ready.home.homeLoading)
        assertFalse(result.publishLoadingState)
        assertTrue(result.autoNavigateEngagements)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `missing profile returns ready state`() = runBlocking {
        val session = TestDomain.session().copy(profileSnapshot = ProfileSnapshot.Missing)

        val result = coordinator.enter(
            session = session,
            onPending = {},
        )

        assertEquals(ProfileEntryResult.ShowReady(RealsRootUiState.Ready(session)), result)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `inactive profile publishes photo loading state`() = runBlocking {
        var pending: ProfileEntryResult.ShowReady? = null
        val session = inactiveProfileSession()

        coordinator.enter(
            session = session,
            onPending = { pending = it },
        )

        assertEquals(session, pending?.state?.session)
        assertTrue(pending?.state?.photos?.loadingPhotos == true)
    }

    @Test
    fun `inactive profile photo success returns sorted photos`() = runBlocking {
        api.photosResponse = Response.success(
            listOf(
                TestDtos.photo(id = "photo-2", position = 2),
                TestDtos.photo(id = "photo-1", position = 1),
            )
        )

        val result = coordinator.enter(
            session = inactiveProfileSession(),
            onPending = {},
        )

        assertTrue(result is ProfileEntryResult.ShowReady)
        val state = (result as ProfileEntryResult.ShowReady).state
        assertFalse(state.photos.loadingPhotos)
        assertEquals(listOf(1, 2), state.photos.profilePhotos.map { it.position })
        assertEquals(null, state.photos.profilePhotosError)
    }

    @Test
    fun `inactive profile photo failure returns ready state with error`() = runBlocking {
        api.photosResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")

        val result = coordinator.enter(
            session = inactiveProfileSession(),
            onPending = {},
        )

        assertTrue(result is ProfileEntryResult.ShowReady)
        val state = (result as ProfileEntryResult.ShowReady).state
        assertFalse(state.photos.loadingPhotos)
        assertTrue(state.photos.profilePhotosError is ApiError.Backend)
    }

    @Test
    fun `account deleted photo failure returns account deletion result`() = runBlocking {
        api.photosResponse = backendErrorResponse(410, "ACCOUNT_DELETED", "deleted")

        val result = coordinator.enter(
            session = inactiveProfileSession(),
            onPending = {},
        )

        assertEquals(ProfileEntryResult.AccountDeletionPendingFromBackend, result)
    }

    private fun inactiveProfileSession() = TestDomain.session().copy(
        profileSnapshot = ProfileSnapshot.Found(TestDtos.profile(status = "DRAFT").toDomain()),
    )

    private fun getProfilePhotos(api: FakeRealsApi): GetProfilePhotosUseCase {
        val repository = ProfileRepository(null, api, FakeAuthTokenProvider(), testApiExecutor())
        return GetProfilePhotosUseCase(repository)
    }
}
