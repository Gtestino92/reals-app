package com.reals.app.ui.chat

import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualApprovalLifecycleTest {
    @Test
    fun `visual warning appears only inside configured window`() {
        val now = millis("2026-06-18T21:00:00Z")

        assertFalse(visualApprovalLifecycleUiState("2026-06-18T21:11:00Z", now).showWarning)
        assertTrue(visualApprovalLifecycleUiState("2026-06-18T21:10:00Z", now).showWarning)
        assertFalse(visualApprovalLifecycleUiState("2026-06-18T20:59:00Z", now).showWarning)
    }

    private fun millis(value: String): Long =
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
