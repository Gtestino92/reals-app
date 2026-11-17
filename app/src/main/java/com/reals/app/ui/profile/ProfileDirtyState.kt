package com.reals.app.ui.profile

import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.core.security.TextSafety

internal fun profileDetailsHaveChanges(profile: Profile, input: UpdateProfileInput): Boolean =
    normalizedDetails(input) != normalizedDetails(UpdateProfileInput(profile.displayName, profile.bio, profile.city, profile.countryCode))

private fun normalizedDetails(input: UpdateProfileInput) = input.copy(
    displayName = TextSafety.normalizeSingleLine(input.displayName, maxLength = 100),
    bio = TextSafety.normalizeMultiline(input.bio.orEmpty(), maxLength = 1_000).ifBlank { null },
    city = TextSafety.normalizeSingleLine(input.city, maxLength = 100),
    countryCode = input.countryCode.trim(),
)

internal fun profileFiltersHaveChanges(profile: Profile, input: UpdateMatchFiltersInput): Boolean =
    input.intention != profile.intention || input.lookingForGenders != profile.lookingForGenders ||
        input.preferredMinAge != profile.preferredMinAge || input.preferredMaxAge != profile.preferredMaxAge ||
        input.maxDistanceKm != profile.maxDistanceKm
