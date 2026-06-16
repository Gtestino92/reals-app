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
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeMatch
import com.reals.app.domain.model.HomeState
import com.reals.app.ui.common.userLabel

@Composable
internal fun PendingActionsCard(
    homeState: HomeState?,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    val firstChatMatches = homeState.pendingFirstChatMatches()
    val visualApprovals = homeState.pendingVisualApprovals()

    if (firstChatMatches.isEmpty() && visualApprovals.isEmpty()) return

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
            firstChatMatches.forEach { match ->
                FirstChatItem(
                    match = match,
                    busy = busy,
                    onOpenFirstChat = onOpenFirstChat,
                )
            }
            visualApprovals.forEach { match ->
                VisualApprovalItem(
                    match = match,
                    busy = busy,
                    onOpenVisualApproval = onOpenVisualApproval,
                )
            }
        }
    }
}

@Composable
internal fun NextStepCard(
    homeState: HomeState?,
    busy: Boolean,
    onOpenPartnerProfile: (matchId: String) -> Unit,
) {
    val connections = homeState.nextStepConnections()

    if (connections.isEmpty()) return

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

            connections.forEach { connection ->
                ConnectionPlaceholderItem(
                    connection = connection,
                    busy = busy,
                    onOpenPartnerProfile = onOpenPartnerProfile,
                )
            }
        }
    }
}

@Composable
private fun FirstChatItem(
    match: HomeMatch,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
) {
    val firstChat = match.firstChat ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chat inicial", style = MaterialTheme.typography.titleMedium)
            val partnerName = firstChat.partner?.displayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)

            Text(
                text = partnerName?.let { "Con $it" } ?: "Chat inicial activo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Valido hasta ${formatBackendDateTime(firstChat.expiresAt)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Estado: ${firstChat.chatStatus.userLabel()}. Podes entrar cuando quieras.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenFirstChat(match.matchId, firstChat.chatId) },
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
    match: HomeMatch,
    busy: Boolean,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val partnerName = match.partnerDisplayName?.takeIf { it.isNotBlank() }

            Text(
                text = partnerName?.let { "Aprobacion visual con $it" }
                    ?: "Aprobacion visual pendiente"
            )
            Text(
                text = "Revisa el perfil visual y decidi si queres continuar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenVisualApproval(match.matchId) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Abrir aprobacion visual")
            }
        }
    }
}

@Composable
private fun ConnectionPlaceholderItem(
    connection: HomeConnection,
    busy: Boolean,
    onOpenPartnerProfile: (matchId: String) -> Unit,
) {
    val partnerName = connection.partnerDisplayName()
        ?.let(TextSafety::safeDisplay)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Coordinacion pendiente", style = MaterialTheme.typography.titleMedium)
            Text(
                text = partnerName?.let { "Con $it" } ?: "Con la otra persona",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Estado: ${connection.connectionState.userLabel()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Ya hubo aprobacion visual mutua. Falta implementar la coordinacion del proximo chat.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenPartnerProfile(connection.matchId) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ver perfil")
            }
        }
    }
}
