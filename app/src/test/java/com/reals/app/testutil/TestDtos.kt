package com.reals.app.testutil

import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.ConnectionResponseDto
import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomePassiveNoticeResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.NegotiationResponseDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.ScheduleProposalResponseDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.data.dto.VisualProfileResponseDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

object TestDtos {
    const val now = "2026-06-18T21:00:00Z"

    fun ping() = PingResponseDto(status = "ok")

    fun user(status: String = "ACTIVE") = UserResponseDto(
        id = "user-1",
        email = "user@example.com",
        status = status,
        deletedAt = null,
        deletionFinalizesAt = null,
        createdAt = now,
    )

    fun profile(status: String = "ACTIVE") = ProfileResponseDto(
        id = "profile-1",
        userId = "user-1",
        displayName = "Alex",
        birthDate = "1998-01-01",
        age = 28,
        identityVerified = true,
        gender = "WOMAN",
        lookingForGender = "MAN",
        intention = "SERIOUS",
        city = "Buenos Aires",
        country = "AR",
        bio = "Hola",
        preferredMinAge = 25,
        preferredMaxAge = 35,
        maxDistanceKm = 10,
        status = status,
        photoCount = 2,
        createdAt = now,
        updatedAt = now,
    )

    fun photo(
        id: String = "photo-1",
        position: Int = 1,
        validationStatus: String = "VALIDATED",
    ) = PhotoResponseDto(
        id = id,
        url = "https://example.com/$id.jpg",
        position = position,
        isPersonPhoto = true,
        isFullBody = false,
        validationStatus = validationStatus,
    )

    fun partner(name: String = "Taylor") = ChatPartnerResponseDto(
        userId = "user-partner",
        profileId = "profile-partner",
        displayName = name,
    )

    fun match(state: String = "CHAT_ACTIVE") = MatchResponseDto(
        id = "match-1",
        userAId = "user-1",
        userBId = "user-2",
        state = state,
        connectionId = "connection-1",
        visualExpiresAt = "2026-06-19T21:00:00Z",
        createdAt = now,
        updatedAt = now,
    )

    fun chat(
        status: String = "ACTIVE",
        myDecision: String? = "PENDING",
        partnerDecision: String? = "PENDING",
    ) = ChatResponseDto(
        id = "chat-1",
        matchId = "match-1",
        connectionId = "connection-1",
        chatType = "FIRST_CHAT",
        status = status,
        startedAt = now,
        availableAt = null,
        activatedAt = now,
        timeoutAt = "2026-06-19T21:00:00Z",
        expiresAt = "2026-06-20T21:00:00Z",
        inactivityExpiresAt = "2026-06-18T21:05:00Z",
        partner = partner(),
        myDecision = myDecision,
        partnerDecision = partnerDecision,
        endedAt = null,
        lastMessageAt = now,
    )

    fun chatMessage(id: String = "message-1") = ChatMessageResponseDto(
        id = id,
        chatSessionId = "chat-1",
        senderId = "user-1",
        content = "hola",
        sentAt = now,
    )

    fun chatMessagesArrayPayload(messages: List<ChatMessageResponseDto> = listOf(chatMessage())): JsonElement =
        testJson.encodeToJsonElement(messages)

    fun chatMessagesPagedPayload(messages: List<ChatMessageResponseDto> = listOf(chatMessage())): JsonElement =
        testJson.parseToJsonElement(
            """{"messages":${testJson.encodeToString(messages)},"hasMore":false,"serverTime":"$now"}"""
        )

    fun exitRequest(
        status: String = "PENDING",
        type: String = "MUTUAL_CANCEL",
        reason: String? = "NO_LONGER_INTERESTED",
    ) = ChatExitRequestResponseDto(
        id = "exit-1",
        chatId = "chat-1",
        requesterUserId = "user-1",
        responderUserId = "user-2",
        type = type,
        status = status,
        reason = reason,
        details = "detalle",
        createdAt = now,
        resolvedAt = null,
    )

    fun exitOutcome() = ChatExitOutcomeResponseDto(
        chat = chat(status = "CANCELLED"),
        exitRequest = exitRequest(status = "ACCEPTED"),
        penaltyApplied = false,
        penalizedUserId = null,
    )

    fun visualProfile(
        myPersonalMessageSubmitted: Boolean = true,
        partnerPersonalMessageSubmitted: Boolean = false,
        partnerPersonalMessageRead: Boolean = true,
        decisionRequiresPartnerPersonalMessageRead: Boolean = false,
    ) = VisualProfileResponseDto(
        profileId = "visual-profile-1",
        displayName = "Taylor",
        age = 27,
        bio = "Bio",
        photos = listOf(photo("photo-2", 2), photo("photo-1", 1)),
        visualExpiresAt = "2026-06-19T21:00:00Z",
        myPersonalMessageSubmitted = myPersonalMessageSubmitted,
        partnerPersonalMessageSubmitted = partnerPersonalMessageSubmitted,
        partnerPersonalMessageRead = partnerPersonalMessageRead,
        decisionRequiresPartnerPersonalMessageRead = decisionRequiresPartnerPersonalMessageRead,
    )

    fun queueStatus(inQueue: Boolean = true) = QueueStatusResponseDto(
        userId = "user-1",
        inQueue = inQueue,
    )

    fun home() = HomeResponseDto(
        profileStatus = "ACTIVE",
        matchmaking = HomeMatchmakingResponseDto(
            inQueue = false,
            canSearch = true,
        ),
        activeInteractionsSummary = HomeActiveInteractionsSummaryResponseDto(
            activeInitialCount = 1,
            activeConnectionCount = 2,
            pendingSchedulingConnectionCount = 3,
            actionableConnectionCount = 4,
        ),
        pendingActions = listOf(
            HomePendingActionResponseDto(
                type = "FIRST_CHAT",
                matchId = "match-1",
                chatId = "chat-1",
                partner = partner("First"),
            ),
            HomePendingActionResponseDto(
                type = "VISUAL_REVIEW",
                matchId = "match-2",
                partner = partner("Visual"),
            ),
        ),
        nextSteps = listOf(
            HomeNextStepResponseDto(
                type = "SCHEDULING",
                connectionId = "connection-1",
                matchId = "match-3",
                partner = partner("Scheduling"),
            ),
            HomeNextStepResponseDto(
                type = "SECOND_CHAT_AVAILABLE",
                connectionId = "connection-2",
                matchId = "match-4",
                secondChat = HomeChatResponseDto(
                    chatId = "chat-2",
                    chatType = "SECOND_CHAT",
                    chatStatus = "AVAILABLE",
                    availableAt = now,
                    expiresAt = now,
                    durationMinutes = 120,
                    partner = partner("Second"),
                ),
            ),
        ),
        passiveNotices = listOf(HomePassiveNoticeResponseDto("SCHEDULING_PREPARING", 2)),
    )

    fun connection(state: String = "SCHEDULING_PHASE") = ConnectionResponseDto(
        id = "connection-1",
        matchId = "match-1",
        userAId = "user-1",
        userBId = "user-2",
        state = state,
        schedulingExpiresAt = "2026-06-19T21:00:00Z",
        createdAt = now,
        updatedAt = now,
    )

    fun negotiation(status: String = "PENDING") = NegotiationResponseDto(
        id = "negotiation-1",
        connectionId = "connection-1",
        roundNumber = 2,
        status = status,
        confirmedDateTime = if (status == "CONFIRMED") now else null,
        chatId = if (status == "CONFIRMED") "chat-2" else null,
        schedulingExpiresAt = "2026-06-19T21:00:00Z",
        createdAt = now,
        updatedAt = now,
    )

    fun proposal(status: String = "PENDING") = ScheduleProposalResponseDto(
        id = "proposal-1",
        connectionId = "connection-1",
        userId = "user-1",
        roundNumber = 2,
        preferenceOrder = 1,
        proposedDateTime = "2026-06-18T21:00:00+00:00",
        status = status,
        chatId = null,
        createdAt = now,
    )
}
