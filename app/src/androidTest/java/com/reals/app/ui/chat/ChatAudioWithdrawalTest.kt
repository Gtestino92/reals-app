package com.reals.app.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.reals.app.domain.model.ChatAudio
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageType
import com.reals.app.ui.root.ChatHistoryOwner
import com.reals.app.ui.root.ChatAudioUploadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatAudioWithdrawalTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun removingAuthorizationReleasesSessionPlayerAndRejectsOldClicks() {
        val remote = ChatMessage(
            id = "partner", senderId = "partner", chatSessionId = "chat", sentAt = "",
            messageType = ChatMessageType.Audio,
            audio = ChatAudio("https://example.test/synthetic", 5000, "audio/mp4", 100),
        )
        val owner = ChatHistoryOwner()
        val own = remote.copy(id = "own", senderId = "me")
        var rows by mutableStateOf(listOf(remote, own))
        val players = mutableListOf<RecordingPlayer>()
        lateinit var session: ChatAudioSessionState
        compose.setContent {
            val available = rows
            session = rememberChatAudioSessionState(
                inputs = ChatAudioSessionInputs(
                    chatId = "chat", historyOwner = owner, canChat = true, canSendMessages = true,
                    audioPolicy = null, sendingMessage = false, messageComposerLoading = false,
                    audioActionLoading = false, textDraft = "", uploadState = ChatAudioUploadUiState(), draft = null,
                ),
                callbacks = ChatAudioSessionExternalCallbacks(
                    resolveRemoteAudio = { id ->
                        available.find { it.id == id && (id == own.id ||
                            owner.access != com.reals.app.domain.model.CounterpartAudioAccess.RESTRICTED) }
                    },
                    onTextDraftChange = {}, onSendText = { false }, onClearTextDraft = {},
                    onClearUploadState = {}, onDraftReady = {}, onDraftReadyAndSend = { false },
                    onDeleteDraft = {}, onSendAudioMessage = { _, _ -> false }, onRefreshAudioUrl = { null },
                ),
                createPlaybackController = { ChatAudioPlaybackController { RecordingPlayer().also(players::add) } },
            )
        }
        for (phase in listOf(ChatAudioPlaybackPhase.Preparing, ChatAudioPlaybackPhase.Playing, ChatAudioPlaybackPhase.Paused)) {
            compose.runOnIdle { rows = listOf(remote, own) }
            compose.runOnIdle {
                session.playRemoteMessage(remote)
                if (phase != ChatAudioPlaybackPhase.Preparing) players.last().prepared(players.last())
                if (phase == ChatAudioPlaybackPhase.Paused) session.pauseAudio()
                assertEquals(phase, session.playbackState.phase)
                rows = listOf(own)
            }
            compose.runOnIdle {
                val count = players.size
                val old = players.last()
                val starts = old.starts
                assertTrue(old.released)
                old.prepared(old)
                assertEquals(starts, old.starts)
                session.playRemoteMessage(remote)
                assertEquals(count, players.size)
                assertEquals(null, session.playbackState.key)
            }
        }
        compose.runOnIdle { rows = listOf(remote, own) }
        compose.runOnIdle {
            session.playRemoteMessage(remote)
            players.last().prepared(players.last())
            session.pauseAudio()
            owner.restrict()
            assertTrue(players.last().released)
            assertEquals(null, session.playbackState.key)
            session.playRemoteMessage(own)
            rows = listOf(own)
        }
        compose.runOnIdle {
            assertEquals(false, players.last().released)
            assertEquals("own", session.playbackState.key)
        }
    }

    private class RecordingPlayer : ChatAudioPlayer {
        lateinit var prepared: (ChatAudioPlayer) -> Unit
        var starts = 0
        var released = false
        override val currentPosition = 0
        override fun configureForSpeech() = Unit
        override fun setDataSource(source: String) = Unit
        override fun setOnPreparedListener(listener: (ChatAudioPlayer) -> Unit) { prepared = listener }
        override fun setOnCompletionListener(listener: (ChatAudioPlayer) -> Unit) = Unit
        override fun setOnErrorListener(listener: (ChatAudioPlayer, Int, Int) -> Boolean) = Unit
        override fun prepareAsync() = Unit
        override fun start() { starts++ }
        override fun pause() = Unit
        override fun seekTo(positionMillis: Int) = Unit
        override fun release() { released = true }
    }
}
