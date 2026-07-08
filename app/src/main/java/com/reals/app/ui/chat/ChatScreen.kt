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
import com.reals.app.ui.common.SearchingDotsIndicator
import com.reals.app.ui.common.formatBackendDateTime
import com.reals.app.ui.common.formatBackendTime
import com.reals.app.ui.root.OptimisticOutgoingMessage
import com.reals.app.ui.root.OutgoingMessageDeliveryState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val MUTUAL_EXIT_TIMEOUT_SECONDS = 20L

@Composable
fun ChatScreen(
    currentUserId: String,
    match: Match?,
    chat: Chat?,
    messages: List<ChatMessage>,
    optimisticMessages: List<OptimisticOutgoingMessage>,
    exitRequests: List<ChatExitRequest>,
    loading: Boolean,
    sending: Boolean,
    actionLoading: Boolean,
    actionLoadingLabel: String?,
    guidance: FirstChatGuidance? = null,
    guidanceActionLoading: Boolean = false,
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
    onSafetyCancel: (String) -> Unit,
    onAcceptExitRequest: (String) -> Unit,
    onRejectExitRequest: (String) -> Unit,
    onExitRequestTimeout: (String) -> Unit,
) {
    var draft by rememberSaveable(chat?.id) { mutableStateOf("") }
    var safetyDetails by rememberSaveable(chat?.id) { mutableStateOf("") }
    var showingSafetyDialog by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var actionsMenuExpanded by rememberSaveable(chat?.id) { mutableStateOf(false) }
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
    val loadingChatAction = actionLoading
    val canEditDraft = canChat && !loadingChatAction
    val canUseChatActions = canChat && !loadingChatAction
    val canUseNavigationActions = !loadingChatAction
    val guidancePanelState = firstChatGuidancePanelState(guidance)
    val pendingExitRequest = exitRequests
        .filter { it.status == ChatExitRequestStatus.Pending }
        .maxByOrNull { it.createdAt }
    val exitFlowLocked = pendingExitRequest != null
    val canOpenOverflowActions = !loadingChatAction &&
            ((!exitFlowLocked && canUseChatActions) || !showMutualExitActions)
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

    if (loading && chat == null) {
        LoadingChatScreen(
            title = "Cargando $chatTitlePrefix",
            body = "Estamos preparando la conversacion.",
        )
        return
    }

    LaunchedEffect(chat?.id, canChat) {
        while (canChat) {
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
                            canChat = canChat,
                            canDecide = canDecide,
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
                    canChat = canChat,
                    canEditDraft = canEditDraft,
                    sendingMessage = sendingMessage,
                    loadingChatAction = loadingChatAction,
                    onDraftChange = { draft = it.take(1_000) },
                    onSend = {
                        if (onSendMessage(draft)) {
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
            actionLoading = actionLoading,
            onDetailsChange = { safetyDetails = it.take(1_000) },
            onDismiss = {
                if (!actionLoading) showingSafetyDialog = false
            },
            onConfirm = {
                onSafetyCancel(safetyDetails)
                safetyDetails = ""
                showingSafetyDialog = false
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
            val deadlineLabel = firstChatLifecycle?.deadline ?: expiresAt
            Text(
                text = when {
                    secondChatReadOnlyUntil != null ->
                        "Este segundo chat venci\u00f3. Pod\u00e9s leerlo hasta ${
                            formatBackendDateTime(
                                secondChatReadOnlyUntil
                            )
                        }."

                    secondChatUnavailable -> "Este segundo chat ya no est\u00e1 disponible."
                    else -> "V\u00e1lido hasta ${formatBackendDateTime(deadlineLabel)}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    canChat: Boolean,
    canDecide: Boolean,
    showMutualExitActions: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRequestMutualExit: () -> Unit,
    onRejectChat: () -> Unit,
    onShowSafety: () -> Unit,
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
                    enabled = !actionLoading && canChat,
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
                enabled = !actionLoading && canChat,
                onClick = onShowSafety,
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
                        "Todavia no hay mensajes.",
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
): FirstChatGuidancePanelState? {
    if (guidance == null) return null
    val finalQuestion = guidance.questionOrdinal >= guidance.maxQuestions
    return FirstChatGuidancePanelState(
        questionText = guidance.question.text,
        showButton = !guidance.completed && !finalQuestion && !guidance.myNextRequested,
        buttonEnabled = !guidance.completed && !finalQuestion && !guidance.myNextRequested && guidance.canRequestNext,
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
    canChat: Boolean,
    canEditDraft: Boolean,
    sendingMessage: Boolean,
    loadingChatAction: Boolean,
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
            if (!canChat) {
                Text(
                    text = "Este chat no esta disponible para enviar mensajes.",
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
                    enabled = canEditDraft,
                    minLines = 1,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )

                FilledIconButton(
                    onClick = onSend,
                    enabled = canChat &&
                            !sendingMessage &&
                            !loadingChatAction &&
                            draft.isNotBlank(),
                ) {
                    if (sendingMessage) {
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
    var timeoutHandled by rememberSaveable(request.id) { mutableStateOf(false) }
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

    LaunchedEffect(request.id, remainingSeconds) {
        if (remainingSeconds == 0L && !timeoutHandled) {
            timeoutHandled = true
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
                text = if (requestedByMe) {
                    "Esperando respuesta. Si no contesta, el chat se cierra en ${remainingSeconds}s."
                } else {
                    "Te propusieron cerrar el chat. Responde en ${remainingSeconds}s."
                },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Podes seguir enviando mensajes mientras se resuelve.",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!requestedByMe && remainingSeconds > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onAcceptExitRequest(request.id) },
                        enabled = !actionsDisabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (actionsDisabled) actionLoadingLabel
                                ?: "Procesando..." else "Aceptar"
                        )
                    }
                    OutlinedButton(
                        onClick = { onRejectExitRequest(request.id) },
                        enabled = !actionsDisabled,
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
    actionLoading: Boolean,
    onDetailsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar y cerrar chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Describi que paso. Este reporte cerrara el chat por seguridad y sera revisado.")
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

private fun chatDecisionSummary(
    myDecision: ChatDecisionState?,
    partnerDecision: ChatDecisionState?,
    partnerName: String?,
): String? {
    if (myDecision == null || partnerDecision == null) return null
    val partnerLabel = partnerName?.takeIf { it.isNotBlank() } ?: "La otra persona"

    return when {
        myDecision == ChatDecisionState.Approved && partnerDecision == ChatDecisionState.Pending ->
            "Aprobaste el chat. Esperando decision de $partnerLabel."

        myDecision == ChatDecisionState.Pending && partnerDecision == ChatDecisionState.Approved ->
            "$partnerLabel aprobo el chat. Falta tu decision."

        myDecision == ChatDecisionState.Approved && partnerDecision == ChatDecisionState.Approved ->
            "Ambas personas aprobaron. Pasando a revision visual."

        myDecision == ChatDecisionState.Rejected || partnerDecision == ChatDecisionState.Rejected ->
            "El chat fue rechazado."

        myDecision == ChatDecisionState.Abandoned || partnerDecision == ChatDecisionState.Abandoned ->
            "El chat fue abandonado."

        else -> null
    }
}
