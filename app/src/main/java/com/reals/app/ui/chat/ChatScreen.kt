package com.reals.app.ui.chat

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.core.time.remainingExitSeconds
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.ManualBlockConfirmationDialog
import com.reals.app.ui.common.SearchingDotsIndicator
import com.reals.app.ui.common.formatBackendDateTime
import com.reals.app.ui.common.formatBackendTime
import com.reals.app.ui.root.OptimisticOutgoingMessage
import com.reals.app.ui.root.OutgoingMessageDeliveryState
import kotlinx.coroutines.delay
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
    val title = chatTitlePrefix.trim().ifBlank { "Chat" }
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
    actionLoading: Boolean,
    actionLoadingLabel: String?,
    guidance: FirstChatGuidance? = null,
    guidanceActionLoading: Boolean = false,
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
    onRequestNextGuidanceQuestion: (() -> Unit)? = null,
    onSendMessage: (String) -> Boolean,
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
    var nowMillis by rememberSaveable(chat?.id) { mutableStateOf(System.currentTimeMillis()) }
    var firstChatExpiryHandled by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var secondChatUnavailableHandled by rememberSaveable(chat?.id) { mutableStateOf(false) }
    val firstChatLifecycle = firstChatLifecycleUiState(chat, nowMillis)
    val firstChatLocallyExpired = firstChatLifecycle?.expired == true
    val readOnlyUntilInstant = backendInstantOrNull(chat?.readOnlyUntil)
    val secondChatReadOnlyFuture = chat?.chatType == ChatType.SecondChat &&
            chat.status == ChatStatus.Expired &&
            readOnlyUntilInstant != null &&
            java.time.Instant.ofEpochMilli(nowMillis).isBefore(readOnlyUntilInstant)
    val secondChatUnavailable = chat?.chatType == ChatType.SecondChat &&
            (
                    chat.status in listOf(
                        ChatStatus.Closed,
                        ChatStatus.Cancelled,
                        ChatStatus.Abandoned,
                        ChatStatus.Finished,
                    ) ||
                            (chat.status == ChatStatus.Expired && !secondChatReadOnlyFuture)
                    )
    val canChat = !firstChatLocallyExpired &&
            !secondChatReadOnlyFuture &&
            !secondChatUnavailable &&
            (chat?.status == ChatStatus.Active ||
                    (allowAvailableChat && chat?.status == ChatStatus.Available)
                    )
    val sendingMessage = sending
    val loadingChatAction = actionLoading || manualBlockLoading
    val canUseChatActions = canChat && !loadingChatAction
    val canUseNavigationActions = !loadingChatAction
    val pendingExitRequest = exitRequests
        .filter { it.status == ChatExitRequestStatus.Pending }
        .maxByOrNull { it.createdAt }
    val exitFlowLocked = pendingExitRequest != null
    val canSendMessages = canChat && !exitFlowLocked
    val guidancePanelState = firstChatGuidancePanelState(
        guidance = guidance,
        canRequestNextWhileChatOpen = canSendMessages,
    )
    val canUseExistingChatActions =
        canUseChatActions && (!showMutualExitActions || !exitFlowLocked)
    val manualBlockBusy =
        loading || refreshing || sending || actionLoading || guidanceActionLoading ||
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
        loadingChatAction = loadingChatAction,
        draft = draft,
    )

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
        while (firstChatLifecycleUiState(chat)?.expired == false) {
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
                )
            }
        }
    }

    if (showingSafetyDialog && showExitActions) {
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
                text = { Text("Bloquear a esta persona") },
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
) {
    val sortedMessages = messages.sortedBy { it.sentAt }
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
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
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
            state.explanatoryCopy?.let { copy ->
                Text(
                    text = copy,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                Text(TextSafety.safeDisplay(message.content))
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

internal fun chatPollingEnabled(canChat: Boolean): Boolean = canChat

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
