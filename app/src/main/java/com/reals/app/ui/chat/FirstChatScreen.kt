package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatContinueDecision
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
import kotlinx.coroutines.delay

@Composable
fun FirstChatScreen(
    currentUserId: String,
    matchId: String,
    match: Match?,
    chat: Chat?,
    messages: List<ChatMessage>,
    exitRequests: List<ChatExitRequest>,
    loading: Boolean,
    refreshing: Boolean,
    sending: Boolean,
    actionLoading: Boolean,
    error: ApiError?,
    message: String?,
    onRefresh: () -> Unit,
    onSendMessage: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRequestMutualExit: () -> Unit,
    onCancelUnilaterally: () -> Unit,
    onSafetyCancel: (String) -> Unit,
    onAcceptExitRequest: (String) -> Unit,
    onRejectExitRequest: (String) -> Unit,
    onBackHome: () -> Unit,
) {
    var draft by rememberSaveable(chat?.id) { mutableStateOf("") }
    var safetyDetails by rememberSaveable(chat?.id) { mutableStateOf("") }
    val busy = loading || refreshing || sending || actionLoading
    val canChat = chat?.status == ChatStatus.Active
    val canDecide = match?.state == MatchState.ChatActive && chat?.status == ChatStatus.Active
    val pendingPartnerExitRequests = exitRequests.filter {
        it.status == ChatExitRequestStatus.Pending && it.requesterUserId != currentUserId
    }

    LaunchedEffect(chat?.id, canChat) {
        while (canChat) {
            delay(5000)
            onRefresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Chat",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Conversa unos minutos y decidi si queres continuar.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado", style = MaterialTheme.typography.titleLarge)
                Text("Experiencia: ${match?.state?.userLabel() ?: "Cargando"}")
                Text("Chat: ${chat?.status?.userLabel() ?: "Cargando"}")
                Text("Disponible hasta: ${chat?.timeoutAt?.substringBefore("T") ?: "-"}")
                error?.let { ApiErrorFeedbackCard(it, ErrorContext.Chat) }
                message?.let { SuccessFeedback(it) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        MessagesCard(
            currentUserId = currentUserId,
            messages = messages,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enviar mensaje", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Mensaje") },
                    enabled = !busy && canChat,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onSendMessage(draft)
                        draft = ""
                    },
                    enabled = !busy && canChat && draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (sending) "Enviando..." else "Enviar")
                }
                if (!canChat) {
                    Text(
                        text = "Este chat no esta disponible para enviar mensajes en este momento.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Decisiones y cancelacion", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onApprove, enabled = !busy && canDecide, modifier = Modifier.weight(1f)) {
                        Text("Aprobar")
                    }
                    OutlinedButton(onClick = onReject, enabled = !busy && canDecide, modifier = Modifier.weight(1f)) {
                        Text("Rechazar")
                    }
                }
                OutlinedButton(onClick = onRequestMutualExit, enabled = !busy && canChat, modifier = Modifier.fillMaxWidth()) {
                    Text("Pedir cancelacion mutua")
                }
                OutlinedButton(onClick = onCancelUnilaterally, enabled = !busy && canChat, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar unilateralmente")
                }
                OutlinedTextField(
                    value = safetyDetails,
                    onValueChange = { safetyDetails = it },
                    label = { Text("Detalle reporte seguridad") },
                    enabled = !busy && canChat,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { onSafetyCancel(safetyDetails) },
                    enabled = !busy && canChat && safetyDetails.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancelar por seguridad")
                }
                pendingPartnerExitRequests.forEach { request ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Solicitud de cancelacion: ${request.reason?.userLabel() ?: "Sin motivo indicado"}")
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
                if (exitRequests.isNotEmpty()) {
                    Text(
                        text = "Solicitudes: ${exitRequests.joinToString { "${it.type.userLabel()} (${it.status.userLabel()})" }}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onBackHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a Home")
        }
    }
}

@Composable
private fun MessagesCard(
    currentUserId: String,
    messages: List<ChatMessage>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mensajes", style = MaterialTheme.typography.titleLarge)
            if (messages.isEmpty()) {
                Text("Todavia no hay mensajes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                messages.sortedBy { it.sentAt }.forEach { item ->
                    val sender = if (item.senderId == currentUserId) "Yo" else "Partner"
                    Text("$sender - ${item.sentAt}")
                    Text(item.content)
                }
            }
        }
    }
}

@Composable
private fun SuccessFeedback(message: String) {
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success)
}
