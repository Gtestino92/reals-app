package com.reals.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class DateTimeFormattersTest {
    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")
    private val locale = Locale.forLanguageTag("es-AR")

    @Test
    fun `formatBackendTime converts UTC to Buenos Aires time`() {
        assertEquals("18:00", formatBackendTime("2026-06-18T21:00:00Z", buenosAires, locale))
    }

    @Test
    fun `formatBackendDateTime converts UTC to Buenos Aires date time`() {
        val formatted = formatBackendDateTime("2026-06-18T21:00:00Z", buenosAires, locale)

        assertTrue(formatted.contains("18:00"))
    }

    @Test
    fun `formatBackendTime handles offset date time`() {
        assertEquals("18:00", formatBackendTime("2026-06-18T21:00:00+00:00", buenosAires, locale))
    }

    @Test
    fun `formatBackendTime returns dash for null or blank`() {
        assertEquals("-", formatBackendTime(null, buenosAires, locale))
        assertEquals("-", formatBackendTime(" ", buenosAires, locale))
    }

    @Test
    fun `formatBackendTime falls back safely for invalid input`() {
        assertEquals("not-a", formatBackendTime("not-a-date", buenosAires, locale))
    }

    @Test
    fun formatBackendDateUsesConvertedLocalDate() {
        assertEquals("17/06/2026", formatBackendDate("2026-06-18T01:30:00Z", buenosAires, locale))
    }
}
