package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.HomeActiveInteractionsSummary

data class HomeScreenModel(
    val pendingActions: List<HomeActionItem>,
    val nextSteps: List<HomeNextStepItem>,
    val activeInteractionsSummary: HomeActiveInteractionsSummary?,
    val passiveNotices: List<HomePassiveNoticeItem>,
    val matchmaking: HomeMatchmakingUiState,
    val draftProfileWarning: DraftProfileHomeWarning? = null,
)

data class DraftProfileHomeWarning(
    val title: String,
    val message: String,
    val actionLabel: String,
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
        val visualStartedAt: String? = null,
        val visualExpiresAt: String? = null,
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
        val chatId: String?,
        val chatStatus: String?,
        val availableAt: String?,
        val expiresAt: String?,
        val durationMinutes: Long?,
    ) : HomeNextStepItem

    data class SecondChatAvailable(
        val connectionId: String,
        val matchId: String,
        val partnerDisplayName: String?,
        val chatId: String?,
        val chatStatus: String?,
        val availableAt: String?,
        val expiresAt: String?,
        val durationMinutes: Long?,
    ) : HomeNextStepItem

    data class SecondChatReadOnly(
        val connectionId: String,
        val matchId: String,
        val partnerDisplayName: String?,
        val chatId: String?,
        val chatStatus: String?,
        val availableAt: String?,
        val expiresAt: String?,
        val readOnlyUntil: String?,
        val durationMinutes: Long?,
    ) : HomeNextStepItem

    data class Unknown(
        val connectionId: String,
        val matchId: String?,
        val rawState: String,
        val partnerDisplayName: String?,
    ) : HomeNextStepItem
}

sealed interface HomePassiveNoticeItem {
    data object SchedulingPreparing : HomePassiveNoticeItem
    data class Unknown(val rawType: String) : HomePassiveNoticeItem
}

data class HomeMatchmakingUiState(
    val inQueue: Boolean,
    val canSearch: Boolean,
    val blockedReason: ApiError?,
)
