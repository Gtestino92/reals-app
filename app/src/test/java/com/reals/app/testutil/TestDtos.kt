package com.reals.app.testutil

import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.AffinityAnswerOptionResponseDto
import com.reals.app.data.dto.AffinityAnswerResponseDto
import com.reals.app.data.dto.AffinityAnswersResponseDto
import com.reals.app.data.dto.AffinityQuestionCatalogResponseDto
import com.reals.app.data.dto.AffinityQuestionCategoryResponseDto
import com.reals.app.data.dto.AffinityQuestionResponseDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatAudioPolicyResponseDto
import com.reals.app.data.dto.ChatAudioResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.ConnectionResponseDto
import com.reals.app.data.dto.CountryReferenceResponseDto
import com.reals.app.data.dto.CurrentLegalDocumentResponseDto
import com.reals.app.data.dto.CurrentLegalDocumentsResponseDto
import com.reals.app.data.dto.FirstChatGuidanceQuestionResponseDto
import com.reals.app.data.dto.FirstChatGuidanceResponseDto
import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomeNextStepResponseDto
import com.reals.app.data.dto.HomePassiveNoticeResponseDto
import com.reals.app.data.dto.HomePendingActionLiteResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.dto.HomePendingSecondChatLiteResponseDto
import com.reals.app.data.dto.HomePendingStateResponseDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.data.dto.HomeStatusResponseDto
import com.reals.app.data.dto.HomeNextStepLiteResponseDto
import com.reals.app.data.dto.LegalDocumentActionResponseDto
import com.reals.app.data.dto.LegalDocumentStatusResponseDto
import com.reals.app.data.dto.LegalStatusResponseDto
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.NegotiationResponseDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.ProfileQuestionAnswerResponseDto
import com.reals.app.data.dto.ProfileQuestionAnswersResponseDto
import com.reals.app.data.dto.ProfileQuestionCatalogResponseDto
import com.reals.app.data.dto.ProfileQuestionResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.PublicProfileQuestionResponseDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.ScheduleProposalResponseDto
import com.reals.app.data.dto.SchedulingAvailabilityResponseDto
import com.reals.app.data.dto.SchedulingUnavailableWindowResponseDto
import com.reals.app.data.dto.SecondChatAttendanceResponseDto
import com.reals.app.data.dto.SecondChatResolutionRequestResponseDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.data.dto.UserBlockResponseDto
import com.reals.app.data.dto.VisualAffinityIndicatorResponseDto
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

    fun currentLegalDocuments(
        documents: List<CurrentLegalDocumentResponseDto> = listOf(currentLegalDocument()),
    ) = CurrentLegalDocumentsResponseDto(documents)

    fun currentLegalDocument(
        type: String = "TERMS_OF_USE",
        version: String = "2026-07-01",
        url: String = "https://example.test/terms",
        requiredAction: String = "ACCEPTED",
    ) = CurrentLegalDocumentResponseDto(
        type = type,
        version = version,
        url = url,
        requiredAction = requiredAction,
    )

    fun legalStatus(
        requirementsSatisfied: Boolean = true,
        documents: List<LegalDocumentStatusResponseDto> = emptyList(),
    ) = LegalStatusResponseDto(
        requirementsSatisfied = requirementsSatisfied,
        documents = documents,
    )

    fun legalDocumentStatus(
        type: String = "TERMS_OF_USE",
        version: String = "2026-07-01",
        requiredAction: String = "ACCEPTED",
        recordedAction: String? = null,
        actedAt: String? = null,
        satisfied: Boolean = false,
    ) = LegalDocumentStatusResponseDto(
        type = type,
        version = version,
        requiredAction = requiredAction,
        recordedAction = recordedAction,
        actedAt = actedAt,
        satisfied = satisfied,
    )

    fun legalAction(
        id: String = "action-1",
        documentType: String = "TERMS_OF_USE",
        documentVersion: String = "2026-07-01",
        action: String = "ACCEPTED",
        actedAt: String = now,
    ) = LegalDocumentActionResponseDto(
        id = id,
        documentType = documentType,
        documentVersion = documentVersion,
        action = action,
        actedAt = actedAt,
    )

    fun profile(status: String = "ACTIVE") = ProfileResponseDto(
        id = "profile-1",
        userId = "user-1",
        displayName = "Alex",
        birthDate = "1998-01-01",
        age = 28,
        authenticityVerified = true,
        authenticityVerificationStatus = "VERIFIED",
        gender = "FEMALE",
        lookingForGenders = setOf("MALE"),
        intention = "SERIOUS",
        city = "Buenos Aires",
        countryCode = "AR",
        bio = "Hola",
        preferredMinAge = 25,
        preferredMaxAge = 35,
        maxDistanceKm = 10,
        status = status,
        photoCount = 2,
        createdAt = now,
        updatedAt = now,
    )

    fun affinityQuestionCatalog(
        categories: List<AffinityQuestionCategoryResponseDto> = listOf(
            affinityQuestionCategory(id = "MUSIC", title = "Música", displayOrder = 1),
            affinityQuestionCategory(id = "PLANS", title = "Planes", displayOrder = 2),
        ),
        questions: List<AffinityQuestionResponseDto> = listOf(
            affinityQuestion(id = "MUSIC_DISCOVERY_001", categoryId = "MUSIC"),
            affinityQuestion(
                id = "PLANS_WEEKEND_001",
                categoryId = "PLANS",
                prompt = "¿Qué plan de fin de semana preferís compartir?",
                answerType = "SINGLE_CHOICE",
            ),
        ),
    ) = AffinityQuestionCatalogResponseDto(
        catalogVersion = "catalog-1",
        categories = categories,
        questions = questions,
    )

    fun affinityQuestionCategory(
        id: String = "MUSIC",
        title: String = "Música",
        description: String? = "Sobre música compartida",
        displayOrder: Int = 1,
    ) = AffinityQuestionCategoryResponseDto(
        id = id,
        title = title,
        description = description,
        displayOrder = displayOrder,
    )

    fun affinityQuestion(
        id: String = "MUSIC_DISCOVERY_001",
        semanticVersion: Int = 1,
        contentVersion: Int = 1,
        categoryId: String = "MUSIC",
        primaryTopic: String = "music_discovery",
        topicTags: List<String> = listOf("music"),
        answerType: String = "ORDINAL_SCALE",
        prompt: String = "¿Qué tanto disfrutás descubrir música nueva con otra persona?",
        options: List<AffinityAnswerOptionResponseDto> = listOf(
            affinityAnswerOption("LOW", "Poco", 1),
            affinityAnswerOption("VERY_HIGH", "Mucho", 2),
        ),
    ) = AffinityQuestionResponseDto(
        id = id,
        semanticVersion = semanticVersion,
        contentVersion = contentVersion,
        categoryId = categoryId,
        primaryTopic = primaryTopic,
        topicTags = topicTags,
        answerType = answerType,
        prompt = prompt,
        options = options,
    )

    fun affinityAnswerOption(
        code: String = "LOW",
        label: String = "Poco",
        displayOrder: Int = 1,
    ) = AffinityAnswerOptionResponseDto(
        code = code,
        label = label,
        displayOrder = displayOrder,
    )

    fun affinityAnswers(
        answers: List<AffinityAnswerResponseDto> = listOf(affinityAnswer()),
    ) = AffinityAnswersResponseDto(answers = answers)

    fun affinityAnswer(
        questionId: String = "MUSIC_DISCOVERY_001",
        questionSemanticVersion: Int = 1,
        answerCode: String = "VERY_HIGH",
    ) = AffinityAnswerResponseDto(
        questionId = questionId,
        questionSemanticVersion = questionSemanticVersion,
        answerCode = answerCode,
        createdAt = now,
        updatedAt = now,
    )

    fun profileQuestionCatalog(
        questions: List<ProfileQuestionResponseDto> = listOf(
            profileQuestion("PERFECT_SUNDAY_001", prompt = "Mi domingo perfecto incluye...", displayOrder = 1),
            profileQuestion("LIFE_SOUNDTRACK_001", prompt = "Mi banda sonora sería...", displayOrder = 2),
            profileQuestion("SMALL_JOY_001", prompt = "Una alegría simple para mí es...", displayOrder = 3),
            profileQuestion("BEST_PLAN_001", prompt = "Un plan que siempre disfruto es...", displayOrder = 4),
        ),
    ) = ProfileQuestionCatalogResponseDto(
        catalogVersion = "2026-08-01",
        questions = questions,
    )

    fun profileQuestion(
        id: String = "PERFECT_SUNDAY_001",
        semanticVersion: Int = 1,
        contentVersion: Int = 1,
        prompt: String = "Mi domingo perfecto incluye...",
        displayOrder: Int = 1,
    ) = ProfileQuestionResponseDto(
        id = id,
        semanticVersion = semanticVersion,
        contentVersion = contentVersion,
        prompt = prompt,
        displayOrder = displayOrder,
    )

    fun profileQuestionAnswers(
        answers: List<ProfileQuestionAnswerResponseDto> = listOf(profileQuestionAnswer()),
    ) = ProfileQuestionAnswersResponseDto(answers = answers)

    fun profileQuestionAnswer(
        questionId: String = "PERFECT_SUNDAY_001",
        questionSemanticVersion: Int = 1,
        answer: String = "Café y una caminata.",
        selectedPosition: Int? = null,
        current: Boolean = true,
    ) = ProfileQuestionAnswerResponseDto(
        questionId = questionId,
        questionSemanticVersion = questionSemanticVersion,
        answer = answer,
        selectedPosition = selectedPosition,
        current = current,
        createdAt = now,
        updatedAt = now,
    )

    fun country(
        code: String = "AR",
        displayName: String = "Argentina",
    ) = CountryReferenceResponseDto(
        code = code,
        displayName = displayName,
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
        moderationStatus = "APPROVED",
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
        guidance: FirstChatGuidanceResponseDto? = null,
        audioPolicy: ChatAudioPolicyResponseDto? = null,
        serverTime: String? = now,
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
        endedReason = null,
        endedAt = null,
        lastMessageAt = now,
        guidance = guidance,
        audioPolicy = audioPolicy,
        serverTime = serverTime,
    )

    fun audioPolicy(
        enabled: Boolean = true,
        unavailableReason: String? = null,
        remainingMessages: Int? = 1,
    ) = ChatAudioPolicyResponseDto(
        enabled = enabled,
        unavailableReason = unavailableReason,
        enabledAt = null,
        maxDurationMillis = 60_000,
        maxFileSizeBytes = 2_097_152,
        remainingMessages = remainingMessages,
    )

    fun firstChatGuidance(
        questionId: String = "Q027",
        questionText: String = "¿Qué cosa pequeña te mejora mucho el día?",
        questionOrdinal: Int = 1,
        maxQuestions: Int = 3,
        requiredCharacters: Int = 40,
        canRequestNext: Boolean = true,
        myNextRequested: Boolean = false,
        completed: Boolean = false,
    ) = FirstChatGuidanceResponseDto(
        question = FirstChatGuidanceQuestionResponseDto(
            id = questionId,
            text = questionText,
        ),
        questionOrdinal = questionOrdinal,
        maxQuestions = maxQuestions,
        requiredCharacters = requiredCharacters,
        canRequestNext = canRequestNext,
        myNextRequested = myNextRequested,
        completed = completed,
    )

    fun chatMessage(id: String = "message-1") = ChatMessageResponseDto(
        id = id,
        chatSessionId = "chat-1",
        senderId = "user-1",
        clientMessageId = null,
        messageType = "TEXT",
        content = "hola",
        audio = null,
        sentAt = now,
    )

    fun audioChatMessage(
        id: String = "audio-message-1",
        clientMessageId: String = "00000000-0000-0000-0000-000000000101",
        url: String = "https://example.test/audio",
    ) = ChatMessageResponseDto(
        id = id,
        chatSessionId = "chat-1",
        senderId = "user-1",
        clientMessageId = clientMessageId,
        messageType = "AUDIO",
        content = null,
        audio = ChatAudioResponseDto(
            url = url,
            durationMillis = 3_158,
            contentType = "audio/mp4",
            sizeBytes = 77_832,
        ),
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

    fun secondChatResolutionRequest(
        id: String = "request-1",
        type: String = "PARTNER_NO_SHOW",
        requesterUserId: String = "user-1",
        responderUserId: String = "user-2",
        status: String = "PENDING",
        expiresAt: String = "2026-06-18T21:01:00Z",
    ) = SecondChatResolutionRequestResponseDto(
        id = id,
        type = type,
        requesterUserId = requesterUserId,
        responderUserId = responderUserId,
        referenceMessageId = null,
        status = status,
        createdAt = now,
        expiresAt = expiresAt,
    )

    fun secondChatStatus(
        chatId: String? = "chat-1",
        myAttendanceStatus: String = "ON_TIME",
        partnerAttendanceStatus: String = "ON_TIME",
        canJoin: Boolean = false,
        canClaimPartnerNoShow: Boolean = false,
        activeNoShowClaim: SecondChatResolutionRequestResponseDto? = null,
        activeResolutionRequest: SecondChatResolutionRequestResponseDto? = activeNoShowClaim,
        chatStatus: String? = "ACTIVE",
        endedReason: String? = null,
        readOnlyUntil: String? = null,
        absoluteExpiresAt: String = "2026-06-18T23:00:00Z",
        serverTime: String = now,
        mutualCompletionEligibleAt: String? = "2026-06-18T21:10:00Z",
        canRequestMutualCompletion: Boolean = false,
        mutualCompletionCooldownUntil: String? = null,
        inactivityClaimableAt: String? = "2026-06-18T21:05:00Z",
        inactivityClosesAt: String? = "2026-06-18T21:10:00Z",
        canClaimPartnerInactivity: Boolean = false,
        mustRespondToPartner: Boolean = false,
        lastMessageAt: String? = now,
        lastMessageSenderId: String? = "user-1",
        audioPolicy: ChatAudioPolicyResponseDto? = null,
    ) = SecondChatAttendanceResponseDto(
        connectionId = "connection-1",
        chatId = chatId,
        scheduledAt = "2026-06-18T21:00:00Z",
        onTimeUntil = "2026-06-18T21:10:00Z",
        entryClosesAt = "2026-06-18T21:20:00Z",
        absoluteExpiresAt = absoluteExpiresAt,
        conversationStartedAt = if (partnerAttendanceStatus == "PENDING") null else now,
        serverTime = serverTime,
        myAttendanceStatus = myAttendanceStatus,
        myJoinedAt = if (myAttendanceStatus == "PENDING") null else now,
        partnerAttendanceStatus = partnerAttendanceStatus,
        partnerJoinedAt = if (partnerAttendanceStatus == "PENDING") null else now,
        canJoin = canJoin,
        canClaimPartnerNoShow = canClaimPartnerNoShow,
        activeNoShowClaim = activeNoShowClaim,
        activeResolutionRequest = activeResolutionRequest,
        chatStatus = chatStatus,
        endedReason = endedReason,
        endedAt = endedReason?.let { now },
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
        audioPolicy = audioPolicy,
    )

    fun visualProfile(
        myPersonalMessageSubmitted: Boolean = true,
        partnerPersonalMessageSubmitted: Boolean = false,
        partnerPersonalMessageRead: Boolean = true,
        decisionRequiresPartnerPersonalMessageRead: Boolean = false,
        affinityIndicators: List<VisualAffinityIndicatorResponseDto> = emptyList(),
        profileQuestions: List<PublicProfileQuestionResponseDto> = emptyList(),
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
        affinityIndicators = affinityIndicators,
        profileQuestions = profileQuestions,
    )

    fun publicProfileQuestion(
        questionId: String = "PERFECT_SUNDAY_001",
        prompt: String = "Mi domingo perfecto incluye...",
        answer: String = "Café y una caminata.",
        position: Int = 1,
    ) = PublicProfileQuestionResponseDto(
        questionId = questionId,
        prompt = prompt,
        answer = answer,
        position = position,
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
            hasPendingSchedulingConnection = true,
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
        passiveNotices = listOf(HomePassiveNoticeResponseDto("SCHEDULING_PREPARING")),
    )

    fun homeWithoutPendingEngagements() = home().copy(
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )

    fun homeStatus(
        version: Long = 1,
        dirty: Boolean = false,
        nextRefreshAt: String? = null,
        serverTime: String? = now,
    ) = HomeStatusResponseDto(
        version = version,
        dirty = dirty,
        nextRefreshAt = nextRefreshAt,
        serverTime = serverTime,
    )

    fun homePending() = HomePendingStateResponseDto(
        version = 1,
        pendingActions = listOf(
            HomePendingActionLiteResponseDto(
                type = "FIRST_CHAT",
                matchId = "match-1",
                chatId = "chat-1",
            ),
            HomePendingActionLiteResponseDto(
                type = "VISUAL_REVIEW",
                matchId = "match-2",
            ),
        ),
        nextSteps = listOf(
            HomeNextStepLiteResponseDto(
                type = "SCHEDULING",
                connectionId = "connection-1",
                matchId = "match-3",
            ),
            HomeNextStepLiteResponseDto(
                type = "SECOND_CHAT_AVAILABLE",
                connectionId = "connection-2",
                matchId = "match-4",
                secondChat = HomePendingSecondChatLiteResponseDto(
                    chatId = "chat-2",
                    availableAt = now,
                    expiresAt = now,
                    durationMinutes = 120,
                ),
            ),
        ),
        passiveNotices = listOf(HomePassiveNoticeResponseDto("SCHEDULING_PREPARING")),
        serverTime = now,
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

    fun schedulingAvailability(
        conflictWindowMinutes: Long = 60,
        unavailableWindows: List<SchedulingUnavailableWindowResponseDto> = emptyList(),
        serverTime: String = now,
    ) = SchedulingAvailabilityResponseDto(
        conflictWindowMinutes = conflictWindowMinutes,
        unavailableWindows = unavailableWindows,
        serverTime = serverTime,
    )

    fun unavailableWindow(
        startsAt: String? = "2026-07-30T19:00:00-03:00",
        endsAt: String? = "2026-07-30T21:00:00-03:00",
    ) = SchedulingUnavailableWindowResponseDto(
        startsAt = startsAt,
        endsAt = endsAt,
    )

    fun userBlock() = UserBlockResponseDto(
        id = "block-1",
        source = "MANUAL",
        createdAt = now,
    )
}
