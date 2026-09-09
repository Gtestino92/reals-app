package com.reals.app.ui.chat

import com.reals.app.domain.model.ChatExitReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyReportReasonsTest {
    @Test
    fun `safety report options contain exactly supported safety reasons`() {
        assertEquals(
            listOf(
                ChatExitReason.InappropriateBehavior,
                ChatExitReason.Harassment,
                ChatExitReason.ChildSafetyConcern,
                ChatExitReason.Other,
            ),
            safetyReportReasonOptions.map { it.reason },
        )
        assertTrue(safetyReportReasonOptions.any { it.reason == ChatExitReason.ChildSafetyConcern })
        assertFalse(safetyReportReasonOptions.any { it.reason == ChatExitReason.NoLongerInterested })
    }

    @Test
    fun `unknown saved reason falls back to inappropriate behavior`() {
        assertEquals(
            ChatExitReason.InappropriateBehavior,
            safetyReportReasonFromRawValue("UNSUPPORTED"),
        )
    }
}
