package com.reals.app.ui.profile

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.CountryReference
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileCountryInputTest {
    @Test
    fun `create profile validation rejects empty country code`() {
        val input = validCreateInput(countryCode = "")

        assertNull(input)
    }

    @Test
    fun `create profile validation reports min age greater than max age specifically`() {
        val validation = validCreateValidation(preferredMinAge = 45, preferredMaxAge = 30)

        assertNull(validation.input)
        assertEquals(setOf(CreateProfileField.AgeRange), validation.errorFields)
        assertEquals("La edad mínima no puede ser mayor que la máxima.", validation.errorMessage)
    }

    @Test
    fun `create profile validation reports excessive distance specifically`() {
        val validation = validCreateValidation(maxDistanceKm = 101)

        assertNull(validation.input)
        assertEquals(setOf(CreateProfileField.Distance), validation.errorFields)
        assertEquals("La distancia debe estar entre 1 y 100 km.", validation.errorMessage)
    }

    @Test
    fun `create profile validation keeps selected canonical country code unchanged`() {
        val input = validCreateInput(countryCode = "AR")

        assertNotNull(input)
        assertEquals("AR", input?.countryCode)
    }

    @Test
    fun `selecting Argentina submits AR code`() {
        val argentina = CountryReference(code = "AR", displayName = "Argentina")
        val input = validCreateInput(countryCode = argentina.code)

        assertEquals("AR", input?.countryCode)
    }

    @Test
    fun `profile editing starts from profile country code and preserves it while countries load`() {
        val profile = TestDtos.profile().toDomain()
        val selectedCountryCode = profile.countryCode

        assertEquals("AR", selectedCountryCode)
        assertEquals("AR", profileCountryDisplayName(profile, emptyList()))
    }

    @Test
    fun `update profile validation sends country code and leaves city free text`() {
        val input = validateUpdateProfileInput(
            displayName = "Alex",
            bio = "Bio",
            city = "Buenos Aires",
            countryCode = "AR",
        )

        assertNotNull(input)
        assertEquals("AR", input?.countryCode)
        assertEquals("Buenos Aires", input?.city)
    }

    @Test
    fun `existing profile display resolves country display name when available`() {
        val profile = TestDtos.profile().toDomain()

        val display = profileCountryDisplayName(
            profile = profile,
            countries = listOf(CountryReference("AR", "Argentina")),
        )

        assertEquals("Argentina", display)
    }

    @Test
    fun `existing profile display falls back to code when unresolved`() {
        val profile = TestDtos.profile().toDomain()

        assertEquals("AR", profileCountryDisplayName(profile, emptyList()))
    }

    private fun validCreateInput(countryCode: String) = validateProfileInput(
        displayName = "Alex",
        birthDate = "1998-01-01",
        gender = "FEMALE",
        lookingForGenders = setOf("MALE"),
        intention = "DATE",
        city = "Buenos Aires",
        countryCode = countryCode,
        bio = "Bio",
        preferredMinAge = "25",
        preferredMaxAge = "35",
        maxDistanceKm = "10",
    )

    private fun validCreateValidation(
        preferredMinAge: Int = 25,
        preferredMaxAge: Int = 35,
        maxDistanceKm: Int = 10,
    ) = validateProfileInputDetailed(
        displayName = "Alex",
        birthDate = "1998-01-01",
        gender = "FEMALE",
        lookingForGenders = setOf("MALE"),
        intention = "DATE",
        city = "Buenos Aires",
        countryCode = "AR",
        bio = "Bio",
        preferredMinAge = preferredMinAge,
        preferredMaxAge = preferredMaxAge,
        maxDistanceKm = maxDistanceKm,
    )
}
