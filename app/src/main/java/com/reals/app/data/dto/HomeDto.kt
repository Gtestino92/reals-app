package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HomeResponseDto(
    val profileStatus: String? = null,
    val queue: HomeQueueResponseDto,
    val activeMatches: List<HomeMatchResponseDto>,
    val activeConnections: List<HomeConnectionResponseDto>,
    val engagementSummary: HomeEngagementSummaryResponseDto? = null,
)

@Serializable
data class HomeEngagementSummaryResponseDto(
    val activeMatchCount: Int = 0,
    val activeConnectionCount: Int = 0,
    val pendingSchedulingConnectionCount: Int = 0,
    val actionableConnectionCount: Int = 0,
)

@Serializable
data class HomeQueueResponseDto(
    val inQueue: Boolean,
)

@Serializable
data class HomeMatchResponseDto(
    val matchId: String,
    val matchState: String,
    val firstChat: HomeChatResponseDto? = null,
    val partner: ChatPartnerResponseDto? = null
)

@Serializable
data class HomeConnectionResponseDto(
    val connectionId: String,
    val matchId: String,
    val connectionState: String,
    val secondChat: HomeChatResponseDto? = null,
    val partner: ChatPartnerResponseDto? = null,
)

@Serializable
data class HomeChatResponseDto(
    val chatId: String,
    val chatType: String,
    val chatStatus: String,
    val expiresAt: String? = null,
    val partner: ChatPartnerResponseDto? = null,
)
