package com.reals.app.data.mapper

import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.ChatAudioPolicyResponseDto
import com.reals.app.data.dto.ChatAudioResponseDto
import com.reals.app.data.dto.FirstChatGuidanceQuestionResponseDto
import com.reals.app.data.dto.FirstChatGuidanceResponseDto
import com.reals.app.data.dto.SecondChatAttendanceResponseDto
import com.reals.app.data.dto.SecondChatResolutionRequestResponseDto
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatAudio
import com.reals.app.domain.model.ChatAudioPolicy
import com.reals.app.domain.model.ChatAudioUnavailableReason
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageType
import com.reals.app.domain.model.ChatPartner
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.domain.model.FirstChatGuidanceQuestion
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatEndedReason
import com.reals.app.domain.model.SecondChatResolutionRequest
import com.reals.app.domain.model.SecondChatResolutionRequestStatus
import com.reals.app.domain.model.SecondChatResolutionRequestType
import com.reals.app.domain.model.SecondChatStatus

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
    endedReason = SecondChatEndedReason.fromBackend(endedReason),
    endedAt = endedAt,
    readOnlyUntil = readOnlyUntil,
    lastMessageAt = lastMessageAt,
    guidance = guidance?.toDomain(),
    audioPolicy = audioPolicy?.toDomain(),
)

fun ChatAudioPolicyResponseDto.toDomain(): ChatAudioPolicy = ChatAudioPolicy(
    enabled = enabled,
    unavailableReason = ChatAudioUnavailableReason.fromBackend(unavailableReason),
    enabledAt = enabledAt,
    maxDurationMillis = maxDurationMillis,
    maxFileSizeBytes = maxFileSizeBytes,
    remainingMessages = remainingMessages,
)

fun SecondChatAttendanceResponseDto.toDomain(): SecondChatStatus = SecondChatStatus(
    connectionId = connectionId,
    chatId = chatId,
    scheduledAt = scheduledAt,
    onTimeUntil = onTimeUntil,
    entryClosesAt = entryClosesAt,
    absoluteExpiresAt = absoluteExpiresAt,
    conversationStartedAt = conversationStartedAt,
    serverTime = serverTime,
    myAttendanceStatus = SecondChatAttendanceStatus.fromBackend(myAttendanceStatus),
    myJoinedAt = myJoinedAt,
    partnerAttendanceStatus = SecondChatAttendanceStatus.fromBackend(partnerAttendanceStatus),
    partnerJoinedAt = partnerJoinedAt,
    canJoin = canJoin,
    canClaimPartnerNoShow = canClaimPartnerNoShow,
    activeNoShowClaim = activeNoShowClaim?.toDomain(),
    activeResolutionRequest = activeResolutionRequest?.toDomain(),
    chatStatus = chatStatus?.let { ChatStatus.fromBackend(it) },
    endedReason = SecondChatEndedReason.fromBackend(endedReason),
    endedAt = endedAt,
    readOnlyUntil = readOnlyUntil,
    mutualCompletionEligibleAt = mutualCompletionEligibleAt,
    canRequestMutualCompletion = canRequestMutualCompletion,
    mutualCompletionCooldownUntil = mutualCompletionCooldownUntil,
    inactivityClaimableAt = inactivityClaimableAt,
    inactivityClosesAt = inactivityClosesAt,
    canClaimPartnerInactivity = canClaimPartnerInactivity,
    mustRespondToPartner = mustRespondToPartner,
    lastMessageAt = lastMessageAt,
    lastMessageSenderId = lastMessageSenderId,
)

fun SecondChatResolutionRequestResponseDto.toDomain(): SecondChatResolutionRequest =
    SecondChatResolutionRequest(
        id = id,
        type = SecondChatResolutionRequestType.fromBackend(type),
        requesterUserId = requesterUserId,
        responderUserId = responderUserId,
        referenceMessageId = referenceMessageId,
        status = SecondChatResolutionRequestStatus.fromBackend(status),
        createdAt = createdAt,
        expiresAt = expiresAt,
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
    clientMessageId = clientMessageId,
    messageType = ChatMessageType.fromBackend(messageType),
    content = content,
    audio = audio?.toDomain(),
    sentAt = sentAt,
)

fun ChatAudioResponseDto.toDomain(): ChatAudio = ChatAudio(
    url = url,
    durationMillis = durationMillis,
    contentType = contentType,
    sizeBytes = sizeBytes,
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
