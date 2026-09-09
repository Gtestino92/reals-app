package com.reals.app.ui.chat

import com.reals.app.domain.model.ChatExitReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSafetyReportFormStateTest {
    @Test
    fun `initial report block choice is false`() {
        val state = ChatSafetyReportFormState.initial()

        assertEquals("", state.details)
        assertEquals(ChatExitReason.InappropriateBehavior.rawValue, state.reasonRawValue)
        assertFalse(state.blockUser)
    }

    @Test
    fun `choosing report block changes submitted state to true`() {
        val state = ChatSafetyReportFormState.initial().copy(blockUser = true)

        assertTrue(state.blockUser)
    }

    @Test
    fun `accepted report submission resets block choice for current or next chat`() {
        val selected = ChatSafetyReportFormState(
            details = "detalle",
            reasonRawValue = ChatExitReason.Harassment.rawValue,
            blockUser = true,
        )

        val reset = selected.resetAfterAcceptedSubmit()
        val nextChatInitial = ChatSafetyReportFormState.initial()

        assertFalse(reset.blockUser)
        assertEquals("", reset.details)
        assertEquals(ChatExitReason.InappropriateBehavior.rawValue, reset.reasonRawValue)
        assertFalse(nextChatInitial.blockUser)
    }
}
