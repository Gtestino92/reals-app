package com.reals.app.ui.scheduling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reals.app.BuildConfig
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.SchedulingAvailability
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.ManualBlockConfirmationDialog
import com.reals.app.ui.common.ManualBlockOverflowMenu
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.formatBackendContextualDateTime
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private val PickerControlMinHeight = 48.dp
private val PickerOptionMinHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingScreen(
    connectionId: String,
    partnerName: String?,
    loading: Boolean,
    refreshing: Boolean,
    submitting: Boolean,
    submittingLabel: String?,
    manualBlockLoading: Boolean,
    manualBlockError: ApiError?,
    negotiation: SchedulingNegotiation?,
    proposals: List<SchedulingProposal>,
    availability: SchedulingAvailability?,
    currentUserId: String,
    error: ApiError?,
    message: String?,
    onRefresh: () -> Unit,
    onSubmitProposals: (List<String>) -> Unit,
    onAcceptProposal: (String) -> Unit,
    onRejectPartnerProposals: () -> Unit,
    onOpenPartnerProfile: () -> Unit,
    onManualBlock: () -> Unit,
    onClearManualBlockError: () -> Unit,
    onBackHome: () -> Unit,
) {
    val partnerDisplayName = partnerName?.takeIf { it.isNotBlank() }?.let(TextSafety::safeDisplay)
    val roundState = deriveSchedulingRoundState(
        loading = loading,
        negotiation = negotiation,
        proposals = proposals,
        currentUserId = currentUserId,
    )
    val stage = roundState.stage
    val myPendingProposals = roundState.myPendingProposals
    val partnerPendingProposals = roundState.partnerPendingProposals
    var nowMillis by rememberSaveable(connectionId) { mutableStateOf(System.currentTimeMillis()) }
    val draftScope = schedulingProposalDraftScope(connectionId, negotiation?.roundNumber)
    var selectedProposalDraft by rememberSaveable(draftScope.connectionId, draftScope.roundNumber) {
        mutableStateOf(emptyList<String>())
    }
    var expiryRefreshRequested by rememberSaveable(connectionId) { mutableStateOf(false) }
    var showingManualBlockDialog by rememberSaveable(connectionId) { mutableStateOf(false) }
    val lifecycle = schedulingLifecycleUiState(negotiation?.schedulingExpiresAt, nowMillis)
    val actionsDisabled = lifecycle.expired || manualBlockLoading
    val interactionBusy = loading || refreshing || submitting || manualBlockLoading
    val errorPlacement = schedulingErrorPlacement(stage, error)

    LaunchedEffect(connectionId, negotiation?.status?.rawValue) {
        while (negotiation?.status == NegotiationStatus.Pending) {
            delay(7.seconds)
            onRefresh()
        }
    }

    LaunchedEffect(negotiation?.schedulingExpiresAt) {
        while (
            negotiation?.schedulingExpiresAt != null &&
            !schedulingLifecycleUiState(negotiation.schedulingExpiresAt).expired
        ) {
            delay(1.seconds)
            nowMillis = System.currentTimeMillis()
        }
        nowMillis = System.currentTimeMillis()
    }

    LaunchedEffect(lifecycle.expired) {
        if (lifecycle.expired && !expiryRefreshRequested) {
            expiryRefreshRequested = true
            onRefresh()
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!interactionBusy) {
                onRefresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "Coordinar horarios",
                        modifier = Modifier.weight(1f),
                        style = RealsType.ScreenTitle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ManualBlockOverflowMenu(
                        enabled = !interactionBusy,
                        onRequestBlock = {
                            onClearManualBlockError()
                            showingManualBlockDialog = true
                        },
                    )
                }
                Text(
                    text = partnerDisplayName?.let { "Con $it" } ?: "Con la otra persona",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RealsBrandDivider(modifier = Modifier.padding(top = 16.dp))
            }
            if (negotiation != null && stage != SchedulingStage.Loading) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ronda ${negotiation.roundNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (refreshing) {
                        Text(
                            text = "Actualizando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            errorPlacement.topLevelError?.let {
                ApiErrorFeedbackCard(it, ErrorContext.Scheduling)
                Spacer(modifier = Modifier.height(12.dp))
            }

            SchedulingDeadlineProgressCard(
                negotiation = negotiation,
                nowMillis = nowMillis,
            )

            if (lifecycle.expired) {
                FeedbackCard(
                    title = "Estado",
                    message = "La coordinaci\u00f3n venci\u00f3. Actualizando estado...",
                    tone = FeedbackTone.Warning,
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else if (lifecycle.showWarning) {
                FeedbackCard(
                    title = "Coordinaci\u00f3n por vencer",
                    message = "La coordinaci\u00f3n de horarios vence pronto. Confirm\u00e1 o envi\u00e1 opciones para no perder la conexi\u00f3n.",
                    tone = FeedbackTone.Warning,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (stage) {
                SchedulingStage.Loading -> LoadingCard()
                SchedulingStage.WaitingForMyProposals -> ProposalSelectorCard(
                    submitting = submitting,
                    actionsDisabled = actionsDisabled,
                    submittingLabel = submittingLabel,
                    proposalError = errorPlacement.proposalError,
                    nowMillis = nowMillis,
                    availability = availability,
                    selected = selectedProposalDraft,
                    onSelectedChange = { selectedProposalDraft = it },
                    onSubmitProposals = onSubmitProposals,
                )

                SchedulingStage.WaitingForPartnerProposals -> WaitingPartnerCard(
                    myPendingProposals = myPendingProposals,
                    nowMillis = nowMillis,
                )
                SchedulingStage.ReviewPartnerProposals -> {
                    ReviewProposalsCard(
                        myPendingProposals = myPendingProposals,
                        partnerPendingProposals = partnerPendingProposals,
                        submitting = submitting,
                        actionsDisabled = actionsDisabled,
                        submittingLabel = submittingLabel,
                        reviewError = errorPlacement.reviewError,
                        nowMillis = nowMillis,
                        availability = availability,
                        onAcceptProposal = onAcceptProposal,
                        onRejectPartnerProposals = onRejectPartnerProposals,
                    )
                }

                SchedulingStage.Scheduled -> ScheduledCard(negotiation?.confirmedDateTime, nowMillis)
                SchedulingStage.Failed -> FailedCard()
                SchedulingStage.Unknown -> UnknownCard()
            }

            if (stage != SchedulingStage.Loading) {
                Spacer(modifier = Modifier.height(18.dp))
                if (BuildConfig.SHOW_EXPLICIT_REFRESH_BUTTONS) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !interactionBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (refreshing) "Actualizando..." else "Actualizar")
                    }
                }
                OutlinedButton(
                    onClick = onOpenPartnerProfile,
                    enabled = !interactionBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ver perfil")
                }
                Button(
                    onClick = onBackHome,
                    enabled = !interactionBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (submitting) submittingLabel ?: "Procesando..." else "Volver a Inicio")
                }
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
private fun SchedulingDeadlineProgressCard(
    negotiation: SchedulingNegotiation?,
    nowMillis: Long,
) {
    if (!shouldShowSchedulingDeadlineProgress(negotiation)) return

    val progress = schedulingDeadlineRemainingFraction(
        negotiationCreatedAt = negotiation?.createdAt,
        schedulingExpiresAt = negotiation?.schedulingExpiresAt,
        nowMillis = nowMillis,
    ) ?: return
    val deadlineLabel = formatBackendContextualDateTime(negotiation?.schedulingExpiresAt, nowMillis)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tiempo de coordinación", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Vence: $deadlineLabel",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cargando coordinación...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ProposalSelectorCard(
    submitting: Boolean,
    actionsDisabled: Boolean,
    submittingLabel: String?,
    proposalError: ApiError?,
    nowMillis: Long,
    availability: SchedulingAvailability?,
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    onSubmitProposals: (List<String>) -> Unit,
) {
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val zoneId = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId)
        .toOffsetDateTime()
    val dayOptions = schedulingDayOptions(now)
    val hasUnavailableWindows = schedulingAvailabilityHasValidUnavailableWindows(availability)
    val initialSelection = firstAvailableSchedulingSelection(now, zoneId, availability)
    var selectedDate by rememberSaveable {
        mutableStateOf(initialSelection?.date?.toString() ?: now.toLocalDate().toString())
    }
    var selectedHour by rememberSaveable { mutableStateOf(initialSelection?.hour ?: 8) }
    var selectedMinute by rememberSaveable { mutableStateOf(initialSelection?.minute ?: 0) }
    LaunchedEffect(
        nowMillis,
        zoneId.id,
        selectedDate,
        selectedHour,
        selectedMinute,
        availability,
    ) {
        val corrected = correctedSchedulingPickerSelection(
            selectedDate = selectedDate,
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            now = now,
            zoneId = zoneId,
            availability = availability,
        )
        if (selectedDate != corrected.date.toString() ||
            selectedHour != corrected.hour ||
            selectedMinute != corrected.minute
        ) {
            selectedDate = corrected.date.toString()
            selectedHour = corrected.hour
            selectedMinute = corrected.minute
            validationError = null
        }
    }
    val selectedLocalDate = runCatching { java.time.LocalDate.parse(selectedDate) }
        .getOrDefault(now.toLocalDate())
    val visibleHours = visibleSchedulingHours(selectedLocalDate, now, zoneId)
    val availableHours = availableSchedulingHours(selectedLocalDate, now, zoneId, availability)
    val conflictBlockedHours = visibleHours
        .filterNot { it in availableHours }
        .toSet()
    val effectiveHour = if (selectedHour in visibleHours) {
        selectedHour
    } else {
        visibleHours.firstOrNull() ?: selectedHour
    }
    val minuteOptions = schedulingMinuteOptions(selectedLocalDate, effectiveHour, now, zoneId, availability)
    val availableMinutes = minuteOptions
        .filter { it.selectable }
        .map { it.minute }
    val conflictingMinutes = minuteOptions
        .filter { it.conflicting }
        .map { it.minute }
        .toSet()
    val effectiveMinute = if (selectedMinute in availableMinutes) {
        selectedMinute
    } else {
        availableMinutes.firstOrNull() ?: selectedMinute
    }
    val candidateSelection = if (effectiveHour in availableHours && effectiveMinute in availableMinutes) {
        SchedulingSlotSelection(
            date = selectedLocalDate,
            hour = effectiveHour,
            minute = effectiveMinute,
        )
    } else {
        null
    }
    val candidateValue = candidateSelection?.let { buildSchedulingSlot(it, zoneId).toString() }
    val selectedLabels = selected
    val currentSelectedValidation = selected.takeIf { it.isNotEmpty() }
        ?.let { validateCurrentSelectedSlots(it, now, availability) }
    val visibleValidationError = validationError ?: currentSelectedValidation

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pickerLayoutSpec = schedulingPickerLayoutSpec(
            maxWidth = maxWidth,
            fontScale = LocalDensity.current.fontScale,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RealsRadii.Card),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Elegir horarios", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Seleccioná entre 1 y 3 opciones futuras. El orden en que las agregás marca tu prioridad.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (hasUnavailableWindows) {
                SchedulingAvailabilityNotice(availability)
            }

            WheelPickerColumn(
                title = "Día",
                options = dayOptions,
                selected = dayOptions.firstOrNull { it.date == selectedLocalDate },
                enabled = !submitting && !actionsDisabled,
                optionLabel = { it.label },
                isOptionEnabled = { day ->
                    availableSchedulingHours(day.date, now, zoneId, availability).isNotEmpty()
                },
                isOptionBlocked = { day ->
                    visibleSchedulingHours(day.date, now, zoneId).isNotEmpty() &&
                        availableSchedulingHours(day.date, now, zoneId, availability).isEmpty()
                },
                onSelected = { day ->
                    val currentHour = effectiveHour
                    val currentMinute = effectiveMinute
                    selectedDate = day.date.toString()
                    val nextVisibleHours = visibleSchedulingHours(day.date, now, zoneId)
                    val nextAvailableHours = availableSchedulingHours(day.date, now, zoneId, availability)
                    val nextHour = currentHour.takeIf { it in nextAvailableHours }
                        ?: nextAvailableHours.firstOrNull()
                    if (nextHour != null) {
                        selectedHour = nextHour
                        val nextMinutes = availableSchedulingMinutes(day.date, nextHour, now, zoneId, availability)
                        selectedMinute = currentMinute.takeIf { it in nextMinutes }
                            ?: nextMinutes.firstOrNull()
                            ?: selectedMinute
                    } else {
                        selectedHour = nextVisibleHours.firstOrNull() ?: selectedHour
                    }
                    validationError = null
                },
                modifier = Modifier.fillMaxWidth(),
                pickerHeight = pickerLayoutSpec.dayViewportHeight,
                controlMinHeight = pickerLayoutSpec.controlMinHeight,
                tagPrefix = SchedulingDayPickerTag,
            )

            val hourPicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                WheelPickerColumn(
                    title = "Hora",
                    options = visibleHours,
                    selected = selectedHour.takeIf { it in visibleHours },
                    enabled = !submitting && !actionsDisabled,
                    optionLabel = { it.toString().padStart(2, '0') },
                    isOptionEnabled = { hour -> hour in availableHours },
                    isOptionBlocked = { hour -> hour in conflictBlockedHours },
                    onSelected = { hour ->
                        selectedHour = hour
                        val nextMinutes = availableSchedulingMinutes(
                            selectedLocalDate,
                            hour,
                            now,
                            zoneId,
                            availability,
                        )
                        selectedMinute = selectedMinute.takeIf { it in nextMinutes }
                            ?: nextMinutes.firstOrNull()
                            ?: selectedMinute
                        validationError = null
                    },
                    modifier = pickerModifier,
                    pickerHeight = pickerLayoutSpec.optionViewportHeight,
                    controlMinHeight = pickerLayoutSpec.controlMinHeight,
                    tagPrefix = SchedulingHourPickerTag,
                )
            }
            val minutePicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                MinutePickerColumn(
                    minutes = listOf(0, 30),
                    selected = selectedMinute.takeIf { it in availableMinutes },
                    enabled = !submitting && !actionsDisabled,
                    enabledMinutes = availableMinutes,
                    conflictingMinutes = conflictingMinutes,
                    onSelected = { minute ->
                        selectedMinute = minute
                        validationError = null
                    },
                    modifier = pickerModifier,
                    minOptionsHeight = pickerLayoutSpec.optionViewportHeight,
                    controlMinHeight = pickerLayoutSpec.controlMinHeight,
                )
            }
            SchedulingHourMinutePickerLayout(
                layout = pickerLayoutSpec.hourMinuteArrangement,
                hourPicker = hourPicker,
                minutePicker = minutePicker,
            )

            Button(
                onClick = {
                    val value = candidateValue
                    val validation = value?.let { validateSelectedSlots(selected + it, now, availability) }
                    when {
                        value == null -> validationError = "Seleccioná un horario válido."
                        value in selected -> validationError = "Ese horario ya está en la lista."
                        selected.size >= 3 -> validationError = "Podés elegir hasta 3 horarios."
                        validation != null -> validationError = validation
                        else -> {
                            onSelectedChange(selected + value)
                            validationError = null
                        }
                    }
                },
                enabled = !submitting && !actionsDisabled && selected.size < 3 && candidateValue != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SchedulingAddOptionTag),
            ) {
                Text("Agregar opción")
            }

            Text("Opciones elegidas", style = MaterialTheme.typography.titleSmall)
            if (selectedLabels.isEmpty()) {
                Text(
                    text = "Todavía no agregaste horarios.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                selectedLabels.forEachIndexed { index, value ->
                    val conflicting = schedulingSlotConflictPolicy(value, availability)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. ${formatBackendContextualDateTime(value, nowMillis)}",
                                color = if (conflicting) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (conflicting) {
                                Text(
                                    text = CONFLICTING_SLOT_MESSAGE,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onSelectedChange(selected.filterNot { it == value })
                                validationError = null
                            },
                            enabled = !submitting && !actionsDisabled,
                        ) {
                            Text("Quitar")
                        }
                    }
                }
            }

            visibleValidationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            proposalError?.let {
                ApiErrorFeedbackCard(it, ErrorContext.Scheduling)
            }
            Button(
                onClick = {
                    val validation = validateCurrentSelectedSlots(selected, now, availability)
                    if (validation == null) {
                        onSubmitProposals(selected)
                    } else {
                        validationError = validation
                    }
                },
                enabled = !submitting && !actionsDisabled && canSubmitSelectedSlots(selected, now, availability),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitting) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(submittingLabel ?: "Enviando...")
                    }
                } else {
                    Text("Enviar opciones")
                }
            }
        }
        }
    }
}

@Composable
private fun SchedulingAvailabilityNotice(
    availability: SchedulingAvailability?,
) {
    val conflictWindowMinutes = availability?.conflictWindowMinutes
        ?.takeIf { it > 0 }
    val marginText = conflictWindowMinutes?.let { "±$it min" } ?: "el margen configurado"
    FeedbackCard(
        title = "Horarios no disponibles",
        message = "Los horarios marcados se superponen con otro segundo chat coordinado o caen dentro de $marginText para esa cita.",
        tone = FeedbackTone.Warning,
    )
}

internal data class SchedulingErrorPlacement(
    val topLevelError: ApiError?,
    val proposalError: ApiError?,
    val reviewError: ApiError?,
)

internal fun schedulingErrorPlacement(
    stage: SchedulingStage,
    error: ApiError?,
): SchedulingErrorPlacement {
    val proposalError = error?.takeIf {
        stage.showsProposalSelector() && it.isProposalSubmissionError()
    }
    val reviewError = error?.takeIf {
        stage == SchedulingStage.ReviewPartnerProposals && it.isReceivedProposalReviewError()
    }
    return SchedulingErrorPlacement(
        topLevelError = error.takeUnless { it == proposalError || it == reviewError },
        proposalError = proposalError,
        reviewError = reviewError,
    )
}

private fun SchedulingStage.showsProposalSelector(): Boolean =
    this == SchedulingStage.WaitingForMyProposals

private fun ApiError.isProposalSubmissionError(): Boolean =
    this is ApiError.Backend &&
        backendErrorCode in setOf(
            BackendErrorCode.SchedulingInvalidProposals,
            BackendErrorCode.SchedulingProposalsAlreadySubmitted,
            BackendErrorCode.SchedulingSlotConflict,
        )

private fun ApiError.isReceivedProposalReviewError(): Boolean =
    this is ApiError.Backend &&
        backendErrorCode in setOf(
            BackendErrorCode.SchedulingRoundChanged,
            BackendErrorCode.SchedulingPartnerProposalsNotAvailable,
            BackendErrorCode.SchedulingProposalNotAvailable,
            BackendErrorCode.SchedulingSlotConflict,
        )

internal data class SchedulingProposalDraftScope(
    val connectionId: String,
    val roundNumber: Int,
)

internal fun schedulingProposalDraftScope(
    connectionId: String,
    roundNumber: Int?,
): SchedulingProposalDraftScope =
    SchedulingProposalDraftScope(
        connectionId = connectionId,
        roundNumber = roundNumber ?: 0,
    )

@Composable
internal fun SchedulingHourMinutePickerLayout(
    layout: SchedulingHourMinuteArrangement,
    hourPicker: @Composable (Modifier) -> Unit,
    minutePicker: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (layout) {
        SchedulingHourMinuteArrangement.SideBySide -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag(SchedulingHourMinuteSideBySideTag),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                hourPicker(Modifier.weight(1f))
                minutePicker(Modifier.weight(1f))
            }
        }

        SchedulingHourMinuteArrangement.Stacked -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag(SchedulingHourMinuteStackedTag),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                hourPicker(Modifier.fillMaxWidth())
                minutePicker(Modifier.fillMaxWidth())
            }
        }
    }
}

internal enum class SchedulingHourMinuteArrangement {
    SideBySide,
    Stacked,
}

internal data class SchedulingPickerLayoutSpec(
    val controlMinHeight: Dp,
    val dayViewportHeight: Dp,
    val optionViewportHeight: Dp,
    val hourMinuteArrangement: SchedulingHourMinuteArrangement,
)

internal fun schedulingPickerLayoutSpec(maxWidth: Dp, fontScale: Float): SchedulingPickerLayoutSpec {
    val constrainedWidth = maxWidth < 300.dp
    val moderateWidthWithLargeText = maxWidth < 340.dp && fontScale >= 1.3f
    val veryLargeText = fontScale >= 1.8f
    val stacked = constrainedWidth || moderateWidthWithLargeText || veryLargeText
    val optionViewportHeight = when {
        fontScale >= 1.8f -> 224.dp
        fontScale >= 1.5f -> 196.dp
        else -> 156.dp
    }
    return SchedulingPickerLayoutSpec(
        controlMinHeight = PickerControlMinHeight,
        dayViewportHeight = if (fontScale >= 1.5f) 168.dp else 132.dp,
        optionViewportHeight = optionViewportHeight,
        hourMinuteArrangement = if (stacked) {
            SchedulingHourMinuteArrangement.Stacked
        } else {
            SchedulingHourMinuteArrangement.SideBySide
        },
    )
}

@Composable
internal fun <T> WheelPickerColumn(
    title: String,
    options: List<T>,
    selected: T?,
    enabled: Boolean,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    pickerHeight: Dp,
    controlMinHeight: Dp,
    tagPrefix: String,
    isOptionEnabled: (T) -> Boolean = { true },
    isOptionBlocked: (T) -> Boolean = { false },
) {
    val selectedIndex = options.indexOf(selected)
    val targetFirstVisibleIndex = centeredWheelFirstVisibleIndex(
        selectedIndex = selectedIndex,
        optionCount = options.size,
    )
    val previousOption = previousEnabledOptionIndex(
        selectedIndex = selectedIndex,
        optionCount = options.size,
        isOptionEnabled = { index -> isOptionEnabled(options[index]) },
    )?.let(options::get)
    val nextOption = nextEnabledOptionIndex(
        selectedIndex = selectedIndex,
        optionCount = options.size,
        isOptionEnabled = { index -> isOptionEnabled(options[index]) },
    )?.let(options::get)

    Column(
        modifier = modifier.testTag(tagPrefix),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = controlMinHeight),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = { previousOption?.let(onSelected) },
                enabled = enabled && previousOption != null,
                modifier = Modifier
                    .heightIn(min = controlMinHeight)
                    .testTag(schedulingPickerPreviousTag(tagPrefix)),
            ) {
                Text(
                    text = "^",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        key(options, targetFirstVisibleIndex) {
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = targetFirstVisibleIndex,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pickerHeight)
                    .testTag(schedulingPickerOptionsTag(tagPrefix)),
                state = listState,
                contentPadding = PaddingValues(vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(options) { _, option ->
                    val optionEnabled = enabled && isOptionEnabled(option)
                    val optionBlocked = isOptionBlocked(option)
                    val optionEmphasis = schedulingPickerOptionEmphasis(
                        optionEnabled = optionEnabled,
                        optionBlocked = optionBlocked,
                    )
                    val isSelected = option == selected
                    if (isSelected) {
                        Button(
                            onClick = { onSelected(option) },
                            enabled = optionEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PickerOptionMinHeight),
                        ) {
                            Text(optionLabel(option), textAlign = TextAlign.Center)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelected(option) },
                            enabled = optionEnabled,
                            border = if (optionBlocked) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PickerOptionMinHeight),
                        ) {
                            Text(
                                text = optionLabel(option),
                                color = schedulingPickerOptionContentColor(optionEmphasis),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = controlMinHeight),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = { nextOption?.let(onSelected) },
                enabled = enabled && nextOption != null,
                modifier = Modifier
                    .heightIn(min = controlMinHeight)
                    .testTag(schedulingPickerNextTag(tagPrefix)),
            ) {
                Text(
                    text = "v",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

internal const val SchedulingDayPickerTag = "scheduling_picker_day"
internal const val SchedulingHourPickerTag = "scheduling_picker_hour"
internal const val SchedulingMinutePickerTag = "scheduling_picker_minute"
internal const val SchedulingMinuteOptionsTag = "scheduling_picker_minute_options"
internal const val SchedulingMinuteUnavailableTag = "scheduling_picker_minute_unavailable"
internal const val SchedulingHourMinuteSideBySideTag = "scheduling_hour_minute_side_by_side"
internal const val SchedulingHourMinuteStackedTag = "scheduling_hour_minute_stacked"
internal const val SchedulingAddOptionTag = "scheduling_add_option"
internal fun schedulingPickerPreviousTag(prefix: String): String = "${prefix}_previous"
internal fun schedulingPickerNextTag(prefix: String): String = "${prefix}_next"
internal fun schedulingPickerOptionsTag(prefix: String): String = "${prefix}_options"
internal fun schedulingMinuteOptionTag(minute: Int): String = "scheduling_minute_option_$minute"

@Composable
internal fun MinutePickerColumn(
    minutes: List<Int>,
    selected: Int?,
    enabled: Boolean,
    enabledMinutes: List<Int>,
    conflictingMinutes: Set<Int>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minOptionsHeight: Dp,
    controlMinHeight: Dp,
) {
    Column(
        modifier = modifier.testTag(SchedulingMinutePickerTag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Min", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(controlMinHeight))
        Column(
            modifier = Modifier
                .heightIn(min = minOptionsHeight)
                .testTag(SchedulingMinuteOptionsTag),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            minutes.forEach { minute ->
                val optionEnabled = enabled && minute in enabledMinutes
                val conflicting = minute in conflictingMinutes
                val optionEmphasis = schedulingPickerOptionEmphasis(
                    optionEnabled = optionEnabled,
                    optionBlocked = conflicting,
                )
                val label = minute.toString().padStart(2, '0')
                if (minute == selected && optionEnabled) {
                    Button(
                        onClick = { onSelected(minute) },
                        enabled = optionEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PickerOptionMinHeight)
                            .testTag(schedulingMinuteOptionTag(minute)),
                    ) {
                        Text(label, textAlign = TextAlign.Center)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(minute) },
                        enabled = optionEnabled,
                        border = if (conflicting) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PickerOptionMinHeight)
                            .testTag(schedulingMinuteOptionTag(minute)),
                    ) {
                        Text(
                            text = label,
                            color = schedulingPickerOptionContentColor(optionEmphasis),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (conflictingMinutes.isNotEmpty()) {
                Text(
                    text = "No disponible",
                    modifier = Modifier.testTag(SchedulingMinuteUnavailableTag),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(controlMinHeight))
    }
}

@Composable
private fun schedulingPickerOptionContentColor(
    emphasis: SchedulingPickerOptionEmphasis,
) = when (emphasis) {
    SchedulingPickerOptionEmphasis.Enabled -> MaterialTheme.colorScheme.onSurface
    SchedulingPickerOptionEmphasis.Disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    SchedulingPickerOptionEmphasis.Blocked -> MaterialTheme.colorScheme.error
}

@Composable
private fun WaitingPartnerCard(
    myPendingProposals: List<SchedulingProposal>,
    nowMillis: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Esperando propuestas de la otra persona", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (myPendingProposals.isNotEmpty()) {
                    "Esperando que la otra persona revise tus opciones."
                } else {
                    "La otra persona rechazó tus opciones. Ahora esperamos que envíe las suyas."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (myPendingProposals.isNotEmpty()) {
                ProposalList(
                    title = "Tus opciones enviadas",
                    proposals = myPendingProposals,
                    nowMillis = nowMillis,
                )
            }
        }
    }
}

@Composable
private fun ReviewProposalsCard(
    myPendingProposals: List<SchedulingProposal>,
    partnerPendingProposals: List<SchedulingProposal>,
    submitting: Boolean,
    actionsDisabled: Boolean,
    submittingLabel: String?,
    reviewError: ApiError?,
    nowMillis: Long,
    availability: SchedulingAvailability?,
    onAcceptProposal: (String) -> Unit,
    onRejectPartnerProposals: () -> Unit,
) {
    val reviewState = schedulingReceivedProposalReviewState(partnerPendingProposals, nowMillis, availability)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Revisá las opciones recibidas", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Elegí una opción recibida o rechazá estas opciones antes de proponer las tuyas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReceivedProposalList(reviewState, nowMillis)
            when {
                reviewState.allExpired -> Text(
                    text = "Todos los horarios recibidos ya pasaron. Rechazá estas opciones para continuar con la coordinación.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                reviewState.noneAcceptable -> Text(
                    text = "Ninguno de los horarios recibidos está disponible para aceptar. Rechazá estas opciones para continuar con la coordinación.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            reviewState.items
                .filter { it.acceptanceAvailable }
                .forEach { item ->
                    Button(
                        onClick = { onAcceptProposal(item.proposal.id) },
                        enabled = !submitting && !actionsDisabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (submitting) {
                                submittingLabel ?: "Procesando..."
                            } else {
                                "Aceptar ${formatBackendContextualDateTime(item.proposal.proposedDateTime, nowMillis)}"
                            }
                        )
                    }
                }
            if (reviewState.items.isEmpty()) {
                Text(
                    text = "No hay horarios recibidos disponibles para revisar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (myPendingProposals.isNotEmpty()) {
                ProposalList(
                    title = "Tus opciones enviadas",
                    proposals = myPendingProposals,
                    nowMillis = nowMillis,
                )
            }
            reviewError?.let {
                ApiErrorFeedbackCard(it, ErrorContext.Scheduling)
            }
            OutlinedButton(
                onClick = onRejectPartnerProposals,
                enabled = !submitting && !actionsDisabled && reviewState.resolutionByRejectionAvailable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (submitting) submittingLabel ?: "Procesando..." else "Rechazar opciones")
            }
        }
    }
}

@Composable
private fun ReceivedProposalList(
    reviewState: SchedulingReceivedProposalReviewState,
    nowMillis: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (reviewState.items.isEmpty()) {
            Text(
                text = "No hay horarios recibidos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            schedulingReceivedProposalPresentationItems(reviewState).forEach { numberedItem ->
                val item = numberedItem.item
                val proposal = item.proposal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${numberedItem.number}. ${
                            if (item.unavailable) {
                                if (item.conflicting) {
                                    formatBackendContextualDateTime(proposal.proposedDateTime, nowMillis)
                                } else {
                                    "Horario no disponible"
                                }
                            } else {
                                formatBackendContextualDateTime(proposal.proposedDateTime, nowMillis)
                            }
                        }",
                        modifier = Modifier.weight(1f),
                        color = if (item.acceptanceAvailable) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    when {
                        item.expired -> Text(
                            text = "Ya pasó",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )

                        item.unavailable -> Text(
                            text = if (item.conflicting) "Se superpone" else "No disponible",
                            color = if (item.conflicting) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledCard(
    confirmedDateTime: String?,
    nowMillis: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Horario confirmado", style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatBackendContextualDateTime(confirmedDateTime, nowMillis),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FailedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = "No hubo acuerdo. La coordinación ya no está disponible.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnknownCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = "No pudimos interpretar el estado actual. Actualizá para intentarlo de nuevo.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProposalList(
    title: String,
    proposals: List<SchedulingProposal>,
    nowMillis: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        val items = schedulingPendingProposalPresentationItems(proposals)
        if (items.isEmpty()) {
            Text("Sin horarios.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            items.forEach { item ->
                Text(
                    text = "${item.number}. ${formatBackendContextualDateTime(item.item.proposedDateTime, nowMillis)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

