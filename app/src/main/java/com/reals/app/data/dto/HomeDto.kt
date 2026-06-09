package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HomeResponseDto(
    val profileStatus: String? = null,
    val queue: HomeQueueResponseDto,
    val activeMatches: List<HomeMatchResponseDto>,
    val activeConnections: List<HomeConnectionResponseDto>,
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
)

@Serializable
data class HomeConnectionResponseDto(
    val connectionId: String,
    val matchId: String,
    val connectionState: String,
    val secondChat: HomeChatResponseDto? = null,
)

@Serializable
data class HomeChatResponseDto(
    val chatId: String,
    val chatType: String,
    val chatStatus: String,
)
