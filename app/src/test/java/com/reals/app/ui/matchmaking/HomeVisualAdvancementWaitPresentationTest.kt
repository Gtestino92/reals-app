package com.reals.app.ui.matchmaking

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HomeVisualAdvancementWaitPresentationTest {
    private val nowMillis = Instant.parse("2026-08-21T12:00:00Z").toEpochMilli()

    @Test
    fun `cap blocker while not queued returns neutral pacing presentation`() {
        val presentation = visualAdvancementWaitPresentation(capMatchmaking(), nowMillis)

        assertNotNull(presentation)
        assertEquals("Tomate tu tiempo", presentation?.title)
        assertEquals("Podrás volver a buscar a alguien nuevo más adelante.", presentation?.body)
    }

    @Test
    fun `cap presentation does not expose quota counts or configured cap`() {
        val text = listOfNotNull(
            visualAdvancementWaitPresentation(capMatchmaking(), nowMillis)?.title,
            visualAdvancementWaitPresentation(capMatchmaking(), nowMillis)?.body,
            visualAdvancementWaitPresentation(capMatchmaking(), nowMillis)?.remainingTimeText,
        ).joinToString(" ")

        assertFalse(text.contains("10/10"))
        assertFalse(text.contains("límite", ignoreCase = true))
        assertFalse(text.contains("Visual Review"))
    }

    @Test
    fun `in queue takes precedence over cap presentation`() {
        assertNull(visualAdvancementWaitPresentation(capMatchmaking(inQueue = true), nowMillis))
    }

    @Test
    fun `can search keeps normal search state`() {
        assertNull(visualAdvancementWaitPresentation(capMatchmaking(canSearch = true), nowMillis))
    }

    @Test
    fun `non cap blockers keep existing blocker behavior`() {
        assertNull(
            visualAdvancementWaitPresentation(
                capMatchmaking(code = "ACTIVE_MATCH_LIMIT_REACHED"),
                nowMillis,
            )
        )
    }

    @Test
    fun `remaining time formats hours and minutes`() {
        assertEquals(
            "Próximo espacio disponible en 3 h 20 min",
            visualAdvancementRemainingTimeText("2026-08-21T15:20:00Z", nowMillis),
        )
    }

    @Test
    fun `remaining time formats hours only`() {
        assertEquals(
            "Próximo espacio disponible en 3 h",
            visualAdvancementRemainingTimeText("2026-08-21T15:00:00Z", nowMillis),
        )
    }

    @Test
    fun `remaining time formats minutes only`() {
        assertEquals(
            "Próximo espacio disponible en 42 min",
            visualAdvancementRemainingTimeText("2026-08-21T12:42:00Z", nowMillis),
        )
    }

    @Test
    fun `remaining time formats under one minute without zero minutes`() {
        assertEquals(
            "Próximo espacio disponible en menos de 1 min",
            visualAdvancementRemainingTimeText("2026-08-21T12:00:30Z", nowMillis),
        )
    }

    @Test
    fun `null blank malformed and past timestamps keep pacing without countdown`() {
        listOf(null, "", " ", "not-a-date", "2026-08-21T11:59:59Z").forEach { value ->
            val presentation = visualAdvancementWaitPresentation(
                capMatchmaking(nextAvailableAt = value),
                nowMillis,
            )

            assertNotNull(presentation)
            assertNull(presentation?.remainingTimeText)
        }
    }

    @Test
    fun `reaching next available does not locally enable search`() {
        val matchmaking = capMatchmaking(nextAvailableAt = "2026-08-21T12:00:00Z")
        val presentation = visualAdvancementWaitPresentation(matchmaking, nowMillis)

        assertFalse(matchmaking.canSearch)
        assertNotNull(presentation)
        assertNull(presentation?.remainingTimeText)
    }

    @Test
    fun `reaching next available requests one reconciliation for same timestamp`() {
        val presentation = visualAdvancementWaitPresentation(
            capMatchmaking(nextAvailableAt = "2026-08-21T12:00:00Z"),
            nowMillis,
        )

        assertEquals(true, shouldRequestVisualAdvancementReconciliation(presentation, nowMillis, null))
        assertEquals(
            false,
            shouldRequestVisualAdvancementReconciliation(
                presentation,
                nowMillis,
                "2026-08-21T12:00:00Z",
            ),
        )
    }

    private fun capMatchmaking(
        inQueue: Boolean = false,
        canSearch: Boolean = false,
        code: String = "VISUAL_ADVANCEMENT_LIMIT_REACHED",
        nextAvailableAt: String? = "2026-08-21T15:20:00Z",
    ): HomeMatchmakingUiState = HomeMatchmakingUiState(
        inQueue = inQueue,
        canSearch = canSearch,
        blockedReason = HomeMatchmakingBlockedReasonUiState(
            code = code,
            message = "Wait",
            nextAvailableAt = nextAvailableAt,
        ),
    )
}
