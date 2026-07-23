package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.ManualBlockConfirmationDialog
import com.reals.app.ui.common.ManualBlockOverflowMenu
import com.reals.app.ui.common.VisualReviewDetailDeadlineStrings
import com.reals.app.ui.common.formatVisualReviewDetailDeadline
import com.reals.app.ui.common.userLabel

internal data class VisualApprovalPresentationState(
    val showInitialLoading: Boolean,
    val showInitialFailure: Boolean,
    val showLoadedContent: Boolean,
    val showRefreshingIndicator: Boolean,
    val showProfileRetry: Boolean,
    val showPartnerMessageRetry: Boolean,
)

internal fun visualApprovalPresentationState(
    match: Match?,
    profile: VisualProfile?,
    loading: Boolean,
    refreshing: Boolean,
    error: ApiError?,
    partnerMessageError: ApiError?,
): VisualApprovalPresentationState {
    val hasContent = match != null || profile != null
    val showInitialLoading = loading && !hasContent && error == null
    val showInitialFailure = !loading && !hasContent && error != null
    return VisualApprovalPresentationState(
        showInitialLoading = showInitialLoading,
        showInitialFailure = showInitialFailure,
        showLoadedContent = !showInitialLoading && !showInitialFailure,
        showRefreshingIndicator = refreshing && hasContent,
        showProfileRetry = !loading && profile == null && error != null,
        showPartnerMessageRetry = profile != null && partnerMessageError != null,
    )
}

@Composable
fun VisualApprovalScreen(
    matchId: String,
    match: Match?,
    profile: VisualProfile?,
    partnerMessage: String?,
    partnerMessageLoaded: Boolean,
    readingPartnerMessage: Boolean,
    partnerMessageError: ApiError?,
    myPersonalMessageSubmitted: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    writingMessage: Boolean,
    deciding: Boolean,
    decidingLabel: String?,
    manualBlockLoading: Boolean,
    manualBlockError: ApiError?,
    error: ApiError?,
    message: String?,
    onRefresh: () -> Unit,
    onReadPartnerMessage: () -> Unit,
    onSavePersonalMessage: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onManualBlock: () -> Unit,
    onClearManualBlockError: () -> Unit,
    onBackHome: () -> Unit,
) {
    var personalMessage by rememberSaveable(matchId) { mutableStateOf("") }
    var nowMillis by rememberSaveable(matchId) { mutableStateOf(System.currentTimeMillis()) }
    var expiryRefreshRequested by rememberSaveable(matchId) { mutableStateOf(false) }
    var showingManualBlockDialog by rememberSaveable(matchId) { mutableStateOf(false) }
    val busy =
        loading || refreshing || readingPartnerMessage || writingMessage || deciding ||
            manualBlockLoading
    val decisionBlockedByUnreadPartnerMessage =
        profile?.decisionRequiresPartnerPersonalMessageRead == true
    val visualExpiresAt = profile?.visualExpiresAt ?: match?.visualExpiresAt
    val lifecycle = visualApprovalLifecycleUiState(visualExpiresAt, nowMillis)
    val visualDeadlineText = formatVisualReviewDetailDeadline(
        visualExpiresAt = visualExpiresAt,
        nowMillis = nowMillis,
        strings = visualReviewDetailDeadlineStrings(),
    )
    val presentationState = visualApprovalPresentationState(
        match = match,
        profile = profile,
        loading = loading,
        refreshing = refreshing,
        error = error,
        partnerMessageError = partnerMessageError,
    )
    val canMakeVisualDecision = !busy &&
        profile != null &&
        !decisionBlockedByUnreadPartnerMessage &&
        !lifecycle.expired

    androidx.compose.runtime.LaunchedEffect(visualExpiresAt) {
        while (visualExpiresAt != null && !visualApprovalLifecycleUiState(visualExpiresAt).expired) {
            kotlinx.coroutines.delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
        nowMillis = System.currentTimeMillis()
    }

    androidx.compose.runtime.LaunchedEffect(lifecycle.expired) {
        if (lifecycle.expired && !expiryRefreshRequested) {
            expiryRefreshRequested = true
            onRefresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Aprobación visual",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                ManualBlockOverflowMenu(
                    enabled = !busy,
                    onRequestBlock = {
                        onClearManualBlockError()
                        showingManualBlockDialog = true
                    },
                )
            }
            Text(
                text = "Revisá el perfil visual antes de decidir si querés continuar.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            visualDeadlineText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (presentationState.showInitialLoading) {
            VisualApprovalInitialLoadingCard()
        } else if (presentationState.showInitialFailure) {
            VisualApprovalInitialFailureCard(
                error = error,
                refreshing = refreshing,
                busy = busy,
                onRefresh = onRefresh,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onBackHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Volver a Home")
            }
        } else {
        StatusCard(
            match = match,
            loading = loading,
            refreshing = refreshing,
            error = error,
            message = message,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (lifecycle.expired) {
            FeedbackCard(
                title = "Estado",
                message = "La revisi\u00f3n visual venci\u00f3. Actualizando estado...",
                tone = FeedbackTone.Warning,
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else if (lifecycle.showWarning) {
            FeedbackCard(
                title = "Revisi\u00f3n por vencer",
                message = "La revisi\u00f3n visual vence pronto. Complet\u00e1 tu decisi\u00f3n para no perder éstaoportunidad.",
                tone = FeedbackTone.Warning,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (profile == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Perfil visual", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (loading) "Cargando perfil..." else "No pudimos cargar el perfil visual todavía.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (presentationState.showProfileRetry) {
                        OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (refreshing) "Actualizando..." else "Reintentar")
                        }
                    }
                }
            }
        } else {
            VisualProfileCard(profile)
        }
        Spacer(modifier = Modifier.height(16.dp))
        PartnerMessageCard(
            profile = profile,
            partnerMessage = partnerMessage,
            partnerMessageLoaded = partnerMessageLoaded,
            readingPartnerMessage = readingPartnerMessage,
            partnerMessageError = partnerMessageError,
            decisionRequiresPartnerPersonalMessageRead = decisionBlockedByUnreadPartnerMessage,
            busy = busy,
            refreshing = refreshing,
            onReadPartnerMessage = onReadPartnerMessage,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mi mensaje personal", style = MaterialTheme.typography.titleMedium)
                if (myPersonalMessageSubmitted) {
                    Text(
                        text = "Ya guardaste tu mensaje personal. No se puede modificar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = personalMessage,
                        onValueChange = { personalMessage = it.take(280) },
                        label = { Text("Mensaje personal") },
                        enabled = !busy,
                        minLines = 2,
                        supportingText = { Text("${personalMessage.length}/280") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            onSavePersonalMessage(personalMessage)
                        },
                        enabled = !busy && personalMessage.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (writingMessage) "Guardando..." else "Guardar mensaje")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Decisión visual", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Si aprobás y la otra persona también aprueba, se crea la conexión para la siguiente etapa.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (decisionBlockedByUnreadPartnerMessage) {
                    Text(
                        text = "Leé el mensaje personal de la otra persona antes de decidir.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onApprove,
                        enabled = canMakeVisualDecision,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (deciding) decidingLabel ?: "Procesando..." else "Aprobar")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        enabled = canMakeVisualDecision,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (deciding) decidingLabel ?: "Procesando..." else "Rechazar")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onBackHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a Home")
        }
        }
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
private fun VisualApprovalInitialLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cargando revisión visual", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Estamos cargando el perfil y el estado de la revisión.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VisualApprovalInitialFailureCard(
    error: ApiError?,
    refreshing: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No pudimos cargar la revisión visual", style = MaterialTheme.typography.titleLarge)
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.VisualReview) }
            OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (refreshing) "Actualizando..." else "Reintentar")
            }
        }
    }
}

@Composable
private fun StatusCard(
    match: Match?,
    loading: Boolean,
    refreshing: Boolean,
    error: ApiError?,
    message: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estado", style = MaterialTheme.typography.titleLarge)
            Text("Match: ${match?.state?.userLabel() ?: "Cargando"}")
            if (loading || refreshing) {
                Text(
                    text = if (loading) "Cargando revisión visual..." else "Actualizando revisión visual...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.VisualReview) }
            message?.let { SuccessFeedback(it) }
        }
    }
}

@Composable
private fun PartnerMessageCard(
    profile: VisualProfile?,
    partnerMessage: String?,
    partnerMessageLoaded: Boolean,
    readingPartnerMessage: Boolean,
    partnerMessageError: ApiError?,
    decisionRequiresPartnerPersonalMessageRead: Boolean,
    busy: Boolean,
    refreshing: Boolean,
    onReadPartnerMessage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mensaje personal de la otra persona", style = MaterialTheme.typography.titleMedium)
            val partnerPersonalMessageSubmitted = profile?.partnerPersonalMessageSubmitted == true
            val body = when {
                profile == null -> "Cargando mensaje personal..."
                !partnerPersonalMessageSubmitted -> "La otra persona todavía no dejó un mensaje personal."
                readingPartnerMessage -> "Leyendo mensaje..."
                partnerMessageError != null -> "No pudimos cargar el mensaje personal. Intentá nuevamente."
                partnerMessageLoaded -> partnerMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { TextSafety.safeDisplay(it, maxLength = 280) }
                    ?: "La otra persona todavía no dejó un mensaje personal."
                !partnerMessageLoaded && decisionRequiresPartnerPersonalMessageRead ->
                    "La otra persona dejó un mensaje personal. Tenés que leerlo antes de decidir."
                !partnerMessageLoaded -> "Cargando mensaje personal..."
                else -> "La otra persona todavía no dejó un mensaje personal."
            }
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (partnerPersonalMessageSubmitted && !partnerMessageLoaded) {
                OutlinedButton(
                    onClick = onReadPartnerMessage,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            readingPartnerMessage -> "Leyendo mensaje..."
                            refreshing || partnerMessageError != null -> "Reintentar lectura"
                            else -> "Leer mensaje"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessFeedback(message: String) {
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success)
}

@Composable
private fun visualReviewDetailDeadlineStrings(): VisualReviewDetailDeadlineStrings =
    VisualReviewDetailDeadlineStrings(
        futureSameYear = stringResource(R.string.visual_review_deadline_detail_future_same_year),
        futureDifferentYear = stringResource(R.string.visual_review_deadline_detail_future_different_year),
        pastSameYear = stringResource(R.string.visual_review_deadline_detail_past_same_year),
        pastDifferentYear = stringResource(R.string.visual_review_deadline_detail_past_different_year),
    )
