package com.reals.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.theme.RealsRadii

@Composable
internal fun FirstChatLifecyclePanel(
    lifecycle: FirstChatLifecycleUiState?,
) {
    lifecycle?.takeIf { it.showCountdown || it.expired }?.let {
        FeedbackCard(
            title = if (it.expired) "Estado" else "Tiempo restante",
            message = if (it.expired) it.expiredCopy() else it.warningCopy(),
            tone = FeedbackTone.Warning,
        )
    }
}

@Composable
internal fun FirstChatUnansweredSuggestionCard(
    state: FirstChatUnansweredSuggestionState,
    onRequestMutualExit: () -> Unit,
    onDismiss: (String) -> Unit,
) {
    if (!state.visible) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Todavía no recibiste respuesta",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = { state.periodReference?.let(onDismiss) },
                    enabled = state.periodReference != null,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Ocultar sugerencia",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "Podés solicitar el cierre de la conversación. Si la otra persona no responde a la solicitud, el chat se cerrará sin penalizarte.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRequestMutualExit,
                enabled = state.actionEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Solicitar cierre")
            }
        }
    }
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
internal fun FirstChatGuidancePanel(
    state: FirstChatGuidancePanelState?,
    actionLoading: Boolean,
    onRequestNext: (() -> Unit)?,
) {
    if (state == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val nextQuestionAvailable = state.showButton &&
                state.buttonEnabled &&
                !actionLoading &&
                onRequestNext != null

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = TextSafety.safeDisplay(state.questionText),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (nextQuestionAvailable) {
                    IconButton(
                        onClick = { onRequestNext?.invoke() },
                        modifier = Modifier.semantics { contentDescription = "Otra pregunta" },
                    ) {
                        Text(
                            text = "›",
                            modifier = Modifier.clearAndSetSemantics {},
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (state.showWaitingCopy) {
                Text(
                    text = "Cambiaremos la pregunta cuando ambos quieran seguir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun FirstChatDecisionOnlyPanel(
    state: FirstChatDecisionOnlyPanelState,
    actionLoading: Boolean,
    canDecide: Boolean,
    actionLoadingLabel: String?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    if (!state.visible) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.approvalCopy?.let { copy ->
                Text(
                    text = copy,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = state.prompt,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onApprove,
                    enabled = !actionLoading && canDecide,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (actionLoading) actionLoadingLabel ?: "Procesando..." else "Aprobar")
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !actionLoading && canDecide,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (actionLoading) actionLoadingLabel ?: "Procesando..." else "No aprobar")
                }
            }
        }
    }
}
