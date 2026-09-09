package com.reals.app.data.mapper

import com.reals.app.data.dto.EnqueueMatchmakingRequestDto
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.UserBlockResponseDto
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.QueueStatus
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.model.UserBlock

fun SearchLocationInput.toDto(): EnqueueMatchmakingRequestDto = EnqueueMatchmakingRequestDto(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
)

fun QueueStatusResponseDto.toDomain(): QueueStatus = QueueStatus(
    userId = userId,
    inQueue = inQueue,
)

fun MatchResponseDto.toDomain(): Match = Match(
    id = id,
    userAId = userAId,
    userBId = userBId,
    state = MatchState.fromBackend(state),
    connectionId = connectionId,
    visualExpiresAt = visualExpiresAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun UserBlockResponseDto.toDomain(): UserBlock = UserBlock(
    id = id,
    source = source,
    createdAt = createdAt,
)
