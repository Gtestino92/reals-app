package com.reals.app.ui.matchmaking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.formatBackendContextualDateTime
import com.reals.app.ui.common.formatBackendTime
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

@Composable
internal fun PendingActionsCard(
    actions: List<HomeActionItem>,
    initiallyExpandedSection: HomeSectionKey?,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    if (actions.isEmpty()) return
    val sections = homeActionSections(actions)

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
            HomeCollapsibleSection(
                title = "Chats iniciales",
                count = sections.firstChats.size,
                visible = sections.firstChats.isNotEmpty(),
                initiallyExpanded = false,
            ) {
                sections.firstChats.forEach { action ->
                    FirstChatItem(
                        action = action,
                        busy = busy,
                        onOpenFirstChat = onOpenFirstChat,
                    )
                }
            }
            HomeCollapsibleSection(
                title = "Revisión visual",
                count = sections.visualReviews.size,
                visible = sections.visualReviews.isNotEmpty(),
                initiallyExpanded = initiallyExpandedSection == HomeSectionKey.VisualReview,
            ) {
                sections.visualReviews.forEach { action ->
                    VisualApprovalItem(
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
    initiallyExpandedSection: HomeSectionKey?,
    busy: Boolean,
    nowMillis: Long,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenPartnerProfile: (matchId: String) -> Unit,
    onDismissSecondChat: (connectionId: String) -> Unit,
) {
    if (nextSteps.isEmpty()) return
    val sections = homeNextStepSections(nextSteps)

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
            HomeCollapsibleSection(
                title = "Coordinación",
                count = sections.schedulingItems.size,
                visible = sections.schedulingItems.isNotEmpty(),
                initiallyExpanded = initiallyExpandedSection == HomeSectionKey.Scheduling,
            ) {
                sections.schedulingItems.forEach { nextStep ->
                    NextStepItem(
                        item = nextStep,
                        busy = busy,
                        nowMillis = nowMillis,
                        onOpenScheduling = onOpenScheduling,
                        onOpenSecondChat = onOpenSecondChat,
                        onOpenPartnerProfile = onOpenPartnerProfile,
                        onDismissSecondChat = onDismissSecondChat,
                    )
                }
            }
            HomeCollapsibleSection(
                title = "Segundos chats",
                count = sections.secondChatItems.size,
                visible = sections.secondChatItems.isNotEmpty(),
                initiallyExpanded = initiallyExpandedSection == HomeSectionKey.SecondChat,
            ) {
                sections.secondChatItems.forEach { nextStep ->
                    NextStepItem(
                        item = nextStep,
                        busy = busy,
                        nowMillis = nowMillis,
                        onOpenScheduling = onOpenScheduling,
                        onOpenSecondChat = onOpenSecondChat,
                        onOpenPartnerProfile = onOpenPartnerProfile,
                        onDismissSecondChat = onDismissSecondChat,
                    )
                }
            }
            HomeCollapsibleSection(
                title = "Otros estados",
                count = sections.unknownItems.size,
                visible = sections.unknownItems.isNotEmpty(),
                initiallyExpanded = false,
            ) {
                sections.unknownItems.forEach { nextStep ->
                    NextStepItem(
                        item = nextStep,
                        busy = busy,
                        nowMillis = nowMillis,
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

internal data class HomeActionSections(
    val firstChats: List<HomeActionItem.FirstChat>,
    val visualReviews: List<HomeActionItem.VisualReview>,
)

internal fun homeActionSections(actions: List<HomeActionItem>): HomeActionSections =
    HomeActionSections(
        firstChats = actions.filterIsInstance<HomeActionItem.FirstChat>(),
        visualReviews = actions.filterIsInstance<HomeActionItem.VisualReview>(),
    )

internal data class HomeNextStepSections(
    val schedulingItems: List<HomeNextStepItem.Scheduling>,
    val secondChatItems: List<HomeNextStepItem>,
    val unknownItems: List<HomeNextStepItem.Unknown>,
)

internal fun homeNextStepSections(nextSteps: List<HomeNextStepItem>): HomeNextStepSections =
    HomeNextStepSections(
        schedulingItems = nextSteps.filterIsInstance<HomeNextStepItem.Scheduling>(),
        secondChatItems = nextSteps.filter {
            it is HomeNextStepItem.SecondChatScheduled ||
                it is HomeNextStepItem.SecondChatAvailable ||
                it is HomeNextStepItem.SecondChatReadOnly
        },
        unknownItems = nextSteps.filterIsInstance<HomeNextStepItem.Unknown>(),
    )

internal enum class HomeSectionKey {
    VisualReview,
    Scheduling,
    SecondChat,
}

internal fun initiallyExpandedHomeSection(
    actions: List<HomeActionItem>,
    nextSteps: List<HomeNextStepItem>,
): HomeSectionKey? {
    val actionSections = homeActionSections(actions)
    val nextStepSections = homeNextStepSections(nextSteps)
    return when {
        actionSections.visualReviews.isNotEmpty() ->
            HomeSectionKey.VisualReview.takeIf { actionSections.visualReviews.size == 1 }
        nextStepSections.schedulingItems.isNotEmpty() ->
            HomeSectionKey.Scheduling.takeIf { nextStepSections.schedulingItems.size == 1 }
        nextStepSections.secondChatItems.isNotEmpty() ->
            HomeSectionKey.SecondChat.takeIf { nextStepSections.secondChatItems.size == 1 }
        else -> null
    }
}

@Composable
private fun HomeCollapsibleSection(
    title: String,
    count: Int,
    visible: Boolean,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    var expanded by rememberSaveable(title, count, initiallyExpanded) { mutableStateOf(initiallyExpanded) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$title ($count)",
                    style = MaterialTheme.typography.titleMedium,
                )
                HomeSectionChevron(expanded = expanded)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun HomeSectionChevron(expanded: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = 2.dp.toPx()
        if (expanded) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.2f, size.height * 0.62f),
                end = Offset(size.width * 0.5f, size.height * 0.34f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.5f, size.height * 0.34f),
                end = Offset(size.width * 0.8f, size.height * 0.62f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        } else {
            drawLine(
                color = color,
                start = Offset(size.width * 0.2f, size.height * 0.38f),
                end = Offset(size.width * 0.5f, size.height * 0.66f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.5f, size.height * 0.66f),
                end = Offset(size.width * 0.8f, size.height * 0.38f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
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
            val partnerName = action.partnerDisplayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)

            Text(
                text = partnerName?.let { "Con $it" } ?: "Chat inicial activo",
                style = MaterialTheme.typography.titleMedium,
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
            val partnerName = action.partnerDisplayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)

            Text(
                text = partnerName?.let { "Con $it" } ?: "Revisión pendiente",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Revisá el perfil visual y decidí si querés continuar.",
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
    nowMillis: Long,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenPartnerProfile: (matchId: String) -> Unit,
    onDismissSecondChat: (connectionId: String) -> Unit,
) {
    val partnerName = item.partnerDisplayName()
        ?.let(TextSafety::safeDisplay)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (item is HomeNextStepItem.Unknown) {
                Text("Conexión no disponible", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = partnerName?.let { "Con $it" } ?: "Con la otra persona",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.homeNextStepBody(nowMillis),
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
            if (
                item is HomeNextStepItem.SecondChatScheduled ||
                item is HomeNextStepItem.SecondChatAvailable ||
                item is HomeNextStepItem.SecondChatReadOnly
            ) {
                val canOpenSecondChat = item.canOpenSecondChat(nowMillis)
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
                    Text(item.secondChatCtaLabel(canOpenSecondChat, nowMillis))
                }
            }
            if (item.canShowPartnerProfile(nowMillis)) {
                Button(
                    onClick = { onOpenPartnerProfile(item.matchIdForProfile()) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ver perfil")
                }
            }
            if (item.canDismissSecondChat(nowMillis)) {
                OutlinedButton(
                    onClick = { onDismissSecondChat(item.connectionIdForSecondChat()) },
                    enabled = !busy && item.connectionIdForSecondChat().isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Eliminar")
                }
            }
        }
    }
}

private fun HomeNextStepItem.partnerDisplayName(): String? = when (this) {
    is HomeNextStepItem.Scheduling -> partnerDisplayName
    is HomeNextStepItem.SecondChatScheduled -> partnerDisplayName
    is HomeNextStepItem.SecondChatAvailable -> partnerDisplayName
    is HomeNextStepItem.SecondChatReadOnly -> partnerDisplayName
    is HomeNextStepItem.Unknown -> partnerDisplayName
}

private fun HomeNextStepItem.matchIdForProfile(): String = when (this) {
    is HomeNextStepItem.Scheduling -> matchId
    is HomeNextStepItem.SecondChatScheduled -> matchId
    is HomeNextStepItem.SecondChatAvailable -> matchId
    is HomeNextStepItem.SecondChatReadOnly -> matchId
    is HomeNextStepItem.Unknown -> matchId.orEmpty()
}

private fun HomeNextStepItem.canShowPartnerProfile(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (matchIdForProfile().isBlank()) return false

    return when (this) {
        is HomeNextStepItem.SecondChatReadOnly -> false
        is HomeNextStepItem.SecondChatScheduled,
        is HomeNextStepItem.SecondChatAvailable -> !isUnavailableSecondChat(nowMillis)
        else -> true
    }
}

private fun HomeNextStepItem.canDismissSecondChat(nowMillis: Long = System.currentTimeMillis()): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatReadOnly -> connectionId.isNotBlank()
        is HomeNextStepItem.SecondChatScheduled,
        is HomeNextStepItem.SecondChatAvailable -> connectionIdForSecondChat().isNotBlank() &&
            (isExpiredSecondChatStatus() || isStaleExpiredSecondChat(nowMillis))
        else -> false
    }

private fun HomeNextStepItem.isExpiredSecondChatStatus(): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> chatStatus == "EXPIRED"
        is HomeNextStepItem.SecondChatAvailable -> chatStatus == "EXPIRED"
        is HomeNextStepItem.SecondChatReadOnly -> chatStatus == "EXPIRED"
        else -> false
    }

private fun HomeNextStepItem.connectionIdForSecondChat(): String = when (this) {
    is HomeNextStepItem.SecondChatScheduled -> connectionId
    is HomeNextStepItem.SecondChatAvailable -> connectionId
    is HomeNextStepItem.SecondChatReadOnly -> connectionId
    else -> ""
}

internal fun HomeNextStepItem.homeNextStepBody(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
): String =
    if (isStaleExpiredSecondChat(nowMillis)) {
        "El horario ya vencio y el segundo chat no esta disponible."
    } else {
        when (this) {
            is HomeNextStepItem.Scheduling -> "Coordinando próximo encuentro."
            is HomeNextStepItem.SecondChatScheduled ->
                "Programado para ${
                    formatBackendContextualDateTime(
                        availableAt,
                        nowMillis,
                        zoneId,
                        locale,
                    )
                }. Duracion maxima: ${durationLabel()}."
            is HomeNextStepItem.SecondChatAvailable ->
                "Disponible desde ${
                    formatBackendContextualDateTime(
                        availableAt,
                        nowMillis,
                        zoneId,
                        locale,
                    )
                }. Duracion maxima: ${durationLabel()}."
            is HomeNextStepItem.SecondChatReadOnly ->
                readOnlyUntil?.let {
                    "Disponible solo para lectura hasta ${
                        formatBackendContextualDateTime(
                            it,
                            nowMillis,
                            zoneId,
                            locale,
                        )
                    }."
                }
                    ?: "Disponible solo para lectura."
            is HomeNextStepItem.Unknown -> "Estado: $rawState."
        }
    }

private fun HomeNextStepItem.canOpenSecondChat(nowMillis: Long = System.currentTimeMillis()): Boolean =
    canOpenSecondChatNow(nowMillis)

private fun HomeNextStepItem.secondChatCtaLabel(canOpenSecondChat: Boolean, nowMillis: Long): String =
    if (canOpenSecondChat) {
        if (this is HomeNextStepItem.SecondChatReadOnly) "Ver segundo chat" else "Entrar al segundo chat"
    } else {
        val availableInstant = secondChatAvailableAt().toInstantOrNull()
        when {
            isStaleExpiredSecondChat() -> "Segundo chat vencido"
            availableInstant != null && !Instant.ofEpochMilli(nowMillis).isBefore(availableInstant) ->
                "Preparando segundo chat..."
            else -> secondChatAvailableAt()?.let { "Disponible a las ${formatBackendTime(it)}" }
                ?: "Segundo chat pendiente"
        }
    }

private fun HomeNextStepItem.secondChatAvailableAt(): String? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> availableAt
        is HomeNextStepItem.SecondChatAvailable -> availableAt
        is HomeNextStepItem.SecondChatReadOnly -> availableAt
        else -> null
    }

private fun String?.toInstantOrNull(): Instant? =
    this?.let { value ->
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

private fun HomeNextStepItem.hasSecondChatReference(): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatAvailable ->
            chatId?.isNotBlank() == true && (chatStatus == "AVAILABLE" || chatStatus == "ACTIVE")
        is HomeNextStepItem.SecondChatReadOnly ->
            chatId?.isNotBlank() == true && chatStatus == "EXPIRED"
        else -> false
    }

private fun HomeNextStepItem.isStaleExpiredSecondChat(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val expiresAt = when (this) {
        is HomeNextStepItem.SecondChatScheduled -> expiresAt
        is HomeNextStepItem.SecondChatAvailable -> expiresAt
        else -> null
    }.toInstantOrNull() ?: return false

    return !Instant.ofEpochMilli(nowMillis).isBefore(expiresAt)
}

private fun HomeNextStepItem.isUnavailableSecondChat(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (isStaleExpiredSecondChat(nowMillis)) return true
    val availableAt = secondChatAvailableAt().toInstantOrNull() ?: return false

    return !hasSecondChatReference() && !Instant.ofEpochMilli(nowMillis).isBefore(availableAt)
}

private fun HomeNextStepItem.durationLabel(): String {
    val minutes = when (this) {
        is HomeNextStepItem.SecondChatScheduled -> durationMinutes
        is HomeNextStepItem.SecondChatAvailable -> durationMinutes
        is HomeNextStepItem.SecondChatReadOnly -> durationMinutes
        else -> null
    } ?: return "2 horas"

    return if (minutes % 60L == 0L) {
        val hours = minutes / 60L
        "$hours ${if (hours == 1L) "hora" else "horas"}"
    } else {
        "$minutes minutos"
    }
}
