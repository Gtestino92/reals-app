package com.reals.app.ui.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reals.app.BuildConfig
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.ManualBlockConfirmationDialog
import com.reals.app.ui.common.ManualBlockOverflowMenu
import com.reals.app.ui.common.formatBackendContextualDateTime
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private val PickerControlSlotHeight = 48.dp
private val PickerOptionSlotHeight = 156.dp

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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Coordinar horarios",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge,
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
                    Text(if (submitting) submittingLabel ?: "Procesando..." else "Volver a Home")
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
private fun LoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
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
private fun ProposalSelectorCard(
    submitting: Boolean,
    actionsDisabled: Boolean,
    submittingLabel: String?,
    proposalError: ApiError?,
    nowMillis: Long,
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
    val initialSelection = firstAvailableSchedulingSelection(now, zoneId)
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
    ) {
        val corrected = correctedSchedulingPickerSelection(
            selectedDate = selectedDate,
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            now = now,
            zoneId = zoneId,
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
    val availableHours = availableSchedulingHours(selectedLocalDate, now, zoneId)
    val effectiveHour = if (selectedHour in availableHours) {
        selectedHour
    } else {
        availableHours.firstOrNull() ?: selectedHour
    }
    val availableMinutes = availableSchedulingMinutes(selectedLocalDate, effectiveHour, now, zoneId)
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
        ?.let { validateCurrentSelectedSlots(it, now) }
    val visibleValidationError = validationError ?: currentSelectedValidation

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Elegir horarios", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Selecciona entre 1 y 3 opciones futuras. El orden en que las agregas marca tu prioridad.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            WheelPickerColumn(
                title = "Dia",
                options = dayOptions,
                selected = dayOptions.firstOrNull { it.date == selectedLocalDate },
                enabled = !submitting && !actionsDisabled,
                optionLabel = { it.label },
                isOptionEnabled = { day -> availableSchedulingHours(day.date, now, zoneId).isNotEmpty() },
                onSelected = { day ->
                    val currentHour = effectiveHour
                    val currentMinute = effectiveMinute
                    selectedDate = day.date.toString()
                    val nextHours = availableSchedulingHours(day.date, now, zoneId)
                    val nextHour = currentHour.takeIf { it in nextHours }
                        ?: nextHours.firstOrNull()
                    if (nextHour != null) {
                        selectedHour = nextHour
                        val nextMinutes = availableSchedulingMinutes(day.date, nextHour, now, zoneId)
                        selectedMinute = currentMinute.takeIf { it in nextMinutes }
                            ?: nextMinutes.firstOrNull()
                            ?: selectedMinute
                    }
                    validationError = null
                },
                modifier = Modifier.fillMaxWidth(),
                pickerHeight = 132.dp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                WheelPickerColumn(
                    title = "Hora",
                    options = availableHours,
                    selected = selectedHour.takeIf { it in availableHours },
                    enabled = !submitting && !actionsDisabled,
                    optionLabel = { it.toString().padStart(2, '0') },
                    onSelected = { hour ->
                        selectedHour = hour
                        val nextMinutes = availableSchedulingMinutes(
                            selectedLocalDate,
                            hour,
                            now,
                            zoneId,
                        )
                        selectedMinute = selectedMinute.takeIf { it in nextMinutes }
                            ?: nextMinutes.firstOrNull()
                            ?: selectedMinute
                        validationError = null
                    },
                    modifier = Modifier.weight(1f),
                )
                MinutePickerColumn(
                    minutes = listOf(0, 30),
                    selected = selectedMinute.takeIf { it in availableMinutes },
                    enabled = !submitting && !actionsDisabled,
                    enabledMinutes = availableMinutes,
                    onSelected = { minute ->
                        selectedMinute = minute
                        validationError = null
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = {
                    val value = candidateValue
                    val validation = value?.let { validateSelectedSlots(selected + it, now) }
                    when {
                        value == null -> validationError = "Selecciona un horario valido."
                        value in selected -> validationError = "Ese horario ya esta en la lista."
                        selected.size >= 3 -> validationError = "Podes elegir hasta 3 horarios."
                        validation != null -> validationError = validation
                        else -> {
                            onSelectedChange(selected + value)
                            validationError = null
                        }
                    }
                },
                enabled = !submitting && !actionsDisabled && selected.size < 3 && candidateValue != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Agregar opcion")
            }

            Text("Opciones elegidas", style = MaterialTheme.typography.titleSmall)
            if (selectedLabels.isEmpty()) {
                Text(
                    text = "Todavia no agregaste horarios.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                selectedLabels.forEachIndexed { index, value ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}. ${formatBackendContextualDateTime(value, nowMillis)}",
                            modifier = Modifier.weight(1f),
                        )
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
                    val validation = validateCurrentSelectedSlots(selected, now)
                    if (validation == null) {
                        onSubmitProposals(selected)
                    } else {
                        validationError = validation
                    }
                },
                enabled = !submitting && !actionsDisabled && canSubmitSelectedSlots(selected, now),
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
        )

private fun ApiError.isReceivedProposalReviewError(): Boolean =
    this is ApiError.Backend &&
        backendErrorCode in setOf(
            BackendErrorCode.SchedulingRoundChanged,
            BackendErrorCode.SchedulingPartnerProposalsNotAvailable,
            BackendErrorCode.SchedulingProposalNotAvailable,
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
private fun <T> WheelPickerColumn(
    title: String,
    options: List<T>,
    selected: T?,
    enabled: Boolean,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    pickerHeight: Dp = PickerOptionSlotHeight,
    isOptionEnabled: (T) -> Boolean = { true },
) {
    val selectedIndex = options.indexOf(selected)
    val targetFirstVisibleIndex = centeredWheelFirstVisibleIndex(
        selectedIndex = selectedIndex,
        optionCount = options.size,
    )
    val previousOption = options
        .take(selectedIndex.coerceAtLeast(0))
        .lastOrNull(isOptionEnabled)
    val nextOption = options
        .drop((selectedIndex + 1).coerceAtLeast(0))
        .firstOrNull(isOptionEnabled)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PickerControlSlotHeight),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = { previousOption?.let(onSelected) },
                enabled = enabled && previousOption != null,
            ) {
                Text(
                    text = "^",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
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
                    .height(pickerHeight),
                state = listState,
                contentPadding = PaddingValues(vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(options) { _, option ->
                    val optionEnabled = enabled && isOptionEnabled(option)
                    val isSelected = option == selected
                    if (isSelected) {
                        Button(
                            onClick = { onSelected(option) },
                            enabled = optionEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(optionLabel(option))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelected(option) },
                            enabled = optionEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(optionLabel(option))
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PickerControlSlotHeight),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = { nextOption?.let(onSelected) },
                enabled = enabled && nextOption != null,
            ) {
                Text(
                    text = "v",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun MinutePickerColumn(
    minutes: List<Int>,
    selected: Int?,
    enabled: Boolean,
    enabledMinutes: List<Int>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Min", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(PickerControlSlotHeight))
        Column(
            modifier = Modifier.height(PickerOptionSlotHeight),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            minutes.forEach { minute ->
                val optionEnabled = enabled && minute in enabledMinutes
                val label = minute.toString().padStart(2, '0')
                if (minute == selected && optionEnabled) {
                    Button(
                        onClick = { onSelected(minute) },
                        enabled = optionEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(minute) },
                        enabled = optionEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(PickerControlSlotHeight))
    }
}

@Composable
private fun WaitingPartnerCard(
    myPendingProposals: List<SchedulingProposal>,
    nowMillis: Long,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Esperando propuestas de la otra persona", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (myPendingProposals.isNotEmpty()) {
                    "Esperando que la otra persona revise tus opciones."
                } else {
                    "La otra persona rechazo tus opciones. Ahora esperamos que envie las suyas."
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
    onAcceptProposal: (String) -> Unit,
    onRejectPartnerProposals: () -> Unit,
) {
    val reviewState = schedulingReceivedProposalReviewState(partnerPendingProposals, nowMillis)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Revisa las opciones recibidas", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Elegi una opcion recibida o rechaza estas opciones antes de proponer las tuyas.",
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
                                "Horario no disponible"
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
                            text = "No disponible",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Horario confirmado", style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatBackendContextualDateTime(confirmedDateTime, nowMillis),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun FailedCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No hubo acuerdo. La coordinacion ya no esta disponible.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnknownCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No pudimos interpretar el estado actual. Actualiza para intentarlo de nuevo.",
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

