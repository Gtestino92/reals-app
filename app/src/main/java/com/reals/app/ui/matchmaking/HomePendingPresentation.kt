package com.reals.app.ui.matchmaking

import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.VisualReviewProgressUrgency
import com.reals.app.ui.common.visualReviewProgressUrgency
import com.reals.app.ui.common.visualReviewRemainingFraction

internal data class HomePendingPresentation(
    val firstChats: List<HomeActionItem.FirstChat>,
    val hubSections: List<HomePendingSection>,
    val summaryText: String?,
    val priorityItems: List<HomePriorityItem>,
    val priorityOverflowCount: Int,
) {
    val hasHubItems: Boolean = hubSections.any { it.items.isNotEmpty() }
}

internal data class HomePendingSection(
    val type: HomePendingSectionType,
    val items: List<HomePendingHubItem>,
) {
    val secondaryGroups: List<HomePendingSecondaryGroup> = items.toPendingSecondaryGroups()
}

internal data class HomePendingSecondaryGroup(
    val kind: HomePendingItemKind?,
    val items: List<HomePendingHubItem>,
) {
    val title: String? = kind?.title
}

internal enum class HomePendingItemKind(val title: String) {
    VisualReview("Perfiles por descubrir"),
    Scheduling("Coordinación"),
    SecondChat("Segundos chats"),
}

internal enum class HomePendingSectionType(val title: String) {
    ActionRequired("Requiere tu acción"),
    InProgress("En curso"),
    Upcoming("Próximamente"),
    Recent("Recientes"),
    Other("Otros"),
}

internal sealed interface HomePendingHubItem {
    val sourceIndex: Int

    data class VisualReview(
        val action: HomeActionItem.VisualReview,
        override val sourceIndex: Int,
        val expiresAtMillis: Long?,
    ) : HomePendingHubItem

    data class NextStep(
        val item: HomeNextStepItem,
        override val sourceIndex: Int,
        val secondChatPresentation: HomeSecondChatPresentation?,
        val availableAtMillis: Long?,
        val eventMillis: Long?,
    ) : HomePendingHubItem
}

internal sealed interface HomePriorityItem {
    val sourceIndex: Int
    val eventMillis: Long?

    data class VisualReview(
        val action: HomeActionItem.VisualReview,
        override val sourceIndex: Int,
        override val eventMillis: Long?,
    ) : HomePriorityItem

    data class SecondChatOpen(
        val item: HomeNextStepItem,
        override val sourceIndex: Int,
        override val eventMillis: Long?,
    ) : HomePriorityItem

    data class SecondChatStartingSoon(
        val item: HomeNextStepItem,
        override val sourceIndex: Int,
        override val eventMillis: Long?,
    ) : HomePriorityItem
}

internal fun homePendingPresentation(
    model: HomeScreenModel,
    nowMillis: Long,
): HomePendingPresentation {
    val firstChats = model.pendingActions.filterIsInstance<HomeActionItem.FirstChat>()
    val visualReviewHubItems = model.pendingActions
        .mapIndexedNotNull { index, action ->
            (action as? HomeActionItem.VisualReview)?.let {
                HomePendingHubItem.VisualReview(
                    action = it,
                    sourceIndex = index,
                    expiresAtMillis = it.visualExpiresAt.toEpochMillisOrNull(),
                )
            }
        }
    val nextStepHubItems = model.nextSteps.mapIndexed { index, item ->
        HomePendingHubItem.NextStep(
            item = item,
            sourceIndex = index,
            secondChatPresentation = item.secondChatHomePresentation(nowMillis),
            availableAtMillis = item.secondChatAvailableAtInstant()?.toEpochMilli(),
            eventMillis = item.pendingEventMillis(nowMillis),
        )
    }

    val actionRequired = buildList {
        addAll(
            visualReviewHubItems.sortedWith(
                compareBy<HomePendingHubItem.VisualReview>(
                    { it.expiresAtMillis ?: Long.MAX_VALUE },
                    { it.sourceIndex },
                ),
            ),
        )
        addAll(nextStepHubItems.filter { it.item is HomeNextStepItem.Scheduling })
    }
    val inProgress = nextStepHubItems
        .filter {
            it.secondChatPresentation?.state == SecondChatHomeState.Open ||
                it.secondChatPresentation?.state == SecondChatHomeState.Preparing
        }
        .sortedByEventThenSource()
    val upcoming = nextStepHubItems
        .filter { it.secondChatPresentation?.state == SecondChatHomeState.Waiting }
        .sortedWith(
            compareBy<HomePendingHubItem.NextStep>(
                { it.availableAtMillis ?: Long.MAX_VALUE },
                { it.sourceIndex },
            ),
        )
    val recent = nextStepHubItems
        .filter {
            it.secondChatPresentation?.state == SecondChatHomeState.ReadOnly ||
                it.secondChatPresentation?.state == SecondChatHomeState.ReadOnlyEnded ||
                it.secondChatPresentation?.state == SecondChatHomeState.Expired
        }
        .sortedByEventThenSource()
    val classifiedNextSteps = (actionRequired + inProgress + upcoming + recent)
        .filterIsInstance<HomePendingHubItem.NextStep>()
        .mapTo(mutableSetOf()) { it.sourceIndex }
    val other = nextStepHubItems
        .filter { it.sourceIndex !in classifiedNextSteps }
        .sortedBy { it.sourceIndex }

    val sections = listOf(
        HomePendingSection(HomePendingSectionType.InProgress, inProgress),
        HomePendingSection(HomePendingSectionType.ActionRequired, actionRequired),
        HomePendingSection(HomePendingSectionType.Upcoming, upcoming),
        HomePendingSection(HomePendingSectionType.Recent, recent),
        HomePendingSection(HomePendingSectionType.Other, other),
    ).filter { it.items.isNotEmpty() }

    val priorityCandidates = buildList {
        addAll(
            visualReviewHubItems
                .filter { it.action.isCriticalFutureVisualReview(nowMillis) }
                .map {
                    HomePriorityItem.VisualReview(
                        action = it.action,
                        sourceIndex = it.sourceIndex,
                        eventMillis = it.expiresAtMillis,
                    )
                },
        )
        addAll(
            nextStepHubItems.mapNotNull { item ->
                when {
                    item.secondChatPresentation?.state == SecondChatHomeState.Open ||
                        item.secondChatPresentation?.state == SecondChatHomeState.Preparing ->
                        HomePriorityItem.SecondChatOpen(
                            item = item.item,
                            sourceIndex = item.sourceIndex,
                            eventMillis = item.eventMillis,
                        )

                    item.isStartingSoonSecondChat(nowMillis) ->
                        HomePriorityItem.SecondChatStartingSoon(
                            item = item.item,
                            sourceIndex = item.sourceIndex,
                            eventMillis = item.availableAtMillis,
                        )

                    else -> null
                }
            },
        )
    }.sortedWith(
        compareBy<HomePriorityItem>(
            { it.eventMillis ?: Long.MAX_VALUE },
            { it.sourceIndex },
            { it.priorityTypeOrder() },
        ),
    )

    return HomePendingPresentation(
        firstChats = firstChats,
        hubSections = sections,
        summaryText = pendingSummaryText(
            inProgress = inProgress.size,
            actionRequired = actionRequired.size,
            upcoming = upcoming.size,
            recent = recent.size,
            other = other.size,
        ),
        priorityItems = priorityCandidates.take(MaxHomePriorityItems),
        priorityOverflowCount = (priorityCandidates.size - MaxHomePriorityItems).coerceAtLeast(0),
    )
}

internal fun HomeActionItem.VisualReview.isCriticalFutureVisualReview(nowMillis: Long): Boolean {
    val expiresAtMillis = visualExpiresAt.toEpochMillisOrNull() ?: return false
    if (nowMillis >= expiresAtMillis) return false
    val remainingFraction = visualReviewRemainingFraction(
        visualStartedAt = visualStartedAt,
        visualExpiresAt = visualExpiresAt,
        nowMillis = nowMillis,
    ) ?: return false
    return visualReviewProgressUrgency(remainingFraction) == VisualReviewProgressUrgency.Critical
}

private fun pendingSummaryText(
    inProgress: Int,
    actionRequired: Int,
    upcoming: Int,
    recent: Int,
    other: Int,
): String? {
    val clauses = buildList {
        if (inProgress > 0) add("$inProgress en curso")
        if (actionRequired > 0) {
            add(if (actionRequired == 1) "1 requiere tu acción" else "$actionRequired requieren tu acción")
        }
        if (upcoming > 0) add(if (upcoming == 1) "1 próximo" else "$upcoming próximos")
        if (recent > 0) add(if (recent == 1) "1 reciente" else "$recent recientes")
    }
    if (clauses.isNotEmpty()) return clauses.joinToString(" · ")
    if (other > 0) return if (other == 1) "1 pendiente" else "$other pendientes"
    return null
}

private fun HomePendingHubItem.NextStep.isStartingSoonSecondChat(nowMillis: Long): Boolean {
    if (secondChatPresentation?.state != SecondChatHomeState.Waiting) return false
    val availableAt = availableAtMillis ?: return false
    val millisUntilAvailable = availableAt - nowMillis
    return millisUntilAvailable in 1..SECOND_CHAT_NEAR_WINDOW_MILLIS
}

private fun HomeNextStepItem.pendingEventMillis(nowMillis: Long): Long? =
    when (secondChatHomePresentation(nowMillis)?.state) {
        SecondChatHomeState.Open,
        SecondChatHomeState.Preparing,
        SecondChatHomeState.Expired -> secondChatExpiresAtInstant()?.toEpochMilli()
        SecondChatHomeState.Waiting -> secondChatAvailableAtInstant()?.toEpochMilli()
        SecondChatHomeState.ReadOnly,
        SecondChatHomeState.ReadOnlyEnded,
        null -> null
    }

private fun List<HomePendingHubItem.NextStep>.sortedByEventThenSource(): List<HomePendingHubItem.NextStep> =
    sortedWith(
        compareBy<HomePendingHubItem.NextStep>(
            { it.eventMillis ?: Long.MAX_VALUE },
            { it.sourceIndex },
        ),
    )

private fun List<HomePendingHubItem>.toPendingSecondaryGroups(): List<HomePendingSecondaryGroup> {
    val groups = mutableListOf<HomePendingSecondaryGroupBuilder>()
    for (item in this) {
        val kind = item.pendingItemKind()
        val existingGroup = groups.firstOrNull { it.kind == kind }
        if (existingGroup != null) {
            existingGroup.items += item
        } else {
            groups += HomePendingSecondaryGroupBuilder(kind = kind, items = mutableListOf(item))
        }
    }
    return groups.map { HomePendingSecondaryGroup(kind = it.kind, items = it.items) }
}

private data class HomePendingSecondaryGroupBuilder(
    val kind: HomePendingItemKind?,
    val items: MutableList<HomePendingHubItem>,
)

internal fun HomePendingHubItem.pendingItemKind(): HomePendingItemKind? =
    when (this) {
        is HomePendingHubItem.VisualReview -> HomePendingItemKind.VisualReview
        is HomePendingHubItem.NextStep -> item.pendingItemKind()
    }

internal fun HomeNextStepItem.pendingItemKind(): HomePendingItemKind? =
    when (this) {
        is HomeNextStepItem.Scheduling -> HomePendingItemKind.Scheduling
        is HomeNextStepItem.SecondChatScheduled,
        is HomeNextStepItem.SecondChatAvailable,
        is HomeNextStepItem.SecondChatExpired,
        is HomeNextStepItem.SecondChatReadOnly -> HomePendingItemKind.SecondChat
        is HomeNextStepItem.Unknown -> null
    }

internal fun HomeActionItem.VisualReview.pendingVisualReviewTitle(): String =
    partnerDisplayName
        ?.takeIf { it.isNotBlank() }
        ?.let(TextSafety::safeDisplay)
        ?.let { "Descubrí el perfil de $it" }
        ?: "Descubrí el perfil"

internal fun HomePriorityItem.homePriorityTitle(): String =
    when (this) {
        is HomePriorityItem.VisualReview ->
            action.partnerDisplayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)
                ?.let { "El perfil de $it está por vencer" }
                ?: "Revisión visual por vencer"

        is HomePriorityItem.SecondChatOpen ->
            item.partnerDisplayName()
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)
                ?.let { "Tu segundo chat con $it ya empezó" }
                ?: "Tu segundo chat ya empezó"

        is HomePriorityItem.SecondChatStartingSoon ->
            item.partnerDisplayName()
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)
                ?.let { "Tu segundo chat con $it empieza pronto" }
                ?: "Tu segundo chat empieza pronto"
    }

internal fun HomeNextStepItem.pendingNextStepTitle(): String =
    when (this) {
        is HomeNextStepItem.Scheduling ->
            partnerDisplayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)
                ?.let { "Coordinación con $it" }
                ?: "Coordinación pendiente"
        is HomeNextStepItem.SecondChatScheduled,
        is HomeNextStepItem.SecondChatAvailable,
        is HomeNextStepItem.SecondChatExpired,
        is HomeNextStepItem.SecondChatReadOnly ->
            partnerDisplayName()
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)
                ?.let { "Segundo chat con $it" }
                ?: "Segundo chat"
        is HomeNextStepItem.Unknown -> "Conexión no disponible"
    }

internal fun HomeNextStepItem.pendingNextStepBody(nowMillis: Long): String =
    when (this) {
        is HomeNextStepItem.Scheduling -> "Elegí horarios para el segundo chat."
        else -> homeNextStepBody(nowMillis)
    }

private fun HomePriorityItem.priorityTypeOrder(): Int =
    when (this) {
        is HomePriorityItem.VisualReview -> 0
        is HomePriorityItem.SecondChatOpen -> 1
        is HomePriorityItem.SecondChatStartingSoon -> 2
    }

private fun String?.toEpochMillisOrNull(): Long? =
    toInstantOrNull()?.toEpochMilli()

private const val MaxHomePriorityItems = 2
