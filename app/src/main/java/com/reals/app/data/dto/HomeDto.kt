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
    val pendingSchedulingConnectionCount: Int = 0,
    val actionableConnectionCount: Int = 0,
)

@Serializable
data class HomePendingActionResponseDto(
    val type: String,
    val matchId: String,
    val chatId: String? = null,
    val partner: ChatPartnerResponseDto? = null,
)

@Serializable
data class HomeNextStepResponseDto(
    val type: String,
    val connectionId: String,
    val matchId: String,
    val partner: ChatPartnerResponseDto? = null,
    val secondChat: HomeChatResponseDto? = null,
)

@Serializable
data class HomePassiveNoticeResponseDto(
    val type: String,
    val count: Int,
)

@Serializable
data class HomeChatResponseDto(
    val chatId: String? = null,
    val chatType: String? = null,
    val chatStatus: String? = null,
    val availableAt: String,
    val expiresAt: String,
    val readOnlyUntil: String? = null,
    val durationMinutes: Long,
    val partner: ChatPartnerResponseDto? = null,
)
