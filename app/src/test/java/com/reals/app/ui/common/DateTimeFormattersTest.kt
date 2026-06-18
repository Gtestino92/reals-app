package com.reals.app.ui.common

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeFormattersTest {
    @Test
    fun formatBackendTimeConvertsToDeviceTimeZone() = withDefaultTimeZone("America/Argentina/Buenos_Aires") {
        assertEquals("09:30", formatBackendTime("2026-06-18T12:30:00Z"))
    }

    @Test
    fun formatBackendDateTimeConvertsToDeviceTimeZone() = withDefaultTimeZone("America/Argentina/Buenos_Aires") {
        assertEquals("18/06/2026 09:30", formatBackendDateTime("2026-06-18T12:30:00Z"))
    }

    @Test
    fun formatBackendDateUsesConvertedLocalDate() = withDefaultTimeZone("America/Argentina/Buenos_Aires") {
        assertEquals("17/06/2026", formatBackendDate("2026-06-18T01:30:00Z"))
    }

    private fun withDefaultTimeZone(id: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
