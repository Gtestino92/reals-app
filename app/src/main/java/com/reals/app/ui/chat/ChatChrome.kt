package com.reals.app.ui.chat

import android.util.Patterns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.core.security.TextSafety
import com.reals.app.core.time.remainingExitSeconds
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessagePresentation
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
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal const val MUTUAL_EXIT_CONVERSATION_PAUSED_COPY =
    "La conversación está pausada mientras se resuelve la solicitud."

private const val MUTUAL_EXIT_TIMEOUT_SECONDS = 20L
private const val MUTUAL_EXIT_TIMEOUT_RETRY_MILLIS = 2_000L

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = chatHeaderTitle(titlePrefix, partnerName),
                    modifier = Modifier.weight(1f),
                    style = RealsType.SectionTitle,
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    Text("Volver a Inicio")
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
    currentUserId: String,
    chatType: ChatType,
    messages: List<ChatMessage>,
    optimisticMessages: List<OptimisticOutgoingMessage>,
    bottomContentPadding: Dp,
    modifier: Modifier,
    onRetryOptimisticMessage: (localId: String, content: String) -> Unit,
    canRetryFailedTextMessages: Boolean,
    playbackState: ChatAudioPlaybackUiState,
    onPlayAudio: (ChatMessage) -> Unit,
    onPauseAudio: () -> Unit,
) {
    val sortedMessages = messages.sortedWith(compareBy<ChatMessage> { it.sentAt }.thenBy { it.id })
    val messageItems = sortedMessages.map { ChatMessageListItem.Backend(it) } +
        optimisticMessages.sortedBy { it.createdAtMillis }
            .map { ChatMessageListItem.Optimistic(it) }
    val listState = rememberLazyListState()
    val latestMessage = messageItems.lastOrNull()
    val latestMessageId = latestMessage?.stableId
    val latestMessageIsMine = latestMessage?.isMine(currentUserId) == true
    var selectionResetGeneration by remember { mutableStateOf(0) }

    LaunchedEffect(latestMessageId) {
        if (latestMessageId == null) return@LaunchedEffect

        val shouldScrollToBottom = latestMessageIsMine || listState.isNearBottom()
        if (shouldScrollToBottom) {
            listState.animateScrollToItem(messageItems.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
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
                            chatType = chatType,
                            selectionResetGeneration = selectionResetGeneration,
                            playbackState = playbackState,
                            onPlayAudio = onPlayAudio,
                            onPauseAudio = onPauseAudio,
                        )

                        is ChatMessageListItem.Optimistic -> OptimisticMessageBubble(
                            message = item.message,
                            chatType = chatType,
                            selectionResetGeneration = selectionResetGeneration,
                            onRetry = onRetryOptimisticMessage,
                            canRetryFailedTextMessages = canRetryFailedTextMessages,
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
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    chatType: ChatType,
    selectionResetGeneration: Int,
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
                topStart = RealsRadii.Row,
                topEnd = RealsRadii.Row,
                bottomStart = if (mine) RealsRadii.Row else 4.dp,
                bottomEnd = if (mine) 4.dp else RealsRadii.Row,
            ),
            border = if (mine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(
                containerColor = if (mine) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                when (val presentation = message.presentation) {
                    is ChatMessagePresentation.Text -> SelectableMessageText(
                        presentation = chatMessageTextPresentation(
                            content = presentation.content,
                            chatType = chatType,
                        ),
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
    chatType: ChatType,
    selectionResetGeneration: Int,
    onRetry: (localId: String, content: String) -> Unit,
    canRetryFailedTextMessages: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = RealsRadii.Row,
                topEnd = RealsRadii.Row,
                bottomStart = RealsRadii.Row,
                bottomEnd = 4.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (
                    optimisticTextRetryAvailable(message, canRetryFailedTextMessages)
                ) {
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
private fun SelectableMessageText(
    presentation: ChatMessageTextPresentation,
    selectionResetGeneration: Int,
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
            )
        }
    }
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
