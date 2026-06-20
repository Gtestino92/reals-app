package com.reals.app.data.mapper

import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatPartner
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType

fun ChatResponseDto.toDomain(): Chat = Chat(
    id = id,
    matchId = matchId,
    connectionId = connectionId,
    chatType = ChatType.fromBackend(chatType),
    status = ChatStatus.fromBackend(status),
    startedAt = startedAt,
    availableAt = availableAt,
    activatedAt = activatedAt,
    timeoutAt = timeoutAt,
    expiresAt = expiresAt ?: timeoutAt,
    partner = partner?.toDomain(),
    myDecision = ChatDecisionState.fromBackend(myDecision),
    partnerDecision = ChatDecisionState.fromBackend(partnerDecision),
    endedAt = endedAt,
    readOnlyUntil = readOnlyUntil,
    lastMessageAt = lastMessageAt,
)

fun ChatPartnerResponseDto.toDomain(): ChatPartner = ChatPartner(
    userId = userId,
    profileId = profileId,
    displayName = displayName,
)

fun ChatMessageResponseDto.toDomain(): ChatMessage = ChatMessage(
    id = id,
    chatSessionId = chatSessionId,
    senderId = senderId,
    content = content,
    sentAt = sentAt,
)

fun ChatExitRequestResponseDto.toDomain(): ChatExitRequest = ChatExitRequest(
    id = id,
    chatId = chatId,
    requesterUserId = requesterUserId,
    responderUserId = responderUserId,
    type = ChatExitRequestType.fromBackend(type),
    status = ChatExitRequestStatus.fromBackend(status),
    reason = reason?.let { ChatExitReason.fromBackend(it) },
    details = details,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
)

fun ChatExitOutcomeResponseDto.toDomain(): ChatExitOutcome = ChatExitOutcome(
    chat = chat.toDomain(),
    exitRequest = exitRequest.toDomain(),
    penaltyApplied = penaltyApplied,
    penalizedUserId = penalizedUserId,
)
