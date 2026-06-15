package com.reals.app.data.mapper

import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeConnectionResponseDto
import com.reals.app.data.dto.HomeMatchResponseDto
import com.reals.app.data.dto.HomeQueueResponseDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.HomeChat
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeMatch
import com.reals.app.domain.model.HomeQueueState
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.ProfileStatus

fun HomeResponseDto.toDomain(): HomeState = HomeState(
    profileStatus = profileStatus?.let { ProfileStatus.fromBackend(it) },
    queue = queue.toDomain(),
    activeMatches = activeMatches.map { it.toDomain() },
    activeConnections = activeConnections.map { it.toDomain() },
)

fun HomeQueueResponseDto.toDomain(): HomeQueueState = HomeQueueState(
    inQueue = inQueue,
)

fun HomeMatchResponseDto.toDomain(): HomeMatch = HomeMatch(
    matchId = matchId,
    matchState = MatchState.fromBackend(matchState),
    firstChat = firstChat?.toDomain(),
    partnerDisplayName = partner?.displayName,
)

fun HomeConnectionResponseDto.toDomain(): HomeConnection = HomeConnection(
    connectionId = connectionId,
    matchId = matchId,
    connectionState = ConnectionState.fromBackend(connectionState),
    secondChat = secondChat?.toDomain(),
    partner = partner?.toDomain(),
)

fun HomeChatResponseDto.toDomain(): HomeChat = HomeChat(
    chatId = chatId,
    chatType = ChatType.fromBackend(chatType),
    chatStatus = ChatStatus.fromBackend(chatStatus),
    expiresAt = expiresAt,
    partner = partner?.toDomain(),
)
