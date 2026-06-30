package com.reals.app.ui.scheduling

import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingLifecycleTest {
    @Test
    fun `scheduling warning appears only inside configured window and expires after deadline`() {
        val now = millis("2026-06-18T21:00:00Z")

        assertFalse(schedulingLifecycleUiState("2026-06-19T21:01:00Z", now).showWarning)
        assertTrue(schedulingLifecycleUiState("2026-06-19T21:00:00Z", now).showWarning)
        assertTrue(schedulingLifecycleUiState("2026-06-18T20:59:00Z", now).expired)
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
