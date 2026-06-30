package com.reals.app.ui.scheduling

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProposalStatus
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.formatBackendDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@Composable
fun SchedulingScreen(
    connectionId: String,
    partnerName: String?,
    loading: Boolean,
    refreshing: Boolean,
    submitting: Boolean,
    submittingLabel: String?,
    negotiation: SchedulingNegotiation?,
    proposals: List<SchedulingProposal>,
    currentUserId: String,
    error: ApiError?,
    message: String?,
    onRefresh: () -> Unit,
    onSubmitProposals: (List<String>) -> Unit,
    onAcceptProposal: (String) -> Unit,
    onRejectRound: () -> Unit,
    onOpenPartnerProfile: () -> Unit,
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
    val myProposals = roundState.myProposals
    val partnerProposals = roundState.partnerProposals
    var nowMillis by rememberSaveable(connectionId) { mutableStateOf(System.currentTimeMillis()) }
    var expiryRefreshRequested by rememberSaveable(connectionId) { mutableStateOf(false) }
    val lifecycle = schedulingLifecycleUiState(negotiation?.schedulingExpiresAt, nowMillis)
    val actionsDisabled = lifecycle.expired

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Coordinar horarios",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = partnerDisplayName?.let { "Con $it" } ?: "Con la otra persona",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        message?.let {
            FeedbackCard(
                title = "Estado actualizado",
                message = it,
                tone = FeedbackTone.Success,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        error?.let {
            ApiErrorFeedbackCard(it, ErrorContext.Scheduling)
            Spacer(modifier = Modifier.height(12.dp))
        }

        StatusCard(
            loading = loading,
            refreshing = refreshing,
            negotiation = negotiation,
            stage = stage,
        )
        Spacer(modifier = Modifier.height(12.dp))
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
                onSubmitProposals = onSubmitProposals,
            )

            SchedulingStage.WaitingForPartnerProposals -> WaitingPartnerCard(myProposals)
            SchedulingStage.ReviewPartnerProposals -> {
                if (myProposals.isEmpty()) {
                    ProposalSelectorCard(
                        submitting = submitting,
                        actionsDisabled = actionsDisabled,
                        submittingLabel = submittingLabel,
                        onSubmitProposals = onSubmitProposals,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                ReviewProposalsCard(
                    myProposals = myProposals,
                    partnerProposals = partnerProposals,
                    submitting = submitting,
                    actionsDisabled = actionsDisabled,
                    submittingLabel = submittingLabel,
                    onAcceptProposal = onAcceptProposal,
                    onRejectRound = onRejectRound,
                )
            }

            SchedulingStage.Scheduled -> ScheduledCard(negotiation?.confirmedDateTime)
            SchedulingStage.Failed -> FailedCard()
            SchedulingStage.Unknown -> UnknownCard()
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (stage != SchedulingStage.WaitingForPartnerProposals &&
            stage != SchedulingStage.ReviewPartnerProposals
        ) {
            ProposalListCard(
                title = "Tus horarios",
                proposals = myProposals,
                emptyMessage = "Todavia no enviaste horarios en esta ronda.",
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProposalListCard(
                title = "Horarios de la otra persona",
                proposals = partnerProposals,
                emptyMessage = "Todavia no hay horarios de la otra persona en esta ronda.",
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        OutlinedButton(
            onClick = onRefresh,
            enabled = !loading && !refreshing && !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (refreshing) "Actualizando..." else "Actualizar")
        }
        OutlinedButton(
            onClick = onOpenPartnerProfile,
            enabled = !loading && !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ver perfil")
        }
        Button(
            onClick = onBackHome,
            enabled = !loading && !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (submitting) submittingLabel ?: "Procesando..." else "Volver a Home")
        }
    }
}

@Composable
private fun StatusCard(
    loading: Boolean,
    refreshing: Boolean,
    negotiation: SchedulingNegotiation?,
    stage: SchedulingStage,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ronda actual", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (loading) {
                    "Cargando..."
                } else {
                    "Estado: ${negotiation?.status?.userLabel() ?: stage.userLabel()}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Numero de ronda: ${negotiation?.roundNumber ?: "-"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (refreshing) {
                Text("Actualizando...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Cargando coordinacion...",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProposalSelectorCard(
    submitting: Boolean,
    actionsDisabled: Boolean,
    submittingLabel: String?,
    onSubmitProposals: (List<String>) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val zoneId = remember { ZoneId.systemDefault() }
    val now = remember(zoneId) { OffsetDateTime.now(zoneId) }
    val dayOptions = remember(now) { schedulingDayOptions(now) }
    val initialSelection = remember(now, zoneId) { firstAvailableSchedulingSelection(now, zoneId) }
    var selectedDate by rememberSaveable {
        mutableStateOf(initialSelection?.date?.toString() ?: now.toLocalDate().toString())
    }
    var selectedHour by rememberSaveable { mutableStateOf(initialSelection?.hour ?: 8) }
    var selectedMinute by rememberSaveable { mutableStateOf(initialSelection?.minute ?: 0) }
    var hourScrollKey by rememberSaveable { mutableStateOf(0) }
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
                    selectedDate = day.date.toString()
                    val nextHours = availableSchedulingHours(day.date, now, zoneId)
                    val nextHour = selectedHour.takeIf { it in nextHours }
                        ?: nextHours.firstOrNull()
                    if (nextHour != null) {
                        if (nextHour != selectedHour) {
                            selectedHour = nextHour
                            hourScrollKey += 1
                        }
                        val nextMinutes = availableSchedulingMinutes(day.date, nextHour, now, zoneId)
                        selectedMinute = selectedMinute.takeIf { it in nextMinutes }
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
                    scrollKey = hourScrollKey,
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
                    val currentNow = OffsetDateTime.now(zoneId)
                    val validation = value?.let { validateSelectedSlots(selected + it, currentNow) }
                    when {
                        value == null -> validationError = "Selecciona un horario valido."
                        value in selected -> validationError = "Ese horario ya esta en la lista."
                        selected.size >= 3 -> validationError = "Podes elegir hasta 3 horarios."
                        validation != null -> validationError = validation
                        else -> {
                            selected = selected + value
                            validationError = null
                        }
                    }
                },
                enabled = !submitting && !actionsDisabled && selected.size < 3 && candidateValue != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Agregar opcion")
            }

            Text("Opciones elegidas por prioridad", style = MaterialTheme.typography.titleSmall)
            if (selectedLabels.isEmpty()) {
                Text(
                    text = "Todavia no agregaste horarios.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                selectedLabels.forEach { value ->
                    val priority = selectedLabels.indexOf(value) + 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Prioridad $priority: ${formatBackendDateTime(value)}",
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                selected = selected.filterNot { it == value }
                                validationError = null
                            },
                            enabled = !submitting && !actionsDisabled,
                        ) {
                            Text("Quitar")
                        }
                    }
                }
            }

            validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val validation = validateSelectedSlots(selected, OffsetDateTime.now(zoneId))
                    if (validation == null) {
                        onSubmitProposals(selected)
                    } else {
                        validationError = validation
                    }
                },
                enabled = !submitting && !actionsDisabled && selected.isNotEmpty(),
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

@Composable
private fun <T> WheelPickerColumn(
    title: String,
    options: List<T>,
    selected: T?,
    enabled: Boolean,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    pickerHeight: Dp = 156.dp,
    scrollKey: Any? = Unit,
    isOptionEnabled: (T) -> Boolean = { true },
) {
    val selectedIndex = options.indexOf(selected)
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
        key(scrollKey, options.size) {
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0),
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Min", style = MaterialTheme.typography.titleSmall)
        Column(
            modifier = Modifier.height(186.dp),
            verticalArrangement = Arrangement.Center,
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
    }
}

@Composable
private fun WaitingPartnerCard(myProposals: List<SchedulingProposal>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Esperando propuestas de la otra persona", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Ya enviamos tus horarios de esta ronda en orden de prioridad. Te avisamos cuando haya opciones para revisar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProposalList("Tus horarios por prioridad", myProposals)
        }
    }
}

@Composable
private fun ReviewProposalsCard(
    myProposals: List<SchedulingProposal>,
    partnerProposals: List<SchedulingProposal>,
    submitting: Boolean,
    actionsDisabled: Boolean,
    submittingLabel: String?,
    onAcceptProposal: (String) -> Unit,
    onRejectRound: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Revisa las opciones recibidas", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Las opciones aparecen en orden de prioridad. Prioridad 1 es la preferida.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProposalList("Tus horarios por prioridad", myProposals)
            ProposalList("Horarios de la otra persona por prioridad", partnerProposals)
            partnerProposals
                .filter { it.status == ProposalStatus.Pending }
                .forEach { proposal ->
                    Button(
                        onClick = { onAcceptProposal(proposal.id) },
                        enabled = !submitting && !actionsDisabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (submitting) {
                                submittingLabel ?: "Procesando..."
                            } else {
                                "Aceptar ${formatBackendDateTime(proposal.proposedDateTime)}"
                            }
                        )
                    }
                }
            OutlinedButton(
                onClick = onRejectRound,
                enabled = !submitting && !actionsDisabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (submitting) submittingLabel ?: "Procesando..." else "Rechazar ronda")
            }
        }
    }
}

@Composable
private fun ScheduledCard(confirmedDateTime: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Horario confirmado", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Quedo confirmado para ${formatBackendDateTime(confirmedDateTime)}.",
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
private fun ProposalListCard(
    title: String,
    proposals: List<SchedulingProposal>,
    emptyMessage: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (proposals.isNotEmpty()) {
                Text(
                    text = "Ordenadas por prioridad. Prioridad 1 es la preferida.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (proposals.isEmpty()) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                proposals.forEach { proposal ->
                    ProposalRow(proposal)
                }
            }
        }
    }
}

@Composable
private fun ProposalList(title: String, proposals: List<SchedulingProposal>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (proposals.isEmpty()) {
            Text("Sin horarios.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            proposals.forEach { ProposalRow(it) }
        }
    }
}

@Composable
private fun ProposalRow(proposal: SchedulingProposal) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Prioridad ${proposal.preferenceOrder}: ${formatBackendDateTime(proposal.proposedDateTime)}")
        Text(
            proposal.status.userLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SchedulingStage.userLabel(): String = when (this) {
    SchedulingStage.Loading -> "Cargando"
    SchedulingStage.WaitingForMyProposals -> "Esperando tus horarios"
    SchedulingStage.WaitingForPartnerProposals -> "Esperando a la otra persona"
    SchedulingStage.ReviewPartnerProposals -> "Revisar propuestas"
    SchedulingStage.Scheduled -> "Programado"
    SchedulingStage.Failed -> "No disponible"
    SchedulingStage.Unknown -> "Estado no disponible"
}

private fun NegotiationStatus.userLabel(): String = when (this) {
    NegotiationStatus.Pending -> "Pendiente"
    NegotiationStatus.Confirmed -> "Confirmada"
    NegotiationStatus.Failed -> "Fallida"
    is NegotiationStatus.Unknown -> "Estado no disponible"
}

private fun ProposalStatus.userLabel(): String = when (this) {
    ProposalStatus.Pending -> "Pendiente"
    ProposalStatus.Accepted -> "Aceptado"
    ProposalStatus.Rejected -> "Rechazado"
    is ProposalStatus.Unknown -> "Estado no disponible"
}

