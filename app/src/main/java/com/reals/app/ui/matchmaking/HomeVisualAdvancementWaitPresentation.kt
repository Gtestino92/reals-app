package com.reals.app.ui.matchmaking

import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.time.backendInstantOrNull
import kotlin.math.ceil

internal const val VISUAL_ADVANCEMENT_WAIT_TITLE = "Tomate tu tiempo"
internal const val VISUAL_ADVANCEMENT_WAIT_BODY = "Podrás volver a buscar a alguien nuevo más adelante."
private const val VISUAL_ADVANCEMENT_WAIT_PREFIX = "Próximo espacio disponible en "
private const val MILLIS_PER_MINUTE = 60_000L

internal data class VisualAdvancementWaitPresentation(
    val title: String,
    val body: String,
    val remainingTimeText: String?,
    val nextAvailableAt: String?,
)

internal fun visualAdvancementWaitPresentation(
    matchmaking: HomeMatchmakingUiState,
    nowMillis: Long,
): VisualAdvancementWaitPresentation? {
    if (matchmaking.inQueue) return null
    if (matchmaking.canSearch) return null

    val blocker = matchmaking.blockedReason ?: return null
    if (blocker.backendErrorCode() != BackendErrorCode.VisualAdvancementLimitReached) return null

    return VisualAdvancementWaitPresentation(
        title = VISUAL_ADVANCEMENT_WAIT_TITLE,
        body = VISUAL_ADVANCEMENT_WAIT_BODY,
        remainingTimeText = visualAdvancementRemainingTimeText(blocker.nextAvailableAt, nowMillis),
        nextAvailableAt = blocker.nextAvailableAt?.takeIf { it.isNotBlank() },
    )
}

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
    presentation: VisualAdvancementWaitPresentation?,
    nowMillis: Long,
    refreshedNextAvailableAt: String?,
): Boolean {
    val nextAvailableAt = presentation?.nextAvailableAt ?: return false
    if (nextAvailableAt == refreshedNextAvailableAt) return false
    val nextAvailableAtMillis = backendInstantOrNull(nextAvailableAt)?.toEpochMilli() ?: return false
    return nowMillis >= nextAvailableAtMillis
}
