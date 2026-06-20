package com.reals.app.data.mapper

import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeMatchmakingBlockedReasonResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomePassiveNoticeResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.HomeChat
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomeMatchmakingBlockedReason
import com.reals.app.domain.model.HomeNextStep
import com.reals.app.domain.model.HomePassiveNotice
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.ProfileStatus

fun HomeResponseDto.toDomain(): HomeState = HomeState(
    profileStatus = profileStatus?.let { ProfileStatus.fromBackend(it) },
    matchmaking = matchmaking.toDomain(),
    activeInteractionsSummary = activeInteractionsSummary.toDomain(),
    pendingActions = pendingActions.map { it.toDomain() },
    nextSteps = nextSteps.map { it.toDomain() },
    passiveNotices = passiveNotices.map { it.toDomain() },
)

fun HomeMatchmakingResponseDto.toDomain(): HomeMatchmaking = HomeMatchmaking(
    inQueue = inQueue,
    canSearch = canSearch,
    blockedReason = blockedReason?.toDomain(),
)

fun HomeMatchmakingBlockedReasonResponseDto.toDomain(): HomeMatchmakingBlockedReason =
    HomeMatchmakingBlockedReason(
        code = code,
        message = message,
    )

fun HomeActiveInteractionsSummaryResponseDto.toDomain(): HomeActiveInteractionsSummary =
    HomeActiveInteractionsSummary(
        activeInitialCount = activeInitialCount,
        activeConnectionCount = activeConnectionCount,
        pendingSchedulingConnectionCount = pendingSchedulingConnectionCount,
        actionableConnectionCount = actionableConnectionCount,
    )

fun HomePendingActionResponseDto.toDomain(): HomePendingAction = when (type.uppercase()) {
    "FIRST_CHAT" -> chatId?.let {
        HomePendingAction.FirstChat(
            matchId = matchId,
            chatId = it,
            partner = partner?.toDomain(),
        )
    } ?: HomePendingAction.Unknown(rawType = type)

    "VISUAL_REVIEW" -> HomePendingAction.VisualReview(
        matchId = matchId,
        partner = partner?.toDomain(),
    )

    else -> HomePendingAction.Unknown(rawType = type)
}

fun HomeNextStepResponseDto.toDomain(): HomeNextStep = when (type.uppercase()) {
    "SCHEDULING" -> HomeNextStep.Scheduling(
        connectionId = connectionId,
        matchId = matchId,
        partner = partner?.toDomain(),
    )

    "SECOND_CHAT_SCHEDULED" -> HomeNextStep.SecondChatScheduled(
        connectionId = connectionId,
        matchId = matchId,
        partner = partner?.toDomain(),
        secondChat = secondChat?.toDomain(),
    )

    "SECOND_CHAT_AVAILABLE" -> HomeNextStep.SecondChatAvailable(
        connectionId = connectionId,
        matchId = matchId,
        partner = partner?.toDomain(),
        secondChat = secondChat?.toDomain(),
    )

    "SECOND_CHAT_READ_ONLY" -> HomeNextStep.SecondChatReadOnly(
        connectionId = connectionId,
        matchId = matchId,
        partner = partner?.toDomain(),
        secondChat = secondChat?.toDomain(),
    )

    else -> HomeNextStep.Unknown(
        rawType = type,
        connectionId = connectionId,
        matchId = matchId,
        partner = partner?.toDomain(),
    )
}

fun HomePassiveNoticeResponseDto.toDomain(): HomePassiveNotice = when (type.uppercase()) {
    "SCHEDULING_PREPARING" -> HomePassiveNotice.SchedulingPreparing(count)
    else -> HomePassiveNotice.Unknown(rawType = type, count = count)
}

fun HomeChatResponseDto.toDomain(): HomeChat = HomeChat(
    chatId = chatId,
    chatType = chatType?.let { ChatType.fromBackend(it) },
    chatStatus = chatStatus?.let { ChatStatus.fromBackend(it) },
    availableAt = availableAt,
    expiresAt = expiresAt,
    readOnlyUntil = readOnlyUntil,
    durationMinutes = durationMinutes,
    partner = partner?.toDomain(),
)
