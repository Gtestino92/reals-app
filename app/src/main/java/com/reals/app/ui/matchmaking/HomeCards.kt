package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.formatBackendTime
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private val SECOND_CHAT_DURATION = Duration.ofHours(2)
private val SECOND_CHAT_ENTRY_TOLERANCE = Duration.ofMinutes(10)

@Composable
internal fun PendingActionsCard(
    actions: List<HomeActionItem>,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    if (actions.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Acciones pendientes",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            actions.forEach { action ->
                when (action) {
                    is HomeActionItem.FirstChat -> FirstChatItem(
                        action = action,
                        busy = busy,
                        onOpenFirstChat = onOpenFirstChat,
                    )

                    is HomeActionItem.VisualReview -> VisualApprovalItem(
                        action = action,
                        busy = busy,
                        onOpenVisualApproval = onOpenVisualApproval,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NextStepCard(
    nextSteps: List<HomeNextStepItem>,
    busy: Boolean,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenPartnerProfile: (matchId: String) -> Unit,
) {
    if (nextSteps.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Siguiente etapa",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            nextSteps.forEach { nextStep ->
                NextStepItem(
                    item = nextStep,
                    busy = busy,
                    onOpenScheduling = onOpenScheduling,
                    onOpenSecondChat = onOpenSecondChat,
                    onOpenPartnerProfile = onOpenPartnerProfile,
                )
            }
        }
    }
}

@Composable
private fun FirstChatItem(
    action: HomeActionItem.FirstChat,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chat inicial", style = MaterialTheme.typography.titleMedium)
            val partnerName = action.partnerDisplayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)

            Text(
                text = partnerName?.let { "Con $it" } ?: "Chat inicial activo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Podes entrar cuando quieras.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenFirstChat(action.matchId, action.chatId) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Entrar al chat")
            }
        }
    }
}

@Composable
private fun VisualApprovalItem(
    action: HomeActionItem.VisualReview,
    busy: Boolean,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val partnerName = action.partnerDisplayName?.takeIf { it.isNotBlank() }

            Text(
                text = partnerName?.let { "Aprobacion visual con $it" }
                    ?: "Aprobacion visual pendiente"
            )
            Text(
                text = "Revisa el perfil visual y decidi si queres continuar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenVisualApproval(action.matchId) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Abrir aprobacion visual")
            }
        }
    }
}

@Composable
private fun NextStepItem(
    item: HomeNextStepItem,
    busy: Boolean,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenPartnerProfile: (matchId: String) -> Unit,
) {
    val partnerName = item.partnerDisplayName()
        ?.let(TextSafety::safeDisplay)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.title(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = partnerName?.let { "Con $it" } ?: "Con la otra persona",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item is HomeNextStepItem.Scheduling) {
                Button(
                    onClick = {
                        onOpenScheduling(
                            item.connectionId,
                            item.matchId,
                            item.partnerDisplayName,
                        )
                    },
                    enabled = !busy && item.connectionId.isNotBlank() && item.matchId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Coordinar horarios")
                }
            }
            if (item is HomeNextStepItem.SecondChatScheduled || item is HomeNextStepItem.SecondChatAvailable) {
                val canOpenSecondChat = item.canOpenSecondChat()
                Button(
                    onClick = {
                        onOpenSecondChat(
                            item.connectionIdForSecondChat(),
                            item.matchIdForProfile(),
                            item.partnerDisplayName(),
                        )
                    },
                    enabled = !busy &&
                        canOpenSecondChat &&
                        item.connectionIdForSecondChat().isNotBlank() &&
                        item.matchIdForProfile().isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(item.secondChatCtaLabel(canOpenSecondChat))
                }
            }
            Button(
                onClick = { onOpenPartnerProfile(item.matchIdForProfile()) },
                enabled = !busy && item.matchIdForProfile().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ver perfil")
            }
        }
    }
}

private fun HomeNextStepItem.partnerDisplayName(): String? = when (this) {
    is HomeNextStepItem.Scheduling -> partnerDisplayName
    is HomeNextStepItem.SecondChatScheduled -> partnerDisplayName
    is HomeNextStepItem.SecondChatAvailable -> partnerDisplayName
    is HomeNextStepItem.Unknown -> partnerDisplayName
}

private fun HomeNextStepItem.matchIdForProfile(): String = when (this) {
    is HomeNextStepItem.Scheduling -> matchId
    is HomeNextStepItem.SecondChatScheduled -> matchId
    is HomeNextStepItem.SecondChatAvailable -> matchId
    is HomeNextStepItem.Unknown -> matchId.orEmpty()
}

private fun HomeNextStepItem.connectionIdForSecondChat(): String = when (this) {
    is HomeNextStepItem.SecondChatScheduled -> connectionId
    is HomeNextStepItem.SecondChatAvailable -> connectionId
    else -> ""
}

private fun HomeNextStepItem.title(): String = when (this) {
    is HomeNextStepItem.Scheduling -> "Coordinacion pendiente"
    is HomeNextStepItem.SecondChatScheduled -> "Segundo chat programado"
    is HomeNextStepItem.SecondChatAvailable -> "Segundo chat pendiente"
    is HomeNextStepItem.Unknown -> "Conexion no disponible"
}

private fun HomeNextStepItem.body(): String = when (this) {
    is HomeNextStepItem.Scheduling -> "Estado: coordinando proximo encuentro."
    is HomeNextStepItem.SecondChatScheduled -> "Estado: segundo chat programado. Duracion maxima: 2 horas."
    is HomeNextStepItem.SecondChatAvailable -> "Estado: segundo chat disponible. Duracion maxima: 2 horas."
    is HomeNextStepItem.Unknown -> "Estado: $rawState."
}

private fun HomeNextStepItem.canOpenSecondChat(nowMillis: Long = System.currentTimeMillis()): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatAvailable -> true
        is HomeNextStepItem.SecondChatScheduled -> {
            chatStatus == "AVAILABLE" ||
                chatStatus == "ACTIVE" ||
                inferredEntryOpensAt()?.let { !Instant.ofEpochMilli(nowMillis).isBefore(it) } == true
        }

        else -> false
    }

private fun HomeNextStepItem.secondChatCtaLabel(canOpenSecondChat: Boolean): String =
    if (canOpenSecondChat) {
        "Entrar al segundo chat"
    } else {
        val opensAt = (this as? HomeNextStepItem.SecondChatScheduled)?.inferredEntryOpensAt()
        opensAt?.let { "Disponible a las ${formatBackendTime(it.toString())}" }
            ?: "Segundo chat pendiente"
    }

private fun HomeNextStepItem.SecondChatScheduled.inferredEntryOpensAt(): Instant? =
    expiresAt?.let { value ->
        try {
            OffsetDateTime.parse(value)
                .toInstant()
                .minus(SECOND_CHAT_DURATION)
                .minus(SECOND_CHAT_ENTRY_TOLERANCE)
        } catch (_: DateTimeParseException) {
            null
        }
    }
