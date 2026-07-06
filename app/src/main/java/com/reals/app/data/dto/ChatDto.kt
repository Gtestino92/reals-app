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
    val endedAt: String? = null,
    val readOnlyUntil: String? = null,
    val lastMessageAt: String? = null,
    val guidance: FirstChatGuidanceResponseDto? = null,
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
data class ChatMessageResponseDto(
    val id: String,
    val chatSessionId: String,
    val senderId: String,
    val content: String,
    val sentAt: String,
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
