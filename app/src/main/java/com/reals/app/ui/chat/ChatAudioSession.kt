package com.reals.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reals.app.domain.model.ChatAudioPolicy
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessagePresentation
import com.reals.app.ui.root.ChatAudioDraftUiState
import com.reals.app.ui.root.ChatAudioUploadUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal enum class ChatAudioStopDisposition {
    Preview,
    Send,
}

internal data class ChatAudioSessionInputs(
    val chatId: String?,
    val audioPolicy: ChatAudioPolicy?,
    val canChat: Boolean,
    val canSendMessages: Boolean,
    val sendingMessage: Boolean,
    val messageComposerLoading: Boolean,
    val messageComposerPausedCopy: String? = null,
    val audioActionLoading: Boolean,
    val textDraft: String,
    val uploadState: ChatAudioUploadUiState,
    val draft: ChatAudioDraftUiState?,
)

internal data class ChatAudioSessionExternalCallbacks(
    val onTextDraftChange: (String) -> Unit,
    val onSendText: (String) -> Boolean,
    val onClearTextDraft: () -> Unit,
    val onClearUploadState: () -> Unit,
    val onDraftReady: (ChatAudioDraftUiState) -> Unit,
    val onDraftReadyAndSend: (ChatAudioDraftUiState) -> Boolean,
    val onDeleteDraft: () -> Unit,
    val onSendAudioMessage: (filePath: String, clientMessageId: String) -> Boolean,
    val onRefreshAudioUrl: suspend (messageId: String) -> String?,
)

@Composable
internal fun rememberChatAudioSessionState(
    inputs: ChatAudioSessionInputs,
    callbacks: ChatAudioSessionExternalCallbacks,
): ChatAudioSessionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState = remember(inputs.chatId) {
        ChatAudioSessionState(
            applicationContext = context.applicationContext,
            coroutineScope = coroutineScope,
        )
    }
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        sessionState.onRecordPermissionResult(granted)
    }

    sessionState.update(
        inputs = inputs,
        callbacks = callbacks,
        onRequestMicrophonePermission = {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
    )

    DisposableEffect(inputs.chatId) {
        onDispose {
            sessionState.disposeForChat()
        }
    }

    DisposableEffect(lifecycleOwner, inputs.chatId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                sessionState.onStoppedInBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(inputs.chatId, inputs.uploadState.completedClientMessageId) {
        sessionState.clearCompletedUploadIfNeeded()
    }

    LaunchedEffect(inputs.chatId, inputs.canSendMessages, sessionState.audioComposerState.visible) {
        sessionState.cancelIfChatStopsAcceptingAudio()
    }

    LaunchedEffect(inputs.chatId, inputs.draft?.filePath) {
        sessionState.cleanStaleDraftFiles()
    }

    LaunchedEffect(sessionState.playbackState.phase, sessionState.playbackState.key) {
        while (sessionState.playbackState.phase == ChatAudioPlaybackPhase.Playing) {
            delay(250.milliseconds)
            sessionState.tickPlayback()
        }
    }

    return sessionState
}

internal class ChatAudioSessionState internal constructor(
    private val applicationContext: Context,
    private val coroutineScope: CoroutineScope,
    private val recorderController: ChatAudioRecorderController = ChatAudioRecorderController(applicationContext),
    private val playbackController: ChatAudioPlaybackController = ChatAudioPlaybackController(),
) {
    private var inputs: ChatAudioSessionInputs? by mutableStateOf(null)
    private var callbacks: ChatAudioSessionExternalCallbacks? = null
    private var onRequestMicrophonePermission: (() -> Unit)? = null
    private var recordingStartedAtMillis by mutableStateOf<Long?>(null)
    private var recordingOperationInFlight by mutableStateOf(false)
    private var localAudioError by mutableStateOf<String?>(null)
    private var localAudioInfo by mutableStateOf<String?>(null)

    val interactionBusy: Boolean
        get() = recordingOperationInFlight ||
            recordingStartedAtMillis != null ||
            currentInputs.uploadState.uploading

    val playbackState: ChatAudioPlaybackUiState
        get() = playbackController.state

    val audioComposerState: ChatAudioComposerUiState
        get() = chatAudioComposerUiState(
            chat = null,
            audioPolicy = currentInputs.audioPolicy,
            canSendMessages = currentInputs.canSendMessages,
            sendingMessage = currentInputs.sendingMessage,
            audioUploading = currentInputs.uploadState.uploading,
            recordingActive = recordingStartedAtMillis != null || recordingOperationInFlight,
            loadingChatAction = currentInputs.audioActionLoading,
        )

    private val messageComposerState: MessageComposerUiState
        get() = messageComposerUiState(
            canChat = currentInputs.canChat,
            canSendMessages = currentInputs.canSendMessages,
            sendingMessage = currentInputs.sendingMessage,
            loadingChatAction = currentInputs.messageComposerLoading || interactionBusy,
            draft = currentInputs.textDraft,
            pausedCopy = currentInputs.messageComposerPausedCopy,
        )

    private val currentInputs: ChatAudioSessionInputs
        get() = checkNotNull(inputs) { "ChatAudioSessionState inputs were not installed." }

    private val currentCallbacks: ChatAudioSessionExternalCallbacks
        get() = checkNotNull(callbacks) { "ChatAudioSessionState callbacks were not installed." }

    internal fun update(
        inputs: ChatAudioSessionInputs,
        callbacks: ChatAudioSessionExternalCallbacks,
        onRequestMicrophonePermission: () -> Unit,
    ) {
        this.inputs = inputs
        this.callbacks = callbacks
        this.onRequestMicrophonePermission = onRequestMicrophonePermission
    }

    internal fun composerPresentation(): ChatAudioComposerPresentation =
        ChatAudioComposerPresentation(
            draft = currentInputs.textDraft,
            textState = messageComposerState,
            audioState = audioComposerState,
            audioDraft = currentInputs.draft,
            uploadState = currentInputs.uploadState,
            localAudioError = localAudioError,
            localAudioInfo = localAudioInfo,
            recordingStartedAtMillis = recordingStartedAtMillis,
            recordingOperationInFlight = recordingOperationInFlight,
            playbackState = playbackState,
        )

    internal fun composerCallbacks(): ChatAudioComposerCallbacks {
        val textState = messageComposerState
        val audioState = audioComposerState
        return ChatAudioComposerCallbacks(
            onDraftChange = { value: String ->
                if (textState.canEditDraft) {
                    currentCallbacks.onTextDraftChange(value.take(1_000))
                }
            },
            onSendText = {
                if (textState.sendButtonEnabled && currentCallbacks.onSendText(currentInputs.textDraft)) {
                    currentCallbacks.onClearTextDraft()
                    localAudioError = null
                    localAudioInfo = null
                }
            },
            onStartRecording = startRecording@{
                if (!audioState.startEnabled) {
                    localAudioError = audioState.disabledCopy
                    return@startRecording
                }
                val hasPermission = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    startRecording()
                } else {
                    onRequestMicrophonePermission?.invoke()
                }
            },
            onStopRecording = {
                stopRecording(ChatAudioStopSource.Manual, ChatAudioStopDisposition.Preview)
            },
            onSendRecording = {
                stopRecording(ChatAudioStopSource.Manual, ChatAudioStopDisposition.Send)
            },
            onMaxDurationReached = {
                stopRecording(ChatAudioStopSource.MaxDuration, ChatAudioStopDisposition.Send)
            },
            onCancelRecording = {
                cancelRecording(ChatAudioStopSource.Cancel)
                localAudioError = null
                localAudioInfo = null
            },
            onPlayDraft = { draftToPlay: ChatAudioDraftUiState ->
                playbackController.playLocal(
                    key = "draft-${draftToPlay.clientMessageId}",
                    filePath = draftToPlay.filePath,
                    durationMillis = draftToPlay.durationMillis,
                    scope = coroutineScope,
                )
            },
            onPauseAudio = ::pauseAudio,
            onDeleteDraft = {
                playbackController.release()
                currentCallbacks.onDeleteDraft()
                localAudioError = null
                localAudioInfo = null
                currentCallbacks.onClearUploadState()
            },
            onSendAudio = { draftToSend: ChatAudioDraftUiState ->
                localAudioInfo = null
                currentCallbacks.onSendAudioMessage(draftToSend.filePath, draftToSend.clientMessageId)
            },
        )
    }

    internal fun playRemoteMessage(message: ChatMessage) {
        val audio = (message.presentation as? ChatMessagePresentation.Audio)?.audio ?: return
        val url = audio.url ?: return
        playbackController.playRemote(
            messageId = message.id,
            url = url,
            durationMillis = audio.durationMillis ?: 0L,
            scope = coroutineScope,
            refreshUrl = { currentCallbacks.onRefreshAudioUrl(message.id) },
        )
    }

    internal fun pauseAudio() {
        playbackController.pause()
    }

    internal fun cleanupForSafetyAction() {
        cancelRecording(ChatAudioStopSource.Cancel)
        playbackController.release()
        localAudioError = null
        localAudioInfo = null
        if (!currentInputs.uploadState.uploading) {
            currentCallbacks.onDeleteDraft()
        }
    }

    internal fun onRecordPermissionResult(granted: Boolean) {
        if (granted && audioComposerState.startEnabled && currentInputs.chatId != null) {
            startRecording()
        } else {
            localAudioError = "Necesitamos permiso de micrófono para grabar audios."
            localAudioInfo = null
        }
    }

    internal fun disposeForChat() {
        recorderController.invalidateAndReleaseAsync(deleteOutput = true)
        playbackController.release()
        localAudioInfo = null
    }

    internal fun onStoppedInBackground() {
        if (recordingStartedAtMillis != null || recordingOperationInFlight) {
            cancelRecording(ChatAudioStopSource.Lifecycle)
            localAudioError = "Se canceló la grabación al salir de la app."
            localAudioInfo = null
        }
        playbackController.onStoppedInBackground()
    }

    internal fun clearCompletedUploadIfNeeded() {
        val completedClientMessageId = currentInputs.uploadState.completedClientMessageId ?: return
        currentInputs.draft
            ?.takeIf { it.clientMessageId == completedClientMessageId }
            ?: return
        playbackController.release()
        localAudioError = null
        localAudioInfo = null
        currentCallbacks.onClearUploadState()
    }

    internal fun cancelIfChatStopsAcceptingAudio() {
        if (
            (!currentInputs.canSendMessages || !audioComposerState.visible) &&
            (recordingStartedAtMillis != null || recordingOperationInFlight)
        ) {
            cancelRecording(ChatAudioStopSource.Lifecycle)
            localAudioError = "La grabación se canceló porque el chat ya no admite mensajes."
            localAudioInfo = null
        }
    }

    internal suspend fun cleanStaleDraftFiles() {
        recorderController.cleanStaleDraftFiles(setOfNotNull(currentInputs.draft?.filePath))
    }

    internal fun tickPlayback() {
        playbackController.tick()
    }

    private fun publishRecorderResult(
        result: ChatAudioRecorderResult,
        disposition: ChatAudioStopDisposition = ChatAudioStopDisposition.Preview,
    ) {
        when (result) {
            is ChatAudioRecorderResult.Ready -> {
                val draftState = result.draft.toUiState()
                localAudioError = null
                localAudioInfo = if (result.stopSource == ChatAudioStopSource.MaxDuration) {
                    "Duración máxima permitida alcanzada"
                } else {
                    null
                }
                val sendsImmediately = disposition == ChatAudioStopDisposition.Send &&
                    (
                        result.stopSource == ChatAudioStopSource.Manual ||
                            result.stopSource == ChatAudioStopSource.MaxDuration
                        )
                if (sendsImmediately) {
                    if (currentCallbacks.onDraftReadyAndSend(draftState) &&
                        result.stopSource != ChatAudioStopSource.MaxDuration
                    ) {
                        localAudioInfo = null
                    }
                } else {
                    currentCallbacks.onDraftReady(draftState)
                    currentCallbacks.onClearUploadState()
                }
            }

            is ChatAudioRecorderResult.Failed -> {
                localAudioError = result.message
                localAudioInfo = null
            }
            ChatAudioRecorderResult.Cancelled,
            ChatAudioRecorderResult.Started -> Unit
        }
    }

    private fun stopRecording(
        source: ChatAudioStopSource = ChatAudioStopSource.Manual,
        disposition: ChatAudioStopDisposition = ChatAudioStopDisposition.Preview,
    ) {
        if (recordingOperationInFlight) return
        recordingOperationInFlight = true
        coroutineScope.launch {
            val result = recorderController.stop(
                maxDurationMillis = currentInputs.audioPolicy?.maxDurationMillis
                    ?: DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS,
                maxFileSizeBytes = currentInputs.audioPolicy?.maxFileSizeBytes
                    ?: DEFAULT_CHAT_AUDIO_MAX_FILE_SIZE_BYTES,
                source = source,
            )
            recordingStartedAtMillis = null
            recordingOperationInFlight = false
            publishRecorderResult(
                result = result,
                disposition = if (source == ChatAudioStopSource.Manual) {
                    disposition
                } else if (source == ChatAudioStopSource.MaxDuration) {
                    ChatAudioStopDisposition.Send
                } else {
                    ChatAudioStopDisposition.Preview
                },
            )
        }
    }

    private fun startRecording() {
        if (recordingOperationInFlight || recordingStartedAtMillis != null) return
        playbackController.release()
        currentCallbacks.onDeleteDraft()
        currentCallbacks.onClearUploadState()
        localAudioInfo = null
        recordingStartedAtMillis = SystemClock.elapsedRealtime()
        recordingOperationInFlight = true
        coroutineScope.launch {
            val result = recorderController.start(
                maxDurationMillis = currentInputs.audioPolicy?.maxDurationMillis
                    ?: DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS,
                maxFileSizeBytes = currentInputs.audioPolicy?.maxFileSizeBytes
                    ?: DEFAULT_CHAT_AUDIO_MAX_FILE_SIZE_BYTES,
                onLimitReached = { stopSource: ChatAudioStopSource ->
                    coroutineScope.launch {
                        stopRecording(stopSource, ChatAudioStopDisposition.Preview)
                    }
                },
            )
            recordingOperationInFlight = false
            when (result) {
                ChatAudioRecorderResult.Started -> {
                    localAudioError = null
                    localAudioInfo = null
                }

                is ChatAudioRecorderResult.Failed -> {
                    recordingStartedAtMillis = null
                    localAudioError = result.message
                    localAudioInfo = null
                }
                ChatAudioRecorderResult.Cancelled -> {
                    recordingStartedAtMillis = null
                }
                is ChatAudioRecorderResult.Ready -> publishRecorderResult(result)
            }
        }
    }

    private fun cancelRecording(source: ChatAudioStopSource = ChatAudioStopSource.Cancel) {
        recorderController.invalidateAndReleaseAsync(source = source)
        recordingStartedAtMillis = null
        recordingOperationInFlight = false
    }
}

private fun LocalChatAudioDraft.toUiState(): ChatAudioDraftUiState =
    ChatAudioDraftUiState(
        filePath = filePath,
        clientMessageId = clientMessageId,
        durationMillis = durationMillis,
        sizeBytes = sizeBytes,
    )
