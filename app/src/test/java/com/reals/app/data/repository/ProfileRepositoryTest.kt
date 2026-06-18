package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
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
                gender = "WOMAN",
                lookingForGender = "MAN",
                intention = "SERIOUS",
                city = "Buenos Aires",
                country = "AR",
                bio = "Bio",
                preferredMinAge = 25,
                preferredMaxAge = 35,
                maxDistanceKm = 10,
            )
        ).successValue()

        assertEquals("Alex", api.createProfileBody?.displayName)
        assertEquals(10, api.createProfileBody?.maxDistanceKm)

        repository.updateMyProfile(
            UpdateProfileInput(
                displayName = "Alex 2",
                bio = null,
                city = "CABA",
                country = "AR",
                intention = "CASUAL",
                lookingForGender = "WOMAN",
            )
        ).successValue()

        assertEquals("Alex 2", api.updateProfileBody?.displayName)
        assertEquals("CABA", api.updateProfileBody?.city)
    }

    @Test
    fun `update match filters sends DTO`() = runBlocking {
        repository.updateMyMatchFilters(
            UpdateMatchFiltersInput(
                preferredMinAge = 24,
                preferredMaxAge = 36,
                maxDistanceKm = 20,
            )
        ).successValue()

        assertEquals(24, api.updateFiltersBody?.preferredMinAge)
        assertEquals(36, api.updateFiltersBody?.preferredMaxAge)
        assertEquals(20, api.updateFiltersBody?.maxDistanceKm)
    }

    @Test
    fun `photo URL operations map and send bodies`() = runBlocking {
        repository.getMyProfilePhotos().successValue()
        repository.addMyProfilePhoto(
            url = "https://example.com/a.jpg",
            position = 2,
            isPersonPhoto = true,
            isFullBody = false,
        ).successValue()
        repository.replaceMyProfilePhoto(
            url = "https://example.com/b.jpg",
            position = 2,
            isPersonPhoto = false,
            isFullBody = true,
        ).successValue()
        repository.deleteMyProfilePhoto("photo-1").successValue()
        repository.activateMyProfile().successValue()

        assertEquals("https://example.com/a.jpg", api.addPhotoBody?.url)
        assertEquals(2, api.addPhotoBody?.position)
        assertEquals("https://example.com/b.jpg", api.replacePhotoBody?.url)
        assertEquals(listOf(
            "getMyProfilePhotos",
            "addMyProfilePhoto",
            "replaceMyProfilePhoto",
            "deleteMyProfilePhoto",
            "activateMyProfile",
        ), api.calls)
    }

}
