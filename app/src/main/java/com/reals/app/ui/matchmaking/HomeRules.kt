package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError

internal fun ApiError?.isActiveMatchLimitReached(): Boolean =
    this is ApiError.Backend &&
        (code == "ACTIVE_MATCH_LIMIT_REACHED" || code == "ACTIVE_CONNECTION_LIMIT_REACHED")

internal fun ApiError?.matchmakingBlockedMessage(): String? {
    if (this == null) return null

    if (isActiveMatchLimitReached()) {
        return "Ya tenes conversaciones o experiencias activas. Termina una antes de buscar otra."
    }

    return when (this) {
        is ApiError.Backend -> message.takeIf { it.isNotBlank() }
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
    matchmaking.inQueue ||
        pendingActions.isNotEmpty() ||
        nextSteps.isNotEmpty() ||
        passiveNotices.isNotEmpty()
