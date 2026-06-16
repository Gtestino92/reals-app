package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.HomeQueueState

data class HomeScreenModel(
    val queue: HomeQueueState,
    val pendingActions: List<HomeActionItem>,
    val nextSteps: List<HomeNextStepItem>,
    val activeInteractionsSummary: HomeActiveInteractionsSummary?,
    val passiveNotices: List<HomePassiveNotice>,
    val matchmaking: HomeMatchmakingUiState,
)

sealed interface HomeActionItem {
    data class FirstChat(
        val matchId: String,
        val chatId: String,
        val partnerDisplayName: String?,
    ) : HomeActionItem

    data class VisualReview(
        val matchId: String,
        val partnerDisplayName: String?,
    ) : HomeActionItem
}

sealed interface HomeNextStepItem {
    data class Scheduling(
        val connectionId: String,
        val matchId: String,
        val partnerDisplayName: String?,
    ) : HomeNextStepItem

    data class SecondChatScheduled(
        val connectionId: String,
        val matchId: String,
        val partnerDisplayName: String?,
    ) : HomeNextStepItem

    data class SecondChatAvailable(
        val connectionId: String,
        val matchId: String,
        val partnerDisplayName: String?,
    ) : HomeNextStepItem

    data class Unknown(
        val connectionId: String,
        val matchId: String?,
        val rawState: String,
        val partnerDisplayName: String?,
    ) : HomeNextStepItem
}

sealed interface HomePassiveNotice {
    data class SchedulingPreparing(val count: Int) : HomePassiveNotice
}

data class HomeMatchmakingUiState(
    val inQueue: Boolean,
    val canSearch: Boolean,
    val blockedReason: ApiError?,
)

