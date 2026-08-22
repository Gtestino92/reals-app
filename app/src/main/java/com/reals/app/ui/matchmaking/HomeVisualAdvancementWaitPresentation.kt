package com.reals.app.ui.matchmaking

import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.time.backendInstantOrNull
import kotlin.math.ceil

internal const val VISUAL_ADVANCEMENT_WAIT_TITLE = "Tomate tu tiempo"
internal const val VISUAL_ADVANCEMENT_WAIT_BODY = "Podrás volver a buscar a alguien nuevo más adelante."
internal const val ACTIVE_INTERACTIONS_UNAVAILABLE_TITLE = "Seguí con lo que ya empezó"
internal const val ACTIVE_INTERACTIONS_UNAVAILABLE_BODY =
    "Antes de buscar a alguien nuevo, avanzá con alguna de tus experiencias actuales."
private const val VISUAL_ADVANCEMENT_WAIT_PREFIX = "Próximo espacio disponible en "
private const val MILLIS_PER_MINUTE = 60_000L

internal enum class MatchmakingUnavailableKind {
    VisualAdvancementWait,
    ActiveInteractions,
}

internal data class MatchmakingUnavailablePresentation(
    val kind: MatchmakingUnavailableKind,
    val title: String,
    val body: String,
    val supportingText: String?,
    val nextAvailableAt: String?,
)

internal fun matchmakingUnavailablePresentation(
    matchmaking: HomeMatchmakingUiState,
    nowMillis: Long,
): MatchmakingUnavailablePresentation? {
    if (matchmaking.inQueue) return null
    if (matchmaking.canSearch) return null

    val blocker = matchmaking.blockedReason ?: return null
    return when (blocker.backendErrorCode()) {
        BackendErrorCode.VisualAdvancementLimitReached -> MatchmakingUnavailablePresentation(
            kind = MatchmakingUnavailableKind.VisualAdvancementWait,
            title = VISUAL_ADVANCEMENT_WAIT_TITLE,
            body = VISUAL_ADVANCEMENT_WAIT_BODY,
            supportingText = visualAdvancementRemainingTimeText(blocker.nextAvailableAt, nowMillis),
            nextAvailableAt = blocker.nextAvailableAt?.takeIf { it.isNotBlank() },
        )

        BackendErrorCode.ActiveMatchLimitReached,
        BackendErrorCode.ActiveConnectionLimitReached -> MatchmakingUnavailablePresentation(
            kind = MatchmakingUnavailableKind.ActiveInteractions,
            title = ACTIVE_INTERACTIONS_UNAVAILABLE_TITLE,
            body = ACTIVE_INTERACTIONS_UNAVAILABLE_BODY,
            supportingText = null,
            nextAvailableAt = null,
        )

        else -> null
    }
}

internal fun visualAdvancementWaitPresentation(
    matchmaking: HomeMatchmakingUiState,
    nowMillis: Long,
): MatchmakingUnavailablePresentation? =
    matchmakingUnavailablePresentation(matchmaking, nowMillis)
        ?.takeIf { it.kind == MatchmakingUnavailableKind.VisualAdvancementWait }

internal fun visualAdvancementRemainingTimeText(
    nextAvailableAt: String?,
    nowMillis: Long,
): String? {
    val nextAvailableAtMillis = backendInstantOrNull(nextAvailableAt)?.toEpochMilli() ?: return null
    val remainingMillis = nextAvailableAtMillis - nowMillis
    if (remainingMillis <= 0L) return null
    if (remainingMillis < MILLIS_PER_MINUTE) {
        return VISUAL_ADVANCEMENT_WAIT_PREFIX + "menos de 1 min"
    }

    val remainingMinutes = ceil(remainingMillis.toDouble() / MILLIS_PER_MINUTE).toLong()
    val hours = remainingMinutes / 60L
    val minutes = remainingMinutes % 60L
    val value = when {
        hours > 0L && minutes > 0L -> "$hours h $minutes min"
        hours > 0L -> "$hours h"
        else -> "$remainingMinutes min"
    }
    return VISUAL_ADVANCEMENT_WAIT_PREFIX + value
}

internal fun shouldRequestVisualAdvancementReconciliation(
    presentation: MatchmakingUnavailablePresentation?,
    nowMillis: Long,
    refreshedNextAvailableAt: String?,
): Boolean {
    val nextAvailableAt = presentation?.nextAvailableAt ?: return false
    if (nextAvailableAt == refreshedNextAvailableAt) return false
    val nextAvailableAtMillis = backendInstantOrNull(nextAvailableAt)?.toEpochMilli() ?: return false
    return nowMillis >= nextAvailableAtMillis
}
