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
        val presentation = matchmakingUnavailablePresentation(blockedMatchmaking(), nowMillis)

        assertNotNull(presentation)
        assertEquals(MatchmakingUnavailableKind.VisualAdvancementWait, presentation?.kind)
        assertEquals("Tomate tu tiempo", presentation?.title)
        assertEquals("Podrás volver a buscar a alguien nuevo más adelante.", presentation?.body)
    }

    @Test
    fun `cap presentation does not expose quota counts or configured cap`() {
        val text = listOfNotNull(
            matchmakingUnavailablePresentation(blockedMatchmaking(), nowMillis)?.title,
            matchmakingUnavailablePresentation(blockedMatchmaking(), nowMillis)?.body,
            matchmakingUnavailablePresentation(blockedMatchmaking(), nowMillis)?.supportingText,
        ).joinToString(" ")

        assertFalse(text.contains("10/10"))
        assertFalse(text.contains("límite", ignoreCase = true))
        assertFalse(text.contains("Visual Review"))
    }

    @Test
    fun `in queue takes precedence over cap presentation`() {
        assertNull(matchmakingUnavailablePresentation(blockedMatchmaking(inQueue = true), nowMillis))
    }

    @Test
    fun `can search keeps normal search state`() {
        assertNull(matchmakingUnavailablePresentation(blockedMatchmaking(canSearch = true), nowMillis))
    }

    @Test
    fun `active match limit returns active interactions presentation`() {
        val presentation = matchmakingUnavailablePresentation(
            blockedMatchmaking(code = "ACTIVE_MATCH_LIMIT_REACHED", nextAvailableAt = "2026-08-21T15:20:00Z"),
            nowMillis,
        )

        assertNotNull(presentation)
        assertEquals(MatchmakingUnavailableKind.ActiveInteractions, presentation?.kind)
        assertEquals("Seguí con lo que ya empezó", presentation?.title)
        assertEquals(
            "Antes de buscar a alguien nuevo, avanzá con alguna de tus experiencias actuales.",
            presentation?.body,
        )
        assertNull(presentation?.supportingText)
        assertNull(presentation?.nextAvailableAt)
    }

    @Test
    fun `active connection limit returns same active interactions presentation`() {
        val presentation = matchmakingUnavailablePresentation(
            blockedMatchmaking(code = "ACTIVE_CONNECTION_LIMIT_REACHED", nextAvailableAt = "2026-08-21T15:20:00Z"),
            nowMillis,
        )

        assertNotNull(presentation)
        assertEquals(MatchmakingUnavailableKind.ActiveInteractions, presentation?.kind)
        assertEquals("Seguí con lo que ya empezó", presentation?.title)
        assertEquals(
            "Antes de buscar a alguien nuevo, avanzá con alguna de tus experiencias actuales.",
            presentation?.body,
        )
        assertNull(presentation?.supportingText)
        assertNull(presentation?.nextAvailableAt)
    }

    @Test
    fun `active interactions presentation does not expose limit counts or backend terminology`() {
        val presentation = matchmakingUnavailablePresentation(
            blockedMatchmaking(code = "ACTIVE_MATCH_LIMIT_REACHED", message = "5/5 max active matches"),
            nowMillis,
        )
        val text = listOfNotNull(
            presentation?.title,
            presentation?.body,
            presentation?.supportingText,
            presentation?.nextAvailableAt,
        ).joinToString(" ")

        assertFalse(text.contains("5/5"))
        assertFalse(text.contains("2/2"))
        assertFalse(text.contains("límite", ignoreCase = true))
        assertFalse(text.contains("máximo", ignoreCase = true))
        assertFalse(text.contains("cupo", ignoreCase = true))
        assertFalse(text.contains("match", ignoreCase = true))
        assertFalse(text.contains("connection", ignoreCase = true))
    }

    @Test
    fun `other blockers keep existing blocker behavior`() {
        assertNull(
            matchmakingUnavailablePresentation(
                blockedMatchmaking(code = "ACTIVE_PENALTY"),
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
            val presentation = matchmakingUnavailablePresentation(
                blockedMatchmaking(nextAvailableAt = value),
                nowMillis,
            )

            assertNotNull(presentation)
            assertNull(presentation?.supportingText)
        }
    }

    @Test
    fun `reaching next available does not locally enable search`() {
        val matchmaking = blockedMatchmaking(nextAvailableAt = "2026-08-21T12:00:00Z")
        val presentation = matchmakingUnavailablePresentation(matchmaking, nowMillis)

        assertFalse(matchmaking.canSearch)
        assertNotNull(presentation)
        assertNull(presentation?.supportingText)
    }

    @Test
    fun `reaching next available requests one reconciliation for same timestamp`() {
        val presentation = matchmakingUnavailablePresentation(
            blockedMatchmaking(nextAvailableAt = "2026-08-21T12:00:00Z"),
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

    @Test
    fun `visual advancement compatibility helper only returns visual presentation`() {
        assertNotNull(visualAdvancementWaitPresentation(blockedMatchmaking(), nowMillis))
        assertNull(
            visualAdvancementWaitPresentation(
                blockedMatchmaking(code = "ACTIVE_MATCH_LIMIT_REACHED"),
                nowMillis,
            )
        )
    }

    private fun blockedMatchmaking(
        inQueue: Boolean = false,
        canSearch: Boolean = false,
        code: String = "VISUAL_ADVANCEMENT_LIMIT_REACHED",
        message: String = "Wait",
        nextAvailableAt: String? = "2026-08-21T15:20:00Z",
    ): HomeMatchmakingUiState = HomeMatchmakingUiState(
        inQueue = inQueue,
        canSearch = canSearch,
        blockedReason = HomeMatchmakingBlockedReasonUiState(
            code = code,
            message = message,
            nextAvailableAt = nextAvailableAt,
        ),
    )
}
