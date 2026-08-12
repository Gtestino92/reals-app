package com.reals.app.ui.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAccountSectionSupportTest {
    @Test
    fun `support visibility follows build capability`() {
        assertTrue(shouldShowSupportReals(showCafecitoSupport = true))
        assertFalse(shouldShowSupportReals(showCafecitoSupport = false))
    }

    @Test
    fun `support copy communicates voluntary support without in app benefits`() {
        assertEquals("Apoyar Reals", SupportRealsTitle)
        assertTrue(SupportRealsBody.contains("aporte voluntario"))
        assertTrue(SupportRealsBody.contains("sostener el proyecto"))
        assertTrue(SupportRealsBody.contains("No te da beneficios dentro de la app"))
        assertEquals("Apoyar en Cafecito", SupportRealsCta)
    }

    @Test
    fun `account subtitle includes support-compatible wording`() {
        assertEquals("Sesión y otras opciones.", AccountSectionSubtitle)
    }
}
