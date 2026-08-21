package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HomeResponseDto(
    val profileStatus: String? = null,
    val matchmaking: HomeMatchmakingResponseDto,
    val activeInteractionsSummary: HomeActiveInteractionsSummaryResponseDto,
    val pendingActions: List<HomePendingActionResponseDto> = emptyList(),
    val nextSteps: List<HomeNextStepResponseDto> = emptyList(),
    val passiveNotices: List<HomePassiveNoticeResponseDto> = emptyList(),
)

@Serializable
data class HomeStatusResponseDto(
    val version: Long,
    val dirty: Boolean,
    val nextRefreshAt: String? = null,
    val serverTime: String? = null,
)

@Serializable
data class HomePendingStateResponseDto(
    val version: Long,
    val pendingActions: List<HomePendingActionLiteResponseDto> = emptyList(),
    val nextSteps: List<HomeNextStepLiteResponseDto> = emptyList(),
    val passiveNotices: List<HomePassiveNoticeResponseDto> = emptyList(),
    val serverTime: String,
)

@Serializable
data class HomeMatchmakingResponseDto(
    val inQueue: Boolean,
    val canSearch: Boolean,
    val blockedReason: HomeMatchmakingBlockedReasonResponseDto? = null,
)

@Serializable
data class HomeMatchmakingBlockedReasonResponseDto(
    val code: String,
    val message: String,
)

@Serializable
data class HomeActiveInteractionsSummaryResponseDto(
    val activeInitialCount: Int = 0,
    val activeConnectionCount: Int = 0,
    val hasPendingSchedulingConnection: Boolean = false,
    val actionableConnectionCount: Int = 0,
)

@Serializable
data class HomePendingActionResponseDto(
    val type: String,
    val matchId: String,
    val chatId: String? = null,
    val visualStartedAt: String? = null,
    val visualExpiresAt: String? = null,
    val partner: ChatPartnerResponseDto? = null,
)

@Serializable
data class HomePendingActionLiteResponseDto(
    val type: String,
    val matchId: String,
    val chatId: String? = null,
    val visualStartedAt: String? = null,
    val visualExpiresAt: String? = null,
)

@Serializable
data class HomeNextStepResponseDto(
    val type: String,
    val connectionId: String,
    val matchId: String,
    val createdAt: String? = null,
    val schedulingExpiresAt: String? = null,
    val partner: ChatPartnerResponseDto? = null,
    val secondChat: HomeChatResponseDto? = null,
)

@Serializable
data class HomeNextStepLiteResponseDto(
    val type: String,
    val connectionId: String,
    val matchId: String,
    val createdAt: String? = null,
    val schedulingExpiresAt: String? = null,
    val secondChat: HomePendingSecondChatLiteResponseDto? = null,
)

@Serializable
data class HomePendingSecondChatLiteResponseDto(
    val chatId: String? = null,
    val availableAt: String? = null,
    val entryClosesAt: String? = null,
    val expiresAt: String? = null,
    val readOnlyUntil: String? = null,
    val durationMinutes: Long? = null,
    val myAttendanceStatus: String? = null,
)

@Serializable
data class HomePassiveNoticeResponseDto(
    val type: String,
)

@Serializable
data class HomeChatResponseDto(
    val chatId: String? = null,
    val chatType: String? = null,
    val chatStatus: String? = null,
    val availableAt: String,
    val entryClosesAt: String? = null,
    val expiresAt: String,
    val readOnlyUntil: String? = null,
    val durationMinutes: Long,
    val myAttendanceStatus: String? = null,
    val partner: ChatPartnerResponseDto? = null,
)

@Serializable
data class ConnectionDismissalResponseDto(
    val dismissed: Boolean,
)
