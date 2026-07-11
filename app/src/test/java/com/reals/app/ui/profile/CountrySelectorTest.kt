package com.reals.app.ui.profile

import com.reals.app.domain.model.CountryReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountrySelectorTest {
    @Test
    fun `AR is extracted as preferred and removed from remaining countries`() {
        val countries = listOf(
            CountryReference("AF", "Afganistán"),
            CountryReference("AR", "Argentina"),
            CountryReference("BR", "Brasil"),
        )

        val sections = countryMenuSections(countries)

        assertEquals(CountryReference("AR", "Argentina"), sections.preferredCountry)
        assertEquals(listOf("AF", "BR"), sections.remainingCountries.map { it.code })
        assertTrue(sections.showPreferredSeparator)
    }

    @Test
    fun `remaining countries preserve backend order`() {
        val countries = listOf(
            CountryReference("CL", "Chile"),
            CountryReference("AR", "Argentina"),
            CountryReference("BO", "Bolivia"),
            CountryReference("UY", "Uruguay"),
        )

        val sections = countryMenuSections(countries)

        assertEquals(listOf("CL", "BO", "UY"), sections.remainingCountries.map { it.code })
    }

    @Test
    fun `missing AR keeps backend list unchanged and hides separator`() {
        val countries = listOf(
            CountryReference("AF", "Afganistán"),
            CountryReference("AL", "Albania"),
        )

        val sections = countryMenuSections(countries)

        assertNull(sections.preferredCountry)
        assertEquals(countries, sections.remainingCountries)
        assertFalse(sections.showPreferredSeparator)
    }

    @Test
    fun `empty country list is safe`() {
        val sections = countryMenuSections(emptyList())

        assertNull(sections.preferredCountry)
        assertTrue(sections.remainingCountries.isEmpty())
        assertFalse(sections.showPreferredSeparator)
    }

    @Test
    fun `separator only shows when preferred and remaining countries exist`() {
        val onlyArgentina = countryMenuSections(listOf(CountryReference("AR", "Argentina")))
        val argentinaAndMore = countryMenuSections(
            listOf(CountryReference("AR", "Argentina"), CountryReference("BR", "Brasil")),
        )

        assertFalse(onlyArgentina.showPreferredSeparator)
        assertTrue(argentinaAndMore.showPreferredSeparator)
    }
}
