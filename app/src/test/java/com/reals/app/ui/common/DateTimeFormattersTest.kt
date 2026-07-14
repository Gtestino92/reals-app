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

    @Test
    fun `contextual date time formats same local date as today`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "Hoy, 20:30",
            formatBackendContextualDateTime(
                "2026-07-15T20:30:00-03:00",
                nowMillis,
                buenosAires,
                locale,
            ),
        )
    }

    @Test
    fun `contextual date time formats next local date as tomorrow`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "Mañana, 18:00",
            formatBackendContextualDateTime(
                "2026-07-16T18:00:00-03:00",
                nowMillis,
                buenosAires,
                locale,
            ),
        )
    }

    @Test
    fun `contextual date time formats later date in same year`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "Viernes 17 de julio, 21:00",
            formatBackendContextualDateTime(
                "2026-07-17T21:00:00-03:00",
                nowMillis,
                buenosAires,
                locale,
            ),
        )
    }

    @Test
    fun `contextual date time formats another year`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "Domingo 3 de enero de 2027, 21:00",
            formatBackendContextualDateTime(
                "2027-01-03T21:00:00-03:00",
                nowMillis,
                buenosAires,
                locale,
            ),
        )
    }

    @Test
    fun `contextual date time compares local dates after timezone conversion`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T23:30:00Z").toEpochMilli()

        assertEquals(
            "Hoy, 21:30",
            formatBackendContextualDateTime(
                "2026-07-16T00:30:00Z",
                nowMillis,
                buenosAires,
                locale,
            ),
        )
    }

    @Test
    fun `contextual date time returns dash for null or blank`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals("-", formatBackendContextualDateTime(null, nowMillis, buenosAires, locale))
        assertEquals("-", formatBackendContextualDateTime(" ", nowMillis, buenosAires, locale))
    }

    @Test
    fun `contextual date time falls back safely for invalid input`() {
        val nowMillis = java.time.Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals("not-a-date", formatBackendContextualDateTime("not-a-date", nowMillis, buenosAires, locale))
    }
}
