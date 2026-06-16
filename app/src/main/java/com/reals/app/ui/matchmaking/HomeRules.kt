package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.HomeQueueState

internal fun ApiError?.isActiveMatchLimitReached(): Boolean =
    this is ApiError.Backend && code == "ACTIVE_MATCH_LIMIT_REACHED"

internal fun emptyHomeScreenModel(): HomeScreenModel = HomeScreenModel(
    queue = HomeQueueState(inQueue = false),
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

internal fun HomeScreenModel.isSearchingOnly(): Boolean =
    matchmaking.inQueue &&
        pendingActions.isEmpty() &&
        nextSteps.isEmpty() &&
        passiveNotices.isEmpty()

internal fun HomeScreenModel.shouldPollHome(): Boolean =
    matchmaking.inQueue ||
        pendingActions.isNotEmpty() ||
        nextSteps.isNotEmpty() ||
        passiveNotices.isNotEmpty()
