package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.realsOutlinedTextFieldColors
import com.reals.app.ui.common.VisualReviewDetailDeadlineStrings
import com.reals.app.ui.common.formatVisualReviewDetailDeadline
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType

internal data class VisualApprovalPresentationState(
    val showInitialLoading: Boolean,
    val showInitialFailure: Boolean,
    val showLoadedContent: Boolean,
    val showRefreshingIndicator: Boolean,
    val showProfileRetry: Boolean,
    val showPartnerMessageRetry: Boolean,
)

internal data class PartnerPersonalMessagePresentationState(
    val hasUnreadPartnerMessage: Boolean,
    val emphasized: Boolean,
    val badgeLabel: String?,
    val body: String,
    val showReadAction: Boolean,
    val readActionLabel: String?,
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

internal fun visualApprovalCanMakeDecision(
    profile: VisualProfile?,
    busy: Boolean,
    lifecycle: VisualApprovalLifecycleUiState,
): Boolean = !busy && profile != null && !lifecycle.expired

internal fun partnerPersonalMessagePresentationState(
    profile: VisualProfile?,
    partnerMessage: String?,
    partnerMessageLoaded: Boolean,
    readingPartnerMessage: Boolean,
    partnerMessageError: ApiError?,
    refreshing: Boolean,
): PartnerPersonalMessagePresentationState {
    val submitted = profile?.partnerPersonalMessageSubmitted == true
    val hasUnreadPartnerMessage = submitted && profile?.partnerPersonalMessageRead == false
    val body = when {
        profile == null -> "Cargando mensaje personal..."
        !submitted -> "No dejó un mensaje personal."
        readingPartnerMessage -> "Leyendo mensaje..."
        partnerMessageError != null -> "No pudimos cargar el mensaje personal. Intentá nuevamente."
        hasUnreadPartnerMessage -> "La otra persona dejó un mensaje personal para vos."
        partnerMessageLoaded -> partnerMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { TextSafety.safeDisplay(it, maxLength = 280) }
            ?: "No dejó un mensaje personal."
        !partnerMessageLoaded -> "Cargando mensaje personal..."
        else -> "No dejó un mensaje personal."
    }
    val showReadAction = submitted && !partnerMessageLoaded
    return PartnerPersonalMessagePresentationState(
        hasUnreadPartnerMessage = hasUnreadPartnerMessage,
        emphasized = hasUnreadPartnerMessage,
        badgeLabel = if (hasUnreadPartnerMessage) "Mensaje nuevo" else null,
        body = body,
        showReadAction = showReadAction,
        readActionLabel = if (showReadAction) {
            when {
                readingPartnerMessage -> "Leyendo mensaje..."
                refreshing || partnerMessageError != null -> "Reintentar lectura"
                else -> "Leer mensaje"
            }
        } else {
            null
        },
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
    val canMakeVisualDecision = visualApprovalCanMakeDecision(
        profile = profile,
        busy = busy,
        lifecycle = lifecycle,
    )

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
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Revisión visual",
                    modifier = Modifier.weight(1f),
                    style = RealsType.ScreenTitle,
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
            RealsBrandDivider(modifier = Modifier.padding(top = 16.dp))
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
                Text("Volver a Inicio")
            }
        } else {
        error?.let {
            ApiErrorFeedbackCard(it, ErrorContext.VisualReview)
            Spacer(modifier = Modifier.height(16.dp))
        }
        message?.let {
            SuccessFeedback(it)
            Spacer(modifier = Modifier.height(16.dp))
        }
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
                message = "La revisi\u00f3n visual vence pronto. Complet\u00e1 tu decisi\u00f3n para no perder esta oportunidad.",
                tone = FeedbackTone.Warning,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (profile == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RealsRadii.Card),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
            VisualProfileCard(
                profile = profile,
                presentationMode = ProfilePresentationMode.Review,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PartnerMessageCard(
            profile = profile,
            partnerMessage = partnerMessage,
            partnerMessageLoaded = partnerMessageLoaded,
            readingPartnerMessage = readingPartnerMessage,
            partnerMessageError = partnerMessageError,
            busy = busy,
            refreshing = refreshing,
            onReadPartnerMessage = onReadPartnerMessage,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RealsRadii.Card),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        shape = RoundedCornerShape(RealsRadii.Button),
                        colors = realsOutlinedTextFieldColors(),
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
            shape = RoundedCornerShape(RealsRadii.Card),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Decisión visual", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Si aprobás y la otra persona también aprueba, se crea la conexión para la siguiente etapa.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VisualDecisionActions(
                    deciding = deciding,
                    decidingLabel = decidingLabel,
                    enabled = canMakeVisualDecision,
                    onApprove = onApprove,
                    onReject = onReject,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onBackHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a Inicio")
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
internal fun VisualDecisionActions(
    deciding: Boolean,
    decidingLabel: String?,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val actionLayout = visualDecisionActionsLayout(maxWidth, LocalDensity.current.fontScale)
        val approveLabel = if (deciding) decidingLabel ?: "Procesando..." else "Aprobar"
        val rejectLabel = if (deciding) decidingLabel ?: "Procesando..." else "Rechazar"
        when (actionLayout) {
            VisualDecisionActionsLayout.Row -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(VisualDecisionActionsRowTag),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VisualApproveButton(
                        label = approveLabel,
                        enabled = enabled,
                        onApprove = onApprove,
                        modifier = Modifier.weight(1f),
                    )
                    VisualRejectButton(
                        label = rejectLabel,
                        enabled = enabled,
                        onReject = onReject,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            VisualDecisionActionsLayout.Stacked -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(VisualDecisionActionsStackedTag),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VisualApproveButton(
                        label = approveLabel,
                        enabled = enabled,
                        onApprove = onApprove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VisualRejectButton(
                        label = rejectLabel,
                        enabled = enabled,
                        onReject = onReject,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualApproveButton(
    label: String,
    enabled: Boolean,
    onApprove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onApprove,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = VisualDecisionActionMinHeight)
            .testTag(VisualDecisionApproveTag),
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VisualRejectButton(
    label: String,
    enabled: Boolean,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onReject,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = VisualDecisionActionMinHeight)
            .testTag(VisualDecisionRejectTag),
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

internal enum class VisualDecisionActionsLayout {
    Row,
    Stacked,
}

internal fun visualDecisionActionsLayout(maxWidth: Dp, fontScale: Float): VisualDecisionActionsLayout {
    val constrained = maxWidth < 300.dp ||
        (maxWidth < 340.dp && fontScale >= 1.3f) ||
        fontScale >= 1.8f
    return if (constrained) VisualDecisionActionsLayout.Stacked else VisualDecisionActionsLayout.Row
}

internal val VisualDecisionActionMinHeight = 48.dp
internal const val VisualDecisionActionsRowTag = "visual_decision_actions_row"
internal const val VisualDecisionActionsStackedTag = "visual_decision_actions_stacked"
internal const val VisualDecisionApproveTag = "visual_decision_approve"
internal const val VisualDecisionRejectTag = "visual_decision_reject"

@Composable
private fun VisualApprovalInitialLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
private fun PartnerMessageCard(
    profile: VisualProfile?,
    partnerMessage: String?,
    partnerMessageLoaded: Boolean,
    readingPartnerMessage: Boolean,
    partnerMessageError: ApiError?,
    busy: Boolean,
    refreshing: Boolean,
    onReadPartnerMessage: () -> Unit,
) {
    val messageState = partnerPersonalMessagePresentationState(
        profile = profile,
        partnerMessage = partnerMessage,
        partnerMessageLoaded = partnerMessageLoaded,
        readingPartnerMessage = readingPartnerMessage,
        partnerMessageError = partnerMessageError,
        refreshing = refreshing,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(
            1.dp,
            if (messageState.emphasized) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mensaje personal de la otra persona", style = MaterialTheme.typography.titleMedium)
            messageState.badgeLabel?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = messageState.body,
                color = if (messageState.emphasized) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (messageState.showReadAction) {
                OutlinedButton(
                    onClick = onReadPartnerMessage,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(messageState.readActionLabel ?: "Leer mensaje")
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
