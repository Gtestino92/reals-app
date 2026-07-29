package com.reals.app.ui.chat

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
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

internal interface ChatAudioPlayer {
    val currentPosition: Int
    fun configureForSpeech()
    fun setDataSource(source: String)
    fun setOnPreparedListener(listener: (ChatAudioPlayer) -> Unit)
    fun setOnCompletionListener(listener: (ChatAudioPlayer) -> Unit)
    fun setOnErrorListener(listener: (ChatAudioPlayer, Int, Int) -> Boolean)
    fun prepareAsync()
    fun start()
    fun pause()
    fun seekTo(positionMillis: Int)
    fun release()
}

internal fun interface ChatAudioPlayerFactory {
    fun create(): ChatAudioPlayer
}

internal class MediaPlayerChatAudioPlayer(
    private val delegate: MediaPlayer = MediaPlayer(),
) : ChatAudioPlayer {
    override val currentPosition: Int get() = delegate.currentPosition

    override fun configureForSpeech() {
        delegate.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }
    override fun setDataSource(source: String) = delegate.setDataSource(source)
    override fun setOnPreparedListener(listener: (ChatAudioPlayer) -> Unit) {
        delegate.setOnPreparedListener { listener(this) }
    }
    override fun setOnCompletionListener(listener: (ChatAudioPlayer) -> Unit) {
        delegate.setOnCompletionListener { listener(this) }
    }
    override fun setOnErrorListener(listener: (ChatAudioPlayer, Int, Int) -> Boolean) {
        delegate.setOnErrorListener { _, what, extra -> listener(this, what, extra) }
    }
    override fun prepareAsync() = delegate.prepareAsync()
    override fun start() = delegate.start()
    override fun pause() = delegate.pause()
    override fun seekTo(positionMillis: Int) = delegate.seekTo(positionMillis)
    override fun release() = delegate.release()
}

internal class ChatAudioPlaybackController(
    private val playerFactory: ChatAudioPlayerFactory = ChatAudioPlayerFactory { MediaPlayerChatAudioPlayer() },
) {
    var state by mutableStateOf(ChatAudioPlaybackUiState())
        private set

    private var mediaPlayer: ChatAudioPlayer? = null
    private var retainedPositionMillis: Int = 0
    private var activeGeneration: Long = 0L
    private var refreshJob: Job? = null

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
        if (state.phase == ChatAudioPlaybackPhase.Preparing) {
            invalidateAndRelease(resetState = true)
            return
        }
        val player = mediaPlayer ?: return
        val generation = activeGeneration
        val key = state.key
        runCatching {
            retainedPositionMillis = player.currentPosition
            player.pause()
            if (owns(generation, player, key)) {
                state = state.copy(
                    phase = ChatAudioPlaybackPhase.Paused,
                    positionMillis = retainedPositionMillis,
                    error = null,
                )
            }
        }.onFailure {
            failCurrent()
        }
    }

    fun release() {
        invalidateAndRelease(resetState = true)
    }

    fun onStoppedInBackground() {
        when (state.phase) {
            ChatAudioPlaybackPhase.Playing -> pause()
            ChatAudioPlaybackPhase.Preparing -> invalidateAndRelease(resetState = true)
            ChatAudioPlaybackPhase.Paused,
            ChatAudioPlaybackPhase.Failed,
            ChatAudioPlaybackPhase.Idle -> Unit
        }
    }

    fun tick() {
        val player = mediaPlayer ?: return
        val generation = activeGeneration
        val key = state.key
        if (state.phase != ChatAudioPlaybackPhase.Playing) return
        runCatching {
            val position = player.currentPosition
            if (owns(generation, player, key)) {
                state = state.copy(positionMillis = position)
            }
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
        val generation = activeGeneration
        val key = state.key
        runCatching {
            player.seekTo(retainedPositionMillis)
            player.start()
            if (owns(generation, player, key)) {
                state = state.copy(phase = ChatAudioPlaybackPhase.Playing, error = null)
            }
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
        val generation = nextGeneration()
        retainedPositionMillis = 0
        state = ChatAudioPlaybackUiState(
            key = key,
            phase = ChatAudioPlaybackPhase.Preparing,
            durationMillis = durationMillis,
        )
        val player = playerFactory.create()
        mediaPlayer = player
        runCatching {
            player.configureForSpeech()
            player.setDataSource(source)
            player.setOnPreparedListener { callbackPlayer ->
                if (!owns(generation, callbackPlayer, key)) {
                    safeReleaseObsolete(callbackPlayer)
                    return@setOnPreparedListener
                }
                runCatching {
                    callbackPlayer.start()
                    if (owns(generation, callbackPlayer, key)) {
                        state = state.copy(phase = ChatAudioPlaybackPhase.Playing, error = null)
                    }
                }.onFailure {
                    handlePlaybackFailure(
                        generation,
                        callbackPlayer,
                        key,
                        source,
                        durationMillis,
                        scope,
                        refreshSource,
                        retriedAfterRefresh,
                        what = null,
                        extra = null,
                    )
                }
            }
            player.setOnCompletionListener { callbackPlayer ->
                if (!owns(generation, callbackPlayer, key)) return@setOnCompletionListener
                retainedPositionMillis = 0
                state = state.copy(
                    phase = ChatAudioPlaybackPhase.Idle,
                    positionMillis = 0,
                    error = null,
                )
            }
            player.setOnErrorListener { callbackPlayer, what, extra ->
                handlePlaybackFailure(
                    generation,
                    callbackPlayer,
                    key,
                    source,
                    durationMillis,
                    scope,
                    refreshSource,
                    retriedAfterRefresh,
                    what,
                    extra,
                )
                true
            }
            player.prepareAsync()
        }.onFailure {
            handlePlaybackFailure(
                generation,
                player,
                key,
                source,
                durationMillis,
                scope,
                refreshSource,
                retriedAfterRefresh,
                what = null,
                extra = null,
            )
        }
    }

    private fun handlePlaybackFailure(
        generation: Long,
        player: ChatAudioPlayer,
        key: String,
        source: String,
        durationMillis: Long,
        scope: CoroutineScope,
        refreshSource: (suspend () -> String?)?,
        retriedAfterRefresh: Boolean,
        what: Int?,
        extra: Int?,
    ) {
        if (!owns(generation, player, key)) {
            safeReleaseObsolete(player)
            return
        }
        debugPlayback(
            key = key,
            phase = state.phase,
            what = what,
            extra = extra,
            refreshAttempted = refreshSource != null,
            refreshedUrlChanged = null,
        )
        runCatching { player.release() }
        if (mediaPlayer === player) mediaPlayer = null
        retainedPositionMillis = 0
        if (refreshSource == null || retriedAfterRefresh) {
            failCurrent(key, durationMillis)
            return
        }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val refreshed = runCatching { refreshSource() }.getOrNull()
            if (!owns(generation, player = null, expectedKey = key)) return@launch
            val changed = !refreshed.isNullOrBlank() && refreshed != source
            debugPlayback(
                key = key,
                phase = state.phase,
                what = what,
                extra = extra,
                refreshAttempted = true,
                refreshedUrlChanged = changed,
            )
            if (changed) {
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

    private fun nextGeneration(): Long {
        refreshJob?.cancel()
        refreshJob = null
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        activeGeneration += 1
        return activeGeneration
    }

    private fun invalidateAndRelease(resetState: Boolean) {
        activeGeneration += 1
        refreshJob?.cancel()
        refreshJob = null
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        retainedPositionMillis = 0
        if (resetState) state = ChatAudioPlaybackUiState()
    }

    private fun owns(
        generation: Long,
        player: ChatAudioPlayer?,
        expectedKey: String?,
    ): Boolean =
        generation == activeGeneration &&
            (player == null || mediaPlayer === player) &&
            state.key == expectedKey

    private fun safeReleaseObsolete(player: ChatAudioPlayer) {
        if (mediaPlayer !== player) {
            runCatching { player.release() }
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

    private fun debugPlayback(
        key: String,
        phase: ChatAudioPlaybackPhase,
        what: Int?,
        extra: Int?,
        refreshAttempted: Boolean,
        refreshedUrlChanged: Boolean?,
    ) {
        if (!isDebugLoggable()) return
        Log.d(
            TAG,
            "playback key=$key phase=$phase what=$what extra=$extra " +
                "refreshAttempted=$refreshAttempted refreshedUrlChanged=$refreshedUrlChanged"
        )
    }

    private companion object {
        private const val TAG = "ChatAudioPlayback"

        private fun isDebugLoggable(): Boolean =
            runCatching { Log.isLoggable(TAG, Log.DEBUG) }.getOrDefault(false)
    }
}
