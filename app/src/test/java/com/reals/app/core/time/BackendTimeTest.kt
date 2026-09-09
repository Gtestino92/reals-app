package com.reals.app.core.time

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendTimeTest {
    @Test
    fun `remaining millis returns future difference`() {
        val now = millis("2026-06-18T21:00:00Z")

        assertEquals(
            60_000L,
            remainingMillisUntil("2026-06-18T21:01:00Z", now),
        )
    }

    @Test
    fun `expired returns true for past timestamps`() {
        val now = millis("2026-06-18T21:00:00Z")

        assertTrue(isExpired("2026-06-18T20:59:59Z", now))
        assertFalse(isExpired("2026-06-18T21:00:01Z", now))
    }

    @Test
    fun `invalid and null timestamps are safe`() {
        assertNull(backendInstantOrNull(null))
        assertNull(backendInstantOrNull("not-a-date"))
        assertNull(remainingMillisUntil("not-a-date", 0L))
        assertFalse(isExpired(null, 0L))
    }

    @Test
    fun `warning window identifies near future only`() {
        val now = millis("2026-06-18T21:00:00Z")

        assertTrue(isWithinWarningWindow("2026-06-18T21:05:00Z", now, 10 * 60_000L))
        assertFalse(isWithinWarningWindow("2026-06-18T21:15:00Z", now, 10 * 60_000L))
        assertFalse(isWithinWarningWindow("2026-06-18T20:59:00Z", now, 10 * 60_000L))
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
