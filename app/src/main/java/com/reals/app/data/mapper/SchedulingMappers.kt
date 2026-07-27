package com.reals.app.data.mapper

import com.reals.app.data.dto.ConnectionResponseDto
import com.reals.app.data.dto.NegotiationResponseDto
import com.reals.app.data.dto.ScheduleProposalResponseDto
import com.reals.app.data.dto.SchedulingAvailabilityResponseDto
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProposalStatus
import com.reals.app.domain.model.SchedulingAvailability
import com.reals.app.domain.model.SchedulingConnection
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal
import com.reals.app.domain.model.SchedulingUnavailableWindow

fun ConnectionResponseDto.toDomain(): SchedulingConnection = SchedulingConnection(
    id = id,
    matchId = matchId,
    userAId = userAId,
    userBId = userBId,
    state = ConnectionState.fromBackend(state),
    schedulingExpiresAt = schedulingExpiresAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun NegotiationResponseDto.toDomain(): SchedulingNegotiation = SchedulingNegotiation(
    id = id,
    connectionId = connectionId,
    roundNumber = roundNumber,
    status = NegotiationStatus.fromBackend(status),
    confirmedDateTime = confirmedDateTime,
    chatId = chatId,
    schedulingExpiresAt = schedulingExpiresAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ScheduleProposalResponseDto.toDomain(): SchedulingProposal = SchedulingProposal(
    id = id,
    connectionId = connectionId,
    userId = userId,
    roundNumber = roundNumber,
    preferenceOrder = preferenceOrder,
    proposedDateTime = proposedDateTime,
    status = ProposalStatus.fromBackend(status),
    chatId = chatId,
    createdAt = createdAt,
)

fun SchedulingAvailabilityResponseDto.toDomain(): SchedulingAvailability = SchedulingAvailability(
    conflictWindowMinutes = conflictWindowMinutes,
    unavailableWindows = unavailableWindows.map { window ->
        SchedulingUnavailableWindow(
            startsAt = window.startsAt,
            endsAt = window.endsAt,
        )
    },
    serverTime = serverTime,
)
