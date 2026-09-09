package com.reals.app.ui.matchmaking

import com.reals.app.domain.model.HomeActiveInteractionsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSummaryTextTest {
    @Test
    fun `zero visible counts hides active experiences line while notice remains`() {
        val summaryText = activeExperiencesSummaryText(
            summary(activeInitialCount = 0, activeConnectionCount = 0),
        )
        val noticeText = passiveNoticeText(HomePassiveNoticeItem.SchedulingPreparing)

        assertNull(summaryText)
        assertEquals(
            "Estamos preparando uno de tus próximos pasos.",
            noticeText,
        )
        assertTrue(noticeText?.contains("0 iniciales") != true)
        assertTrue(noticeText?.contains("0 conexiones") != true)
    }

    @Test
    fun `only initial count omits zero connections`() {
        val text = activeExperiencesSummaryText(
            summary(activeInitialCount = 1, activeConnectionCount = 0),
        )

        assertEquals("Experiencias activas: 1 inicial.", text)
        assertTrue(text?.contains("0 conexiones") != true)
    }

    @Test
    fun `only connection count omits zero initials`() {
        val text = activeExperiencesSummaryText(
            summary(activeInitialCount = 0, activeConnectionCount = 1),
        )

        assertEquals("Experiencias activas: 1 conexión.", text)
        assertTrue(text?.contains("0 iniciales") != true)
    }

    @Test
    fun `visible counts and passive notice both render text`() {
        assertEquals(
            "Experiencias activas: 1 inicial, 1 conexión.",
            activeExperiencesSummaryText(summary(activeInitialCount = 1, activeConnectionCount = 1)),
        )
        assertEquals(
            "Estamos preparando uno de tus próximos pasos.",
            passiveNoticeText(HomePassiveNoticeItem.SchedulingPreparing),
        )
    }

    @Test
    fun `scheduling preparing notice is generic and count-free`() {
        assertEquals(
            "Estamos preparando uno de tus próximos pasos.",
            passiveNoticeText(HomePassiveNoticeItem.SchedulingPreparing),
        )
    }

    private fun summary(
        activeInitialCount: Int,
        activeConnectionCount: Int,
    ): HomeActiveInteractionsSummary = HomeActiveInteractionsSummary(
        activeInitialCount = activeInitialCount,
        activeConnectionCount = activeConnectionCount,
        hasPendingSchedulingConnection = false,
        actionableConnectionCount = 0,
    )
}
