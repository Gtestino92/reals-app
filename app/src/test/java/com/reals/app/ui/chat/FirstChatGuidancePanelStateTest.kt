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
    }

    @Test
    fun `button hidden and waiting copy visible after own request`() {
        val state = panelState(myNextRequested = true)

        assertEquals(false, state.showButton)
        assertEquals(false, state.buttonEnabled)
        assertEquals(true, state.showWaitingCopy)
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
        myNextRequested: Boolean = false,
        completed: Boolean = false,
    ): FirstChatGuidancePanelState =
        firstChatGuidancePanelState(
            TestDtos.firstChatGuidance(
                questionId = questionId,
                questionText = questionText,
                questionOrdinal = questionOrdinal,
                canRequestNext = canRequestNext,
                myNextRequested = myNextRequested,
                completed = completed,
            ).toDomain()
        ) ?: error("Expected guidance panel state")
}
