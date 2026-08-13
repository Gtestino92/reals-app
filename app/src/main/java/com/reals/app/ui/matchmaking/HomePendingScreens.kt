package com.reals.app.ui.matchmaking

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                Text("Pendientes", style = MaterialTheme.typography.titleLarge)
                presentation.summaryText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Prioridad ahora",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
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
                    Text("Ver ${presentation.priorityOverflowCount} más en Pendientes")
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
    val title = when (item) {
        is HomePriorityItem.VisualReview -> "Revisión visual por vencer"
        is HomePriorityItem.SecondChatOpen -> "Tu segundo chat ya empezó"
        is HomePriorityItem.SecondChatStartingSoon -> "Tu segundo chat empieza pronto"
    }
    val body = when (item) {
        is HomePriorityItem.VisualReview -> "Revisá antes de que venza."
        is HomePriorityItem.SecondChatOpen -> "Entrar al chat"
        is HomePriorityItem.SecondChatStartingSoon -> item.item.homeNextStepBody(nowMillis)
    }
    val actionLabel = when (item) {
        is HomePriorityItem.VisualReview -> "Revisar ahora"
        is HomePriorityItem.SecondChatOpen -> "Entrar al chat"
        is HomePriorityItem.SecondChatStartingSoon -> "Ver en Pendientes"
    }

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Text(
            text = "Pendientes",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Tus revisiones, próximos pasos y segundos chats en un solo lugar.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (presentation.hubSections.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                Spacer(modifier = Modifier.height(16.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(section.type.title, style = MaterialTheme.typography.titleLarge)
            section.secondaryGroups.forEach { group ->
                group.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                group.items.forEach { item ->
                    when (item) {
                        is HomePendingHubItem.VisualReview -> VisualApprovalItem(
                            action = item.action,
                            busy = busy,
                            nowMillis = nowMillis,
                            titleOverride = item.action.pendingVisualReviewTitle(),
                            onOpenVisualApproval = onOpenVisualApproval,
                        )
                        is HomePendingHubItem.NextStep -> NextStepItem(
                            item = item.item,
                            busy = busy,
                            nowMillis = nowMillis,
                            dismissContentDescription = "Quitar de Pendientes",
                            titleOverride = item.item.pendingNextStepTitle(),
                            bodyOverride = item.item.pendingNextStepBody(nowMillis),
                            onOpenScheduling = onOpenScheduling,
                            onOpenSecondChat = onOpenSecondChat,
                            onOpenPartnerProfile = onOpenPartnerProfile,
                            onDismissSecondChat = onDismissSecondChat,
                        )
                    }
                }
            }
        }
    }
}
