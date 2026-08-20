package com.reals.app.ui.scheduling

import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.SchedulingNegotiation
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `scheduling remaining progress uses negotiation creation as start and scheduling expiry as end`() {
        val start = "2026-06-18T21:00:00Z"
        val end = "2026-06-20T21:00:00Z"

        assertEquals(1.0, schedulingDeadlineRemainingFraction(start, end, millis(start))!!, 0.0)
        assertEquals(0.5, schedulingDeadlineRemainingFraction(start, end, millis("2026-06-19T21:00:00Z"))!!, 0.0)
        assertEquals(0.0, schedulingDeadlineRemainingFraction(start, end, millis(end))!!, 0.0)
    }

    @Test
    fun `scheduling remaining progress clamps and handles invalid duration safely`() {
        val start = "2026-06-18T21:00:00Z"
        val end = "2026-06-20T21:00:00Z"

        assertEquals(1.0, schedulingDeadlineRemainingFraction(start, end, millis("2026-06-18T20:00:00Z"))!!, 0.0)
        assertEquals(0.0, schedulingDeadlineRemainingFraction(start, end, millis("2026-06-21T21:00:00Z"))!!, 0.0)
        assertNull(schedulingDeadlineRemainingFraction(start, start, millis(start)))
        assertNull(schedulingDeadlineRemainingFraction(end, start, millis(start)))
        assertNull(schedulingDeadlineRemainingFraction(null, end, millis(start)))
        assertNull(schedulingDeadlineRemainingFraction(start, null, millis(start)))
        assertNull(schedulingDeadlineRemainingFraction("not-a-date", end, millis(start)))
    }

    @Test
    fun `scheduling progress is visible only while negotiation is pending`() {
        assertTrue(shouldShowSchedulingDeadlineProgress(negotiation(NegotiationStatus.Pending)))
        assertFalse(shouldShowSchedulingDeadlineProgress(negotiation(NegotiationStatus.Confirmed)))
        assertFalse(shouldShowSchedulingDeadlineProgress(negotiation(NegotiationStatus.Failed)))
        assertFalse(shouldShowSchedulingDeadlineProgress(negotiation(NegotiationStatus.Unknown("PAUSED"))))
        assertFalse(shouldShowSchedulingDeadlineProgress(null))
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()

    private fun negotiation(status: NegotiationStatus) = SchedulingNegotiation(
        id = "negotiation-1",
        connectionId = "connection-1",
        roundNumber = 1,
        status = status,
        confirmedDateTime = null,
        chatId = null,
        schedulingExpiresAt = "2026-06-20T21:00:00Z",
        createdAt = "2026-06-18T21:00:00Z",
        updatedAt = "2026-06-18T21:00:00Z",
    )
}
