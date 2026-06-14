package com.reals.app.data.mapper

import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatPartnerResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.CreateProfileRequestDto
import com.reals.app.data.dto.EnqueueMatchmakingRequestDto
import com.reals.app.data.dto.HomeChatResponseDto
import com.reals.app.data.dto.HomeConnectionResponseDto
import com.reals.app.data.dto.HomeMatchResponseDto
import com.reals.app.data.dto.HomeQueueResponseDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.VisualProfileResponseDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.UpdateMatchFiltersRequestDto
import com.reals.app.data.dto.UpdateProfileRequestDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.BackendUserStatus
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
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.HomeChat
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeMatch
import com.reals.app.domain.model.HomeQueueState
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.QueueStatus
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.VisualProfile

fun UserResponseDto.toDomain(): BackendUser = BackendUser(
    id = id,
    email = email,
    status = BackendUserStatus.fromBackend(status),
    deletedAt = deletedAt,
    deletionFinalizesAt = deletionFinalizesAt,
    createdAt = createdAt,
)

fun ProfileResponseDto.toDomain(): Profile = Profile(
    id = id,
    userId = userId,
    displayName = displayName,
    birthDate = birthDate,
    age = age,
    identityVerified = identityVerified,
    gender = gender,
    lookingForGender = lookingForGender,
    intention = intention,
    city = city,
    country = country,
    bio = bio,
    preferredMinAge = preferredMinAge,
    preferredMaxAge = preferredMaxAge,
    maxDistanceKm = maxDistanceKm,
    status = ProfileStatus.fromBackend(status),
    photoCount = photoCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CreateProfileInput.toDto(): CreateProfileRequestDto = CreateProfileRequestDto(
    displayName = displayName,
    birthDate = birthDate,
    gender = gender,
    lookingForGender = lookingForGender,
    intention = intention,
    city = city,
    country = country,
    bio = bio,
    preferredMinAge = preferredMinAge,
    preferredMaxAge = preferredMaxAge,
    maxDistanceKm = maxDistanceKm,
)

fun UpdateProfileInput.toDto(): UpdateProfileRequestDto = UpdateProfileRequestDto(
    displayName = displayName,
    bio = bio,
    city = city,
    country = country,
    intention = intention,
    lookingForGender = lookingForGender,
)

fun UpdateMatchFiltersInput.toDto(): UpdateMatchFiltersRequestDto = UpdateMatchFiltersRequestDto(
    preferredMinAge = preferredMinAge,
    preferredMaxAge = preferredMaxAge,
    maxDistanceKm = maxDistanceKm,
)

fun PhotoResponseDto.toDomain(): ProfilePhoto = ProfilePhoto(
    id = id,
    url = url,
    position = position,
    isPersonPhoto = isPersonPhoto,
    isFullBody = isFullBody,
    validationStatus = validationStatus,
)

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
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun VisualProfileResponseDto.toDomain(): VisualProfile = VisualProfile(
    profileId = profileId,
    displayName = displayName,
    age = age,
    bio = bio,
    photos = photos.map { it.toDomain() }.sortedBy { it.position },
)

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
