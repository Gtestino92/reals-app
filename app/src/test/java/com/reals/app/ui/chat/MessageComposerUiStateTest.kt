package com.reals.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageComposerUiStateTest {
    @Test
    fun `pending mutual cancellation disables draft editing`() {
        val state = lockedComposerState()

        assertFalse(state.canEditDraft)
    }

    @Test
    fun `pending mutual cancellation disables sending`() {
        val state = lockedComposerState()

        assertFalse(state.canSendMessages)
        assertFalse(state.sendButtonEnabled)
    }

    @Test
    fun `pending mutual cancellation keeps polling level chat available`() {
        assertTrue(chatPollingEnabled(canChat = true))
    }

    @Test
    fun `pending mutual cancellation shows paused conversation copy`() {
        val state = lockedComposerState()

        assertEquals(MUTUAL_EXIT_CONVERSATION_PAUSED_COPY, state.explanatoryCopy)
    }

    @Test
    fun `available chat enables filled draft without paused copy`() {
        val state = messageComposerUiState(
            canChat = true,
            canSendMessages = true,
            sendingMessage = false,
            loadingChatAction = false,
            draft = "hola",
        )

        assertTrue(state.canEditDraft)
        assertTrue(state.sendButtonEnabled)
        assertNull(state.explanatoryCopy)
    }

    @Test
    fun `in-flight text send keeps draft editable without paused copy`() {
        val state = messageComposerUiState(
            canChat = true,
            canSendMessages = true,
            sendingMessage = true,
            loadingChatAction = false,
            draft = "segundo mensaje",
        )

        assertTrue(state.canEditDraft)
        assertFalse(state.sendButtonEnabled)
        assertNull(state.explanatoryCopy)
    }

    @Test
    fun `draft becomes sendable after in-flight text send finishes`() {
        val state = messageComposerUiState(
            canChat = true,
            canSendMessages = true,
            sendingMessage = false,
            loadingChatAction = false,
            draft = "segundo mensaje",
        )

        assertTrue(state.canEditDraft)
        assertTrue(state.sendButtonEnabled)
        assertNull(state.explanatoryCopy)
    }

    @Test
    fun `real composer loading lock still disables editing and sending`() {
        val state = messageComposerUiState(
            canChat = true,
            canSendMessages = true,
            sendingMessage = false,
            loadingChatAction = true,
            draft = "hola",
        )

        assertFalse(state.canEditDraft)
        assertFalse(state.sendButtonEnabled)
        assertNull(state.explanatoryCopy)
    }

    private fun lockedComposerState(): MessageComposerUiState =
        messageComposerUiState(
            canChat = true,
            canSendMessages = false,
            sendingMessage = false,
            loadingChatAction = false,
            draft = "hola",
        )
}
