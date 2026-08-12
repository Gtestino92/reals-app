package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode

internal const val SECOND_CHAT_NEAR_WINDOW_MILLIS = 15 * 60 * 1000L
internal const val HOME_POLL_INTERVAL_MILLIS = 5 * 1000L
private const val SECOND_CHAT_POLL_INTERVAL_MILLIS = HOME_POLL_INTERVAL_MILLIS

internal fun ApiError?.isActiveMatchLimitReached(): Boolean {
    if (this !is ApiError.Backend) return false
    return when (backendErrorCode) {
        BackendErrorCode.ActiveMatchLimitReached,
        BackendErrorCode.ActiveConnectionLimitReached -> true
        else -> false
    }
}

internal fun ApiError?.matchmakingBlockedMessage(): String? {
    if (this == null) return null

    if (isActiveMatchLimitReached()) {
        return "Ya tenés conversaciones o experiencias activas. Terminá una antes de buscar otra."
    }

    return when (this) {
        is ApiError.Backend -> when (backendErrorCode) {
            BackendErrorCode.ProfileRequired -> "Necesitás crear tu perfil antes de buscar chat."
            BackendErrorCode.ProfileNotActive ->
                "Tu perfil está en borrador. Reactivalo para buscar nuevas personas."
            BackendErrorCode.ActivePenalty ->
                "Por ahora no podés entrar a la búsqueda. Intentá nuevamente más adelante."
            BackendErrorCode.ActiveMatchLimitReached,
            BackendErrorCode.ActiveConnectionLimitReached ->
                "Ya tenés conversaciones o experiencias activas. Terminá una antes de buscar otra."
            else -> null
        }
        else -> null
    }
}

internal fun emptyHomeScreenModel(): HomeScreenModel = HomeScreenModel(
    pendingActions = emptyList(),
    nextSteps = emptyList(),
    activeInteractionsSummary = null,
    passiveNotices = emptyList(),
    matchmaking = HomeMatchmakingUiState(
        inQueue = false,
        canSearch = true,
        blockedReason = null,
    ),
)

internal fun HomeScreenModel.shouldPollHome(): Boolean =
    true

internal fun HomeScreenModel.shouldShowMatchmakingLocationCopy(): Boolean =
    draftProfileWarning == null

internal fun HomeScreenModel.shouldPollSecondChatAvailability(
    nowMillis: Long = System.currentTimeMillis(),
): Boolean =
    nextSteps.any { it.needsSecondChatAvailabilityPolling(nowMillis) }

internal fun HomeScreenModel.nextSecondChatPollDelayMillis(
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    val nextDueDelay = nextSteps
        .mapNotNull { it.secondChatAvailableAtInstant() }
        .map { it.toEpochMilli() - nowMillis }
        .filter { it in 1..SECOND_CHAT_POLL_INTERVAL_MILLIS }
        .minOrNull()

    return nextDueDelay ?: SECOND_CHAT_POLL_INTERVAL_MILLIS
}

private fun HomeNextStepItem.needsSecondChatAvailabilityPolling(nowMillis: Long): Boolean {
    val availableAt = secondChatAvailableAtInstant() ?: return false
    val presentation = secondChatHomePresentation(nowMillis) ?: return false
    if (presentation.state == SecondChatHomeState.Expired) return false
    val millisUntilAvailable = availableAt.toEpochMilli() - nowMillis
    val isNearOrDue = millisUntilAvailable <= SECOND_CHAT_NEAR_WINDOW_MILLIS
    if (!isNearOrDue) return false

    return when (this) {
        is HomeNextStepItem.SecondChatScheduled -> true
        is HomeNextStepItem.SecondChatAvailable -> !hasOpenSecondChatReference()
        else -> false
    }
}
