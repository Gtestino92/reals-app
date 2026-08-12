package com.reals.app.ui.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSupportSectionTest {
    @Test
    fun `support visibility follows build capability`() {
        assertTrue(shouldShowSupportReals(showCafecitoSupport = true))
        assertFalse(shouldShowSupportReals(showCafecitoSupport = false))
    }

    @Test
    fun `support copy communicates voluntary support without in app benefits`() {
        assertEquals("Apoyar Reals", SupportRealsTitle)
        assertTrue(SupportRealsBody.contains("aporte voluntario"))
        assertTrue(SupportRealsBody.contains("No cambia tu experiencia"))
        assertTrue(SupportRealsBody.contains("ni te da beneficios dentro de la app"))
    }

    @Test
    fun `support cta uses canonical cafecito url`() {
        assertEquals("Apoyar en Cafecito", SupportRealsCta)
        assertEquals("https://cafecito.app/reals-app", CafecitoSupportUrl)
    }
}
