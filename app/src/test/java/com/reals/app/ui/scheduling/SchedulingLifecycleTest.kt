package com.reals.app.ui.scheduling

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `scheduling progress uses negotiation creation as start and scheduling expiry as end`() {
        val start = "2026-06-18T21:00:00Z"
        val end = "2026-06-20T21:00:00Z"

        assertEquals(0.0, schedulingDeadlineProgressFraction(start, end, millis(start))!!, 0.0)
        assertEquals(0.5, schedulingDeadlineProgressFraction(start, end, millis("2026-06-19T21:00:00Z"))!!, 0.0)
        assertEquals(1.0, schedulingDeadlineProgressFraction(start, end, millis(end))!!, 0.0)
    }

    @Test
    fun `scheduling progress clamps and handles invalid duration safely`() {
        val start = "2026-06-18T21:00:00Z"
        val end = "2026-06-20T21:00:00Z"

        assertEquals(0.0, schedulingDeadlineProgressFraction(start, end, millis("2026-06-18T20:00:00Z"))!!, 0.0)
        assertEquals(1.0, schedulingDeadlineProgressFraction(start, end, millis("2026-06-21T21:00:00Z"))!!, 0.0)
        assertNull(schedulingDeadlineProgressFraction(start, start, millis(start)))
        assertNull(schedulingDeadlineProgressFraction(end, start, millis(start)))
        assertNull(schedulingDeadlineProgressFraction(null, end, millis(start)))
        assertNull(schedulingDeadlineProgressFraction(start, null, millis(start)))
        assertNull(schedulingDeadlineProgressFraction("not-a-date", end, millis(start)))
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
