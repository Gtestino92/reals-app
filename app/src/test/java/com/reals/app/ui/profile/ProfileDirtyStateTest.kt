package com.reals.app.ui.profile

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.testutil.TestDtos
import org.junit.Assert.*
import org.junit.Test

class ProfileDirtyStateTest {
    private val profile = TestDtos.profile().toDomain()
    @Test fun `details compare normalized text and empty optional bio`() {
        val original = UpdateProfileInput(profile.displayName, profile.bio, profile.city, profile.countryCode)
        assertFalse(profileDetailsHaveChanges(profile, original.copy(displayName = " ${profile.displayName} ", city = " ${profile.city} ")))
        assertFalse(profileDetailsHaveChanges(profile.copy(bio = null), original.copy(bio = "  ")))
        assertTrue(profileDetailsHaveChanges(profile, original.copy(city = "Rosario")))
        assertTrue(profileDetailsHaveChanges(profile, original.copy(bio = "Otra bio")))
        assertTrue(profileDetailsHaveChanges(profile, original.copy(city = "")))
    }
    @Test fun `filters ignore gender ordering but compare each field`() {
        val original = UpdateMatchFiltersInput(profile.intention, profile.lookingForGenders, profile.preferredMinAge, profile.preferredMaxAge, profile.maxDistanceKm)
        assertFalse(profileFiltersHaveChanges(profile, original.copy(lookingForGenders = profile.lookingForGenders.reversed().toSet())))
        assertTrue(profileFiltersHaveChanges(profile, original.copy(maxDistanceKm = profile.maxDistanceKm + 1)))
        assertTrue(profileFiltersHaveChanges(profile, original.copy(preferredMaxAge = profile.preferredMaxAge + 1)))
        assertTrue(profileFiltersHaveChanges(profile, original.copy(intention = "FRIENDSHIP")))
    }
}
