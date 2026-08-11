package com.reals.app.ui.chat

import com.reals.app.testutil.TestDtos
import com.reals.app.data.mapper.toDomain
import com.reals.app.ui.root.ChatAudioUploadUiState
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
    fun `second chat status policy enables recording when chat policy is absent`() {
        val chat = TestDtos.chat(audioPolicy = null).copy(chatType = "SECOND_CHAT").toDomain()
        val lifecycle = com.reals.app.ui.root.SecondChatLifecycleUiState(
            status = TestDtos.secondChatStatus(
                audioPolicy = TestDtos.audioPolicy(enabled = true),
            ).toDomain(),
            statusReceivedAtMillis = 1L,
        )
        val state = chatAudioComposerUiState(
            chat = chat,
            audioPolicy = effectiveChatAudioPolicy(chat, lifecycle),
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

    @Test
    fun `recording elapsed duration floors subsecond as zero`() {
        assertEquals("0:00", formatRecordingElapsedDuration(0))
        assertEquals("0:00", formatRecordingElapsedDuration(838))
        assertEquals("0:07", formatRecordingElapsedDuration(7_059))
    }

    @Test
    fun `audio draft remains visible and deletable but cannot send when messages are disabled`() {
        val state = audioDraftComposerActionState(
            canSendMessages = false,
            uploadState = ChatAudioUploadUiState(),
        )

        assertTrue(state.visible)
        assertTrue(state.playbackAvailable)
        assertTrue(state.deleteAvailable)
        assertFalse(state.sendAvailable)
    }

    @Test
    fun `audio draft can send when messages are enabled and upload state allows it`() {
        val state = audioDraftComposerActionState(
            canSendMessages = true,
            uploadState = ChatAudioUploadUiState(),
        )

        assertTrue(state.visible)
        assertTrue(state.playbackAvailable)
        assertTrue(state.deleteAvailable)
        assertTrue(state.sendAvailable)
    }
}
