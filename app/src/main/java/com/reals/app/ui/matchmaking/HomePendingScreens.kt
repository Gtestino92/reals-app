package com.reals.app.ui.matchmaking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.ui.common.RealsScreenHeader
import com.reals.app.ui.common.RealsSectionLabel
import com.reals.app.ui.common.RealsThinDivider
import com.reals.app.ui.theme.RealsRadii

@Composable
internal fun HomeFirstChatsBlock(
    firstChats: List<HomeActionItem.FirstChat>,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
) {
    if (firstChats.isEmpty()) return
    Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        firstChats.forEach { firstChat ->
            FirstChatItem(
                action = firstChat,
                busy = busy,
                onOpenFirstChat = onOpenFirstChat,
            )
        }
    }
}

@Composable
internal fun HomePendingSummaryCard(
    presentation: HomePendingPresentation,
    busy: Boolean,
    onOpenPending: () -> Unit,
) {
    if (!presentation.hasHubItems) return
    Card(
        onClick = onOpenPending,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Actividad", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = presentation.summaryText ?: "Sin acciones requeridas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HomeTrailingChevron()
        }
    }
}

@Composable
internal fun HomePriorityBlock(
    presentation: HomePendingPresentation,
    busy: Boolean,
    nowMillis: Long,
    onOpenPending: () -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
) {
    if (presentation.priorityItems.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Prioridad ahora",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            presentation.priorityItems.forEach { item ->
                HomePriorityRow(
                    item = item,
                    busy = busy,
                    nowMillis = nowMillis,
                    onOpenPending = onOpenPending,
                    onOpenVisualApproval = onOpenVisualApproval,
                    onOpenSecondChat = onOpenSecondChat,
                )
            }
            if (presentation.priorityOverflowCount > 0) {
                OutlinedButton(
                    onClick = onOpenPending,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ver ${presentation.priorityOverflowCount} más en Actividad")
                }
            }
        }
    }
}

@Composable
private fun HomePriorityRow(
    item: HomePriorityItem,
    busy: Boolean,
    nowMillis: Long,
    onOpenPending: () -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
) {
    val nextStep = when (item) {
        is HomePriorityItem.SecondChatOpen -> item.item
        is HomePriorityItem.SecondChatStartingSoon -> item.item
        is HomePriorityItem.VisualReview -> null
    }
    val secondChatPresentation = nextStep?.secondChatHomePresentation(nowMillis)
    val enabled = !busy && when (item) {
        is HomePriorityItem.VisualReview -> true
        is HomePriorityItem.SecondChatOpen ->
            secondChatPresentation?.canOpenChat == true &&
                item.item.connectionIdForSecondChat().isNotBlank() &&
                item.item.matchIdForProfile().isNotBlank()
        is HomePriorityItem.SecondChatStartingSoon -> true
    }
    val onClick: () -> Unit = when (item) {
        is HomePriorityItem.VisualReview -> {
            { onOpenVisualApproval(item.action.matchId) }
        }
        is HomePriorityItem.SecondChatOpen -> {
            {
                onOpenSecondChat(
                    item.item.connectionIdForSecondChat(),
                    item.item.matchIdForProfile(),
                    item.item.partnerDisplayName(),
                )
            }
        }
        is HomePriorityItem.SecondChatStartingSoon -> onOpenPending
    }
    val title = item.homePriorityTitle()
    val actionLabel = when (item) {
        is HomePriorityItem.VisualReview -> "Descubrir ahora"
        is HomePriorityItem.SecondChatOpen -> "Entrar al chat"
        is HomePriorityItem.SecondChatStartingSoon -> "Ver en Actividad
    }
    val body = item.homePriorityBody(nowMillis)

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HomeTrailingChevron()
        }
    }
}

internal fun HomePriorityItem.homePriorityBody(nowMillis: Long): String =
    when (this) {
        is HomePriorityItem.VisualReview -> "Revisá antes de que venza."
        is HomePriorityItem.SecondChatOpen -> "Ya está disponible."
        is HomePriorityItem.SecondChatStartingSoon -> item.homeNextStepBody(nowMillis)
    }

@Composable
internal fun PendingInteractionsScreen(
    presentation: HomePendingPresentation,
    busy: Boolean,
    nowMillis: Long,
    onBackHome: () -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenPartnerProfile: (matchId: String) -> Unit,
    onDismissSecondChat: (connectionId: String) -> Unit,
) {
    BackHandler(onBack = onBackHome)
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            RealsScreenHeader(
                title = "Actividad",
                subtitle = "Descubrimientos, próximos pasos y segundos chats en un solo lugar.",
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onBackHome,
                enabled = !busy,
            ) {
                Text("Volver")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (presentation.hubSections.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RealsRadii.Card),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = "No tenés pendientes por ahora.",
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            presentation.hubSections.forEach { section ->
                PendingHubSection(
                    section = section,
                    busy = busy,
                    nowMillis = nowMillis,
                    onOpenVisualApproval = onOpenVisualApproval,
                    onOpenScheduling = onOpenScheduling,
                    onOpenSecondChat = onOpenSecondChat,
                    onOpenPartnerProfile = onOpenPartnerProfile,
                    onDismissSecondChat = onDismissSecondChat,
                )
                Spacer(modifier = Modifier.height(22.dp))
            }
        }
        Button(
            onClick = onBackHome,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver a Inicio")
        }
    }
}

@Composable
private fun PendingHubSection(
    section: HomePendingSection,
    busy: Boolean,
    nowMillis: Long,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenPartnerProfile: (matchId: String) -> Unit,
    onDismissSecondChat: (connectionId: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(section.type.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        RealsThinDivider()
        section.secondaryGroups.forEach { group ->
            group.title?.let { title ->
                RealsSectionLabel(
                    text = title,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            group.items.forEachIndexed { index, item ->
                when (item) {
                    is HomePendingHubItem.VisualReview -> VisualApprovalItem(
                        action = item.action,
                        busy = busy,
                        nowMillis = nowMillis,
                        titleOverride = item.action.pendingVisualReviewTitle(),
                        usePendingRowOutline = true,
                        onOpenVisualApproval = onOpenVisualApproval,
                    )
                    is HomePendingHubItem.NextStep -> NextStepItem(
                        item = item.item,
                        busy = busy,
                        nowMillis = nowMillis,
                        dismissContentDescription = "Quitar de Actividad",
                        titleOverride = item.item.pendingNextStepTitle(),
                        bodyOverride = item.item.pendingNextStepBodyOverride(nowMillis),
                        usePendingRowOutline = true,
                        onOpenScheduling = onOpenScheduling,
                        onOpenSecondChat = onOpenSecondChat,
                        onOpenPartnerProfile = onOpenPartnerProfile,
                        onDismissSecondChat = onDismissSecondChat,
                    )
                }
                if (index < group.items.lastIndex) {
                    RealsThinDivider(modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}
