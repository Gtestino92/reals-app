package com.reals.app.ui.matchmaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.reals.app.R
import com.reals.app.core.security.TextSafety
import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.ui.common.VisualReviewHomeDeadlineStrings
import com.reals.app.ui.common.VisualReviewProgressUrgency
import com.reals.app.ui.common.formatBackendContextualDateTime
import com.reals.app.ui.common.formatVisualReviewHomeDeadline
import com.reals.app.ui.common.visualReviewProgressUrgency
import com.reals.app.ui.common.visualReviewRemainingFraction
import com.reals.app.ui.profile.AffinityOverviewPrimaryAction
import com.reals.app.ui.profile.overviewActionPolicy
import com.reals.app.ui.profile.progress
import com.reals.app.ui.profile.reviewRows
import com.reals.app.ui.root.AffinityHomeSummaryUiState
import com.reals.app.ui.scheduling.schedulingDeadlineRemainingFraction
import com.reals.app.ui.theme.RealsRadii
import kotlinx.coroutines.delay
import java.time.ZoneId
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
    val visualNowMillis = rememberVisualReviewNowMillis(
        actions.any { it is HomeActionItem.VisualReview },
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeCollapsibleSection(
                title = "Acciones pendientes",
                count = actions.size,
                visible = actions.isNotEmpty(),
                initiallyExpanded = initiallyExpandedSection == HomeSectionKey.PendingActions,
            ) {
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
                            nowMillis = visualNowMillis,
                            onOpenVisualApproval = onOpenVisualApproval,
                        )
                    }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeCollapsibleSection(
                title = "Próximos pasos",
                count = nextSteps.size,
                visible = nextSteps.isNotEmpty(),
                initiallyExpanded = initiallyExpandedSection == HomeSectionKey.NextSteps,
            ) {
                nextSteps.forEach { nextStep ->
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

internal enum class HomeSectionKey {
    PendingActions,
    NextSteps,
}

internal fun initiallyExpandedHomeSection(
    actions: List<HomeActionItem>,
    nextSteps: List<HomeNextStepItem>,
): HomeSectionKey? {
    return when {
        actions.isNotEmpty() -> HomeSectionKey.PendingActions.takeIf { actions.size == 1 }
        nextSteps.isNotEmpty() -> HomeSectionKey.NextSteps.takeIf { nextSteps.size == 1 }
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
    var expanded by rememberSaveable(homeCollapsibleSectionStateKey(title)) {
        mutableStateOf(initiallyExpanded)
    }

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

internal fun homeCollapsibleSectionStateKey(title: String): String =
    "home-section:$title"

@Composable
internal fun HomeAffinityCard(
    summary: AffinityHomeSummaryUiState,
    busy: Boolean,
    onOpenAffinityQuestions: () -> Unit,
) {
    val presentation = homeAffinityCardPresentation(summary)
    Card(
        onClick = onOpenAffinityQuestions,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = presentation.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                presentation.progressText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (presentation.loading) {
                    Text(
                        text = "Actualizando afinidades...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = presentation.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HomeTrailingChevron()
        }
    }
}

@Composable
internal fun HomeManagementEntryCard(
    title: String,
    body: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
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

internal data class HomeAffinityCardPresentation(
    val title: String,
    val body: String,
    val progressText: String?,
    val actionLabel: String,
    val loading: Boolean,
)

internal fun homeAffinityCardPresentation(
    summary: AffinityHomeSummaryUiState,
): HomeAffinityCardPresentation {
    val catalog = summary.catalog ?: return HomeAffinityCardPresentation(
        title = "Descubrí tus afinidades",
        body = HomeAffinityBody,
        progressText = null,
        actionLabel = "Empezar",
        loading = summary.loading,
    )
    val progress = catalog.progress(summary.answers)
    val actionPolicy = progress.overviewActionPolicy(
        hasReviewRows = catalog.reviewRows(summary.answers).isNotEmpty(),
    )
    val allAnswered = progress.totalQuestionCount > 0 &&
        progress.answeredCount == progress.totalQuestionCount
    val primaryAction = actionPolicy.primaryAction

    return HomeAffinityCardPresentation(
        title = when {
            allAnswered -> "Tus afinidades"
            progress.answeredCount == 0 -> "Descubrí tus afinidades"
            else -> "Seguí construyendo tus afinidades"
        },
        body = HomeAffinityBody,
        progressText = if (progress.totalQuestionCount > 0) {
            "${progress.answeredCount} de ${progress.totalQuestionCount} respondidas"
        } else {
            null
        },
        actionLabel = when (primaryAction) {
            AffinityOverviewPrimaryAction.Start -> "Empezar"
            AffinityOverviewPrimaryAction.Continue -> "Continuar"
            AffinityOverviewPrimaryAction.Review -> "Revisar respuestas"
            null -> "Abrir"
        },
        loading = summary.loading,
    )
}

private const val HomeAffinityBody =
    "Tus respuestas son opcionales y privadas. Ayudan a Reals a encontrar afinidades compartidas y contexto para conversar sin mostrar tus respuestas exactas."

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
internal fun FirstChatItem(
    action: HomeActionItem.FirstChat,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
) {
    Card(
        onClick = { onOpenFirstChat(action.matchId, action.chatId) },
        enabled = !busy,
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
            val partnerName = action.partnerDisplayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = partnerName?.let { "Chat inicial con $it" } ?: "Chat inicial activo",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Entrar al chat",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HomeTrailingChevron()
        }
    }
}

@Composable
internal fun VisualApprovalItem(
    action: HomeActionItem.VisualReview,
    busy: Boolean,
    nowMillis: Long,
    titleOverride: String? = null,
    usePendingRowOutline: Boolean = false,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    val deadlineText = formatVisualReviewHomeDeadline(
        visualExpiresAt = action.visualExpiresAt,
        nowMillis = nowMillis,
        strings = visualReviewHomeDeadlineStrings(),
    )
    val remainingFraction = visualReviewRemainingFraction(
        visualStartedAt = action.visualStartedAt,
        visualExpiresAt = action.visualExpiresAt,
        nowMillis = nowMillis,
    )

    Card(
        onClick = { onOpenVisualApproval(action.matchId) },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = if (usePendingRowOutline) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val partnerName = action.partnerDisplayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = titleOverride
                            ?: (partnerName?.let { "Con $it" } ?: "Revisión pendiente"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    deadlineText?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                HomeTrailingChevron()
            }
            remainingFraction?.let {
                DeadlineProgressLine(
                    remainingFraction = it,
                    progressColor = visualReviewProgressColor(it),
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun HomeTrailingChevron() {
    val color = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier = Modifier
            .size(22.dp)
            .clearAndSetSemantics {},
    ) {
        val strokeWidth = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.25f),
            end = Offset(size.width * 0.65f, size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.65f, size.height * 0.5f),
            end = Offset(size.width * 0.35f, size.height * 0.75f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DeadlineProgressLine(
    remainingFraction: Double,
    progressColor: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .height(4.dp)
            .clip(shape)
            .background(trackColor)
            .clearAndSetSemantics {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(remainingFraction.toFloat().coerceIn(0f, 1f))
                .height(4.dp)
                .clip(shape)
                .background(progressColor),
        )
    }
}

@Composable
private fun visualReviewProgressColor(remainingFraction: Double): Color =
    when (visualReviewProgressUrgency(remainingFraction)) {
        VisualReviewProgressUrgency.Normal -> MaterialTheme.colorScheme.primary
        VisualReviewProgressUrgency.Warning -> MaterialTheme.colorScheme.secondary
        VisualReviewProgressUrgency.Critical -> MaterialTheme.colorScheme.error
    }

@Composable
private fun schedulingProgressColor(remainingFraction: Double): Color =
    when {
        remainingFraction <= 0.10 -> MaterialTheme.colorScheme.error
        remainingFraction <= 0.40 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun visualReviewHomeDeadlineStrings(): VisualReviewHomeDeadlineStrings =
    VisualReviewHomeDeadlineStrings(
        expired = stringResource(R.string.visual_review_deadline_home_expired),
        underOneHour = stringResource(R.string.visual_review_deadline_home_under_one_hour),
        today = stringResource(R.string.visual_review_deadline_home_today),
        tomorrow = stringResource(R.string.visual_review_deadline_home_tomorrow),
        laterSameYear = stringResource(R.string.visual_review_deadline_home_later_same_year),
        laterDifferentYear = stringResource(R.string.visual_review_deadline_home_later_different_year),
    )

@Composable
private fun rememberVisualReviewNowMillis(enabled: Boolean): Long {
    var nowMillis by rememberSaveable(enabled) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            nowMillis = now
            val nextMinuteDelay = (60_000L - (now % 60_000L)).coerceIn(1_000L, 60_000L)
            delay(nextMinuteDelay)
        }
    }

    return nowMillis
}

@Composable
internal fun NextStepItem(
    item: HomeNextStepItem,
    busy: Boolean,
    nowMillis: Long,
    dismissContentDescription: String = "Quitar de Inicio",
    titleOverride: String? = null,
    bodyOverride: String? = null,
    usePendingRowOutline: Boolean = false,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenPartnerProfile: (matchId: String) -> Unit,
    onDismissSecondChat: (connectionId: String) -> Unit,
) {
    val partnerName = item.partnerDisplayName()
        ?.let(TextSafety::safeDisplay)
    val secondChatPresentation =
        if (
            item is HomeNextStepItem.SecondChatScheduled ||
            item is HomeNextStepItem.SecondChatAvailable ||
            item is HomeNextStepItem.SecondChatExpired ||
            item is HomeNextStepItem.SecondChatReadOnly
        ) {
            item.secondChatHomePresentation(nowMillis)
        } else {
            null
        }
    val primaryAction: (() -> Unit)? = when {
        item is HomeNextStepItem.Scheduling -> {
            {
                onOpenScheduling(
                    item.connectionId,
                    item.matchId,
                    item.partnerDisplayName,
                )
            }
        }

        secondChatPresentation?.canOpenChat == true -> {
            {
                onOpenSecondChat(
                    item.connectionIdForSecondChat(),
                    item.matchIdForProfile(),
                    item.partnerDisplayName(),
                )
            }
        }

        else -> null
    }
    val primaryEnabled = !busy &&
        when {
            item is HomeNextStepItem.Scheduling ->
                item.connectionId.isNotBlank() && item.matchId.isNotBlank()

            secondChatPresentation?.canOpenChat == true ->
                item.connectionIdForSecondChat().isNotBlank() && item.matchIdForProfile().isNotBlank()

            else -> false
        }
    val primaryLabel = when {
        item is HomeNextStepItem.Scheduling -> "Elegir horarios"
        secondChatPresentation?.canOpenChat == true -> secondChatPresentation.primaryCtaLabel
        else -> secondChatPresentation?.primaryCtaLabel
    }
    val schedulingRemainingFraction = (item as? HomeNextStepItem.Scheduling)?.let {
        schedulingDeadlineRemainingFraction(
            negotiationCreatedAt = it.createdAt,
            schedulingExpiresAt = it.schedulingExpiresAt,
            nowMillis = nowMillis,
        )
    }
    val schedulingDeadlineText = (item as? HomeNextStepItem.Scheduling)
        ?.schedulingDeadlineText(nowMillis = nowMillis)
    val showPartnerProfile = item.canShowPartnerProfile(nowMillis)
    val showDismiss = item.canShowSecondChatDismissAction(nowMillis)
    val rowBorderColor = if (usePendingRowOutline) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    @Composable
    fun Content(showPrimaryChevron: Boolean) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = titleOverride ?: if (item is HomeNextStepItem.Unknown) {
                        "Conexión no disponible"
                    } else {
                        partnerName?.let { "Con $it" } ?: "Con la otra persona"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                val body = bodyOverride ?: item.homeNextStepRowBody(nowMillis)
                body?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                primaryLabel?.let { label ->
                    Text(
                        text = label,
                        color = if (primaryAction != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                schedulingDeadlineText?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                schedulingRemainingFraction?.let {
                    DeadlineProgressLine(
                        remainingFraction = it,
                        progressColor = schedulingProgressColor(it),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .fillMaxWidth(),
                    )
                }
                if (showPartnerProfile) {
                    TextButton(
                        onClick = { onOpenPartnerProfile(item.matchIdForProfile()) },
                        enabled = !busy,
                    ) {
                        Text("Ver perfil")
                    }
                }
            }
            if (showDismiss) {
                IconButton(
                    onClick = { onDismissSecondChat(item.connectionIdForSecondChat()) },
                    enabled = !busy && item.connectionIdForSecondChat().isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = dismissContentDescription },
                ) {
                    Text(
                        text = "×",
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            if (showPrimaryChevron) {
                HomeTrailingChevron()
            }
        }
    }

    if (primaryAction != null) {
        Card(
            onClick = primaryAction,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RealsRadii.Row),
            border = BorderStroke(1.dp, rowBorderColor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Content(showPrimaryChevron = true)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RealsRadii.Row),
            border = BorderStroke(1.dp, rowBorderColor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Content(showPrimaryChevron = false)
        }
    }
}

internal fun HomeNextStepItem.homeNextStepRowBody(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
): String? = when (this) {
    is HomeNextStepItem.Scheduling -> null
    else -> homeNextStepBody(nowMillis, zoneId, locale)
}

internal fun HomeNextStepItem.Scheduling.schedulingDeadlineText(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
): String? {
    val expiresAt = schedulingExpiresAt?.takeIf { it.isNotBlank() } ?: return null
    backendInstantOrNull(expiresAt) ?: return null
    return "Vence: ${formatBackendContextualDateTime(expiresAt, nowMillis, zoneId, locale)}"
}

internal fun HomeNextStepItem.partnerDisplayName(): String? = when (this) {
    is HomeNextStepItem.Scheduling -> partnerDisplayName
    is HomeNextStepItem.SecondChatScheduled -> partnerDisplayName
    is HomeNextStepItem.SecondChatAvailable -> partnerDisplayName
    is HomeNextStepItem.SecondChatExpired -> partnerDisplayName
    is HomeNextStepItem.SecondChatReadOnly -> partnerDisplayName
    is HomeNextStepItem.Unknown -> partnerDisplayName
}

internal fun HomeNextStepItem.matchIdForProfile(): String = when (this) {
    is HomeNextStepItem.Scheduling -> matchId
    is HomeNextStepItem.SecondChatScheduled -> matchId
    is HomeNextStepItem.SecondChatAvailable -> matchId
    is HomeNextStepItem.SecondChatExpired -> matchId
    is HomeNextStepItem.SecondChatReadOnly -> matchId
    is HomeNextStepItem.Unknown -> matchId.orEmpty()
}

private fun HomeNextStepItem.canShowPartnerProfile(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (matchIdForProfile().isBlank()) return false

    return when (this) {
        is HomeNextStepItem.SecondChatScheduled,
        is HomeNextStepItem.SecondChatAvailable,
        is HomeNextStepItem.SecondChatExpired,
        is HomeNextStepItem.SecondChatReadOnly -> secondChatHomePresentation(nowMillis)?.canOpenPartnerProfile == true
        else -> true
    }
}

internal fun HomeNextStepItem.canShowSecondChatDismissAction(nowMillis: Long = System.currentTimeMillis()): Boolean =
    secondChatHomePresentation(nowMillis)?.canDismiss == true

internal fun HomeNextStepItem.homeNextStepBody(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
): String =
    when (secondChatHomePresentation(nowMillis)?.state) {
        SecondChatHomeState.ReadOnlyEnded -> "El período de solo lectura terminó."
        SecondChatHomeState.Expired -> "La ventana para entrar terminó."
        else ->
        when (this) {
            is HomeNextStepItem.Scheduling -> "Proponé opciones para el segundo chat."
            is HomeNextStepItem.SecondChatScheduled ->
                "Programado para ${
                    formatBackendContextualDateTime(
                        availableAt,
                        nowMillis,
                        zoneId,
                        locale,
                    )
                }. Duración máxima: ${durationLabel()}."
            is HomeNextStepItem.SecondChatAvailable ->
                "Disponible desde ${
                    formatBackendContextualDateTime(
                        availableAt,
                        nowMillis,
                        zoneId,
                        locale,
                    )
                }. Duración máxima: ${durationLabel()}."
            is HomeNextStepItem.SecondChatExpired -> "La ventana para entrar terminó."
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

private fun HomeNextStepItem.durationLabel(): String {
    val minutes = when (this) {
        is HomeNextStepItem.SecondChatScheduled -> durationMinutes
        is HomeNextStepItem.SecondChatAvailable -> durationMinutes
        is HomeNextStepItem.SecondChatExpired -> durationMinutes
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
