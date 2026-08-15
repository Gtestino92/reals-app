package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponseDto(
    val id: String,
    val matchId: String,
    val connectionId: String? = null,
    val chatType: String,
    val status: String,
    val startedAt: String,
    val availableAt: String? = null,
    val activatedAt: String? = null,
    val timeoutAt: String,
    val expiresAt: String? = null,
    val inactivityExpiresAt: String? = null,
    val partner: ChatPartnerResponseDto? = null,
    val myDecision: String? = null,
    val partnerDecision: String? = null,
    val endedReason: String? = null,
    val endedAt: String? = null,
    val readOnlyUntil: String? = null,
    val lastMessageAt: String? = null,
    val guidance: FirstChatGuidanceResponseDto? = null,
    val audioPolicy: ChatAudioPolicyResponseDto? = null,
    val serverTime: String? = null,
)

@Serializable
data class ChatAudioPolicyResponseDto(
    val enabled: Boolean,
    val unavailableReason: String? = null,
    val enabledAt: String? = null,
    val maxDurationMillis: Long,
    val maxFileSizeBytes: Long,
    val remainingMessages: Int? = null,
)

@Serializable
data class ChatPartnerResponseDto(
    val userId: String,
    val profileId: String,
    val displayName: String,
)

@Serializable
data class FirstChatGuidanceQuestionResponseDto(
    val id: String,
    val text: String,
)

@Serializable
data class FirstChatGuidanceResponseDto(
    val question: FirstChatGuidanceQuestionResponseDto,
    val questionOrdinal: Int,
    val maxQuestions: Int,
    val requiredCharacters: Int,
    val canRequestNext: Boolean,
    val myNextRequested: Boolean,
    val completed: Boolean,
)

@Serializable
data class SendMessageRequestDto(
    val content: String,
)

@Serializable
data class PutMessageReactionRequestDto(
    val type: String,
)

@Serializable
data class ChatMessageResponseDto(
    val id: String,
    val chatSessionId: String,
    val senderId: String,
    val clientMessageId: String? = null,
    val messageType: String = "TEXT",
    val content: String? = null,
    val audio: ChatAudioResponseDto? = null,
    val reactionType: String? = null,
    val sentAt: String,
)

@Serializable
data class ChatAudioResponseDto(
    val url: String? = null,
    val durationMillis: Long? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class ChatMessagesResponseDto(
    val messages: List<ChatMessageResponseDto>,
    val hasMore: Boolean = false,
    val serverTime: String? = null,
)

@Serializable
data class ChatExitRequestCreateRequestDto(
    val reason: String? = null,
    val details: String? = null,
)

@Serializable
data class ChatExitRequestResponseDto(
    val id: String,
    val chatId: String,
    val requesterUserId: String,
    val responderUserId: String,
    val type: String,
    val status: String,
    val reason: String? = null,
    val details: String? = null,
    val createdAt: String,
    val resolvedAt: String? = null,
)

@Serializable
data class ChatExitOutcomeResponseDto(
    val chat: ChatResponseDto,
    val exitRequest: ChatExitRequestResponseDto,
    val penaltyApplied: Boolean = false,
    val penalizedUserId: String? = null,
)

@Serializable
data class SecondChatResolutionRequestResponseDto(
    val id: String,
    val type: String,
    val requesterUserId: String,
    val responderUserId: String,
    val referenceMessageId: String? = null,
    val status: String,
    val createdAt: String,
    val expiresAt: String,
)

@Serializable
data class SecondChatAttendanceResponseDto(
    val connectionId: String,
    val chatId: String? = null,
    val scheduledAt: String,
    val onTimeUntil: String,
    val entryClosesAt: String,
    val absoluteExpiresAt: String,
    val conversationStartedAt: String? = null,
    val serverTime: String,
    val myAttendanceStatus: String,
    val myJoinedAt: String? = null,
    val partnerAttendanceStatus: String,
    val partnerJoinedAt: String? = null,
    val canJoin: Boolean,
    val canClaimPartnerNoShow: Boolean,
    val activeNoShowClaim: SecondChatResolutionRequestResponseDto? = null,
    val activeResolutionRequest: SecondChatResolutionRequestResponseDto? = null,
    val chatStatus: String? = null,
    val endedReason: String? = null,
    val endedAt: String? = null,
    val readOnlyUntil: String? = null,
    val mutualCompletionEligibleAt: String? = null,
    val canRequestMutualCompletion: Boolean = false,
    val mutualCompletionCooldownUntil: String? = null,
    val inactivityClaimableAt: String? = null,
    val inactivityClosesAt: String? = null,
    val canClaimPartnerInactivity: Boolean = false,
    val mustRespondToPartner: Boolean = false,
    val lastMessageAt: String? = null,
    val lastMessageSenderId: String? = null,
    val audioPolicy: ChatAudioPolicyResponseDto? = null,
)

@Serializable
data class SecondChatCompletionDecisionRequestDto(
    val decision: String,
)
