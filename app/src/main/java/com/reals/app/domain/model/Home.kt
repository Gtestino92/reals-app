package com.reals.app.domain.model

data class HomeState(
    val profileStatus: ProfileStatus?,
    val matchmaking: HomeMatchmaking,
    val activeInteractionsSummary: HomeActiveInteractionsSummary,
    val pendingActions: List<HomePendingAction>,
    val nextSteps: List<HomeNextStep>,
    val passiveNotices: List<HomePassiveNotice>,
)

data class HomeStatus(
    val version: Long,
    val dirty: Boolean,
    val serverTime: String,
)

data class HomePendingState(
    val version: Long,
    val pendingActions: List<HomePendingAction>,
    val nextSteps: List<HomeNextStep>,
    val passiveNotices: List<HomePassiveNotice>,
    val serverTime: String,
)

data class HomeMatchmaking(
    val inQueue: Boolean,
    val canSearch: Boolean,
    val blockedReason: HomeMatchmakingBlockedReason?,
)

data class HomeMatchmakingBlockedReason(
    val code: String,
    val message: String,
)

data class HomeActiveInteractionsSummary(
    val activeInitialCount: Int,
    val activeConnectionCount: Int,
    val hasPendingSchedulingConnection: Boolean,
    val actionableConnectionCount: Int,
)

sealed interface HomePendingAction {
    data class FirstChat(
        val matchId: String,
        val chatId: String,
        val partner: ChatPartner?,
    ) : HomePendingAction

    data class VisualReview(
        val matchId: String,
        val partner: ChatPartner?,
        val visualStartedAt: String? = null,
        val visualExpiresAt: String? = null,
    ) : HomePendingAction

    data class Unknown(
        val rawType: String,
    ) : HomePendingAction
}

sealed interface HomeNextStep {
    data class Scheduling(
        val connectionId: String,
        val matchId: String,
        val partner: ChatPartner?,
    ) : HomeNextStep

    data class SecondChatScheduled(
        val connectionId: String,
        val matchId: String,
        val partner: ChatPartner?,
        val secondChat: HomeChat?,
    ) : HomeNextStep

    data class SecondChatAvailable(
        val connectionId: String,
        val matchId: String,
        val partner: ChatPartner?,
        val secondChat: HomeChat?,
    ) : HomeNextStep

    data class SecondChatReadOnly(
        val connectionId: String,
        val matchId: String,
        val partner: ChatPartner?,
        val secondChat: HomeChat?,
    ) : HomeNextStep

    data class Unknown(
        val rawType: String,
        val connectionId: String?,
        val matchId: String?,
        val partner: ChatPartner?,
    ) : HomeNextStep
}

sealed interface HomePassiveNotice {
    data object SchedulingPreparing : HomePassiveNotice
    data class Unknown(val rawType: String) : HomePassiveNotice
}

data class HomeChat(
    val chatId: String?,
    val chatType: ChatType?,
    val chatStatus: ChatStatus?,
    val availableAt: String,
    val expiresAt: String,
    val readOnlyUntil: String?,
    val durationMinutes: Long,
    val partner: ChatPartner?,
)
