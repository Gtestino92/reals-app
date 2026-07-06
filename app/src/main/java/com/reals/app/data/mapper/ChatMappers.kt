package com.reals.app.data.mapper

import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.FirstChatGuidanceQuestionResponseDto
import com.reals.app.data.dto.FirstChatGuidanceResponseDto
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
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.domain.model.FirstChatGuidanceQuestion

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
    inactivityExpiresAt = inactivityExpiresAt,
    partner = partner?.toDomain(),
    myDecision = ChatDecisionState.fromBackend(myDecision),
    partnerDecision = ChatDecisionState.fromBackend(partnerDecision),
    endedAt = endedAt,
    readOnlyUntil = readOnlyUntil,
    lastMessageAt = lastMessageAt,
    guidance = guidance?.toDomain(),
)

fun ChatPartnerResponseDto.toDomain(): ChatPartner = ChatPartner(
    userId = userId,
    profileId = profileId,
    displayName = displayName,
)

fun FirstChatGuidanceQuestionResponseDto.toDomain(): FirstChatGuidanceQuestion =
    FirstChatGuidanceQuestion(
        id = id,
        text = text,
    )

fun FirstChatGuidanceResponseDto.toDomain(): FirstChatGuidance =
    FirstChatGuidance(
        question = question.toDomain(),
        questionOrdinal = questionOrdinal,
        maxQuestions = maxQuestions,
        requiredCharacters = requiredCharacters,
        canRequestNext = canRequestNext,
        myNextRequested = myNextRequested,
        completed = completed,
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
