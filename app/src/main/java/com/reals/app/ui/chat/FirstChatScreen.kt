package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.userLabel
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val MUTUAL_EXIT_TIMEOUT_SECONDS = 20L

@Composable
fun FirstChatScreen(
    currentUserId: String,
    matchId: String,
    match: Match?,
    chat: Chat?,
    messages: List<ChatMessage>,
    exitRequests: List<ChatExitRequest>,
    loading: Boolean,
    sending: Boolean,
    actionLoading: Boolean,
    error: ApiError?,
    message: String?,
    onRefresh: () -> Unit,
    onSendMessage: (String) -> Unit,
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
    var showingActionsDialog by rememberSaveable(chat?.id) { mutableStateOf(false) }
    val busy = loading || sending || actionLoading
    val canChat = chat?.status == ChatStatus.Active
    val pendingExitRequest = exitRequests
        .filter { it.status == ChatExitRequestStatus.Pending }
        .maxByOrNull { it.createdAt }
    val exitFlowLocked = pendingExitRequest != null
    val canDecide = match?.state == MatchState.ChatActive &&
        chat?.status == ChatStatus.Active &&
        chat.myDecision == ChatDecisionState.Pending &&
        !exitFlowLocked
    val partnerDisplayName = chat?.partner?.displayName
        ?.takeIf { it.isNotBlank() }

    LaunchedEffect(chat?.id, canChat) {
        while (canChat) {
            delay(2000.milliseconds)
            onRefresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatHeader(
            partnerName = partnerDisplayName,
            expiresAt = chat?.expiresAt,
            myDecision = chat?.myDecision,
            partnerDecision = chat?.partnerDecision,
        )
        error?.let { ApiErrorFeedbackCard(it, ErrorContext.Chat) }
        message?.let { SuccessFeedback(it) }
        MessageList(
            currentUserId = currentUserId,
            messages = messages,
            modifier = Modifier.weight(1f),
        )
        ChatComposer(
            draft = draft,
            canChat = canChat,
            busy = busy,
            sending = sending,
            canDecide = canDecide,
            currentUserId = currentUserId,
            activeExitRequest = pendingExitRequest,
            canOpenActions = !exitFlowLocked,
            onDraftChange = { draft = it.take(1_000) },
            onSend = {
                onSendMessage(draft)
                draft = ""
            },
            onApprove = onApprove,
            onShowActions = { showingActionsDialog = true },
            onAcceptExitRequest = onAcceptExitRequest,
            onRejectExitRequest = onRejectExitRequest,
            onExitRequestTimeout = onExitRequestTimeout,
        )
    }

    if (showingSafetyDialog) {
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

    if (showingActionsDialog) {
        ChatActionsDialog(
            actionLoading = actionLoading,
            canChat = canChat,
            canDecide = canDecide,
            exitRequests = exitRequests,
            onDismiss = {
                if (!actionLoading) showingActionsDialog = false
            },
            onRequestMutualExit = {
                showingActionsDialog = false
                onRequestMutualExit()
            },
            onRejectChat = {
                showingActionsDialog = false
                onReject()
            },
            onShowSafety = {
                showingActionsDialog = false
                showingSafetyDialog = true
            },
        )
    }
}

@Composable
private fun ChatHeader(
    partnerName: String?,
    expiresAt: String?,
    myDecision: ChatDecisionState?,
    partnerDecision: ChatDecisionState?,
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
            Text(
                text = partnerName?.let { "Chat con ${TextSafety.safeDisplay(it)}" } ?: "Cargando chat",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Valido hasta ${formatBackendDateTime(expiresAt)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            chatDecisionSummary(myDecision, partnerDecision, partnerName)?.let {
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
private fun MessageList(
    currentUserId: String,
    messages: List<ChatMessage>,
    modifier: Modifier,
) {
    val sortedMessages = messages.sortedBy { it.sentAt }
    val listState = rememberLazyListState()

    LaunchedEffect(sortedMessages.size) {
        if (sortedMessages.isNotEmpty()) {
            listState.animateScrollToItem(sortedMessages.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text("Todavia no hay mensajes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(sortedMessages, key = { it.id }) { item ->
                    MessageBubble(
                        message = item,
                        mine = item.senderId == currentUserId,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    canChat: Boolean,
    busy: Boolean,
    sending: Boolean,
    canDecide: Boolean,
    currentUserId: String,
    activeExitRequest: ChatExitRequest?,
    canOpenActions: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onApprove: () -> Unit,
    onShowActions: () -> Unit,
    onAcceptExitRequest: (String) -> Unit,
    onRejectExitRequest: (String) -> Unit,
    onExitRequestTimeout: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            activeExitRequest?.let { request ->
                TimedExitRequestCard(
                    currentUserId = currentUserId,
                    request = request,
                    busy = busy,
                    onAcceptExitRequest = onAcceptExitRequest,
                    onRejectExitRequest = onRejectExitRequest,
                    onExitRequestTimeout = onExitRequestTimeout,
                )
            }
            if (!canChat) {
                Text(
                    text = "Este chat no esta disponible para enviar mensajes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("Mensaje") },
                enabled = !busy && canChat,
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSend,
                    enabled = !busy && canChat && draft.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (sending) "Enviando..." else "Enviar")
                }
                OutlinedButton(
                    onClick = onShowActions,
                    enabled = !busy && canOpenActions,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Mas acciones")
                }
            }
            Button(
                onClick = onApprove,
                enabled = !busy && canDecide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Aprobar chat")
            }
        }
    }
}

@Composable
private fun TimedExitRequestCard(
    currentUserId: String,
    request: ChatExitRequest,
    busy: Boolean,
    onAcceptExitRequest: (String) -> Unit,
    onRejectExitRequest: (String) -> Unit,
    onExitRequestTimeout: (String) -> Unit,
) {
    var nowMillis by rememberSaveable(request.id) { mutableStateOf(System.currentTimeMillis()) }
    var timeoutHandled by rememberSaveable(request.id) { mutableStateOf(false) }
    val remainingSeconds = request.remainingExitSeconds(nowMillis)
    val requestedByMe = request.requesterUserId == currentUserId

    LaunchedEffect(request.id) {
        while (true) {
            delay(1_000)
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Aceptar")
                    }
                    OutlinedButton(
                        onClick = { onRejectExitRequest(request.id) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Rechazar")
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
private fun ChatActionsDialog(
    actionLoading: Boolean,
    canChat: Boolean,
    canDecide: Boolean,
    exitRequests: List<ChatExitRequest>,
    onDismiss: () -> Unit,
    onRequestMutualExit: () -> Unit,
    onRejectChat: () -> Unit,
    onShowSafety: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acciones del chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Opciones menos frecuentes para cerrar o reportar esta conversacion.")
                if (exitRequests.isNotEmpty()) {
                    Text(
                        text = "Solicitudes: ${exitRequests.joinToString { "${it.type.userLabel()} (${it.status.userLabel()})" }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !actionLoading && canChat,
                    onClick = onRequestMutualExit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Salida consensuada")
                }
                OutlinedButton(
                    enabled = !actionLoading && canDecide,
                    onClick = onRejectChat,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Rechazar chat")
                }
                OutlinedButton(
                    enabled = !actionLoading && canChat,
                    onClick = onShowSafety,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reportar seguridad")
                }
                TextButton(enabled = !actionLoading, onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        },
    )
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
        title = { Text("Reporte de seguridad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Describe que paso. Este reporte cierra el chat por seguridad.")
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
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success, modifier = modifier)
}

private fun formatBackendDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault()))
    }.getOrElse {
        value.replace("T", " ").substringBeforeLast(":")
    }
}

private fun formatBackendTime(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    }.getOrElse {
        value.substringAfter("T", value).take(5).ifBlank { "-" }
    }
}

private fun ChatExitRequest.remainingExitSeconds(nowMillis: Long): Long {
    val createdAtMillis = runCatching {
        OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
    }.getOrElse {
        Instant.now().toEpochMilli()
    }
    val elapsedSeconds = ((nowMillis - createdAtMillis).coerceAtLeast(0L)) / 1_000L
    return (MUTUAL_EXIT_TIMEOUT_SECONDS - elapsedSeconds).coerceAtLeast(0L)
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
