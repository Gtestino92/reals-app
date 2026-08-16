package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FirstChatGuidancePanelStateTest {
    @Test
    fun `no panel for null guidance`() {
        assertNull(firstChatGuidancePanelState(null))
    }

    @Test
    fun `question text is visible for active guidance`() {
        val state = panelState(questionText = "Pregunta activa")

        assertEquals("Pregunta activa", state.questionText)
        assertEquals(true, state.showButton)
    }

    @Test
    fun `button disabled when cannot request next`() {
        val state = panelState(canRequestNext = false)

        assertEquals(true, state.showButton)
        assertEquals(false, state.buttonEnabled)
    }

    @Test
    fun `button enabled when can request next`() {
        val state = panelState(canRequestNext = true)

        assertEquals(true, state.showButton)
        assertEquals(true, state.buttonEnabled)
        assertEquals("Siguiente", state.buttonLabel)
    }

    @Test
    fun `complete progression shows final action using backend canRequestNext`() {
        val disabled = panelState(
            questionOrdinal = 3,
            progressionAction = "COMPLETE",
            canRequestNext = false,
        )
        val enabled = panelState(
            questionOrdinal = 3,
            progressionAction = "COMPLETE",
            canRequestNext = true,
        )

        assertEquals(true, disabled.showButton)
        assertEquals(false, disabled.buttonEnabled)
        assertEquals("Completar", disabled.buttonLabel)
        assertEquals(false, disabled.closeAvailable)

        assertEquals(true, enabled.buttonEnabled)
        assertEquals(false, enabled.closeAvailable)
    }

    @Test
    fun `button disabled when mutual cancellation pauses first chat advancement`() {
        val state = panelState(
            canRequestNext = true,
            canRequestNextWhileChatOpen = false,
        )

        assertEquals(true, state.showButton)
        assertEquals(false, state.buttonEnabled)
    }

    @Test
    fun `button hidden and waiting copy visible after own request`() {
        val state = panelState(myNextRequested = true)

        assertEquals(false, state.showButton)
        assertEquals(false, state.buttonEnabled)
        assertEquals(true, state.showWaitingCopy)
        assertEquals(
            "Cambiaremos la pregunta cuando ambos quieran seguir.",
            state.waitingCopy,
        )
    }

    @Test
    fun `final question waiting copy describes completion`() {
        val state = panelState(
            questionOrdinal = 3,
            progressionAction = "COMPLETE",
            canRequestNext = true,
            myNextRequested = true,
        )

        assertEquals(false, state.showButton)
        assertEquals(false, state.buttonEnabled)
        assertEquals(true, state.showWaitingCopy)
        assertEquals(true, state.closeAvailable)
        assertEquals(
            "Completaremos esta etapa cuando ambos quieran seguir.",
            state.waitingCopy,
        )
    }

    @Test
    fun `Q3 remains visible after completion`() {
        val state = panelState(
            questionId = "Q003",
            questionText = "Pregunta final",
            questionOrdinal = 3,
            completed = true,
            canRequestNext = false,
            myNextRequested = false,
        )

        assertEquals("Pregunta final", state.questionText)
    }

    @Test
    fun `button and waiting copy are hidden after completion`() {
        val state = panelState(
            questionOrdinal = 3,
            completed = true,
            myNextRequested = false,
            canRequestNext = false,
        )

        assertEquals(false, state.showButton)
        assertEquals(false, state.buttonEnabled)
        assertEquals(false, state.showWaitingCopy)
        assertNull(state.waitingCopy)
        assertEquals(true, state.closeAvailable)
    }

    @Test
    fun `completed null progression action is safe`() {
        val state = panelState(
            progressionAction = null,
            completed = true,
            canRequestNext = false,
        )

        assertEquals(false, state.showButton)
        assertEquals(false, state.buttonEnabled)
    }

    @Test
    fun `ordinal progress text is not exposed by panel state`() {
        val state = panelState(questionOrdinal = 1)

        assertFalse(state.toString().contains("1 de 3"))
    }

    @Test
    fun `second chat default guidance path shows no panel`() {
        assertNull(firstChatGuidancePanelState(null))
    }

    private fun panelState(
        questionId: String = "Q027",
        questionText: String = "Pregunta inicial",
        questionOrdinal: Int = 1,
        canRequestNext: Boolean = true,
        canRequestNextWhileChatOpen: Boolean = true,
        myNextRequested: Boolean = false,
        completed: Boolean = false,
        progressionAction: String? = "NEXT_QUESTION",
    ): FirstChatGuidancePanelState =
        firstChatGuidancePanelState(
            guidance = TestDtos.firstChatGuidance(
                questionId = questionId,
                questionText = questionText,
                questionOrdinal = questionOrdinal,
                canRequestNext = canRequestNext,
                myNextRequested = myNextRequested,
                completed = completed,
                progressionAction = progressionAction,
            ).toDomain(),
            canRequestNextWhileChatOpen = canRequestNextWhileChatOpen,
        ) ?: error("Expected guidance panel state")
}
