package com.reals.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reals.app.R
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.core.time.remainingExitSeconds
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatAudioUnavailableReason
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessagePresentation
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatCompletionDecision
import com.reals.app.domain.model.SecondChatResolutionRequestType
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.ManualBlockConfirmationDialog
import com.reals.app.ui.common.SearchingDotsIndicator
import com.reals.app.ui.common.formatBackendDateTime
import com.reals.app.ui.common.formatBackendTime
import com.reals.app.ui.root.OptimisticOutgoingMessage
import com.reals.app.ui.root.OutgoingMessageDeliveryState
import com.reals.app.ui.root.ChatAudioUploadUiState
import com.reals.app.ui.root.SecondChatLifecycleUiState
import com.reals.app.ui.root.SecondChatResolutionPresentation
import com.reals.app.ui.root.hasPendingNoShowClaim
import com.reals.app.ui.root.isWaitingForPartner
import com.reals.app.ui.root.remainingMillisFromServerSnapshot
import com.reals.app.ui.root.resolutionPresentation
import com.reals.app.ui.root.secondChatResultCopy
import com.reals.app.ui.root.timingPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val MUTUAL_EXIT_TIMEOUT_SECONDS = 20L
private const val MUTUAL_EXIT_TIMEOUT_RETRY_MILLIS = 2_000L
internal const val MUTUAL_EXIT_CONVERSATION_PAUSED_COPY =
    "La conversaci\u00f3n est\u00e1 pausada mientras se resuelve la solicitud."

internal data class ChatLoadingPresentation(
    val title: String,
    val body: String,
)

internal fun chatLoadingPresentation(
    chatTitlePrefix: String,
    partnerName: String?,
): ChatLoadingPresentation {
    val title = chatTitlePrefix.trim().ifBlank { "Preparando chat" }
    val safePartnerName = partnerName
        ?.takeIf { it.isNotBlank() }
        ?.let { TextSafety.safeDisplay(it, maxLength = 100) }
    val isSecondChat = title.equals("Segundo chat", ignoreCase = true)
    val body = when {
        isSecondChat && safePartnerName != null -> "Estamos cargando el segundo chat con $safePartnerName."
        isSecondChat -> "Estamos cargando el segundo chat."
        safePartnerName != null -> "Estamos preparando la conversación con $safePartnerName."
        else -> "Estamos preparando la conversación."
    }
    return ChatLoadingPresentation(title = title, body = body)
}

@Composable
fun ChatScreen(
    currentUserId: String,
    match: Match?,
    chat: Chat?,
    messages: List<ChatMessage>,
    optimisticMessages: List<OptimisticOutgoingMessage>,
    exitRequests: List<ChatExitRequest>,
    loading: Boolean,
    refreshing: Boolean,
    sending: Boolean,
    audioUpload: ChatAudioUploadUiState = ChatAudioUploadUiState(),
    actionLoading: Boolean,
    actionLoadingLabel: String?,
    guidance: FirstChatGuidance? = null,
    guidanceActionLoading: Boolean = false,
    secondChatLifecycle: SecondChatLifecycleUiState? = null,
    manualBlockLoading: Boolean,
    manualBlockError: ApiError?,
    error: ApiError?,
    message: String?,
    chatTitlePrefix: String = "Chat",
    partnerNameFallback: String? = null,
    showDecisionActions: Boolean = true,
    showExitActions: Boolean = true,
    showMutualExitActions: Boolean = true,
    allowAvailableChat: Boolean = false,
    onBackHome: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onFirstChatLocalExpiry: (inactivity: Boolean) -> Unit = {},
    onSecondChatUnavailable: () -> Unit = {},
    onSecondChatLocalAbsoluteExpiry: () -> Unit = {},
    onRequestNextGuidanceQuestion: (() -> Unit)? = null,
    onClaimSecondChatNoShow: () -> Unit = {},
    onRequestSecondChatCompletion: () -> Unit = {},
    onDecideSecondChatCompletion: (String, SecondChatCompletionDecision) -> Unit = { _, _ -> },
    onClaimSecondChatInactivity: () -> Unit = {},
    onSendMessage: (String) -> Boolean,
    onSendAudioMessage: (filePath: String, clientMessageId: String) -> Boolean = { _, _ -> false },
    onClearAudioUploadState: () -> Unit = {},
    onRefreshAudioUrl: suspend (messageId: String) -> String? = { null },
    onRetryOptimisticMessage: (localId: String, content: String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRequestMutualExit: () -> Unit,
    onSafetyCancel: (ChatExitReason, String) -> Unit,
    onManualBlock: () -> Unit,
    onClearManualBlockError: () -> Unit,
    onAcceptExitRequest: (String) -> Unit,
    onRejectExitRequest: (String) -> Unit,
    onExitRequestTimeout: (String) -> Unit,
) {
    var draft by rememberSaveable(chat?.id) { mutableStateOf("") }
    var safetyDetails by rememberSaveable(chat?.id) { mutableStateOf("") }
    var safetyReasonRawValue by rememberSaveable(chat?.id) {
        mutableStateOf(ChatExitReason.InappropriateBehavior.rawValue)
    }
    var showingSafetyDialog by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var actionsMenuExpanded by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var showingManualBlockDialog by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var showingSecondChatCompletionDialog by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var showingSecondChatInactivityDialog by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var secondChatResolutionRefreshHandledKey by rememberSaveable(chat?.id) { mutableStateOf<String?>(null) }
    var nowMillis by rememberSaveable(chat?.id) { mutableStateOf(System.currentTimeMillis()) }
    var firstChatExpiryHandled by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var secondChatUnavailableHandled by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var recordingStartedAtMillis by rememberSaveable(chat?.id) { mutableStateOf<Long?>(null) }
    var audioDraft by remember(chat?.id) { mutableStateOf<LocalChatAudioDraft?>(null) }
    var localAudioError by rememberSaveable(chat?.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val recorderController = remember(chat?.id) { ChatAudioRecorderController(context.applicationContext) }
    val playbackController = remember(chat?.id) { ChatAudioPlaybackController() }
    val firstChatLifecycle = firstChatLifecycleUiState(chat, nowMillis)
    val firstChatLocallyExpired = firstChatLifecycle?.expired == true
    val secondChatTiming = secondChatLifecycle?.timingPresentation(nowMillis)
    val secondChatLocallyExpired = secondChatTiming?.locallyExpired == true
    val readOnlyUntilInstant = backendInstantOrNull(chat?.readOnlyUntil)
    val secondChatTerminalReadable = chat?.status in listOf(
        ChatStatus.Expired,
        ChatStatus.Finished,
        ChatStatus.Abandoned,
    )
    val secondChatReadOnlyFuture = chat?.chatType == ChatType.SecondChat &&
            secondChatTerminalReadable &&
            readOnlyUntilInstant != null &&
            java.time.Instant.ofEpochMilli(nowMillis).isBefore(readOnlyUntilInstant)
    val secondChatUnavailable = chat?.chatType == ChatType.SecondChat &&
            (
                    chat.status in listOf(
                        ChatStatus.Closed,
                        ChatStatus.Cancelled,
                    ) ||
                            (secondChatTerminalReadable && !secondChatReadOnlyFuture)
                    )
    val canChat = !firstChatLocallyExpired &&
            !secondChatLocallyExpired &&
            !secondChatReadOnlyFuture &&
            !secondChatUnavailable &&
            (chat?.status == ChatStatus.Active ||
                    (allowAvailableChat && chat?.status == ChatStatus.Available)
                    )
    val sendingMessage = sending
    val loadingChatAction = actionLoading || manualBlockLoading
    val secondChatResolution = secondChatLifecycle?.resolutionPresentation(
        currentUserId = currentUserId,
        nowMillis = nowMillis,
        actionLoading = loadingChatAction,
    )
    val canUseChatActions = canChat && !loadingChatAction
    val canUseNavigationActions = !loadingChatAction &&
            secondChatTiming?.genuinelyActive != true
    val pendingExitRequest = exitRequests
        .filter { it.status == ChatExitRequestStatus.Pending }
        .maxByOrNull { it.createdAt }
    val exitFlowLocked = pendingExitRequest != null
    val canSendMessages = canChat && !exitFlowLocked
    val audioComposerState = chatAudioComposerUiState(
        chat = chat,
        canSendMessages = canSendMessages,
        sendingMessage = sendingMessage,
        audioUploading = audioUpload.uploading,
        recordingActive = recordingStartedAtMillis != null,
        loadingChatAction = loadingChatAction || guidanceActionLoading,
    )
    val guidancePanelState = firstChatGuidancePanelState(
        guidance = guidance,
        canRequestNextWhileChatOpen = canSendMessages,
    )
    val canUseExistingChatActions =
        canUseChatActions &&
            (secondChatLifecycle == null || secondChatTiming?.genuinelyActive == true) &&
            (!showMutualExitActions || !exitFlowLocked)
    val manualBlockBusy =
        loading || refreshing || sending || audioUpload.uploading || actionLoading || guidanceActionLoading ||
            manualBlockLoading
    val canManualBlock = !manualBlockBusy
    val canOpenOverflowActions =
        (!loadingChatAction && ((!exitFlowLocked && canUseChatActions) || !showMutualExitActions)) ||
            canManualBlock
    val canDecide = showDecisionActions &&
            match?.state == MatchState.ChatActive &&
            chat?.status == ChatStatus.Active &&
            chat.myDecision == ChatDecisionState.Pending &&
            !exitFlowLocked &&
            !firstChatLocallyExpired
    val partnerDisplayName = chat?.partner?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: partnerNameFallback?.takeIf { it.isNotBlank() }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val bottomContentPadding = bottomBarHeight.takeIf { it > 0.dp } ?: 180.dp
    val composerState = messageComposerUiState(
        canChat = canChat,
        canSendMessages = canSendMessages,
        sendingMessage = sendingMessage,
        loadingChatAction = loadingChatAction || audioUpload.uploading || recordingStartedAtMillis != null,
        draft = draft,
    )
    fun stopRecordingToPreview() {
        val result = recorderController.stop(
            maxDurationMillis = chat?.audioPolicy?.maxDurationMillis ?: DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS,
            maxFileSizeBytes = chat?.audioPolicy?.maxFileSizeBytes ?: DEFAULT_CHAT_AUDIO_MAX_FILE_SIZE_BYTES,
        )
        recordingStartedAtMillis = null
        when (result) {
            is ChatAudioRecorderResult.Ready -> {
                audioDraft?.filePath?.let { File(it).delete() }
                audioDraft = result.draft
                localAudioError = null
                onClearAudioUploadState()
            }

            is ChatAudioRecorderResult.Failed -> localAudioError = result.message
            ChatAudioRecorderResult.Cancelled,
            ChatAudioRecorderResult.Started -> Unit
        }
    }

    fun startRecording() {
        playbackController.release()
        audioDraft?.filePath?.let { File(it).delete() }
        audioDraft = null
        onClearAudioUploadState()
        val result = recorderController.start(
            maxDurationMillis = chat?.audioPolicy?.maxDurationMillis ?: DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS,
            maxFileSizeBytes = chat?.audioPolicy?.maxFileSizeBytes ?: DEFAULT_CHAT_AUDIO_MAX_FILE_SIZE_BYTES,
            onLimitReached = { coroutineScope.launch { stopRecordingToPreview() } },
        )
        when (result) {
            ChatAudioRecorderResult.Started -> {
                recordingStartedAtMillis = System.currentTimeMillis()
                localAudioError = null
            }

            is ChatAudioRecorderResult.Failed -> localAudioError = result.message
            ChatAudioRecorderResult.Cancelled,
            is ChatAudioRecorderResult.Ready -> Unit
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && audioComposerState.startEnabled && chat != null) {
            startRecording()
        } else {
            localAudioError = "Necesitamos permiso de micrófono para grabar audios."
        }
    }

    DisposableEffect(chat?.id) {
        onDispose {
            recorderController.release(deleteOutput = true)
            playbackController.release()
            audioDraft?.filePath?.let { File(it).delete() }
        }
    }

    DisposableEffect(lifecycleOwner, chat?.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (recordingStartedAtMillis != null) {
                    recorderController.cancel()
                    recordingStartedAtMillis = null
                    localAudioError = "Se canceló la grabación al salir de la app."
                }
                playbackController.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(chat?.id, audioUpload.completedClientMessageId) {
        val completedClientMessageId = audioUpload.completedClientMessageId ?: return@LaunchedEffect
        val draftToClear = audioDraft?.takeIf { it.clientMessageId == completedClientMessageId } ?: return@LaunchedEffect
        playbackController.release()
        File(draftToClear.filePath).delete()
        audioDraft = null
        localAudioError = null
        onClearAudioUploadState()
    }

    LaunchedEffect(chat?.id, canSendMessages, audioComposerState.visible) {
        if ((!canSendMessages || !audioComposerState.visible) && recordingStartedAtMillis != null) {
            recorderController.cancel()
            recordingStartedAtMillis = null
            localAudioError = "La grabación se canceló porque el chat ya no admite mensajes."
        }
    }

    LaunchedEffect(playbackController.state.phase, playbackController.state.key) {
        while (playbackController.state.phase == ChatAudioPlaybackPhase.Playing) {
            delay(250.milliseconds)
            playbackController.tick()
        }
    }

    if (loading && chat == null) {
        val loadingPresentation = chatLoadingPresentation(chatTitlePrefix, partnerNameFallback)
        LoadingChatScreen(
            title = loadingPresentation.title,
            body = loadingPresentation.body,
        )
        return
    }

    val pollChat = chatPollingEnabled(canChat)
    LaunchedEffect(chat?.id, pollChat) {
        while (pollChat) {
            delay(2000.milliseconds)
            onRefresh()
        }
    }

    LaunchedEffect(chat?.id, firstChatLifecycle?.deadline) {
        while (
            firstChatLifecycleUiState(chat)?.expired == false ||
            secondChatLifecycle?.timingPresentation(nowMillis)?.genuinelyActive == true
        ) {
            delay(1_000.milliseconds)
            nowMillis = System.currentTimeMillis()
        }
        nowMillis = System.currentTimeMillis()
    }

    LaunchedEffect(chat?.id, firstChatLocallyExpired, firstChatLifecycle?.reason) {
        val lifecycle = firstChatLifecycle ?: return@LaunchedEffect
        if (firstChatLocallyExpired && !firstChatExpiryHandled) {
            firstChatExpiryHandled = true
            onFirstChatLocalExpiry(lifecycle.reason == FirstChatExpiryReason.Inactivity)
        }
    }

    LaunchedEffect(chat?.id, secondChatUnavailable) {
        if (secondChatUnavailable && !secondChatUnavailableHandled) {
            secondChatUnavailableHandled = true
            onSecondChatUnavailable()
        }
    }

    LaunchedEffect(chat?.id, secondChatLocallyExpired) {
        if (shouldDispatchSecondChatLocalAbsoluteExpiry(secondChatLocallyExpired)) {
            onSecondChatLocalAbsoluteExpiry()
        }
    }

    val secondChatResolutionRefreshKey = secondChatResolution?.activeRequest?.refreshKey
        ?: secondChatResolution?.completionCooldown?.refreshKey
    LaunchedEffect(chat?.id, secondChatResolutionRefreshKey) {
        if (secondChatResolutionRefreshKey == null) {
            secondChatResolutionRefreshHandledKey = null
        } else if (secondChatResolutionRefreshHandledKey != secondChatResolutionRefreshKey) {
            secondChatResolutionRefreshHandledKey = secondChatResolutionRefreshKey
            onRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatHeader(
                titlePrefix = chatTitlePrefix,
                partnerName = partnerDisplayName,
                expiresAt = chat?.expiresAt,
                firstChatLifecycle = firstChatLifecycle,
                secondChatReadOnlyUntil = chat?.readOnlyUntil.takeIf { secondChatReadOnlyFuture },
                secondChatUnavailable = secondChatUnavailable,
                myDecision = chat?.myDecision,
                partnerDecision = chat?.partnerDecision,
                showDecisionSummary = showDecisionActions,
                trailingContent = if (showExitActions) {
                    {
                        ChatOverflowMenu(
                            expanded = actionsMenuExpanded,
                            enabled = canOpenOverflowActions,
                            actionLoading = loadingChatAction,
                            canUseExistingChatActions = canUseExistingChatActions,
                            canDecide = canDecide,
                            canManualBlock = canManualBlock,
                            showMutualExitActions = showMutualExitActions,
                            onExpandedChange = { actionsMenuExpanded = it },
                            onRequestMutualExit = {
                                actionsMenuExpanded = false
                                onRequestMutualExit()
                            },
                            onRejectChat = {
                                actionsMenuExpanded = false
                                onReject()
                            },
                            onShowSafety = {
                                actionsMenuExpanded = false
                                showingSafetyDialog = true
                            },
                            onShowManualBlock = {
                                actionsMenuExpanded = false
                                onClearManualBlockError()
                                showingManualBlockDialog = true
                            },
                        )
                    }
                } else {
                    null
                },
            )
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.Chat) }
            message?.let { SuccessFeedback(it) }
            firstChatLifecycle?.takeIf { it.showCountdown || it.expired }?.let { lifecycle ->
                FeedbackCard(
                    title = if (lifecycle.expired) "Estado" else "Tiempo restante",
                    message = if (lifecycle.expired) lifecycle.expiredCopy() else lifecycle.warningCopy(),
                    tone = FeedbackTone.Warning,
                )
            }
            if (secondChatTiming?.showAbsoluteExpiryWarning == true) {
                FeedbackCard(
                    title = "Tiempo restante",
                    message = "El segundo chat vence pronto. Al finalizar volverás a Home.",
                    tone = FeedbackTone.Warning,
                )
            }
            SecondChatLifecyclePanel(
                lifecycle = secondChatLifecycle,
                partnerName = partnerDisplayName,
                actionLoading = loadingChatAction,
                onClaimNoShow = onClaimSecondChatNoShow,
                onRefresh = onRefresh,
            )
            SecondChatResolutionPanel(
                presentation = secondChatResolution,
                actionLoading = loadingChatAction,
                actionLoadingLabel = actionLoadingLabel,
                onRequestCompletion = { showingSecondChatCompletionDialog = true },
                onAcceptCompletion = { requestId ->
                    onDecideSecondChatCompletion(requestId, SecondChatCompletionDecision.Accepted)
                },
                onRejectCompletion = { requestId ->
                    onDecideSecondChatCompletion(requestId, SecondChatCompletionDecision.Rejected)
                },
                onRequestInactivityClaim = { showingSecondChatInactivityDialog = true },
            )
            ChatActionsPanel(
                currentUserId = currentUserId,
                activeExitRequest = if (showExitActions) pendingExitRequest else null,
                loadingChatAction = loadingChatAction,
                actionLoadingLabel = actionLoadingLabel,
                canDecide = canDecide,
                canUseNavigationActions = canUseNavigationActions,
                showDecisionActions = showDecisionActions,
                onBackHome = onBackHome,
                onApprove = onApprove,
                onAcceptExitRequest = onAcceptExitRequest,
                onRejectExitRequest = onRejectExitRequest,
                onExitRequestTimeout = onExitRequestTimeout,
            )
            FirstChatGuidancePanel(
                state = guidancePanelState,
                actionLoading = guidanceActionLoading,
                onRequestNext = onRequestNextGuidanceQuestion,
            )
            MessageList(
                currentUserId = currentUserId,
                messages = messages,
                optimisticMessages = optimisticMessages,
                bottomContentPadding = bottomContentPadding + 12.dp,
                modifier = Modifier.weight(1f),
                onRetryOptimisticMessage = onRetryOptimisticMessage,
                playbackController = playbackController,
                coroutineScope = coroutineScope,
                onRefreshAudioUrl = onRefreshAudioUrl,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier.onSizeChanged { size ->
                    bottomBarHeight = with(density) { size.height.toDp() }
                },
            ) {
                MessageComposer(
                    draft = draft,
                    state = composerState,
                    audioState = audioComposerState,
                    audioDraft = audioDraft,
                    uploadState = audioUpload,
                    localAudioError = localAudioError,
                    recordingStartedAtMillis = recordingStartedAtMillis,
                    playbackState = playbackController.state,
                    onDraftChange = {
                        if (composerState.canEditDraft) {
                            draft = it.take(1_000)
                        }
                    },
                    onSend = {
                        if (composerState.sendButtonEnabled && onSendMessage(draft)) {
                            draft = ""
                        }
                    },
                    onStartRecording = {
                        if (!audioComposerState.startEnabled) {
                            localAudioError = audioComposerState.disabledCopy
                            return@MessageComposer
                        }
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            startRecording()
                        } else {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecording = { stopRecordingToPreview() },
                    onCancelRecording = {
                        recorderController.cancel()
                        recordingStartedAtMillis = null
                        localAudioError = null
                    },
                    onPlayDraft = { draftToPlay ->
                        playbackController.playLocal(
                            key = "draft-${draftToPlay.clientMessageId}",
                            filePath = draftToPlay.filePath,
                            durationMillis = draftToPlay.durationMillis,
                            scope = coroutineScope,
                        )
                    },
                    onPauseAudio = playbackController::pause,
                    onDeleteDraft = {
                        playbackController.release()
                        audioDraft?.filePath?.let { File(it).delete() }
                        audioDraft = null
                        localAudioError = null
                        onClearAudioUploadState()
                    },
                    onSendAudio = { draftToSend ->
                        onSendAudioMessage(draftToSend.filePath, draftToSend.clientMessageId)
                    },
                )
            }
        }
    }

    if (showingSafetyDialog && showExitActions && canUseExistingChatActions) {
        SafetyReportDialog(
            details = safetyDetails,
            selectedReason = safetyReportReasonFromRawValue(safetyReasonRawValue),
            actionLoading = actionLoading,
            onDetailsChange = { safetyDetails = it.take(1_000) },
            onReasonChange = { safetyReasonRawValue = it.rawValue },
            onDismiss = {
                if (!actionLoading) showingSafetyDialog = false
            },
            onConfirm = {
                onSafetyCancel(
                    safetyReportReasonFromRawValue(safetyReasonRawValue),
                    safetyDetails,
                )
                safetyDetails = ""
                safetyReasonRawValue = ChatExitReason.InappropriateBehavior.rawValue
                showingSafetyDialog = false
            },
        )
    }

    if (showingManualBlockDialog) {
        ManualBlockConfirmationDialog(
            loading = manualBlockLoading,
            error = manualBlockError,
            onConfirm = onManualBlock,
            onDismiss = {
                if (!manualBlockLoading) {
                    onClearManualBlockError()
                    showingManualBlockDialog = false
                }
            },
        )
    }

    secondChatResolution?.createCompletion?.let { create ->
        if (showingSecondChatCompletionDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!loadingChatAction) showingSecondChatCompletionDialog = false
                },
                title = { Text(create.confirmationTitle) },
                text = { Text(create.confirmationBody) },
                confirmButton = {
                    TextButton(
                        enabled = create.enabled && !loadingChatAction,
                        onClick = {
                            showingSecondChatCompletionDialog = false
                            onRequestSecondChatCompletion()
                        },
                    ) {
                        Text("Enviar solicitud")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !loadingChatAction,
                        onClick = { showingSecondChatCompletionDialog = false },
                    ) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }

    secondChatResolution?.createInactivityClaim?.let { create ->
        if (showingSecondChatInactivityDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!loadingChatAction) showingSecondChatInactivityDialog = false
                },
                title = { Text(create.confirmationTitle) },
                text = { Text(create.confirmationBody) },
                confirmButton = {
                    TextButton(
                        enabled = create.enabled && !loadingChatAction,
                        onClick = {
                            showingSecondChatInactivityDialog = false
                            onClaimSecondChatInactivity()
                        },
                    ) {
                        Text("Enviar reclamo")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !loadingChatAction,
                        onClick = { showingSecondChatInactivityDialog = false },
                    ) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

@Composable
private fun LoadingChatScreen(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchingDotsIndicator()
        Text(
            text = title,
            modifier = Modifier.padding(top = 28.dp),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SecondChatLifecyclePanel(
    lifecycle: SecondChatLifecycleUiState?,
    partnerName: String?,
    actionLoading: Boolean,
    onClaimNoShow: () -> Unit,
    onRefresh: () -> Unit,
) {
    val status = lifecycle?.status ?: return
    val safePartnerName = partnerName?.takeIf { it.isNotBlank() } ?: "la otra persona"
    var nowMillis by rememberSaveable(status.serverTime, status.activeNoShowClaim?.expiresAt) {
        mutableStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(status.serverTime, status.activeNoShowClaim?.expiresAt) {
        while (status.hasPendingNoShowClaim()) {
            delay(1_000.milliseconds)
            nowMillis = System.currentTimeMillis()
            val expiresAt = status.activeNoShowClaim?.expiresAt ?: break
            if (
                lifecycle.statusReceivedAtMillis?.let { receivedAtMillis ->
                    status.remainingMillisFromServerSnapshot(
                        targetTime = expiresAt,
                        statusReceivedAtMillis = receivedAtMillis,
                        nowMillis = nowMillis,
                    )
                }?.let { it <= 0 } == true
            ) {
                onRefresh()
                break
            }
        }
    }

    when {
        status.chatStatus in listOf(ChatStatus.Finished, ChatStatus.Abandoned, ChatStatus.Expired) -> {
            FeedbackCard(
                title = "Cita finalizada",
                message = status.endedReason.secondChatResultCopy(),
                tone = FeedbackTone.Info,
            )
        }
        status.myAttendanceStatus == SecondChatAttendanceStatus.Pending && !status.canJoin -> {
            FeedbackCard(
                title = "Todavía no está disponible",
                message = "El segundo chat abre a las ${formatBackendTime(status.scheduledAt)}.",
                tone = FeedbackTone.Info,
            )
        }
        status.hasPendingNoShowClaim() -> {
            val seconds = ((
                lifecycle.statusReceivedAtMillis?.let { receivedAtMillis ->
                    status.remainingMillisFromServerSnapshot(
                        targetTime = status.activeNoShowClaim?.expiresAt.orEmpty(),
                        statusReceivedAtMillis = receivedAtMillis,
                        nowMillis = nowMillis,
                    )
                } ?: 0
                ) + 999) / 1000
            FeedbackCard(
                title = "Esperando a la otra persona",
                message = "Puede entrar durante los próximos ${seconds.coerceAtLeast(0)} segundos.",
                tone = FeedbackTone.Warning,
            )
        }
        status.isWaitingForPartner() -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Ya estás en la cita", style = MaterialTheme.typography.titleMedium)
                    Text("Estamos esperando a $safePartnerName.")
                    Text(
                        when (status.myAttendanceStatus) {
                            SecondChatAttendanceStatus.OnTime -> "Llegaste a horario"
                            SecondChatAttendanceStatus.Late -> "Llegaste tarde"
                            else -> "Tu asistencia está registrada"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Podés mandar mensajes mientras esperás. Eso no significa que la otra persona haya llegado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status.canClaimPartnerNoShow) {
                        Button(
                            onClick = onClaimNoShow,
                            enabled = !actionLoading && lifecycle.claimingNoShow.not(),
                        ) {
                            Text("La otra persona no llegó")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondChatResolutionPanel(
    presentation: SecondChatResolutionPresentation?,
    actionLoading: Boolean,
    actionLoadingLabel: String?,
    onRequestCompletion: () -> Unit,
    onAcceptCompletion: (String) -> Unit,
    onRejectCompletion: (String) -> Unit,
    onRequestInactivityClaim: () -> Unit,
) {
    val state = presentation ?: return
    if (
        state.createCompletion == null &&
        state.completionCooldown == null &&
        state.createInactivityClaim == null &&
        state.activeRequest == null
    ) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.activeRequest?.let { request ->
                Text(request.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = request.message,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                request.remainingMillis?.let { remainingMillis ->
                    Text(
                        text = if (request.locallyExpired) {
                            "La solicitud venci\u00f3. Actualizando estado..."
                        } else {
                            "Quedan ${((remainingMillis + 999) / 1000).coerceAtLeast(0)}s."
                        },
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (request.type == SecondChatResolutionRequestType.MutualCompletion) {
                    Text(
                        text = "Pueden seguir conversando; un nuevo mensaje cancela esta solicitud.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (request.showAcceptRejectControls) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onAcceptCompletion(request.requestId) },
                            enabled = request.controlsEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (actionLoading) actionLoadingLabel
                                    ?: "Procesando..." else "Finalizar el chat"
                            )
                        }
                        OutlinedButton(
                            onClick = { onRejectCompletion(request.requestId) },
                            enabled = request.controlsEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (actionLoading) actionLoadingLabel
                                    ?: "Procesando..." else "Seguir conversando"
                            )
                        }
                    }
                }
            }

            state.completionCooldown?.let { cooldown ->
                Text(
                    text = cooldown.message,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            state.createCompletion?.let { create ->
                OutlinedButton(
                    onClick = onRequestCompletion,
                    enabled = create.enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(create.label)
                }
            }

            state.createInactivityClaim?.let { create ->
                OutlinedButton(
                    onClick = onRequestInactivityClaim,
                    enabled = create.enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(create.label)
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    titlePrefix: String,
    partnerName: String?,
    expiresAt: String?,
    firstChatLifecycle: FirstChatLifecycleUiState?,
    secondChatReadOnlyUntil: String?,
    secondChatUnavailable: Boolean,
    myDecision: ChatDecisionState?,
    partnerDecision: ChatDecisionState?,
    showDecisionSummary: Boolean,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = partnerName?.let { "$titlePrefix con ${TextSafety.safeDisplay(it)}" }
                        ?: "Cargando $titlePrefix",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                trailingContent?.invoke()
            }
            chatHeaderStatusText(
                expiresAt = expiresAt,
                firstChatLifecycle = firstChatLifecycle,
                secondChatReadOnlyUntil = secondChatReadOnlyUntil,
                secondChatUnavailable = secondChatUnavailable,
            )?.let { statusText ->
                Text(
                    text = statusText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showDecisionSummary) chatDecisionSummary(
                myDecision,
                partnerDecision,
                partnerName
            )?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ChatOverflowMenu(
    expanded: Boolean,
    enabled: Boolean,
    actionLoading: Boolean,
    canUseExistingChatActions: Boolean,
    canDecide: Boolean,
    canManualBlock: Boolean,
    showMutualExitActions: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRequestMutualExit: () -> Unit,
    onRejectChat: () -> Unit,
    onShowSafety: () -> Unit,
    onShowManualBlock: () -> Unit,
) {
    Box {
        IconButton(
            onClick = { onExpandedChange(true) },
            enabled = enabled,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "Más acciones",
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                if (!actionLoading) {
                    onExpandedChange(false)
                }
            },
        ) {
            if (showMutualExitActions) {
                DropdownMenuItem(
                    text = { Text("Salida consensuada") },
                    enabled = !actionLoading && canUseExistingChatActions,
                    onClick = onRequestMutualExit,
                )

                DropdownMenuItem(
                    text = { Text("Rechazar chat") },
                    enabled = !actionLoading && canDecide,
                    onClick = onRejectChat,
                )
            }

            DropdownMenuItem(
                text = { Text("Reportar y cerrar chat") },
                enabled = !actionLoading && canUseExistingChatActions,
                onClick = onShowSafety,
            )

            DropdownMenuItem(
                text = { Text("Bloquear a ésta persona") },
                enabled = canManualBlock,
                onClick = onShowManualBlock,
            )
        }
    }
}

@Composable
private fun MessageList(
    currentUserId: String,
    messages: List<ChatMessage>,
    optimisticMessages: List<OptimisticOutgoingMessage>,
    bottomContentPadding: Dp,
    modifier: Modifier,
    onRetryOptimisticMessage: (localId: String, content: String) -> Unit,
    playbackController: ChatAudioPlaybackController,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onRefreshAudioUrl: suspend (messageId: String) -> String?,
) {
    val sortedMessages = messages.sortedWith(compareBy<ChatMessage> { it.sentAt }.thenBy { it.id })
    val messageItems = sortedMessages.map { ChatMessageListItem.Backend(it) } +
            optimisticMessages.sortedBy { it.createdAtMillis }
                .map { ChatMessageListItem.Optimistic(it) }
    val listState = rememberLazyListState()
    val latestMessage = messageItems.lastOrNull()
    val latestMessageId = latestMessage?.stableId
    val latestMessageIsMine = latestMessage?.isMine(currentUserId) == true

    LaunchedEffect(latestMessageId) {
        if (latestMessageId == null) return@LaunchedEffect

        val shouldScrollToBottom = latestMessageIsMine || listState.isNearBottom()
        if (shouldScrollToBottom) {
            listState.animateScrollToItem(messageItems.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messageItems.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay mensajes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(messageItems, key = { it.stableId }) { item ->
                    when (item) {
                        is ChatMessageListItem.Backend -> MessageBubble(
                            message = item.message,
                            mine = item.message.senderId == currentUserId,
                            playbackState = playbackController.state,
                            onPlayAudio = { message ->
                                val audio = message.audio ?: return@MessageBubble
                                val url = audio.url ?: return@MessageBubble
                                playbackController.playRemote(
                                    messageId = message.id,
                                    url = url,
                                    durationMillis = audio.durationMillis ?: 0L,
                                    scope = coroutineScope,
                                    refreshUrl = { onRefreshAudioUrl(message.id) },
                                )
                            },
                            onPauseAudio = playbackController::pause,
                        )

                        is ChatMessageListItem.Optimistic -> OptimisticMessageBubble(
                            message = item.message,
                            onRetry = onRetryOptimisticMessage,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListState.isNearBottom(bufferItems: Int = 2): Boolean {
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return true

    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisibleIndex >= totalItems - 1 - bufferItems
}

internal data class FirstChatGuidancePanelState(
    val questionText: String,
    val showButton: Boolean,
    val buttonEnabled: Boolean,
    val showWaitingCopy: Boolean,
)

internal fun firstChatGuidancePanelState(
    guidance: FirstChatGuidance?,
    canRequestNextWhileChatOpen: Boolean = true,
): FirstChatGuidancePanelState? {
    if (guidance == null) return null
    val finalQuestion = guidance.questionOrdinal >= guidance.maxQuestions
    return FirstChatGuidancePanelState(
        questionText = guidance.question.text,
        showButton = !guidance.completed && !finalQuestion && !guidance.myNextRequested,
        buttonEnabled = !guidance.completed &&
                !finalQuestion &&
                !guidance.myNextRequested &&
                guidance.canRequestNext &&
                canRequestNextWhileChatOpen,
        showWaitingCopy = !guidance.completed && guidance.myNextRequested,
    )
}

@Composable
private fun FirstChatGuidancePanel(
    state: FirstChatGuidancePanelState?,
    actionLoading: Boolean,
    onRequestNext: (() -> Unit)?,
) {
    if (state == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = TextSafety.safeDisplay(state.questionText),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (state.showButton) {
                OutlinedButton(
                    onClick = { onRequestNext?.invoke() },
                    enabled = state.buttonEnabled &&
                            !actionLoading &&
                            onRequestNext != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Otra pregunta")
                }
            }

            if (state.showWaitingCopy) {
                Text(
                    text = "Cambiaremos la pregunta cuando ambos quieran seguir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private sealed interface ChatMessageListItem {
    val stableId: String

    fun isMine(currentUserId: String): Boolean

    data class Backend(val message: ChatMessage) : ChatMessageListItem {
        override val stableId: String = "backend-${message.id}"

        override fun isMine(currentUserId: String): Boolean = message.senderId == currentUserId
    }

    data class Optimistic(val message: OptimisticOutgoingMessage) : ChatMessageListItem {
        override val stableId: String = "optimistic-${message.localId}"

        override fun isMine(currentUserId: String): Boolean = message.senderId == currentUserId
    }
}

@Composable
private fun ChatActionsPanel(
    currentUserId: String,
    activeExitRequest: ChatExitRequest?,
    loadingChatAction: Boolean,
    actionLoadingLabel: String?,
    canDecide: Boolean,
    canUseNavigationActions: Boolean,
    showDecisionActions: Boolean,
    onBackHome: (() -> Unit)?,
    onApprove: () -> Unit,
    onAcceptExitRequest: (String) -> Unit,
    onRejectExitRequest: (String) -> Unit,
    onExitRequestTimeout: (String) -> Unit,
) {
    if (
        activeExitRequest == null &&
        !showDecisionActions &&
        onBackHome == null
    ) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            activeExitRequest?.let { request ->
                TimedExitRequestCard(
                    currentUserId = currentUserId,
                    request = request,
                    actionsDisabled = loadingChatAction,
                    actionLoadingLabel = actionLoadingLabel,
                    onAcceptExitRequest = onAcceptExitRequest,
                    onRejectExitRequest = onRejectExitRequest,
                    onExitRequestTimeout = onExitRequestTimeout,
                )
            }
            onBackHome?.let { back ->
                OutlinedButton(
                    onClick = back,
                    enabled = canUseNavigationActions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Volver a Home")
                }
            }
            if (showDecisionActions) {
                Button(
                    onClick = onApprove,
                    enabled = !loadingChatAction && canDecide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (loadingChatAction) actionLoadingLabel
                            ?: "Procesando..." else "Aprobar chat"
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    draft: String,
    state: MessageComposerUiState,
    audioState: ChatAudioComposerUiState,
    audioDraft: LocalChatAudioDraft?,
    uploadState: ChatAudioUploadUiState,
    localAudioError: String?,
    recordingStartedAtMillis: Long?,
    playbackState: ChatAudioPlaybackUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayDraft: (LocalChatAudioDraft) -> Unit,
    onPauseAudio: () -> Unit,
    onDeleteDraft: () -> Unit,
    onSendAudio: (LocalChatAudioDraft) -> Boolean,
) {
    var recordingNowMillis by rememberSaveable(recordingStartedAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(recordingStartedAtMillis) {
        while (recordingStartedAtMillis != null) {
            delay(250.milliseconds)
            recordingNowMillis = System.currentTimeMillis()
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            localAudioError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            uploadState.error?.let {
                ApiErrorFeedbackCard(it, ErrorContext.Chat)
            }
            state.explanatoryCopy?.let { copy ->
                Text(
                    text = copy,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recordingStartedAtMillis != null) {
                val elapsedMillis = (recordingNowMillis - recordingStartedAtMillis).coerceAtLeast(0L)
                RecordingComposer(
                    elapsedMillis = elapsedMillis,
                    maxDurationMillis = audioState.maxDurationMillis,
                    onStop = onStopRecording,
                    onCancel = onCancelRecording,
                )
            } else if (audioDraft != null) {
                AudioDraftComposer(
                    draft = audioDraft,
                    uploadState = uploadState,
                    playbackState = playbackState,
                    onPlay = { onPlayDraft(audioDraft) },
                    onPause = onPauseAudio,
                    onDelete = onDeleteDraft,
                    onSend = { onSendAudio(audioDraft) },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        placeholder = { Text("Mensaje") },
                        enabled = state.canEditDraft,
                        minLines = 1,
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )

                    FilledIconButton(
                        onClick = onSend,
                        enabled = state.sendButtonEnabled,
                    ) {
                        if (state.sendingMessage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_send),
                                contentDescription = "Enviar",
                            )
                        }
                    }

                    if (audioState.visible) {
                        FilledIconButton(
                            onClick = onStartRecording,
                            enabled = audioState.startEnabled,
                        ) {
                            Text("Mic")
                        }
                    }
                }

                if (audioState.visible && !audioState.startEnabled && audioState.disabledCopy != null) {
                    Text(
                        text = audioState.disabledCopy,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingComposer(
    elapsedMillis: Long,
    maxDurationMillis: Long,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Grabando ${formatAudioDuration(elapsedMillis)} / ${formatAudioDuration(maxDurationMillis)}")
        LinearProgressIndicator(
            progress = { (elapsedMillis.toFloat() / maxDurationMillis.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                Text("Detener")
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun AudioDraftComposer(
    draft: LocalChatAudioDraft,
    uploadState: ChatAudioUploadUiState,
    playbackState: ChatAudioPlaybackUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Audio listo para enviar")
        AudioPlaybackRow(
            key = "draft-${draft.clientMessageId}",
            durationMillis = draft.durationMillis,
            playbackState = playbackState,
            onPlay = onPlay,
            onPause = onPause,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onDelete,
                enabled = !uploadState.uploading,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (uploadState.nonRetryable) "Borrar" else "Cancelar")
            }
            Button(
                onClick = { onSend() },
                enabled = !uploadState.uploading && !uploadState.nonRetryable,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (uploadState.uploading) {
                        "Enviando..."
                    } else if (uploadState.error != null) {
                        "Reintentar"
                    } else {
                        "Enviar"
                    }
                )
            }
        }
    }
}

@Composable
private fun TimedExitRequestCard(
    currentUserId: String,
    request: ChatExitRequest,
    actionsDisabled: Boolean,
    actionLoadingLabel: String?,
    onAcceptExitRequest: (String) -> Unit,
    onRejectExitRequest: (String) -> Unit,
    onExitRequestTimeout: (String) -> Unit,
) {
    var nowMillis by rememberSaveable(request.id) { mutableStateOf(System.currentTimeMillis()) }
    val remainingSeconds = remainingExitSeconds(
        createdAt = request.createdAt,
        nowMillis = nowMillis,
        timeoutSeconds = MUTUAL_EXIT_TIMEOUT_SECONDS,
    )
    val requestedByMe = request.requesterUserId == currentUserId

    LaunchedEffect(request.id) {
        while (true) {
            delay(1_000.milliseconds)
            nowMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(request.id, remainingSeconds, actionsDisabled) {
        if (shouldRequestExitTimeout(remainingSeconds, actionsDisabled)) {
            delay(MUTUAL_EXIT_TIMEOUT_RETRY_MILLIS.milliseconds)
            onExitRequestTimeout(request.id)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Salida consensuada pendiente", style = MaterialTheme.typography.titleMedium)
            Text(
                text = timedExitRequestBodyText(
                    requestedByMe = requestedByMe,
                    remainingSeconds = remainingSeconds,
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = MUTUAL_EXIT_CONVERSATION_PAUSED_COPY,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            if (showExitRequestResponseActions(requestedByMe, remainingSeconds)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onAcceptExitRequest(request.id) },
                        enabled = exitRequestActionsEnabled(actionsDisabled),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (actionsDisabled) actionLoadingLabel
                                ?: "Procesando..." else "Aceptar"
                        )
                    }
                    OutlinedButton(
                        onClick = { onRejectExitRequest(request.id) },
                        enabled = exitRequestActionsEnabled(actionsDisabled),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (actionsDisabled) actionLoadingLabel
                                ?: "Procesando..." else "Rechazar"
                        )
                    }
                }
            }
        }
    }
}

internal fun shouldRequestExitTimeout(
    remainingSeconds: Long,
    actionsDisabled: Boolean,
): Boolean =
    remainingSeconds == 0L && !actionsDisabled

internal fun showExitRequestResponseActions(
    requestedByMe: Boolean,
    remainingSeconds: Long,
): Boolean = !requestedByMe && remainingSeconds > 0L

internal fun exitRequestActionsEnabled(actionsDisabled: Boolean): Boolean = !actionsDisabled

internal fun timedExitRequestBodyText(
    requestedByMe: Boolean,
    remainingSeconds: Long,
): String =
    when {
        remainingSeconds == 0L -> "La solicitud venció. Estamos cerrando el chat."
        requestedByMe -> "Esperando respuesta. Si no contesta, el chat se cierra en ${remainingSeconds}s."
        else -> "Te propusieron cerrar el chat. Respondé en ${remainingSeconds}s."
    }

@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    playbackState: ChatAudioPlaybackUiState,
    onPlayAudio: (ChatMessage) -> Unit,
    onPauseAudio: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                when (val presentation = message.presentation) {
                    is ChatMessagePresentation.Text -> Text(TextSafety.safeDisplay(presentation.content))
                    is ChatMessagePresentation.Audio -> AudioPlaybackRow(
                        key = message.id,
                        durationMillis = presentation.audio.durationMillis ?: 0L,
                        playbackState = playbackState,
                        onPlay = { onPlayAudio(message) },
                        onPause = onPauseAudio,
                    )

                    ChatMessagePresentation.Unsupported -> Text("Mensaje no compatible")
                }
                Text(
                    text = formatBackendTime(message.sentAt),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudioPlaybackRow(
    key: String,
    durationMillis: Long,
    playbackState: ChatAudioPlaybackUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
) {
    val active = playbackState.key == key
    val phase = if (active) playbackState.phase else ChatAudioPlaybackPhase.Idle
    val positionMillis = if (active) playbackState.positionMillis.toLong() else 0L
    val progress = if (durationMillis > 0) {
        (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    if (phase == ChatAudioPlaybackPhase.Playing || phase == ChatAudioPlaybackPhase.Preparing) {
                        onPause()
                    } else {
                        onPlay()
                    }
                },
                enabled = phase != ChatAudioPlaybackPhase.Preparing,
            ) {
                Text(
                    when (phase) {
                        ChatAudioPlaybackPhase.Playing -> "Pausar"
                        ChatAudioPlaybackPhase.Preparing -> "Cargando..."
                        ChatAudioPlaybackPhase.Failed -> "Reintentar"
                        ChatAudioPlaybackPhase.Idle,
                        ChatAudioPlaybackPhase.Paused -> "Reproducir"
                    }
                )
            }
            Text("${formatAudioDuration(positionMillis.takeIf { active } ?: 0L)} / ${formatAudioDuration(durationMillis)}")
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        if (active && phase == ChatAudioPlaybackPhase.Failed) {
            Text(
                playbackState.error ?: "No pudimos reproducir este audio.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatAudioDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun OptimisticMessageBubble(
    message: OptimisticOutgoingMessage,
    onRetry: (localId: String, content: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 4.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(TextSafety.safeDisplay(message.content))
                Text(
                    text = when (message.deliveryState) {
                        OutgoingMessageDeliveryState.Sending -> "Enviando..."
                        OutgoingMessageDeliveryState.Failed -> "No se pudo enviar"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.deliveryState == OutgoingMessageDeliveryState.Failed) {
                    TextButton(
                        onClick = { onRetry(message.localId, message.content) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyReportDialog(
    details: String,
    selectedReason: ChatExitReason,
    actionLoading: Boolean,
    onDetailsChange: (String) -> Unit,
    onReasonChange: (ChatExitReason) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var reasonMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedOption = safetyReportReasonOptions.first { it.reason == selectedReason }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar y cerrar chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Describí que pasó. Este reporte cerrará el chat por seguridad y será revisado.")
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { reasonMenuExpanded = true },
                        enabled = !actionLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(selectedOption.label)
                    }
                    DropdownMenu(
                        expanded = reasonMenuExpanded,
                        onDismissRequest = { reasonMenuExpanded = false },
                    ) {
                        safetyReportReasonOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onReasonChange(option.reason)
                                    reasonMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                selectedOption.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = onDetailsChange,
                    label = { Text("Detalle") },
                    enabled = !actionLoading,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !actionLoading && details.isNotBlank(),
                onClick = onConfirm,
            ) {
                Text("Enviar reporte")
            }
        },
        dismissButton = {
            TextButton(enabled = !actionLoading, onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun SuccessFeedback(message: String, modifier: Modifier = Modifier) {
    FeedbackCard(
        title = "Listo",
        message = message,
        tone = FeedbackTone.Success,
        modifier = modifier
    )
}

internal fun firstChatHeaderDeadlineLabel(
    expiresAt: String?,
    firstChatLifecycle: FirstChatLifecycleUiState?,
): String? = if (firstChatLifecycle == null) {
    expiresAt?.takeIf { it.isNotBlank() }
} else {
    null
}

internal fun chatHeaderStatusText(
    expiresAt: String?,
    firstChatLifecycle: FirstChatLifecycleUiState?,
    secondChatReadOnlyUntil: String?,
    secondChatUnavailable: Boolean,
    formatDateTime: (String?) -> String = ::formatBackendDateTime,
): String? = when {
    secondChatReadOnlyUntil != null ->
        "Este segundo chat venci\u00f3. Pod\u00e9s leerlo hasta ${formatDateTime(secondChatReadOnlyUntil)}."

    secondChatUnavailable -> "Este segundo chat ya no est\u00e1 disponible."
    else -> firstChatHeaderDeadlineLabel(expiresAt, firstChatLifecycle)
        ?.let { "V\u00e1lido hasta ${formatDateTime(it)}" }
}

internal data class MessageComposerUiState(
    val canSendMessages: Boolean,
    val canEditDraft: Boolean,
    val sendButtonEnabled: Boolean,
    val sendingMessage: Boolean,
    val explanatoryCopy: String?,
)

internal fun messageComposerUiState(
    canChat: Boolean,
    canSendMessages: Boolean,
    sendingMessage: Boolean,
    loadingChatAction: Boolean,
    draft: String,
): MessageComposerUiState {
    val canEditDraft = canSendMessages && !loadingChatAction
    return MessageComposerUiState(
        canSendMessages = canSendMessages,
        canEditDraft = canEditDraft,
        sendButtonEnabled = canSendMessages &&
                !sendingMessage &&
                !loadingChatAction &&
                draft.isNotBlank(),
        sendingMessage = sendingMessage,
        explanatoryCopy = when {
            !canChat -> "Este chat no est\u00e1 disponible para enviar mensajes."
            !canSendMessages -> MUTUAL_EXIT_CONVERSATION_PAUSED_COPY
            else -> null
        },
    )
}

internal data class ChatAudioComposerUiState(
    val visible: Boolean,
    val startEnabled: Boolean,
    val disabledCopy: String?,
    val maxDurationMillis: Long,
)

internal fun chatAudioComposerUiState(
    chat: Chat?,
    canSendMessages: Boolean,
    sendingMessage: Boolean,
    audioUploading: Boolean,
    recordingActive: Boolean,
    loadingChatAction: Boolean,
): ChatAudioComposerUiState {
    val policy = chat?.audioPolicy
    val visible = policy != null &&
        policy.unavailableReason != ChatAudioUnavailableReason.FeatureDisabled
    val startEnabled = visible &&
        canSendMessages &&
        policy?.enabled == true &&
        !sendingMessage &&
        !audioUploading &&
        !recordingActive &&
        !loadingChatAction
    return ChatAudioComposerUiState(
        visible = visible,
        startEnabled = startEnabled,
        disabledCopy = if (visible && !startEnabled) {
            audioUnavailableCopy(policy?.unavailableReason)
        } else {
            null
        },
        maxDurationMillis = policy?.maxDurationMillis ?: DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS,
    )
}

private fun audioUnavailableCopy(reason: ChatAudioUnavailableReason?): String = when (reason) {
    ChatAudioUnavailableReason.GuidanceRequired ->
        "Respondan la pregunta actual para habilitar audios."
    ChatAudioUnavailableReason.GuidanceNotAvailable ->
        "Los audios se habilitarán al avanzar en las preguntas."
    ChatAudioUnavailableReason.LimitReached ->
        "Ya enviaste el audio disponible en este chat."
    ChatAudioUnavailableReason.WaitingForBoth ->
        "El audio se habilita cuando ambas personas hayan ingresado."
    ChatAudioUnavailableReason.WaitingDelay ->
        "El audio todavía no está disponible."
    ChatAudioUnavailableReason.ChatNotWritable ->
        "Este chat no admite nuevos mensajes."
    ChatAudioUnavailableReason.FeatureDisabled ->
        "Los audios no están disponibles."
    is ChatAudioUnavailableReason.Unknown,
    null -> "El audio no está disponible en este momento."
}

internal fun chatPollingEnabled(canChat: Boolean): Boolean = canChat

internal fun shouldDispatchSecondChatLocalAbsoluteExpiry(secondChatLocallyExpired: Boolean): Boolean =
    secondChatLocallyExpired

private fun chatDecisionSummary(
    myDecision: ChatDecisionState?,
    partnerDecision: ChatDecisionState?,
    partnerName: String?,
): String? {
    if (myDecision == null || partnerDecision == null) return null
    val partnerLabel = partnerName?.takeIf { it.isNotBlank() } ?: "La otra persona"

    return when {
        myDecision == ChatDecisionState.Approved && partnerDecision == ChatDecisionState.Pending ->
            "Aprobaste el chat. Esperando decisión de $partnerLabel."

        myDecision == ChatDecisionState.Pending && partnerDecision == ChatDecisionState.Approved ->
            "$partnerLabel aprobó el chat. Falta tu decisión."

        myDecision == ChatDecisionState.Approved && partnerDecision == ChatDecisionState.Approved ->
            "Ambas personas aprobaron. Pasando a revisión visual."

        myDecision == ChatDecisionState.Rejected || partnerDecision == ChatDecisionState.Rejected ->
            "El chat fue rechazado."

        myDecision == ChatDecisionState.Abandoned || partnerDecision == ChatDecisionState.Abandoned ->
            "El chat fue abandonado."

        else -> null
    }
}
