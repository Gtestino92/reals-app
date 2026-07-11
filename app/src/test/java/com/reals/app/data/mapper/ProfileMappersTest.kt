package com.reals.app.data.mapper

import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.testJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMappersTest {
    @Test
    fun `ProfileResponseDto maps profile response`() {
        val profile = TestDtos.profile(status = "ACTIVE").toDomain()

        assertEquals("profile-1", profile.id)
        assertEquals("user-1", profile.userId)
        assertEquals("Alex", profile.displayName)
        assertEquals(28, profile.age)
        assertEquals(true, profile.authenticityVerified)
        assertEquals("VERIFIED", profile.authenticityVerificationStatus)
        assertEquals("AR", profile.countryCode)
        assertEquals(ProfileStatus.Active, profile.status)
        assertEquals(2, profile.photoCount)
    }

    @Test
    fun `CountryReferenceResponseDto maps code and display name`() {
        val country = TestDtos.country(code = "AR", displayName = "Argentina").toDomain()

        assertEquals("AR", country.code)
        assertEquals("Argentina", country.displayName)
    }

    @Test
    fun `CreateProfileInput maps country code and does not serialize legacy country`() {
        val dto = CreateProfileInput(
            displayName = "Alex",
            birthDate = "1998-01-01",
            gender = "FEMALE",
            lookingForGenders = setOf("MALE"),
            intention = "DATE",
            city = "Buenos Aires",
            countryCode = "AR",
            bio = "Bio",
            preferredMinAge = 25,
            preferredMaxAge = 35,
            maxDistanceKm = 10,
        ).toDto()
        val json = testJson.encodeToJsonElement(dto).jsonObject

        assertEquals("AR", dto.countryCode)
        assertHasCountryCodeOnly(json)
    }

    @Test
    fun `ProfileResponseDto preserves unknown status`() {
        val profile = TestDtos.profile(status = "SUSPENDED").toDomain()

        assertTrue(profile.status is ProfileStatus.Unknown)
        assertEquals("SUSPENDED", profile.status.rawValue)
    }

    @Test
    fun `PhotoResponseDto maps photo metadata and validation status`() {
        val photo = TestDtos.photo(
            id = "photo-external",
            position = 3,
            validationStatus = "PENDING_VALIDATION",
        ).toDomain()

        assertEquals("photo-external", photo.id)
        assertEquals("https://example.com/photo-external.jpg", photo.url)
        assertEquals(3, photo.position)
        assertEquals(true, photo.isPersonPhoto)
        assertEquals(false, photo.isFullBody)
        assertEquals("PENDING_VALIDATION", photo.validationStatus)
        assertEquals("APPROVED", photo.moderationStatus)
    }

    @Test
    fun `UpdateProfileInput maps editable fields`() {
        val dto = UpdateProfileInput(
            displayName = "Alex",
            bio = "Bio",
            city = "CABA",
            countryCode = "AR",
        ).toDto()

        assertEquals("Alex", dto.displayName)
        assertEquals("Bio", dto.bio)
        assertEquals("CABA", dto.city)
        assertEquals("AR", dto.countryCode)
        assertHasCountryCodeOnly(testJson.encodeToJsonElement(dto).jsonObject)
    }

    @Test
    fun `UpdateMatchFiltersInput maps filters`() {
        val dto = UpdateMatchFiltersInput(
            intention = "DATE",
            lookingForGenders = setOf("FEMALE", "NON_BINARY"),
            preferredMinAge = 25,
            preferredMaxAge = 35,
            maxDistanceKm = 12,
        ).toDto()

        assertEquals("DATE", dto.intention)
        assertEquals(setOf("FEMALE", "NON_BINARY"), dto.lookingForGenders)
        assertEquals(25, dto.preferredMinAge)
        assertEquals(35, dto.preferredMaxAge)
        assertEquals(12, dto.maxDistanceKm)
    }

    private fun assertHasCountryCodeOnly(json: JsonObject) {
        assertTrue(json.containsKey("countryCode"))
        assertFalse(json.containsKey("country"))
    }
}
