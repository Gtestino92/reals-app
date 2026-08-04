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

    @Test
    fun `canonical countries put Argentina first without duplicating it`() {
        val countries = listOf(
            CountryReference("UY", "Uruguay"),
            CountryReference("AR", "Argentina"),
            CountryReference("MX", "México"),
        )

        val canonicalCountries = countryMenuSections(countries).canonicalCountries

        assertEquals(listOf("AR", "UY", "MX"), canonicalCountries.map { it.code })
    }

    @Test
    fun `search normalization is case accent and whitespace insensitive`() {
        assertEquals("mexico", normalizeCountrySearchText(" México "))
        assertEquals("mexico", normalizeCountrySearchText("MÉXICO"))
        assertEquals("espana", normalizeCountrySearchText("España"))
        assertEquals("arg", normalizeCountrySearchText(" ARG "))
        assertEquals("", normalizeCountrySearchText("   "))
    }

    @Test
    fun `search entries precompute normalized display names and codes`() {
        val entries = buildCountrySearchEntries(
            listOf(
                CountryReference("MX", "México"),
                CountryReference("AR", "Argentina"),
            ),
        )

        assertEquals(listOf("AR", "MX"), entries.map { it.country.code })
        assertEquals("argentina", entries.first().normalizedDisplayName)
        assertEquals("ar", entries.first().normalizedCode)
        assertEquals("mexico", entries.last().normalizedDisplayName)
        assertEquals("mx", entries.last().normalizedCode)
    }

    @Test
    fun `filter matches display name prefix substring and ISO code`() {
        val countries = listOf(
            CountryReference("UY", "Uruguay"),
            CountryReference("AR", "Argentina"),
            CountryReference("MX", "México"),
            CountryReference("ES", "España"),
        )
        val entries = buildCountrySearchEntries(countries)

        assertEquals(listOf("AR"), filterCountrySearchEntries(entries, normalizeCountrySearchText("arg")).map { it.code })
        assertEquals(listOf("ES"), filterCountrySearchEntries(entries, normalizeCountrySearchText("pana")).map { it.code })
        assertEquals(listOf("UY"), filterCountrySearchEntries(entries, normalizeCountrySearchText("uy")).map { it.code })
    }

    @Test
    fun `filter is case and accent insensitive`() {
        val entries = buildCountrySearchEntries(
            listOf(
                CountryReference("MX", "México"),
                CountryReference("ES", "España"),
            ),
        )

        assertEquals(listOf("MX"), filterCountrySearchEntries(entries, normalizeCountrySearchText("mexico")).map { it.code })
        assertEquals(listOf("MX"), filterCountrySearchEntries(entries, normalizeCountrySearchText("MÉXICO")).map { it.code })
        assertEquals(listOf("ES"), filterCountrySearchEntries(entries, normalizeCountrySearchText("espana")).map { it.code })
        assertEquals(listOf("ES"), filterCountrySearchEntries(entries, normalizeCountrySearchText("españa")).map { it.code })
    }

    @Test
    fun `filter preserves canonical ordering and does not inject Argentina when unmatched`() {
        val countries = listOf(
            CountryReference("UY", "Uruguay"),
            CountryReference("AR", "Argentina"),
            CountryReference("MX", "México"),
            CountryReference("ES", "España"),
        )
        val originalCodes = countries.map { it.code }
        val entries = buildCountrySearchEntries(countries)

        assertEquals(listOf("AR", "UY", "ES"), filterCountrySearchEntries(entries, normalizeCountrySearchText("a")).map { it.code })
        assertEquals(listOf("MX"), filterCountrySearchEntries(entries, normalizeCountrySearchText("mex")).map { it.code })
        assertTrue(filterCountrySearchEntries(entries, normalizeCountrySearchText("zzz")).isEmpty())
        assertEquals(originalCodes, countries.map { it.code })
    }

    @Test
    fun `selected label handles loading blank known and unknown codes`() {
        val countriesByCode = mapOf(
            "AR" to CountryReference("AR", "Argentina"),
            "MX" to CountryReference("MX", "México"),
        )

        assertEquals("Cargando países...", selectedCountryLabel(true, "AR", countriesByCode))
        assertEquals("Seleccionar país", selectedCountryLabel(false, "", countriesByCode))
        assertEquals("México", selectedCountryLabel(false, "MX", countriesByCode))
        assertEquals("ZZ", selectedCountryLabel(false, "ZZ", countriesByCode))
    }
}
