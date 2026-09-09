package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.PhotoPlacementInput
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.failureError
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {
    private val api = FakeRealsApi()
    private val repository = ProfileRepository(null, api, FakeAuthTokenProvider(), testApiExecutor())

    @Test
    fun `get profile maps found profile`() = runBlocking {
        val snapshot = repository.getMyProfileSnapshot().successValue()

        assertTrue(snapshot is ProfileSnapshot.Found)
        assertEquals("profile-1", (snapshot as ProfileSnapshot.Found).profile.id)
    }

    @Test
    fun `get profile maps 404 to missing snapshot`() = runBlocking {
        api.profileResponse = backendErrorResponse(404, "PROFILE_NOT_FOUND", "missing")

        val snapshot = repository.getMyProfileSnapshot().successValue()

        assertEquals(ProfileSnapshot.Missing, snapshot)
    }

    @Test
    fun `get profile propagates non 404 errors`() = runBlocking {
        api.profileResponse = backendErrorResponse(500, "SERVER_ERROR", "boom")

        val error = repository.getMyProfileSnapshot().failureError()

        assertTrue(error is ApiError.Backend)
        assertEquals(500, (error as ApiError.Backend).statusCode)
    }

    @Test
    fun `create and update profile send DTOs`() = runBlocking {
        repository.createMyProfile(
            CreateProfileInput(
                displayName = "Alex",
                birthDate = "1998-01-01",
                gender = "FEMALE",
                lookingForGenders = setOf("MALE"),
                intention = "SERIOUS",
                city = "Buenos Aires",
                countryCode = "AR",
                bio = "Bio",
                preferredMinAge = 25,
                preferredMaxAge = 35,
                maxDistanceKm = 10,
            )
        ).successValue()

        assertEquals("Alex", api.createProfileBody?.displayName)
        assertEquals("AR", api.createProfileBody?.countryCode)
        assertEquals(10, api.createProfileBody?.maxDistanceKm)

        repository.updateMyProfile(
            UpdateProfileInput(
                displayName = "Alex 2",
                bio = null,
                city = "CABA",
                countryCode = "AR",
            )
        ).successValue()

        assertEquals("Alex 2", api.updateProfileBody?.displayName)
        assertEquals("CABA", api.updateProfileBody?.city)
        assertEquals("AR", api.updateProfileBody?.countryCode)
    }

    @Test
    fun `get countries uses authenticated reference endpoint and maps domain list`() = runBlocking {
        val countries = repository.getCountries().successValue()

        assertEquals(listOf("getCountries"), api.calls)
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(listOf("AR", "BR"), countries.map { it.code })
        assertEquals(listOf("Argentina", "Brasil"), countries.map { it.displayName })
    }

    @Test
    fun `update match filters sends DTO`() = runBlocking {
        repository.updateMyMatchFilters(
            UpdateMatchFiltersInput(
                intention = "CASUAL",
                lookingForGenders = setOf("FEMALE"),
                preferredMinAge = 24,
                preferredMaxAge = 36,
                maxDistanceKm = 20,
            )
        ).successValue()

        assertEquals("CASUAL", api.updateFiltersBody?.intention)
        assertEquals(setOf("FEMALE"), api.updateFiltersBody?.lookingForGenders)
        assertEquals(24, api.updateFiltersBody?.preferredMinAge)
        assertEquals(36, api.updateFiltersBody?.preferredMaxAge)
        assertEquals(20, api.updateFiltersBody?.maxDistanceKm)
    }

    @Test
    fun `photo read reorder delete and activation operations call API`() = runBlocking {
        repository.getMyProfilePhotos().successValue()
        repository.reorderMyProfilePhotos(
            listOf(
                PhotoPlacementInput(photoId = "photo-1", position = 4),
                PhotoPlacementInput(photoId = "photo-2", position = 1),
            )
        ).successValue()
        repository.deleteMyProfilePhoto("photo-1").successValue()
        repository.activateMyProfile().successValue()

        assertEquals("photo-1", api.reorderPhotosBody?.placements?.firstOrNull()?.photoId)
        assertEquals(4, api.reorderPhotosBody?.placements?.firstOrNull()?.position)
        assertEquals(listOf(
            "getMyProfilePhotos",
            "reorderMyProfilePhotos",
            "deleteMyProfilePhoto",
            "activateMyProfile",
        ), api.calls)
    }

}
