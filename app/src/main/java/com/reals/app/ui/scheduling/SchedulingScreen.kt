package com.reals.app.ui.scheduling

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@Composable
fun SchedulingScreen(
    connectionId: String,
    partnerName: String?,
    loading: Boolean,
    refreshing: Boolean,
    submitting: Boolean,
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
    val currentRound = negotiation?.roundNumber
    val currentRoundProposals = proposals
        .filter { currentRound != null && it.roundNumber == currentRound }
        .sortedWith(compareBy<SchedulingProposal> { it.userId != currentUserId }.thenBy { it.preferenceOrder })
    val myProposals = currentRoundProposals.filter { it.userId == currentUserId }
    val partnerProposals = currentRoundProposals.filter { it.userId != currentUserId }
    val stage = deriveSchedulingStage(
        loading = loading,
        negotiation = negotiation,
        myProposals = myProposals,
        partnerProposals = partnerProposals,
    )

    LaunchedEffect(connectionId, negotiation?.status?.rawValue) {
        while (negotiation?.status == NegotiationStatus.Pending) {
            delay(7.seconds)
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
            ApiErrorFeedbackCard(it, ErrorContext.General)
            Spacer(modifier = Modifier.height(12.dp))
        }

        StatusCard(
            loading = loading,
            refreshing = refreshing,
            negotiation = negotiation,
            stage = stage,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (stage) {
            SchedulingStage.Loading -> LoadingCard()
            SchedulingStage.WaitingForMyProposals -> ProposalSelectorCard(
                submitting = submitting,
                onSubmitProposals = onSubmitProposals,
            )

            SchedulingStage.WaitingForPartnerProposals -> WaitingPartnerCard(myProposals)
            SchedulingStage.ReviewPartnerProposals -> ReviewProposalsCard(
                myProposals = myProposals,
                partnerProposals = partnerProposals,
                submitting = submitting,
                onAcceptProposal = onAcceptProposal,
                onRejectRound = onRejectRound,
            )

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
            Text("Volver a Home")
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
            Text("Negociacion", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (loading) {
                    "Cargando..."
                } else {
                    "Estado: ${negotiation?.status?.userLabel() ?: stage.userLabel()}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Ronda: ${negotiation?.roundNumber ?: "-"}",
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
    onSubmitProposals: (List<String>) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val presets = rememberSchedulingPresets()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Elegir horarios", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Selecciona entre 1 y 3 opciones futuras.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            presets.forEach { preset ->
                val isSelected = preset.value in selected
                if (isSelected) {
                    Button(
                        onClick = {
                            selected = selected.filterNot { it == preset.value }
                            validationError = null
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(preset.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (selected.size < 3) {
                                selected = selected + preset.value
                                validationError = null
                            } else {
                                validationError = "Podes elegir hasta 3 horarios."
                            }
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(preset.label)
                    }
                }
            }
            validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val validation = validateSelectedSlots(selected)
                    if (validation == null) {
                        onSubmitProposals(selected)
                    } else {
                        validationError = validation
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (submitting) "Enviando..." else "Enviar horarios")
            }
        }
    }
}

@Composable
private fun WaitingPartnerCard(myProposals: List<SchedulingProposal>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Esperando respuesta", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Ya enviamos tus horarios. Estamos esperando a la otra persona.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProposalList("Tus horarios", myProposals)
        }
    }
}

@Composable
private fun ReviewProposalsCard(
    myProposals: List<SchedulingProposal>,
    partnerProposals: List<SchedulingProposal>,
    submitting: Boolean,
    onAcceptProposal: (String) -> Unit,
    onRejectRound: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Revisar horarios", style = MaterialTheme.typography.titleMedium)
            ProposalList("Tus horarios", myProposals)
            ProposalList("Horarios de la otra persona", partnerProposals)
            partnerProposals
                .filter { it.status == ProposalStatus.Pending }
                .forEach { proposal ->
                    Button(
                        onClick = { onAcceptProposal(proposal.id) },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Aceptar ${formatBackendDateTime(proposal.proposedDateTime)}")
                    }
                }
            OutlinedButton(
                onClick = onRejectRound,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (submitting) "Procesando..." else "Rechazar ronda")
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
            Text("Segundo chat programado", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Segundo chat programado para ${formatBackendDateTime(confirmedDateTime)}.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun FailedCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "La coordinacion ya no esta disponible.",
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
        Text(formatBackendDateTime(proposal.proposedDateTime))
        Text(
            proposal.status.userLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun deriveSchedulingStage(
    loading: Boolean,
    negotiation: SchedulingNegotiation?,
    myProposals: List<SchedulingProposal>,
    partnerProposals: List<SchedulingProposal>,
): SchedulingStage = when {
    loading -> SchedulingStage.Loading
    negotiation == null -> SchedulingStage.Unknown
    negotiation.status == NegotiationStatus.Confirmed -> SchedulingStage.Scheduled
    negotiation.status == NegotiationStatus.Failed -> SchedulingStage.Failed
    negotiation.status is NegotiationStatus.Unknown -> SchedulingStage.Unknown
    negotiation.status == NegotiationStatus.Pending && partnerProposals.isNotEmpty() ->
        SchedulingStage.ReviewPartnerProposals
    negotiation.status == NegotiationStatus.Pending && myProposals.isEmpty() ->
        SchedulingStage.WaitingForMyProposals
    negotiation.status == NegotiationStatus.Pending -> SchedulingStage.WaitingForPartnerProposals
    else -> SchedulingStage.Unknown
}

private enum class SchedulingStage {
    Loading,
    WaitingForMyProposals,
    WaitingForPartnerProposals,
    ReviewPartnerProposals,
    Scheduled,
    Failed,
    Unknown,
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

private data class SchedulingPreset(
    val label: String,
    val value: String,
)

@Composable
private fun rememberSchedulingPresets(): List<SchedulingPreset> =
    remember {
        val now = OffsetDateTime.now()
        val tomorrow = LocalDate.now().plusDays(1)
        listOf(
            roundUpToHalfHour(now.plusHours(1)),
            roundUpToHalfHour(now.plusHours(2)),
            roundUpToHalfHour(now.plusHours(3)),
            OffsetDateTime.of(tomorrow, LocalTime.of(20, 0), now.offset),
            OffsetDateTime.of(tomorrow, LocalTime.of(20, 30), now.offset),
            OffsetDateTime.of(tomorrow, LocalTime.of(21, 0), now.offset),
            OffsetDateTime.of(tomorrow, LocalTime.of(21, 30), now.offset),
        )
            .distinctBy { it.toInstant() }
            .map { slot ->
                SchedulingPreset(
                    label = formatBackendDateTime(slot.toString()),
                    value = slot.toString(),
                )
            }
    }

private fun roundUpToHalfHour(value: OffsetDateTime): OffsetDateTime {
    val clean = value.withSecond(0).withNano(0)
    return when {
        clean.minute == 0 || clean.minute == 30 -> clean
        clean.minute < 30 -> clean.withMinute(30)
        else -> clean.plusHours(1).withMinute(0)
    }
}

private fun validateSelectedSlots(values: List<String>): String? {
    if (values.isEmpty()) return "Selecciona al menos un horario."
    if (values.size > 3) return "Podes elegir hasta 3 horarios."
    val parsed = values.map { value ->
        runCatching { OffsetDateTime.parse(value) }.getOrNull()
            ?: return "Hay un horario con formato invalido."
    }
    if (parsed.distinctBy { it.toInstant() }.size != parsed.size) {
        return "Los horarios no pueden repetirse."
    }
    val now = OffsetDateTime.now()
    if (parsed.any { !it.isAfter(now) }) {
        return "Todos los horarios tienen que ser futuros."
    }
    if (parsed.any { it.minute !in listOf(0, 30) || it.second != 0 || it.nano != 0 }) {
        return "Los horarios tienen que estar alineados a media hora."
    }
    return null
}

private fun formatBackendDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault()))
    }.getOrElse {
        value.replace("T", " ").substringBeforeLast(":")
    }
}
