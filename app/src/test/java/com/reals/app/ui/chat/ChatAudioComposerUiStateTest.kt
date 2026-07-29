package com.reals.app.ui.chat

import com.reals.app.testutil.TestDtos
import com.reals.app.data.mapper.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAudioComposerUiStateTest {
    @Test
    fun `policy absent hides recording without affecting message rendering`() {
        val state = chatAudioComposerUiState(
            chat = TestDtos.chat(audioPolicy = null).toDomain(),
            canSendMessages = true,
            sendingMessage = false,
            audioUploading = false,
            recordingActive = false,
            loadingChatAction = false,
        )

        assertFalse(state.visible)
        assertFalse(state.startEnabled)
    }

    @Test
    fun `enabled policy allows recording when ordinary gates allow it`() {
        val state = chatAudioComposerUiState(
            chat = TestDtos.chat(audioPolicy = TestDtos.audioPolicy(enabled = true)).toDomain(),
            canSendMessages = true,
            sendingMessage = false,
            audioUploading = false,
            recordingActive = false,
            loadingChatAction = false,
        )

        assertTrue(state.visible)
        assertTrue(state.startEnabled)
    }

    @Test
    fun `feature disabled hides recording control`() {
        val state = chatAudioComposerUiState(
            chat = TestDtos.chat(
                audioPolicy = TestDtos.audioPolicy(enabled = false, unavailableReason = "FEATURE_DISABLED"),
            ).toDomain(),
            canSendMessages = true,
            sendingMessage = false,
            audioUploading = false,
            recordingActive = false,
            loadingChatAction = false,
        )

        assertFalse(state.visible)
        assertFalse(state.startEnabled)
    }

    @Test
    fun `limit reached remains visible but disabled with copy`() {
        val state = chatAudioComposerUiState(
            chat = TestDtos.chat(
                audioPolicy = TestDtos.audioPolicy(enabled = false, unavailableReason = "LIMIT_REACHED", remainingMessages = 0),
            ).toDomain(),
            canSendMessages = true,
            sendingMessage = false,
            audioUploading = false,
            recordingActive = false,
            loadingChatAction = false,
        )

        assertTrue(state.visible)
        assertFalse(state.startEnabled)
        assertEquals("Ya enviaste el audio disponible en este chat.", state.disabledCopy)
    }

    @Test
    fun `audio duration formats positive subsecond as one second`() {
        assertEquals("0:00", formatAudioDuration(0))
        assertEquals("0:01", formatAudioDuration(838))
        assertEquals("0:07", formatAudioDuration(7_059))
    }
}
