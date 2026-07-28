package com.reals.app.ui.chat

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class ChatAudioPlaybackPhase {
    Idle,
    Preparing,
    Playing,
    Paused,
    Failed,
}

internal data class ChatAudioPlaybackUiState(
    val key: String? = null,
    val phase: ChatAudioPlaybackPhase = ChatAudioPlaybackPhase.Idle,
    val positionMillis: Int = 0,
    val durationMillis: Long = 0L,
    val error: String? = null,
)

internal class ChatAudioPlaybackController {
    var state by mutableStateOf(ChatAudioPlaybackUiState())
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var retainedPositionMillis: Int = 0

    fun playLocal(
        key: String,
        filePath: String,
        durationMillis: Long,
        scope: CoroutineScope,
    ) {
        play(
            key = key,
            source = filePath,
            durationMillis = durationMillis,
            scope = scope,
            refreshSource = null,
        )
    }

    fun playRemote(
        messageId: String,
        url: String,
        durationMillis: Long,
        scope: CoroutineScope,
        refreshUrl: suspend () -> String?,
    ) {
        play(
            key = messageId,
            source = url,
            durationMillis = durationMillis,
            scope = scope,
            refreshSource = refreshUrl,
        )
    }

    fun pause() {
        val player = mediaPlayer ?: return
        runCatching {
            retainedPositionMillis = player.currentPosition
            player.pause()
            state = state.copy(
                phase = ChatAudioPlaybackPhase.Paused,
                positionMillis = retainedPositionMillis,
                error = null,
            )
        }.onFailure {
            failCurrent()
        }
    }

    fun release() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        retainedPositionMillis = 0
        state = ChatAudioPlaybackUiState()
    }

    fun tick() {
        val player = mediaPlayer ?: return
        if (state.phase != ChatAudioPlaybackPhase.Playing) return
        runCatching {
            state = state.copy(positionMillis = player.currentPosition)
        }
    }

    private fun play(
        key: String,
        source: String,
        durationMillis: Long,
        scope: CoroutineScope,
        refreshSource: (suspend () -> String?)?,
    ) {
        if (state.key == key && state.phase == ChatAudioPlaybackPhase.Paused) {
            resume()
            return
        }
        startPreparing(
            key = key,
            source = source,
            durationMillis = durationMillis,
            scope = scope,
            refreshSource = refreshSource,
            retriedAfterRefresh = false,
        )
    }

    private fun resume() {
        val player = mediaPlayer ?: return
        runCatching {
            player.seekTo(retainedPositionMillis)
            player.start()
            state = state.copy(phase = ChatAudioPlaybackPhase.Playing, error = null)
        }.onFailure {
            failCurrent()
        }
    }

    private fun startPreparing(
        key: String,
        source: String,
        durationMillis: Long,
        scope: CoroutineScope,
        refreshSource: (suspend () -> String?)?,
        retriedAfterRefresh: Boolean,
    ) {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        retainedPositionMillis = 0
        state = ChatAudioPlaybackUiState(
            key = key,
            phase = ChatAudioPlaybackPhase.Preparing,
            durationMillis = durationMillis,
        )
        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(source)
            player.setOnPreparedListener {
                runCatching {
                    it.start()
                    state = state.copy(phase = ChatAudioPlaybackPhase.Playing, error = null)
                }.onFailure {
                    handlePlaybackFailure(key, source, durationMillis, scope, refreshSource, retriedAfterRefresh)
                }
            }
            player.setOnCompletionListener {
                retainedPositionMillis = 0
                state = state.copy(
                    phase = ChatAudioPlaybackPhase.Idle,
                    positionMillis = 0,
                    error = null,
                )
            }
            player.setOnErrorListener { _, _, _ ->
                handlePlaybackFailure(key, source, durationMillis, scope, refreshSource, retriedAfterRefresh)
                true
            }
            player.prepareAsync()
        }.onFailure {
            handlePlaybackFailure(key, source, durationMillis, scope, refreshSource, retriedAfterRefresh)
        }
    }

    private fun handlePlaybackFailure(
        key: String,
        source: String,
        durationMillis: Long,
        scope: CoroutineScope,
        refreshSource: (suspend () -> String?)?,
        retriedAfterRefresh: Boolean,
    ) {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        retainedPositionMillis = 0
        if (refreshSource == null || retriedAfterRefresh) {
            failCurrent(key, durationMillis)
            return
        }
        scope.launch {
            val refreshed = runCatching { refreshSource() }.getOrNull()
            if (!refreshed.isNullOrBlank() && refreshed != source) {
                startPreparing(
                    key = key,
                    source = refreshed,
                    durationMillis = durationMillis,
                    scope = scope,
                    refreshSource = refreshSource,
                    retriedAfterRefresh = true,
                )
            } else {
                failCurrent(key, durationMillis)
            }
        }
    }

    private fun failCurrent(
        key: String? = state.key,
        durationMillis: Long = state.durationMillis,
    ) {
        state = ChatAudioPlaybackUiState(
            key = key,
            phase = ChatAudioPlaybackPhase.Failed,
            durationMillis = durationMillis,
            error = "No pudimos reproducir este audio.",
        )
    }
}
