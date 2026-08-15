package com.reals.app.ui.chat

import android.util.Patterns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import com.reals.app.R
import com.reals.app.core.security.TextSafety
import com.reals.app.core.time.remainingExitSeconds
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessagePresentation
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.domain.model.ChatType
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.SearchingDotsIndicator
import com.reals.app.ui.common.formatBackendDateTime
import com.reals.app.ui.common.formatBackendTime
import com.reals.app.ui.root.OptimisticOutgoingMessage
import com.reals.app.ui.root.OptimisticOutgoingMessageType
import com.reals.app.ui.root.OutgoingMessageDeliveryState
import com.reals.app.ui.root.reactableIncomingMessageIds
import com.reals.app.ui.theme.LocalRealsDarkTheme
import com.reals.app.ui.theme.RealsColors
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

internal const val MUTUAL_EXIT_CONVERSATION_PAUSED_COPY =
    "La conversación está pausada mientras se resuelve la solicitud."

private const val MUTUAL_EXIT_TIMEOUT_SECONDS = 20L
private const val MUTUAL_EXIT_TIMEOUT_RETRY_MILLIS = 2_000L
private val ChatBubbleOppositeGutter = 28.dp
private val ChatBubbleMaxWidth = 340.dp
private val ChatReactionLaneWidth = 48.dp
private val ChatReactionBadgeBottomExtent = 12.dp
private val ChatReactionSideOffsetY = 4.dp
private val ReplySwipeThreshold = 72.dp

@Composable
internal fun Modifier.replySwipeTarget(
    enabled: Boolean,
    onReply: () -> Unit,
): Modifier {
    if (!enabled) return this
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { ReplySwipeThreshold.toPx() }
    var offsetPx by remember { mutableStateOf(0f) }
    var selectedForGesture by remember { mutableStateOf(false) }
    return this
        .offset { IntOffset(offsetPx.roundToInt(), 0) }
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction("Responder") {
                    onReply()
                    true
                }
            )
        }
        .pointerInput(enabled, thresholdPx) {
            detectHorizontalDragGestures(
                onDragStart = {
                    selectedForGesture = false
                    offsetPx = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    val nextOffset = (offsetPx + dragAmount)
                        .coerceAtLeast(0f)
                        .coerceAtMost(thresholdPx * 1.35f)
                    if (nextOffset != offsetPx) {
                        change.consume()
                        offsetPx = nextOffset
                    }
                },
                onDragEnd = {
                    if (!selectedForGesture && shouldSelectReplyForSwipe(offsetPx, thresholdPx)) {
                        selectedForGesture = true
                        onReply()
                    }
                    offsetPx = 0f
                },
                onDragCancel = {
                    offsetPx = 0f
                    selectedForGesture = false
                },
            )
        }
}

@Composable
internal fun LoadingChatScreen(
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
            style = RealsType.ScreenTitle,
            color = MaterialTheme.colorScheme.primary,
        )
        RealsBrandDivider(modifier = Modifier.padding(top = 16.dp))
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
internal fun ChatHeader(
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = chatHeaderPhaseLabel(titlePrefix),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = chatHeaderPrimaryTitle(titlePrefix, partnerName),
                    style = RealsType.SectionTitle,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
                style = MaterialTheme.typography.bodyMedium,
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
        RealsBrandDivider(
            modifier = Modifier
                .padding(top = 3.dp)
                .fillMaxWidth(),
        )
    }
}

private fun chatHeaderPhaseLabel(titlePrefix: String): String =
    titlePrefix.trim().ifBlank { "Chat" }.uppercase()

private fun chatHeaderPrimaryTitle(
    titlePrefix: String,
    partnerName: String?,
): String {
    val safeTitlePrefix = titlePrefix.trim().ifBlank { "Chat" }
    return partnerName
        ?.takeIf { it.isNotBlank() }
        ?.let { TextSafety.safeDisplay(it) }
        ?: safeTitlePrefix
}

internal fun chatOverflowCanOpen(
    loadingChatAction: Boolean,
    canUseExistingChatActions: Boolean,
    canDecide: Boolean,
    canUseSafetyActions: Boolean,
    canManualBlock: Boolean,
    visibility: FirstChatOverflowActionVisibility,
    secondChatCompletion: SecondChatCompletionOverflowPresentation,
): Boolean =
    !loadingChatAction &&
        (
            (visibility.showMutualExit && canUseExistingChatActions) ||
                secondChatCompletion.visible ||
                (visibility.showReject && canDecide) ||
                (visibility.showSafety && canUseSafetyActions)
            ) ||
        (visibility.showManualBlock && canManualBlock)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ChatOverflowMenu(
    expanded: Boolean,
    enabled: Boolean,
    actionLoading: Boolean,
    secondChatCompletion: SecondChatCompletionOverflowPresentation,
    showMutualCompletionCoachmark: Boolean,
    canUseExistingChatActions: Boolean,
    canUseSafetyActions: Boolean,
    canDecide: Boolean,
    canManualBlock: Boolean,
    visibility: FirstChatOverflowActionVisibility,
    onExpandedChange: (Boolean) -> Unit,
    onMutualCompletionCoachmarkDismissed: () -> Unit,
    onRequestMutualExit: () -> Unit,
    onRequestSecondChatCompletion: () -> Unit,
    onRejectChat: () -> Unit,
    onShowSafety: () -> Unit,
    onShowManualBlock: () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val overflowScale = remember { Animatable(1f) }
    LaunchedEffect(showMutualCompletionCoachmark) {
        if (!showMutualCompletionCoachmark) return@LaunchedEffect
        coroutineScope {
            launch {
                overflowScale.snapTo(1f)
                overflowScale.animateTo(1.08f, animationSpec = tween(durationMillis = 180))
                overflowScale.animateTo(1f, animationSpec = tween(durationMillis = 220))
            }
            launch {
                tooltipState.show()
                onMutualCompletionCoachmarkDismissed()
            }
        }
    }

    Box(contentAlignment = Alignment.TopEnd) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Text(SECOND_CHAT_COMPLETION_COACHMARK_COPY)
                }
            },
            state = tooltipState,
            focusable = false,
            enableUserInput = false,
        ) {
            IconButton(
                onClick = {
                    tooltipState.dismiss()
                    onMutualCompletionCoachmarkDismissed()
                    onExpandedChange(true)
                },
                enabled = enabled,
                modifier = Modifier.scale(overflowScale.value),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "Más acciones",
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                if (!actionLoading) {
                    onExpandedChange(false)
                }
            },
        ) {
            if (visibility.showMutualExit) {
                DropdownMenuItem(
                    text = { Text("Salida consensuada") },
                    enabled = !actionLoading && canUseExistingChatActions,
                    onClick = onRequestMutualExit,
                )
            }

            if (secondChatCompletion.visible) {
                DropdownMenuItem(
                    text = { Text(secondChatCompletion.label) },
                    enabled = secondChatCompletionOverflowMenuItemEnabled(
                        action = secondChatCompletion,
                        actionLoading = actionLoading,
                    ),
                    onClick = onRequestSecondChatCompletion,
                )
            }

            if (visibility.showReject) {
                DropdownMenuItem(
                    text = { Text("Rechazar chat") },
                    enabled = !actionLoading && canDecide,
                    onClick = onRejectChat,
                )
            }

            if (visibility.showSafety) {
                DropdownMenuItem(
                    text = { Text("Reportar y cerrar chat") },
                    enabled = !actionLoading && canUseSafetyActions,
                    onClick = onShowSafety,
                )
            }

            if (visibility.showManualBlock) {
                DropdownMenuItem(
                    text = { Text("Bloquear a ésta persona") },
                    enabled = canManualBlock,
                    onClick = onShowManualBlock,
                )
            }
        }
    }
}

@Composable
internal fun ChatActionsPanel(
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

    val approvalLabel = if (loadingChatAction) actionLoadingLabel ?: "Procesando..." else "Aprobar chat"
    val approvalEnabled = !loadingChatAction && canDecide
    if (activeExitRequest == null && onBackHome == null && showDecisionActions) {
        FirstChatApprovalAction(
            label = approvalLabel,
            enabled = approvalEnabled,
            onClick = onApprove,
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text("Volver a Inicio")
                }
            }
            if (showDecisionActions) {
                FirstChatApprovalAction(
                    label = approvalLabel,
                    enabled = approvalEnabled,
                    onClick = onApprove,
                )
            }
        }
    }
}

@Composable
private fun FirstChatApprovalAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalRealsDarkTheme.current
    val containerColor = if (darkTheme) {
        RealsColors.DarkSurfaceHigh.copy(alpha = if (enabled) 0.98f else 0.54f)
    } else {
        RealsColors.Ink.copy(alpha = if (enabled) 0.94f else 0.22f)
    }
    val contentColor = if (darkTheme) {
        MaterialTheme.colorScheme.onSurface
    } else {
        RealsColors.Ivory
    }
    val borderColor = if (darkTheme) {
        MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 0.60f else 0.30f)
    } else {
        RealsColors.SoftGold.copy(alpha = if (enabled) 0.74f else 0.38f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealsApprovalDiamond(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 0.90f else 0.42f),
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = if (enabled) 1f else 0.54f),
            )
        }
    }
}

@Composable
private fun RealsApprovalDiamond(
    color: Color,
) {
    Canvas(modifier = Modifier.size(8.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        drawPath(path, color)
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

    Card(
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MUTUAL_EXIT_CONVERSATION_PAUSED_COPY,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun MessageList(
    chatId: String?,
    initialHistoryLoading: Boolean,
    currentUserId: String,
    partnerDisplayName: String?,
    chatType: ChatType,
    messages: List<ChatMessage>,
    optimisticMessages: List<OptimisticOutgoingMessage>,
    pendingReactionMessageIds: Set<String> = emptySet(),
    reactionAddingEnabled: Boolean = false,
    bottomContentPadding: Dp,
    modifier: Modifier,
    onRetryOptimisticMessage: (localId: String) -> Unit,
    onReactToMessage: (messageId: String) -> Unit = {},
    canInitiateReply: Boolean = false,
    onReplyToMessage: (ChatMessage) -> Unit = {},
    canRetryFailedTextMessages: Boolean,
    playbackState: ChatAudioPlaybackUiState,
    onPlayAudio: (ChatMessage) -> Unit,
    onPauseAudio: () -> Unit,
) {
    val sortedMessages = messages.sortedWith(compareBy<ChatMessage> { it.sentAt }.thenBy { it.id })
    val reactableMessageIds = if (reactionAddingEnabled) {
        reactableIncomingMessageIds(sortedMessages, currentUserId)
    } else {
        emptySet()
    }
    val messageItems = sortedMessages.map { ChatMessageListItem.Backend(it) } +
        optimisticMessages.sortedBy { it.createdAtMillis }
            .map { ChatMessageListItem.Optimistic(it) }
    val canvasAppearance = chatCanvasAppearance(chatType)
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val latestMessage = messageItems.lastOrNull()
    val latestMessageId = latestMessage?.stableId
    val latestMessageIsMine = latestMessage?.isMine(currentUserId) == true
    var selectionResetGeneration by remember { mutableStateOf(0) }
    var knownMessageIds by remember(chatId) { mutableStateOf<Set<String>?>(null) }
    var knownBackendMessageIds by remember(chatId) { mutableStateOf<Set<String>?>(null) }
    var knownReactionLayoutIdentities by remember(chatId) {
        mutableStateOf<List<MessageReactionLayoutIdentity>?>(null)
    }
    var messageBaselineEstablished by remember(chatId) { mutableStateOf(false) }
    var hasUnseenIncomingMessages by remember(chatId) { mutableStateOf(false) }
    var listNearBottom by remember(chatId) { mutableStateOf(true) }
    var previousBottomContentPadding by remember(chatId) { mutableStateOf(bottomContentPadding) }
    val currentMessageIds = messageItems.map { it.stableId }.toSet()
    val backendMessageIdentities = sortedMessages.map {
        BackendMessageIdentity(id = it.id, senderId = it.senderId)
    }
    val reactionLayoutIdentities = sortedMessages.map { message ->
        MessageReactionLayoutIdentity(
            id = message.id,
            hasReactionExtent = chatMessageReactionPresentation(
                message = message,
                mine = message.senderId == currentUserId,
                pendingReactionMessageIds = pendingReactionMessageIds,
                reactableMessageIds = reactableMessageIds,
            ).hasReactionBadgeExtent(),
        )
    }
    val currentBackendMessageIds = backendMessageIdentities.mapTo(LinkedHashSet()) { it.id }
    val entranceBaselineIds = knownMessageIds.takeIf { messageBaselineEstablished }
    val currentMessageIdsForScroll by rememberUpdatedState(currentMessageIds)
    val knownMessageIdsForScroll by rememberUpdatedState(knownMessageIds)

    LaunchedEffect(chatId) {
        snapshotFlow { listState.isNearBottom() }
            .collect { nearBottom ->
                if (currentMessageIdsForScroll.allKnownBy(knownMessageIdsForScroll)) {
                    listNearBottom = nearBottom
                    if (nearBottom) {
                        hasUnseenIncomingMessages = false
                    }
                }
            }
    }

    LaunchedEffect(knownMessageIds) {
        if (currentMessageIds.allKnownBy(knownMessageIds)) {
            val nearBottom = listState.isNearBottom()
            listNearBottom = nearBottom
            if (nearBottom) {
                hasUnseenIncomingMessages = false
            }
        }
    }

    LaunchedEffect(latestMessageId) {
        if (latestMessageId == null) return@LaunchedEffect
        if (!messageBaselineEstablished) return@LaunchedEffect

        val shouldScrollToBottom = shouldAutoScrollForLatestMessage(
            latestMessageIsMine = latestMessageIsMine,
            wasNearBottomBeforeLatestChange = listNearBottom,
        )
        if (shouldScrollToBottom) {
            listState.animateLatestItemIntoView(messageItems.lastIndex)
            hasUnseenIncomingMessages = false
        }
    }

    LaunchedEffect(bottomContentPadding) {
        val previousPadding = previousBottomContentPadding
        previousBottomContentPadding = bottomContentPadding
        if (previousPadding == bottomContentPadding || !messageBaselineEstablished || messageItems.isEmpty()) {
            return@LaunchedEffect
        }
        if (shouldPreserveBottomForComposerHeightChange(listNearBottom)) {
            listState.animateLatestItemIntoView(messageItems.lastIndex)
        }
    }

    LaunchedEffect(reactionLayoutIdentities) {
        val previous = knownReactionLayoutIdentities
        if (previous == null || !messageBaselineEstablished) {
            knownReactionLayoutIdentities = reactionLayoutIdentities
            return@LaunchedEffect
        }

        if (
            shouldPreserveBottomForReactionLayoutChange(
                previous = previous,
                current = reactionLayoutIdentities,
                wasNearBottomBeforeReactionChange = listNearBottom,
            ) && messageItems.isNotEmpty()
        ) {
            listState.animateLatestItemIntoView(messageItems.lastIndex)
        }
        knownReactionLayoutIdentities = reactionLayoutIdentities
    }

    LaunchedEffect(currentMessageIds, currentBackendMessageIds, initialHistoryLoading) {
        if (!messageBaselineEstablished) {
            if (initialHistoryLoading) return@LaunchedEffect
            knownMessageIds = currentMessageIds
            knownBackendMessageIds = currentBackendMessageIds
            knownReactionLayoutIdentities = reactionLayoutIdentities
            messageBaselineEstablished = true
            if (messageItems.isNotEmpty()) {
                listState.scrollToItem(messageItems.lastIndex)
            }
        } else {
            if (
                shouldMarkIncomingMessagesUnseen(
                    baselineEstablished = true,
                    previousBackendMessageIds = knownBackendMessageIds,
                    currentBackendMessages = backendMessageIdentities,
                    currentUserId = currentUserId,
                    wasNearBottomBeforeMessageChange = listNearBottom,
                )
            ) {
                hasUnseenIncomingMessages = true
            }
            knownMessageIds = knownMessageIds
                ?.plus(currentMessageIds)
                ?: currentMessageIds
            knownBackendMessageIds = knownBackendMessageIds
                ?.plus(currentBackendMessageIds)
                ?: currentBackendMessageIds
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = canvasAppearance.border,
        colors = CardDefaults.cardColors(containerColor = canvasAppearance.containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { selectionResetGeneration++ },
                        )
                    },
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = 10.dp,
                    end = 8.dp,
                    bottom = bottomContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (messageItems.isEmpty()) {
                    item {
                        EmptyMessageState(
                            appearance = canvasAppearance,
                        )
                    }
                } else {
                    items(messageItems, key = { it.stableId }) { item ->
                        val shouldSlideIn = entranceBaselineIds?.let { knownIds ->
                            item.stableId !in knownIds && when (item) {
                                is ChatMessageListItem.Backend -> !item.isMine(currentUserId)
                                is ChatMessageListItem.Optimistic -> item.isMine(currentUserId)
                            }
                        } == true
                        val itemModifier = (entranceBaselineIds?.let {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = tween(durationMillis = 180),
                                fadeOutSpec = null,
                            )
                        } ?: Modifier)
                            .then(
                                rememberMessageArrivalModifier(
                                    stableId = item.stableId,
                                    slideIn = shouldSlideIn,
                                )
                            )
                        when (item) {
                            is ChatMessageListItem.Backend -> MessageBubble(
                                message = item.message,
                                mine = item.message.senderId == currentUserId,
                                currentUserId = currentUserId,
                                partnerDisplayName = partnerDisplayName,
                                chatType = chatType,
                                selectionResetGeneration = selectionResetGeneration,
                                playbackState = playbackState,
                                reactionPresentation = chatMessageReactionPresentation(
                                    message = item.message,
                                    mine = item.message.senderId == currentUserId,
                                    pendingReactionMessageIds = pendingReactionMessageIds,
                                    reactableMessageIds = reactableMessageIds,
                                ),
                                modifier = itemModifier,
                                onReactToMessage = onReactToMessage,
                                canInitiateReply = canInitiateReply,
                                onReplyToMessage = onReplyToMessage,
                                onPlayAudio = onPlayAudio,
                                onPauseAudio = onPauseAudio,
                            )

                            is ChatMessageListItem.Optimistic -> OptimisticMessageBubble(
                                message = item.message,
                                currentUserId = currentUserId,
                                partnerDisplayName = partnerDisplayName,
                                chatType = chatType,
                                selectionResetGeneration = selectionResetGeneration,
                                modifier = itemModifier,
                                onRetry = onRetryOptimisticMessage,
                                canRetryFailedTextMessages = canRetryFailedTextMessages,
                            )
                        }
                    }
                }
            }
            if (hasUnseenIncomingMessages) {
                NewMessagesIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomContentPadding + 12.dp),
                    onClick = {
                        scrollScope.launch {
                            if (messageItems.isNotEmpty()) {
                                listState.animateLatestItemIntoView(messageItems.lastIndex)
                            }
                            hasUnseenIncomingMessages = false
                        }
                    },
                )
            }
        }
    }
}

private suspend fun LazyListState.animateLatestItemIntoView(lastIndex: Int) {
    withFrameNanos { }
    val latestItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
    val usableViewportEnd = usableLazyListViewportEnd(
        viewportEndOffset = layoutInfo.viewportEndOffset,
        afterContentPadding = layoutInfo.afterContentPadding,
    )
    val overflow = latestItemInfo
        ?.let { latestItemOverflow(itemEndOffset = it.offset + it.size, usableViewportEnd = usableViewportEnd) }

    when {
        latestItemInfo == null -> animateScrollToItem(lastIndex)
        overflow != null && overflow > 0 -> animateScrollBy(overflow.toFloat())
    }
}

internal fun usableLazyListViewportEnd(
    viewportEndOffset: Int,
    afterContentPadding: Int,
): Int = viewportEndOffset - afterContentPadding

internal fun latestItemOverflow(
    itemEndOffset: Int,
    usableViewportEnd: Int,
): Int = (itemEndOffset - usableViewportEnd).coerceAtLeast(0)

internal fun shouldAutoScrollForLatestMessage(
    latestMessageIsMine: Boolean,
    wasNearBottomBeforeLatestChange: Boolean,
): Boolean = latestMessageIsMine || wasNearBottomBeforeLatestChange

internal data class BackendMessageIdentity(
    val id: String,
    val senderId: String,
)

internal data class MessageReactionLayoutIdentity(
    val id: String,
    val hasReactionExtent: Boolean,
)

internal fun shouldMarkIncomingMessagesUnseen(
    baselineEstablished: Boolean,
    previousBackendMessageIds: Set<String>?,
    currentBackendMessages: List<BackendMessageIdentity>,
    currentUserId: String,
    wasNearBottomBeforeMessageChange: Boolean,
): Boolean {
    if (!baselineEstablished || previousBackendMessageIds == null) return false
    if (wasNearBottomBeforeMessageChange) return false

    return currentBackendMessages.any { message ->
        message.id !in previousBackendMessageIds && message.senderId != currentUserId
    }
}

internal fun shouldPreserveBottomForReactionLayoutChange(
    previous: List<MessageReactionLayoutIdentity>,
    current: List<MessageReactionLayoutIdentity>,
    wasNearBottomBeforeReactionChange: Boolean,
): Boolean {
    if (!wasNearBottomBeforeReactionChange) return false
    if (previous.map { it.id } != current.map { it.id }) return false
    return previous.zip(current).any { (old, new) ->
        !old.hasReactionExtent && new.hasReactionExtent
    }
}

private fun Set<String>.allKnownBy(knownIds: Set<String>?): Boolean =
    knownIds?.containsAll(this) == true

@Composable
private fun NewMessagesIndicator(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Ir a mensajes nuevos"
        },
        shape = RoundedCornerShape(RealsRadii.Row),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "\u2193 Mensajes nuevos",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun ComposerReplyPreview(
    preview: ChatReplyPreview,
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 5.dp, end = 2.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(modifier = Modifier.size(width = 3.dp, height = 34.dp)) {
                drawRect(accentColor)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = preview.label,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = preview.text,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Quitar respuesta",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun rememberMessageArrivalModifier(
    stableId: String,
    slideIn: Boolean,
): Modifier {
    val initialSlideIn = remember(stableId) { slideIn }
    val offsetY = remember(stableId) { Animatable(if (initialSlideIn) 10f else 0f) }

    LaunchedEffect(stableId) {
        if (initialSlideIn) {
            offsetY.snapTo(10f)
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 170),
            )
        } else {
            offsetY.snapTo(0f)
        }
    }

    return Modifier.offset(y = offsetY.value.dp)
}

private data class ChatCanvasAppearance(
    val containerColor: Color,
    val border: BorderStroke?,
    val emptyStateColor: Color,
    val emptyStateVerticalPadding: Dp,
)

@Composable
private fun chatCanvasAppearance(chatType: ChatType): ChatCanvasAppearance {
    val darkTheme = LocalRealsDarkTheme.current
    return when (chatType) {
        ChatType.FirstChat -> ChatCanvasAppearance(
            containerColor = if (darkTheme) {
                RealsColors.DarkSurface.copy(alpha = 0.50f)
            } else {
                RealsColors.Paper.copy(alpha = 0.38f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (darkTheme) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)
                } else {
                    RealsColors.SoftGold.copy(alpha = 0.48f)
                },
            ),
            emptyStateColor = MaterialTheme.colorScheme.onSurfaceVariant,
            emptyStateVerticalPadding = 28.dp,
        )

        ChatType.SecondChat -> ChatCanvasAppearance(
            containerColor = if (darkTheme) {
                RealsColors.DarkSurface.copy(alpha = 0.26f)
            } else {
                RealsColors.Paper.copy(alpha = 0.16f)
            },
            border = if (darkTheme) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
            } else {
                null
            },
            emptyStateColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
            emptyStateVerticalPadding = 20.dp,
        )

        is ChatType.Unknown -> ChatCanvasAppearance(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
            emptyStateColor = MaterialTheme.colorScheme.onSurfaceVariant,
            emptyStateVerticalPadding = 24.dp,
        )
    }
}

@Composable
private fun EmptyMessageState(
    appearance: ChatCanvasAppearance,
) {
    Text(
        text = "Todavía no hay mensajes.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = appearance.emptyStateVerticalPadding),
        style = MaterialTheme.typography.bodyMedium,
        color = appearance.emptyStateColor,
        textAlign = TextAlign.Center,
    )
}

private fun LazyListState.isNearBottom(bufferItems: Int = 2): Boolean {
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return true

    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    if (lastVisibleIndex < totalItems - 1 - bufferItems) return false

    val latestItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == totalItems - 1 } ?: return true
    val usableViewportEnd = usableLazyListViewportEnd(
        viewportEndOffset = layoutInfo.viewportEndOffset,
        afterContentPadding = layoutInfo.afterContentPadding,
    )
    return latestItemOverflow(
        itemEndOffset = latestItemInfo.offset + latestItemInfo.size,
        usableViewportEnd = usableViewportEnd,
    ) == 0
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

internal enum class ChatMessageReactionPresentation {
    None,
    AddHeart,
    GivenHeart,
    ReceivedHeart,
}

private fun ChatMessageReactionPresentation.hasReactionBadgeExtent(): Boolean =
    this == ChatMessageReactionPresentation.ReceivedHeart

internal fun chatMessageReactionPresentation(
    message: ChatMessage,
    mine: Boolean,
    pendingReactionMessageIds: Set<String>,
    reactableMessageIds: Set<String>,
): ChatMessageReactionPresentation {
    val confirmedHeart = message.reactionType == ChatMessageReactionType.Heart
    return when {
        mine && confirmedHeart -> ChatMessageReactionPresentation.ReceivedHeart
        mine -> ChatMessageReactionPresentation.None
        confirmedHeart || message.id in pendingReactionMessageIds -> ChatMessageReactionPresentation.GivenHeart
        message.reactionType == null && message.id in reactableMessageIds -> ChatMessageReactionPresentation.AddHeart
        else -> ChatMessageReactionPresentation.None
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    currentUserId: String,
    partnerDisplayName: String?,
    chatType: ChatType,
    selectionResetGeneration: Int,
    playbackState: ChatAudioPlaybackUiState,
    reactionPresentation: ChatMessageReactionPresentation,
    modifier: Modifier = Modifier,
    onReactToMessage: (messageId: String) -> Unit,
    canInitiateReply: Boolean,
    onReplyToMessage: (ChatMessage) -> Unit,
    onPlayAudio: (ChatMessage) -> Unit,
    onPauseAudio: () -> Unit,
) {
    val appearance = chatBubbleAppearance(mine = mine)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (mine) ChatBubbleOppositeGutter else 0.dp,
                end = 0.dp,
        ),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (mine) {
            Box(
                modifier = Modifier.padding(bottom = if (reactionPresentation == ChatMessageReactionPresentation.ReceivedHeart) 12.dp else 0.dp),
            ) {
                MessageBubbleCard(
                    message = message,
                    mine = true,
                    currentUserId = currentUserId,
                    partnerDisplayName = partnerDisplayName,
                    chatType = chatType,
                    appearance = appearance,
                    selectionResetGeneration = selectionResetGeneration,
                    playbackState = playbackState,
                    onPlayAudio = onPlayAudio,
                    onPauseAudio = onPauseAudio,
                )
                if (reactionPresentation == ChatMessageReactionPresentation.ReceivedHeart) {
                    PassiveReceivedHeartBadge(
                        contentDescription = "La otra persona reaccionó con corazón",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-16).dp, y = 14.dp),
                    )
                }
            }
        } else {
            IncomingMessageBubbleLayout(
                message = message,
                currentUserId = currentUserId,
                partnerDisplayName = partnerDisplayName,
                chatType = chatType,
                appearance = appearance,
                selectionResetGeneration = selectionResetGeneration,
                playbackState = playbackState,
                reactionPresentation = reactionPresentation,
                canInitiateReply = canInitiateReply,
                onReplyToMessage = onReplyToMessage,
                onReact = { onReactToMessage(message.id) },
                onPlayAudio = onPlayAudio,
                onPauseAudio = onPauseAudio,
            )
        }
    }
}

@Composable
private fun IncomingMessageBubbleLayout(
    message: ChatMessage,
    currentUserId: String,
    partnerDisplayName: String?,
    chatType: ChatType,
    appearance: ChatBubbleAppearance,
    selectionResetGeneration: Int,
    playbackState: ChatAudioPlaybackUiState,
    reactionPresentation: ChatMessageReactionPresentation,
    canInitiateReply: Boolean,
    onReplyToMessage: (ChatMessage) -> Unit,
    onReact: () -> Unit,
    onPlayAudio: (ChatMessage) -> Unit,
    onPauseAudio: () -> Unit,
) {
    Layout(
        content = {
            MessageBubbleCard(
                message = message,
                mine = false,
                currentUserId = currentUserId,
                partnerDisplayName = partnerDisplayName,
                chatType = chatType,
                appearance = appearance,
                selectionResetGeneration = selectionResetGeneration,
                playbackState = playbackState,
                onPlayAudio = onPlayAudio,
                onPauseAudio = onPauseAudio,
                modifier = Modifier.replySwipeTarget(
                    enabled = canInitiateReply && message.isCitableReplyTarget(currentUserId),
                    onReply = { onReplyToMessage(message) },
                ),
            )
            IncomingReactionSideSlot(
                presentation = reactionPresentation,
                onReact = onReact,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val laneWidth = ChatReactionLaneWidth.roundToPx()
        val bottomExtent = if (reactionPresentation.hasReactionBadgeExtent()) {
            ChatReactionBadgeBottomExtent.roundToPx()
        } else {
            0
        }
        val maxBubbleWidth = (constraints.maxWidth - laneWidth)
            .coerceAtLeast(0)
            .coerceAtMost(ChatBubbleMaxWidth.roundToPx())
        val bubble = measurables[0].measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = maxBubbleWidth,
            ),
        )
        val lane = measurables[1].measure(
            Constraints.fixedWidth(laneWidth),
        )
        val layoutWidth = constraints.maxWidth
        val layoutHeight = (bubble.height + bottomExtent)
            .coerceAtLeast(constraints.minHeight)
            .coerceAtMost(constraints.maxHeight)
        val laneY = (bubble.height - lane.height + ChatReactionSideOffsetY.roundToPx())
            .coerceAtLeast(0)
        layout(layoutWidth, layoutHeight) {
            bubble.placeRelative(0, 0)
            lane.placeRelative(bubble.width, laneY)
        }
    }
}

@Composable
private fun MessageBubbleCard(
    message: ChatMessage,
    mine: Boolean,
    currentUserId: String,
    partnerDisplayName: String?,
    chatType: ChatType,
    appearance: ChatBubbleAppearance,
    selectionResetGeneration: Int,
    playbackState: ChatAudioPlaybackUiState,
    onPlayAudio: (ChatMessage) -> Unit,
    onPauseAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.widthIn(max = ChatBubbleMaxWidth),
        shape = RoundedCornerShape(
            topStart = if (mine) RealsRadii.Row else 8.dp,
            topEnd = if (mine) 8.dp else RealsRadii.Row,
            bottomStart = if (mine) RealsRadii.Row else 2.dp,
            bottomEnd = if (mine) 2.dp else RealsRadii.Row,
        ),
        border = appearance.border,
        colors = CardDefaults.cardColors(
            containerColor = appearance.containerColor,
            contentColor = appearance.contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            message.replyTo
                ?.toPreview(currentUserId = currentUserId, partnerDisplayName = partnerDisplayName)
                ?.let { preview ->
                    InlineReplyQuote(preview = preview)
                }
            when (val presentation = message.presentation) {
                is ChatMessagePresentation.Text -> MessageTextWithTimestamp(
                    presentation = chatMessageTextPresentation(
                        content = presentation.content,
                        chatType = chatType,
                    ),
                    timestamp = formatBackendTime(message.sentAt),
                    appearance = appearance,
                    selectionResetGeneration = selectionResetGeneration,
                )
                is ChatMessagePresentation.Audio -> AudioPlaybackRow(
                    key = message.id,
                    durationMillis = presentation.audio.durationMillis ?: 0L,
                    playbackState = playbackState,
                    onPlay = { onPlayAudio(message) },
                    onPause = onPauseAudio,
                )

                ChatMessagePresentation.Unsupported -> Text("Mensaje no compatible")
            }
            if (message.presentation !is ChatMessagePresentation.Text) {
                MessageTimestamp(
                    text = formatBackendTime(message.sentAt),
                    color = appearance.metadataColor,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun InlineReplyQuote(
    preview: ChatReplyPreview,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier
                .size(width = 3.dp, height = 34.dp),
        ) {
            drawRect(accentColor)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = preview.label,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = preview.text,
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IncomingReactionSideSlot(
    presentation: ChatMessageReactionPresentation,
    onReact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd,
    ) {
        IncomingReactionSlot(
            presentation = presentation,
            onReact = onReact,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun IncomingReactionSlot(
    presentation: ChatMessageReactionPresentation,
    onReact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (presentation) {
        ChatMessageReactionPresentation.AddHeart -> HeartReactionChip(
            filled = false,
            contentDescription = "Reaccionar con corazón",
            clickable = true,
            onClick = onReact,
            modifier = modifier,
        )
        ChatMessageReactionPresentation.GivenHeart -> HeartReactionChip(
            filled = true,
            contentDescription = "Reaccionaste con corazón",
            clickable = false,
            modifier = modifier,
        )
        ChatMessageReactionPresentation.ReceivedHeart,
        ChatMessageReactionPresentation.None -> Unit
    }
}

@Composable
private fun PassiveReceivedHeartBadge(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_heart_filled),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HeartReactionChip(
    filled: Boolean,
    contentDescription: String,
    clickable: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val scale = remember(filled) { Animatable(if (filled) 0.86f else 1f) }
    LaunchedEffect(filled) {
        if (filled) {
            scale.snapTo(0.86f)
            scale.animateTo(1f, animationSpec = tween(durationMillis = 150))
        }
    }
    val semanticModifier = if (clickable) {
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
    } else {
        Modifier.semantics {
            this.contentDescription = contentDescription
        }
    }
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(semanticModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (filled) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
            contentDescription = null,
            tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .scale(scale.value),
        )
    }
}

private data class ChatBubbleAppearance(
    val containerColor: Color,
    val border: BorderStroke?,
    val contentColor: Color,
    val metadataColor: Color,
)

@Composable
private fun chatBubbleAppearance(mine: Boolean): ChatBubbleAppearance {
    val darkTheme = LocalRealsDarkTheme.current
    return if (mine) {
        ChatBubbleAppearance(
            containerColor = if (darkTheme) {
                RealsColors.DarkSurfaceHigh.copy(alpha = 0.98f)
            } else {
                RealsColors.Ink.copy(alpha = 0.14f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (darkTheme) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)
                } else {
                    RealsColors.Ink.copy(alpha = 0.12f)
                },
            ),
            contentColor = MaterialTheme.colorScheme.onSurface,
            metadataColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
        )
    } else {
        ChatBubbleAppearance(
            containerColor = if (darkTheme) {
                RealsColors.DarkSurface.copy(alpha = 0.92f)
            } else {
                RealsColors.Paper.copy(alpha = 0.94f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (darkTheme) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                } else {
                    RealsColors.SoftGold.copy(alpha = 0.42f)
                },
            ),
            contentColor = MaterialTheme.colorScheme.onSurface,
            metadataColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AudioPlaybackRow(
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
            IconButton(
                onClick = {
                    if (phase == ChatAudioPlaybackPhase.Playing || phase == ChatAudioPlaybackPhase.Preparing) {
                        onPause()
                    } else {
                        onPlay()
                    }
                },
                enabled = phase != ChatAudioPlaybackPhase.Preparing,
            ) {
                when (phase) {
                    ChatAudioPlaybackPhase.Preparing -> CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .semantics { contentDescription = "Cargando audio" },
                        strokeWidth = 2.dp,
                    )
                    ChatAudioPlaybackPhase.Playing -> Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "Pausar audio",
                    )
                    ChatAudioPlaybackPhase.Failed -> Icon(
                        painter = painterResource(R.drawable.ic_replay),
                        contentDescription = "Reintentar audio",
                    )
                    ChatAudioPlaybackPhase.Idle,
                    ChatAudioPlaybackPhase.Paused -> Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = "Reproducir audio",
                    )
                }
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

@Composable
private fun OptimisticMessageBubble(
    message: OptimisticOutgoingMessage,
    currentUserId: String,
    partnerDisplayName: String?,
    chatType: ChatType,
    selectionResetGeneration: Int,
    modifier: Modifier = Modifier,
    onRetry: (localId: String) -> Unit,
    canRetryFailedTextMessages: Boolean,
) {
    val appearance = chatBubbleAppearance(mine = true)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ChatBubbleOppositeGutter),
        horizontalArrangement = Arrangement.End,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = RealsRadii.Row,
                topEnd = 8.dp,
                bottomStart = RealsRadii.Row,
                bottomEnd = 2.dp,
            ),
            border = appearance.border,
            colors = CardDefaults.cardColors(
                containerColor = appearance.containerColor,
                contentColor = appearance.contentColor,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                message.replyTo
                    ?.toPreview(currentUserId = currentUserId, partnerDisplayName = partnerDisplayName)
                    ?.let { preview ->
                        InlineReplyQuote(preview = preview)
                    }
                when (message.messageType) {
                    OptimisticOutgoingMessageType.Text -> SelectableMessageText(
                        presentation = chatMessageTextPresentation(
                            content = message.content,
                            chatType = chatType,
                        ),
                        selectionResetGeneration = selectionResetGeneration,
                    )
                    OptimisticOutgoingMessageType.Audio -> {
                        Text("Audio ${formatAudioDuration(message.audioDurationMillis ?: 0L)}")
                    }
                }
                Text(
                    text = when (message.deliveryState) {
                        OutgoingMessageDeliveryState.Sending -> "Enviando..."
                        OutgoingMessageDeliveryState.Failed -> "No se pudo enviar"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall,
                    color = appearance.metadataColor,
                )
                if (
                    optimisticTextRetryAvailable(message, canRetryFailedTextMessages)
                ) {
                    TextButton(
                        onClick = { onRetry(message.localId) },
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
private fun SelectableMessageText(
    presentation: ChatMessageTextPresentation,
    selectionResetGeneration: Int,
    modifier: Modifier = Modifier,
) {
    key(selectionResetGeneration) {
        SelectionContainer {
            Text(
                text = presentation.annotatedText(
                    linkStyle = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MessageTextWithTimestamp(
    presentation: ChatMessageTextPresentation,
    timestamp: String,
    appearance: ChatBubbleAppearance,
    selectionResetGeneration: Int,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        SelectableMessageText(
            presentation = presentation,
            selectionResetGeneration = selectionResetGeneration,
            modifier = Modifier.padding(end = 42.dp),
        )
        MessageTimestamp(
            text = timestamp,
            color = appearance.metadataColor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 4.dp),
        )
    }
}

@Composable
private fun MessageTimestamp(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            lineHeight = 9.sp,
        ),
        color = color.copy(alpha = 0.78f),
    )
}

internal data class ChatMessageTextPresentation(
    val text: String,
    val phoneLinks: List<ChatMessagePhoneLink> = emptyList(),
) {
    fun annotatedText(linkStyle: SpanStyle) = buildAnnotatedString {
        append(text)
        val styles = TextLinkStyles(style = linkStyle)
        phoneLinks.forEach { link ->
            addLink(
                url = LinkAnnotation.Url(
                    url = link.uri,
                    styles = styles,
                ),
                start = link.start,
                end = link.end,
            )
        }
    }
}

internal data class ChatMessagePhoneLink(
    val start: Int,
    val end: Int,
    val uri: String,
)

internal data class PhoneNumberCandidate(
    val start: Int,
    val end: Int,
)

internal fun chatMessageTextPresentation(
    content: String,
    chatType: ChatType,
    phoneNumberCandidates: (String) -> List<PhoneNumberCandidate> = ::platformPhoneNumberCandidates,
): ChatMessageTextPresentation {
    val safeText = TextSafety.safeDisplay(content)
    if (chatType != ChatType.SecondChat) {
        return ChatMessageTextPresentation(text = safeText)
    }
    return ChatMessageTextPresentation(
        text = safeText,
        phoneLinks = telephoneLinksFor(
            text = safeText,
            candidates = phoneNumberCandidates(safeText),
        ),
    )
}

private fun platformPhoneNumberCandidates(text: String): List<PhoneNumberCandidate> {
    val matcher = Patterns.PHONE.matcher(text)
    return buildList {
        while (matcher.find()) {
            add(PhoneNumberCandidate(start = matcher.start(), end = matcher.end()))
        }
    }
}

internal fun telephoneLinksFor(
    text: String,
    candidates: List<PhoneNumberCandidate>,
): List<ChatMessagePhoneLink> {
    val links = mutableListOf<ChatMessagePhoneLink>()
    candidates
        .sortedWith(compareBy<PhoneNumberCandidate> { it.start }.thenBy { it.end })
        .forEach { candidate ->
            if (candidate.start < 0 || candidate.end > text.length || candidate.start >= candidate.end) {
                return@forEach
            }
            if (links.any { candidate.start < it.end && candidate.end > it.start }) {
                return@forEach
            }
            normalizedTelUri(text.substring(candidate.start, candidate.end))?.let { uri ->
                links += ChatMessagePhoneLink(
                    start = candidate.start,
                    end = candidate.end,
                    uri = uri,
                )
            }
        }
    return links
}

private fun normalizedTelUri(candidate: String): String? {
    val trimmed = candidate.trim()
    if (trimmed.isBlank() || looksLikeDateOrTime(trimmed)) return null
    val digits = trimmed.filter(Char::isDigit)
    if (digits.length < MIN_TELEPHONE_DIGITS) return null
    val hasLeadingPlus = trimmed.firstOrNull { !it.isWhitespace() } == '+'
    return "tel:${if (hasLeadingPlus) "+$digits" else digits}"
}

private fun looksLikeDateOrTime(text: String): Boolean =
    likelyDatePattern.matches(text) || likelyTimePattern.matches(text)

private val likelyDatePattern = Regex("""\d{1,4}[-/]\d{1,2}[-/]\d{1,4}""")
private val likelyTimePattern = Regex("""\d{1,2}:\d{2}(:\d{2})?""")
private const val MIN_TELEPHONE_DIGITS = 8

internal fun optimisticTextRetryAvailable(
    message: OptimisticOutgoingMessage,
    canRetryFailedTextMessages: Boolean,
): Boolean =
    canRetryFailedTextMessages &&
        message.deliveryState == OutgoingMessageDeliveryState.Failed &&
        message.messageType == OptimisticOutgoingMessageType.Text

@Composable
internal fun SuccessFeedback(message: String, modifier: Modifier = Modifier) {
    FeedbackCard(
        title = "Listo",
        message = message,
        tone = FeedbackTone.Success,
        modifier = modifier
    )
}

internal fun chatHeaderStatusText(
    expiresAt: String?,
    firstChatLifecycle: FirstChatLifecycleUiState?,
    secondChatReadOnlyUntil: String?,
    secondChatUnavailable: Boolean,
    formatDateTime: (String?) -> String = ::formatBackendDateTime,
): String? = when {
    secondChatReadOnlyUntil != null ->
        "Este segundo chat venció. Podés leerlo hasta ${formatDateTime(secondChatReadOnlyUntil)}."

    secondChatUnavailable -> "Este segundo chat ya no está disponible."
    else -> null
}

internal fun chatDecisionSummary(
    myDecision: ChatDecisionState?,
    partnerDecision: ChatDecisionState?,
    partnerName: String?,
): String? {
    if (myDecision == null || partnerDecision == null) return null
    val partnerLabel = partnerName
        ?.takeIf { it.isNotBlank() }
        ?.let { TextSafety.safeDisplay(it) }
        ?: "La otra persona"

    return when {
        myDecision == ChatDecisionState.Approved && partnerDecision == ChatDecisionState.Pending ->
            "Aprobaste el chat. Esperando decisión de $partnerLabel."

        myDecision == ChatDecisionState.Pending && partnerDecision == ChatDecisionState.Approved ->
            "$partnerLabel aprobó el chat. Ahora te toca decidir."

        myDecision == ChatDecisionState.Approved && partnerDecision == ChatDecisionState.Approved ->
            "Ambas personas aprobaron. Pasando a revisión visual."

        myDecision == ChatDecisionState.Rejected || partnerDecision == ChatDecisionState.Rejected ->
            "El chat fue rechazado."

        myDecision == ChatDecisionState.Abandoned || partnerDecision == ChatDecisionState.Abandoned ->
            "El chat fue abandonado."

        else -> null
    }
}
