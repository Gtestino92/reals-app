package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ChatRepository
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.domain.model.ChatReplyTarget
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.domain.model.SecondChatCompletionDecision
import com.reals.app.domain.model.SecondChatStatus
import java.io.File

class GetChatUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String): ApiResult<Chat> =
        chatRepository.getChat(chatId)
}

class GetSecondChatForConnectionUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<Chat> =
        chatRepository.getSecondChatForConnection(connectionId)
}

class GetSecondChatStatusUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<SecondChatStatus> =
        chatRepository.getSecondChatStatus(connectionId)
}

class JoinSecondChatUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<SecondChatStatus> =
        chatRepository.joinSecondChat(connectionId)
}

class CreateSecondChatNoShowClaimUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<SecondChatStatus> =
        chatRepository.createSecondChatNoShowClaim(connectionId)
}

class CreateSecondChatCompletionRequestUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<SecondChatStatus> =
        chatRepository.createSecondChatCompletionRequest(connectionId)
}

class DecideSecondChatCompletionRequestUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        connectionId: String,
        requestId: String,
        decision: SecondChatCompletionDecision,
    ): ApiResult<SecondChatStatus> =
        chatRepository.decideSecondChatCompletionRequest(connectionId, requestId, decision)
}

class CreateSecondChatInactivityClaimUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<SecondChatStatus> =
        chatRepository.createSecondChatInactivityClaim(connectionId)
}

class DismissSecondChatForConnectionUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(connectionId: String): ApiResult<Boolean> =
        chatRepository.dismissSecondChatForConnection(connectionId)
}

class GetChatMessagesUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, afterMessageId: String? = null): ApiResult<List<ChatMessage>> =
        chatRepository.getMessages(chatId, afterMessageId)
}

class SendChatMessageUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: String,
        content: String,
        clientMessageId: String,
        replyTo: ChatReplyTarget? = null,
    ): ApiResult<ChatMessage> =
        chatRepository.sendMessage(chatId, content, clientMessageId, replyTo)
}

class SendChatAudioMessageUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: String,
        file: File,
        clientMessageId: String,
        replyTo: ChatReplyTarget? = null,
    ): ApiResult<ChatMessage> =
        chatRepository.sendAudioMessage(
            chatId = chatId,
            file = file,
            clientMessageId = clientMessageId,
            replyTo = replyTo,
        )
}

class PutChatMessageReactionUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: String,
        messageId: String,
        reactionType: ChatMessageReactionType,
    ): ApiResult<ChatMessage> =
        chatRepository.putMessageReaction(chatId, messageId, reactionType)
}

class RequestNextFirstChatGuidanceQuestionUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String): ApiResult<FirstChatGuidance> =
        chatRepository.requestNextFirstChatGuidanceQuestion(chatId)
}

class GetChatExitRequestsUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String): ApiResult<List<ChatExitRequest>> =
        chatRepository.getExitRequests(chatId)
}

class RequestMutualChatExitUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: String,
        reason: ChatExitReason?,
        details: String?,
    ): ApiResult<ChatExitRequest> =
        chatRepository.requestMutualExit(chatId, reason, details)
}

class AcceptChatExitRequestUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, exitRequestId: String): ApiResult<ChatExitOutcome> =
        chatRepository.acceptExitRequest(chatId, exitRequestId)
}

class RejectChatExitRequestUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, exitRequestId: String): ApiResult<ChatExitOutcome> =
        chatRepository.rejectExitRequest(chatId, exitRequestId)
}

class TimeoutChatExitRequestUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, exitRequestId: String): ApiResult<ChatExitOutcome> =
        chatRepository.timeoutExitRequest(chatId, exitRequestId)
}

class CancelChatUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: String,
        reason: ChatExitReason?,
        details: String?,
    ): ApiResult<ChatExitOutcome> =
        chatRepository.cancelChat(chatId, reason, details)
}

class SafetyCancelChatUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: String, reason: ChatExitReason, details: String): ApiResult<ChatExitOutcome> =
        chatRepository.safetyCancelChat(chatId, reason, details)
}
