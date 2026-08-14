package com.reals.app.ui.chat

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.core.time.ServerClockSnapshot
import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatAudioPolicy
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.SecondChatCompletionDecision
import com.reals.app.domain.model.isFirstChatDecisionOnlyForCurrentUser
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.root.ChatAudioDraftUiState
import com.reals.app.ui.root.ChatAudioUploadUiState
import com.reals.app.ui.root.OptimisticOutgoingMessage
import com.reals.app.ui.root.SecondChatLifecycleUiState
import com.reals.app.ui.root.canReturnHomeAfterPartnerEntryCutoff
import com.reals.app.ui.root.resolutionPresentation
import com.reals.app.ui.root.timingPresentation
import java.time.Instant
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal const val FIRST_CHAT_DECISION_ONLY_COMPOSER_PAUSED_COPY =
    "El chat está pausado mientras decidís."

internal data class ChatLoadingPresentation(
    val title: String,
    val body: String,
)

internal fun chatLoadingPresentation(
    loadingTitle: String,
    partnerName: String?,
): ChatLoadingPresentation {
    val title = loadingTitle.trim().ifBlank { "Cargando chat" }
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

internal fun chatHeaderTitle(
    titlePrefix: String,
    partnerName: String?,
): String {
    val safeTitlePrefix = titlePrefix.trim().ifBlank { "Chat" }
    val safePartnerName = partnerName
        ?.takeIf { it.isNotBlank() }
        ?.let { TextSafety.safeDisplay(it) }
    return safePartnerName?.let { "$safeTitlePrefix con $it" } ?: safeTitlePrefix
}

@Composable
fun ChatScreen(
    currentUserId: String,
    match: Match?,
    chat: Chat?,
    messages: List<ChatMessage>,
    optimisticMessages: List<OptimisticOutgoingMessage>,
    exitRequests: List<ChatExitRequest>,
    serverClockSnapshot: ServerClockSnapshot? = null,
    dismissedUnansweredPeriodReference: String? = null,
    loading: Boolean,
    refreshing: Boolean,
    sending: Boolean,
    audioUpload: ChatAudioUploadUiState = ChatAudioUploadUiState(),
    audioDraft: ChatAudioDraftUiState? = null,
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
    loadingChatTitle: String = "Cargando chat",
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
    onAudioDraftReady: (ChatAudioDraftUiState) -> Unit = {},
    onAudioDraftReadyAndSend: (ChatAudioDraftUiState) -> Boolean = { false },
    onDeleteAudioDraft: () -> Unit = {},
    onRefreshAudioUrl: suspend (messageId: String) -> String? = { null },
    onRetryOptimisticMessage: (localId: String, content: String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRequestMutualExit: () -> Unit,
    onDismissFirstChatUnansweredSuggestion: (String) -> Unit = {},
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
    var mutualCompletionCoachmarkState by remember(chat?.id) {
        mutableStateOf(MutualCompletionCoachmarkState())
    }
    var showingMutualCompletionCoachmark by remember(chat?.id) { mutableStateOf(false) }
    var nowMillis by rememberSaveable(chat?.id) { mutableStateOf(System.currentTimeMillis()) }
    var elapsedRealtimeMillis by remember(chat?.id) { mutableStateOf(SystemClock.elapsedRealtime()) }
    var firstChatExpiryHandled by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var secondChatUnavailableHandled by rememberSaveable(chat?.id) { mutableStateOf(false) }

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
        Instant.ofEpochMilli(nowMillis).isBefore(readOnlyUntilInstant)
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
    val pendingExitRequest = exitRequests
        .filter { it.status == ChatExitRequestStatus.Pending }
        .maxByOrNull { it.createdAt }
    val exitFlowLocked = pendingExitRequest != null
    val firstChatPolicy = firstChatInteractionPolicy(
        chat = chat,
        canChat = canChat,
        exitFlowLocked = exitFlowLocked,
        showDecisionActions = showDecisionActions,
        matchIsChatActive = match?.state == MatchState.ChatActive,
        firstChatLocallyExpired = firstChatLocallyExpired,
        audioInteractionBusy = false,
    )
    val decisionOnlyForCurrentUser = chat?.isFirstChatDecisionOnlyForCurrentUser() == true
    val canSendMessages = firstChatPolicy.canSendMessages
    val audioPolicy = effectiveChatAudioPolicy(chat, secondChatLifecycle)
    val audioSession = rememberChatAudioSessionState(
        inputs = ChatAudioSessionInputs(
            chatId = chat?.id,
            audioPolicy = audioPolicy,
            canChat = canChat,
            canSendMessages = canSendMessages,
            sendingMessage = sendingMessage,
            messageComposerLoading = loadingChatAction,
            messageComposerPausedCopy = if (decisionOnlyForCurrentUser) {
                FIRST_CHAT_DECISION_ONLY_COMPOSER_PAUSED_COPY
            } else {
                null
            },
            audioActionLoading = loadingChatAction || guidanceActionLoading,
            textDraft = draft,
            uploadState = audioUpload,
            draft = audioDraft,
        ),
        callbacks = ChatAudioSessionExternalCallbacks(
            onTextDraftChange = { value: String ->
                draft = value
            },
            onSendText = { value: String ->
                onSendMessage(value)
            },
            onClearTextDraft = {
                draft = ""
            },
            onClearUploadState = onClearAudioUploadState,
            onDraftReady = onAudioDraftReady,
            onDraftReadyAndSend = onAudioDraftReadyAndSend,
            onDeleteDraft = onDeleteAudioDraft,
            onSendAudioMessage = onSendAudioMessage,
            onRefreshAudioUrl = onRefreshAudioUrl,
        ),
    )
    val audioInteractionBusy = audioSession.interactionBusy
    val secondChatResolution = secondChatLifecycle?.resolutionPresentation(
        currentUserId = currentUserId,
        nowMillis = nowMillis,
        actionLoading = loadingChatAction || audioInteractionBusy,
    )
    val secondChatCompletionOverflow = secondChatCompletionOverflowPresentation(secondChatResolution)
    val canReturnHomeAfterPartnerCutoff = secondChatLifecycle?.status?.canReturnHomeAfterPartnerEntryCutoff(
        statusReceivedAtMillis = secondChatLifecycle.statusReceivedAtMillis,
        nowMillis = nowMillis,
    ) == true
    val canUseChatActions = firstChatPolicy.canUseOrdinaryConversationActions &&
        !loadingChatAction &&
        !audioInteractionBusy
    val canUseNavigationActions = !loadingChatAction && !audioInteractionBusy &&
        (secondChatTiming?.genuinelyActive != true || canReturnHomeAfterPartnerCutoff)
    val showBackHomeAction = shouldShowBackHomeAction(
        hasBackHomeCallback = onBackHome != null,
        hasSecondChatLifecycle = secondChatLifecycle != null,
        genuinelyActive = secondChatTiming?.genuinelyActive == true,
        canReturnAfterPartnerCutoff = canReturnHomeAfterPartnerCutoff,
    )
    val guidancePanelState = if (firstChatPolicy.decisionOnly) {
        null
    } else {
        firstChatGuidancePanelState(
            guidance = guidance,
            canRequestNextWhileChatOpen = firstChatPolicy.canRequestGuidance && !audioInteractionBusy,
        )
    }
    val canUseExistingChatActions =
        canUseChatActions &&
            (secondChatLifecycle == null || secondChatTiming?.genuinelyActive == true) &&
            (!showMutualExitActions || !exitFlowLocked)
    val firstChatUnansweredSuggestion = firstChatUnansweredSuggestionState(
        chat = chat,
        currentUserId = currentUserId,
        confirmedMessages = messages,
        pendingExitRequest = pendingExitRequest,
        estimatedServerNowMillis = serverClockSnapshot?.estimatedServerTimeEpochMillis(elapsedRealtimeMillis),
        dismissedPeriodReference = dismissedUnansweredPeriodReference,
        mutualExitActionAvailable = showMutualExitActions && canUseExistingChatActions,
    )
    val manualBlockBusy =
        loading || refreshing || sending || actionLoading || guidanceActionLoading || manualBlockLoading
    val secondChatSafetyEligible = secondChatSafetyActionsAllowed(
        chatType = chat?.chatType,
        attendanceStatus = secondChatLifecycle?.status?.myAttendanceStatus,
    )
    val canManualBlock = !manualBlockBusy && secondChatSafetyEligible
    val canUseSafetyActions =
        firstChatPolicy.safetyAvailable &&
            !loadingChatAction &&
            !manualBlockLoading &&
            secondChatSafetyEligible
    val canDecide = firstChatInteractionPolicy(
        chat = chat,
        canChat = canChat,
        exitFlowLocked = exitFlowLocked,
        showDecisionActions = showDecisionActions,
        matchIsChatActive = match?.state == MatchState.ChatActive,
        firstChatLocallyExpired = firstChatLocallyExpired,
        audioInteractionBusy = audioInteractionBusy,
    ).canDecide
    val overflowVisibility = firstChatOverflowActionVisibility(
        showMutualExitActions = showMutualExitActions,
        showDecisionActions = showDecisionActions,
        decisionOnlyForCurrentUser = decisionOnlyForCurrentUser,
        canRequestOrdinaryExit = firstChatPolicy.canRequestOrdinaryExit,
        canDecide = canDecide,
        canUseSafetyActions = canUseSafetyActions,
        canManualBlock = canManualBlock,
    )
    val canOpenOverflowActions = chatOverflowCanOpen(
        loadingChatAction = loadingChatAction,
        canUseExistingChatActions = canUseExistingChatActions,
        canDecide = canDecide,
        canUseSafetyActions = canUseSafetyActions,
        canManualBlock = canManualBlock,
        visibility = overflowVisibility,
        secondChatCompletion = secondChatCompletionOverflow,
    )
    val partnerDisplayName = chat?.partner?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: partnerNameFallback?.takeIf { it.isNotBlank() }
    val decisionOnlyPanelState = firstChatDecisionOnlyPanelState(
        chat = chat,
        partnerName = partnerDisplayName,
    )
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val composerPresentationPolicy = firstChatComposerPresentationPolicy(
        canSendMessages = canSendMessages,
        decisionOnlyForCurrentUser = decisionOnlyForCurrentUser,
        audioInteractionBusy = audioSession.interactionBusy,
    )
    val showMessageComposer = composerPresentationPolicy.visible
    val bottomContentPadding = if (showMessageComposer) {
        bottomBarHeight.takeIf { it > 0.dp } ?: 132.dp
    } else {
        0.dp
    }

    if (loading && chat == null) {
        val loadingPresentation = chatLoadingPresentation(loadingChatTitle, partnerNameFallback)
        LoadingChatScreen(
            title = loadingPresentation.title,
            body = loadingPresentation.body,
        )
        return
    }

    val pollChat = chatPollingEnabled(firstChatPolicy.pollingEnabled && !audioInteractionBusy)
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

    LaunchedEffect(chat?.id, serverClockSnapshot, canChat) {
        while (canChat && serverClockSnapshot != null) {
            delay(1_000.milliseconds)
            elapsedRealtimeMillis = SystemClock.elapsedRealtime()
        }
        elapsedRealtimeMillis = SystemClock.elapsedRealtime()
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

    val observedMutualCompletionEligibility = secondChatLifecycle
        ?.takeIf { it.statusReceivedAtMillis != null }
        ?.let { secondChatCompletionOverflow.visible }
    LaunchedEffect(chat?.id, observedMutualCompletionEligibility) {
        val observed = observedMutualCompletionEligibility ?: return@LaunchedEffect
        val update = mutualCompletionCoachmarkState.next(observed)
        mutualCompletionCoachmarkState = update.state
        if (update.showCoachmark) {
            showingMutualCompletionCoachmark = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                showDecisionSummary = showDecisionActions && !decisionOnlyPanelState.visible,
                trailingContent = if (showExitActions) {
                    {
                        ChatOverflowMenu(
                            expanded = actionsMenuExpanded,
                            enabled = canOpenOverflowActions,
                            actionLoading = loadingChatAction,
                            secondChatCompletion = secondChatCompletionOverflow,
                            showMutualCompletionCoachmark = showingMutualCompletionCoachmark,
                            canUseExistingChatActions = canUseExistingChatActions &&
                                firstChatPolicy.canRequestOrdinaryExit,
                            canUseSafetyActions = canUseSafetyActions,
                            canDecide = canDecide,
                            canManualBlock = canManualBlock,
                            visibility = overflowVisibility,
                            onExpandedChange = {
                                if (it) showingMutualCompletionCoachmark = false
                                actionsMenuExpanded = it
                            },
                            onMutualCompletionCoachmarkDismissed = {
                                showingMutualCompletionCoachmark = false
                            },
                            onRequestMutualExit = {
                                actionsMenuExpanded = false
                                if (firstChatPolicy.canRequestOrdinaryExit) onRequestMutualExit()
                            },
                            onRequestSecondChatCompletion = {
                                handleSecondChatCompletionOverflowClick(
                                    action = secondChatCompletionOverflow,
                                    actionLoading = loadingChatAction,
                                    onCloseMenu = { actionsMenuExpanded = false },
                                    onShowConfirmation = { showingSecondChatCompletionDialog = true },
                                )
                            },
                            onRejectChat = {
                                actionsMenuExpanded = false
                                onReject()
                            },
                            onShowSafety = {
                                actionsMenuExpanded = false
                                audioSession.cleanupForSafetyAction()
                                showingSafetyDialog = true
                            },
                            onShowManualBlock = {
                                actionsMenuExpanded = false
                                onClearManualBlockError()
                                audioSession.cleanupForSafetyAction()
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
            FirstChatLifecyclePanel(firstChatLifecycle)
            FirstChatUnansweredSuggestionCard(
                state = firstChatUnansweredSuggestion,
                onRequestMutualExit = onRequestMutualExit,
                onDismiss = onDismissFirstChatUnansweredSuggestion,
            )
            SecondChatAbsoluteExpiryWarning(
                visible = secondChatTiming?.showAbsoluteExpiryWarning == true,
            )
            SecondChatLifecyclePanel(
                lifecycle = secondChatLifecycle,
                partnerName = partnerDisplayName,
                actionLoading = loadingChatAction || audioInteractionBusy,
                partnerEntryCutoffReached = canReturnHomeAfterPartnerCutoff,
                onClaimNoShow = onClaimSecondChatNoShow,
                onRefresh = onRefresh,
            )
            SecondChatResolutionPanel(
                presentation = secondChatResolution,
                actionLoading = loadingChatAction || audioInteractionBusy,
                actionLoadingLabel = actionLoadingLabel,
                onAcceptCompletion = { requestId ->
                    onDecideSecondChatCompletion(requestId, SecondChatCompletionDecision.Accepted)
                },
                onRejectCompletion = { requestId ->
                    onDecideSecondChatCompletion(requestId, SecondChatCompletionDecision.Rejected)
                },
                onRequestInactivityClaim = { showingSecondChatInactivityDialog = true },
            )
            FirstChatDecisionOnlyPanel(
                state = decisionOnlyPanelState,
                actionLoading = loadingChatAction || audioInteractionBusy,
                canDecide = canDecide,
                actionLoadingLabel = actionLoadingLabel,
                onApprove = onApprove,
                onReject = onReject,
            )
            ChatActionsPanel(
                currentUserId = currentUserId,
                activeExitRequest = if (showExitActions) pendingExitRequest else null,
                loadingChatAction = loadingChatAction || audioInteractionBusy,
                actionLoadingLabel = actionLoadingLabel,
                canDecide = canDecide,
                canUseNavigationActions = canUseNavigationActions,
                showDecisionActions = showDecisionActions && !decisionOnlyPanelState.visible,
                onBackHome = onBackHome.takeIf { showBackHomeAction },
                onApprove = onApprove,
                onAcceptExitRequest = onAcceptExitRequest,
                onRejectExitRequest = onRejectExitRequest,
                onExitRequestTimeout = onExitRequestTimeout,
            )
            FirstChatGuidancePanel(
                state = guidancePanelState,
                actionLoading = guidanceActionLoading || audioInteractionBusy,
                onRequestNext = onRequestNextGuidanceQuestion,
            )
            MessageList(
                currentUserId = currentUserId,
                chatType = chat?.chatType ?: ChatType.Unknown(""),
                messages = messages,
                optimisticMessages = optimisticMessages,
                bottomContentPadding = bottomContentPadding + 8.dp,
                modifier = Modifier.weight(1f),
                onRetryOptimisticMessage = onRetryOptimisticMessage,
                canRetryFailedTextMessages = firstChatPolicy.canRetryFailedTextMessages,
                playbackState = audioSession.playbackState,
                onPlayAudio = audioSession::playRemoteMessage,
                onPauseAudio = audioSession::pauseAudio,
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
                if (showMessageComposer) {
                    MessageComposer(
                        presentation = audioSession.composerPresentation(),
                        callbacks = audioSession.composerCallbacks(),
                    )
                }
            }
        }
    }

    ChatSafetyDialogs(
        showingSafetyDialog = showingSafetyDialog,
        showingManualBlockDialog = showingManualBlockDialog,
        showExitActions = showExitActions,
        canUseSafetyActions = canUseSafetyActions,
        safetyDetails = safetyDetails,
        selectedSafetyReason = safetyReportReasonFromRawValue(safetyReasonRawValue),
        actionLoading = actionLoading,
        manualBlockLoading = manualBlockLoading,
        manualBlockError = manualBlockError,
        onSafetyDetailsChange = { safetyDetails = it.take(1_000) },
        onSafetyReasonChange = { safetyReasonRawValue = it.rawValue },
        onDismissSafetyDialog = {
            if (!actionLoading) showingSafetyDialog = false
        },
        onConfirmSafetyReport = {
            audioSession.cleanupForSafetyAction()
            onSafetyCancel(
                safetyReportReasonFromRawValue(safetyReasonRawValue),
                safetyDetails,
            )
            safetyDetails = ""
            safetyReasonRawValue = ChatExitReason.InappropriateBehavior.rawValue
            showingSafetyDialog = false
        },
        onDismissManualBlockDialog = {
            if (!manualBlockLoading) {
                onClearManualBlockError()
                showingManualBlockDialog = false
            }
        },
        onConfirmManualBlock = {
            audioSession.cleanupForSafetyAction()
            onManualBlock()
        },
    )

    SecondChatDialogs(
        resolution = secondChatResolution,
        showingCompletionDialog = showingSecondChatCompletionDialog,
        showingInactivityDialog = showingSecondChatInactivityDialog,
        loadingChatAction = loadingChatAction,
        onDismissCompletionDialog = { showingSecondChatCompletionDialog = false },
        onDismissInactivityDialog = { showingSecondChatInactivityDialog = false },
        onRequestSecondChatCompletion = onRequestSecondChatCompletion,
        onClaimSecondChatInactivity = onClaimSecondChatInactivity,
    )
}

internal fun chatPollingEnabled(canChat: Boolean): Boolean = canChat

internal fun shouldDispatchSecondChatLocalAbsoluteExpiry(secondChatLocallyExpired: Boolean): Boolean =
    secondChatLocallyExpired

internal fun effectiveChatAudioPolicy(
    chat: Chat?,
    secondChatLifecycle: SecondChatLifecycleUiState?,
): ChatAudioPolicy? =
    secondChatLifecycle?.status?.audioPolicy ?: chat?.audioPolicy
