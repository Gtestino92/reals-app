package com.reals.app.ui.common

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisualReviewDeadlineTest {
    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")
    private val locale = Locale.forLanguageTag("es-AR")

    @Test
    fun `home deadline formats today without seconds`() {
        val now = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "vence hoy 22:30",
            formatVisualReviewHomeDeadline(
                visualExpiresAt = "2026-07-15T22:30:00-03:00",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `home deadline formats tomorrow`() {
        val now = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "vence mañana 08:15",
            formatVisualReviewHomeDeadline(
                visualExpiresAt = "2026-07-16T08:15:00-03:00",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `home deadline formats later date in same year`() {
        val now = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "vence el viernes 17 de julio, 23:30",
            formatVisualReviewHomeDeadline(
                visualExpiresAt = "2026-07-17T23:30:00-03:00",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `home deadline formats date in different year`() {
        val now = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "vence el viernes 16 de julio de 2027, 23:30",
            formatVisualReviewHomeDeadline(
                visualExpiresAt = "2027-07-16T23:30:00-03:00",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `home deadline formats expired timestamp`() {
        val now = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "vencida",
            formatVisualReviewHomeDeadline(
                visualExpiresAt = "2026-07-15T08:30:00-03:00",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `home deadline formats remaining minutes under one hour`() {
        val now = Instant.parse("2026-07-16T02:45:00Z").toEpochMilli()

        assertEquals(
            "vence en 25 min",
            formatVisualReviewHomeDeadline(
                visualExpiresAt = "2026-07-16T03:10:00Z",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `home deadline compares local dates across midnight boundary`() {
        val now = Instant.parse("2026-07-16T01:05:00Z").toEpochMilli()

        assertEquals(
            "vence mañana 00:10",
            formatVisualReviewHomeDeadline(
                visualExpiresAt = "2026-07-16T03:10:00Z",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `detail deadline includes exact date and time`() {
        val now = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertEquals(
            "Vence el viernes 17 de julio a las 23:30",
            formatVisualReviewDetailDeadline(
                visualExpiresAt = "2026-07-17T23:30:00-03:00",
                nowMillis = now,
                zoneId = buenosAires,
                locale = locale,
            ),
        )
        assertEquals(
            "Venció el viernes 16 de julio de 2027 a las 23:30",
            formatVisualReviewDetailDeadline(
                visualExpiresAt = "2027-07-16T23:30:00-03:00",
                nowMillis = Instant.parse("2028-07-18T12:00:00Z").toEpochMilli(),
                zoneId = buenosAires,
                locale = locale,
            ),
        )
    }

    @Test
    fun `remaining fraction clamps and handles invalid windows safely`() {
        val start = "2026-07-15T10:00:00Z"
        val end = "2026-07-15T14:00:00Z"

        assertEquals(1.0, visualReviewRemainingFraction(start, end, Instant.parse(start).toEpochMilli())!!, 0.0)
        assertEquals(1.0, visualReviewRemainingFraction(start, end, Instant.parse("2026-07-15T09:00:00Z").toEpochMilli())!!, 0.0)
        assertEquals(0.5, visualReviewRemainingFraction(start, end, Instant.parse("2026-07-15T12:00:00Z").toEpochMilli())!!, 0.0)
        assertEquals(0.0, visualReviewRemainingFraction(start, end, Instant.parse(end).toEpochMilli())!!, 0.0)
        assertEquals(0.0, visualReviewRemainingFraction(start, end, Instant.parse("2026-07-15T15:00:00Z").toEpochMilli())!!, 0.0)
        assertNull(visualReviewRemainingFraction(start, start, Instant.parse(start).toEpochMilli()))
        assertNull(visualReviewRemainingFraction(end, start, Instant.parse(start).toEpochMilli()))
        assertNull(visualReviewRemainingFraction(null, end, Instant.parse(start).toEpochMilli()))
        assertNull(visualReviewRemainingFraction(start, null, Instant.parse(start).toEpochMilli()))
    }

    @Test
    fun `progress urgency uses exact boundaries`() {
        assertEquals(VisualReviewProgressUrgency.Normal, visualReviewProgressUrgency(1.00))
        assertEquals(VisualReviewProgressUrgency.Normal, visualReviewProgressUrgency(0.41))
        assertEquals(VisualReviewProgressUrgency.Warning, visualReviewProgressUrgency(0.40))
        assertEquals(VisualReviewProgressUrgency.Warning, visualReviewProgressUrgency(0.11))
        assertEquals(VisualReviewProgressUrgency.Critical, visualReviewProgressUrgency(0.10))
        assertEquals(VisualReviewProgressUrgency.Critical, visualReviewProgressUrgency(0.00))
    }
}
