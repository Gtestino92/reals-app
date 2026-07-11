package com.reals.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MatchFiltersValidationTest {
    @Test
    fun `match filters validation reports min age greater than max age specifically`() {
        val validation = validMatchFiltersValidation(minAge = 45, maxAge = 30)

        assertNull(validation.input)
        assertEquals(setOf(MatchFiltersField.AgeRange), validation.errorFields)
        assertEquals("La edad mínima no puede ser mayor que la máxima.", validation.errorMessage)
    }

    @Test
    fun `match filters validation reports excessive distance specifically`() {
        val validation = validMatchFiltersValidation(distance = 101)

        assertNull(validation.input)
        assertEquals(setOf(MatchFiltersField.Distance), validation.errorFields)
        assertEquals("La distancia debe estar entre 1 y 100 km.", validation.errorMessage)
    }

    @Test
    fun `match filters validation accepts valid slider values`() {
        val validation = validMatchFiltersValidation(minAge = 25, maxAge = 35, distance = 80)

        assertNotNull(validation.input)
        assertEquals(25, validation.input?.preferredMinAge)
        assertEquals(35, validation.input?.preferredMaxAge)
        assertEquals(80, validation.input?.maxDistanceKm)
    }

    private fun validMatchFiltersValidation(
        minAge: Int = 25,
        maxAge: Int = 35,
        distance: Int = 10,
    ) = validateMatchFiltersInputDetailed(
        intention = "DATE",
        lookingForGenders = setOf("FEMALE"),
        minAge = minAge,
        maxAge = maxAge,
        distance = distance,
    )
}
