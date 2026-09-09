package com.reals.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatCompletionDecision
import com.reals.app.domain.model.SecondChatResolutionRequestType
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.root.SecondChatEntryAvailabilityState
import com.reals.app.ui.root.SecondChatLifecycleUiState
import com.reals.app.ui.root.SecondChatResolutionPresentation
import com.reals.app.ui.root.entryAvailabilityPresentation
import com.reals.app.ui.root.hasPendingNoShowClaim
import com.reals.app.ui.root.isWaitingForPartner
import com.reals.app.ui.root.remainingMillisFromServerSnapshot
import com.reals.app.ui.root.secondChatResultCopy
import com.reals.app.ui.theme.RealsRadii
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal const val SECOND_CHAT_COMPLETION_COACHMARK_COPY =
    "Ya podés finalizar la charla de común acuerdo desde acá."

@Composable
internal fun SecondChatAbsoluteExpiryWarning(
    visible: Boolean,
) {
    if (!visible) return
    FeedbackCard(
        title = "Tiempo restante",
        message = "El segundo chat vence pronto. Al finalizar volverás a Home.",
        tone = FeedbackTone.Warning,
    )
}

@Composable
internal fun SecondChatLifecyclePanel(
    lifecycle: SecondChatLifecycleUiState?,
    partnerName: String?,
    actionLoading: Boolean,
    partnerEntryCutoffReached: Boolean,
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
        status.entryAvailabilityPresentation(
            statusReceivedAtMillis = lifecycle.statusReceivedAtMillis,
            nowMillis = nowMillis,
        )?.state in listOf(
            SecondChatEntryAvailabilityState.BeforeStart,
            SecondChatEntryAvailabilityState.EntryClosed,
            SecondChatEntryAvailabilityState.Unavailable,
        ) -> {
            val presentation = status.entryAvailabilityPresentation(
                statusReceivedAtMillis = lifecycle.statusReceivedAtMillis,
                nowMillis = nowMillis,
            )
            FeedbackCard(
                title = presentation?.title ?: "Segundo chat no disponible",
                message = presentation?.message ?: "No se puede entrar en este momento.",
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
                shape = RoundedCornerShape(RealsRadii.Card),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    if (shouldShowPartnerNoShowClaim(status.canClaimPartnerNoShow, partnerEntryCutoffReached)) {
                        Text(
                            "Si pasa el tiempo de espera, podés reclamar. " +
                                "Si la otra persona no entra durante el plazo de confirmación, la cita termina sin penalizarte.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onClaimNoShow,
                            enabled = !actionLoading && lifecycle.claimingNoShow.not(),
                        ) {
                            Text("$safePartnerName no se presentó")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SecondChatResolutionPanel(
    presentation: SecondChatResolutionPresentation?,
    actionLoading: Boolean,
    actionLoadingLabel: String?,
    onAcceptCompletion: (String) -> Unit,
    onRejectCompletion: (String) -> Unit,
    onRequestInactivityClaim: () -> Unit,
) {
    val state = presentation ?: return
    if (!secondChatResolutionBodyVisible(state)) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.activeRequest?.let { request ->
                Text(request.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = request.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                request.remainingMillis?.let { remainingMillis ->
                    Text(
                        text = if (request.locallyExpired) {
                            "La solicitud venció. Actualizando estado..."
                        } else {
                            "Quedan ${((remainingMillis + 999) / 1000).coerceAtLeast(0)}s."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (request.type == SecondChatResolutionRequestType.MutualCompletion) {
                    Text(
                        text = "Pueden seguir conversando; un nuevo mensaje cancela ésta solicitud.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
internal fun SecondChatDialogs(
    resolution: SecondChatResolutionPresentation?,
    showingCompletionDialog: Boolean,
    showingInactivityDialog: Boolean,
    loadingChatAction: Boolean,
    onDismissCompletionDialog: () -> Unit,
    onDismissInactivityDialog: () -> Unit,
    onRequestSecondChatCompletion: () -> Unit,
    onClaimSecondChatInactivity: () -> Unit,
) {
    resolution?.createCompletion?.let { create ->
        if (showingCompletionDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!loadingChatAction) onDismissCompletionDialog()
                },
                title = { Text(create.confirmationTitle) },
                text = { Text(create.confirmationBody) },
                confirmButton = {
                    TextButton(
                        enabled = create.enabled && !loadingChatAction,
                        onClick = {
                            onDismissCompletionDialog()
                            onRequestSecondChatCompletion()
                        },
                    ) {
                        Text("Enviar solicitud")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !loadingChatAction,
                        onClick = onDismissCompletionDialog,
                    ) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }

    resolution?.createInactivityClaim?.let { create ->
        if (showingInactivityDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!loadingChatAction) onDismissInactivityDialog()
                },
                title = { Text(create.confirmationTitle) },
                text = { Text(create.confirmationBody) },
                confirmButton = {
                    TextButton(
                        enabled = create.enabled && !loadingChatAction,
                        onClick = {
                            onDismissInactivityDialog()
                            onClaimSecondChatInactivity()
                        },
                    ) {
                        Text("Enviar reclamo")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !loadingChatAction,
                        onClick = onDismissInactivityDialog,
                    ) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

internal data class SecondChatCompletionOverflowPresentation(
    val visible: Boolean,
    val enabled: Boolean,
    val label: String,
)

internal fun secondChatCompletionOverflowPresentation(
    presentation: SecondChatResolutionPresentation?,
): SecondChatCompletionOverflowPresentation {
    val createCompletion = presentation?.createCompletion
    return SecondChatCompletionOverflowPresentation(
        visible = createCompletion != null,
        enabled = createCompletion?.enabled == true,
        label = createCompletion?.label.orEmpty(),
    )
}

internal fun secondChatSafetyActionsAllowed(
    chatType: ChatType?,
    attendanceStatus: SecondChatAttendanceStatus?,
): Boolean =
    chatType != ChatType.SecondChat ||
        attendanceStatus == SecondChatAttendanceStatus.OnTime ||
        attendanceStatus == SecondChatAttendanceStatus.Late

internal fun shouldShowBackHomeAction(
    hasBackHomeCallback: Boolean,
    hasSecondChatLifecycle: Boolean,
    genuinelyActive: Boolean,
    canReturnAfterPartnerCutoff: Boolean,
): Boolean =
    hasBackHomeCallback &&
        (
            !hasSecondChatLifecycle ||
                !genuinelyActive ||
                canReturnAfterPartnerCutoff
            )

internal fun shouldShowPartnerNoShowClaim(
    backendCanClaim: Boolean,
    partnerEntryCutoffReached: Boolean,
): Boolean = backendCanClaim && !partnerEntryCutoffReached

internal fun secondChatCompletionOverflowMenuItemEnabled(
    action: SecondChatCompletionOverflowPresentation,
    actionLoading: Boolean,
): Boolean = action.visible && action.enabled && !actionLoading

internal fun handleSecondChatCompletionOverflowClick(
    action: SecondChatCompletionOverflowPresentation,
    actionLoading: Boolean,
    onCloseMenu: () -> Unit,
    onShowConfirmation: () -> Unit,
): Boolean {
    if (!secondChatCompletionOverflowMenuItemEnabled(action, actionLoading)) return false
    onCloseMenu()
    onShowConfirmation()
    return true
}

internal fun secondChatResolutionBodyVisible(
    presentation: SecondChatResolutionPresentation?,
): Boolean =
    presentation?.activeRequest != null ||
        presentation?.completionCooldown != null ||
        presentation?.createInactivityClaim != null

internal data class MutualCompletionCoachmarkState(
    val baselineEstablished: Boolean = false,
    val previouslyEligible: Boolean = false,
    val alreadyShown: Boolean = false,
) {
    fun next(eligible: Boolean): MutualCompletionCoachmarkUpdate =
        when {
            !baselineEstablished -> MutualCompletionCoachmarkUpdate(
                state = copy(
                    baselineEstablished = true,
                    previouslyEligible = eligible,
                ),
                showCoachmark = false,
            )
            !alreadyShown && !previouslyEligible && eligible -> MutualCompletionCoachmarkUpdate(
                state = copy(
                    previouslyEligible = true,
                    alreadyShown = true,
                ),
                showCoachmark = true,
            )
            else -> MutualCompletionCoachmarkUpdate(
                state = copy(previouslyEligible = eligible),
                showCoachmark = false,
            )
        }
}

internal data class MutualCompletionCoachmarkUpdate(
    val state: MutualCompletionCoachmarkState,
    val showCoachmark: Boolean,
)
