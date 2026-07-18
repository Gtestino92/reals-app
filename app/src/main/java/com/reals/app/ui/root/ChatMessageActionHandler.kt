package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.ChatExitRequestStatus

internal object ChatMessageActionHandler {
    fun prepareFirstChatSend(
        current: RealsRootUiState.FirstChat,
        content: String,
    ): ChatMessageSendPreparation<RealsRootUiState.FirstChat> {
        if (current.loading || current.refreshing || current.sending || current.actionLoading) {
            return ChatMessageSendPreparation.Ignored
        }
        if (current.hasPendingExitRequest()) {
            return ChatMessageSendPreparation.Ignored
        }
        val chat = current.chat ?: return ChatMessageSendPreparation.Ignored
        return prepareSend(
            content = content,
            chatId = chat.id,
            senderId = current.session.user.id,
            invalidState = {
                current.copy(
                    error = invalidMessageError(),
                    message = null,
                )
            },
            pendingState = { optimisticMessage ->
                current.copy(
                    optimisticMessages = current.optimisticMessages + optimisticMessage,
                    sending = true,
                    error = null,
                    message = null,
                )
            },
        )
    }

    fun prepareSecondChatSend(
        current: RealsRootUiState.SecondChat,
        content: String,
    ): ChatMessageSendPreparation<RealsRootUiState.SecondChat> {
        if (current.loading || current.refreshing || current.sending || current.actionLoading) {
            return ChatMessageSendPreparation.Ignored
        }
        val chat = current.chat ?: return ChatMessageSendPreparation.Ignored
        return prepareSend(
            content = content,
            chatId = chat.id,
            senderId = current.session.user.id,
            invalidState = {
                current.copy(
                    error = invalidMessageError(),
                    message = null,
                )
            },
            pendingState = { optimisticMessage ->
                current.copy(
                    optimisticMessages = current.optimisticMessages + optimisticMessage,
                    sending = true,
                    error = null,
                    message = null,
                )
            },
        )
    }

    fun retryFirstChat(
        current: RealsRootUiState.FirstChat,
        localId: String,
    ): RealsRootUiState.FirstChat =
        if (current.hasPendingExitRequest()) {
            current
        } else {
            current.copy(
                optimisticMessages = current.optimisticMessages.withoutOptimisticMessage(localId),
            )
        }

    fun retrySecondChat(
        current: RealsRootUiState.SecondChat,
        localId: String,
    ): RealsRootUiState.SecondChat = current.copy(
        optimisticMessages = current.optimisticMessages.withoutOptimisticMessage(localId),
    )

    private fun <T> prepareSend(
        content: String,
        chatId: String,
        senderId: String,
        invalidState: () -> T,
        pendingState: (OptimisticOutgoingMessage) -> T,
    ): ChatMessageSendPreparation<T> {
        val cleanContent = TextSafety.normalizeMultiline(content, maxLength = 1_000)
        if (cleanContent.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanContent)) {
            return ChatMessageSendPreparation.Rejected(invalidState())
        }

        val optimisticMessage = newOptimisticOutgoingMessage(
            chatId = chatId,
            senderId = senderId,
            content = cleanContent,
        )
        return ChatMessageSendPreparation.Accepted(
            pendingState = pendingState(optimisticMessage),
            cleanContent = cleanContent,
            localId = optimisticMessage.localId,
        )
    }

    private fun invalidMessageError(): ApiError =
        ApiError.Unexpected("El mensaje no es válido.")

    private fun RealsRootUiState.FirstChat.hasPendingExitRequest(): Boolean =
        exitRequests.any { it.status == ChatExitRequestStatus.Pending }
}

internal sealed interface ChatMessageSendPreparation<out T> {
    data class Accepted<T>(
        val pendingState: T,
        val cleanContent: String,
        val localId: String,
    ) : ChatMessageSendPreparation<T>

    data class Rejected<T>(
        val state: T,
    ) : ChatMessageSendPreparation<T>

    data object Ignored : ChatMessageSendPreparation<Nothing>
}
