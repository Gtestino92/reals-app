package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.ChatAudioUnavailableReason
import com.reals.app.domain.model.ChatExitRequestStatus
import java.io.File

internal object ChatMessageActionHandler {
    fun prepareFirstChatSend(
        current: RealsRootUiState.FirstChat,
        content: String,
    ): ChatMessageSendPreparation<RealsRootUiState.FirstChat> {
        if (current.loading || current.refreshing || current.sending || current.audioUpload.uploading || current.actionLoading) {
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
                    audioUpload = current.audioUpload.copy(error = null, completedClientMessageId = null),
                    error = null,
                    message = null,
                )
            },
        )
    }

    fun prepareFirstChatAudioSend(
        current: RealsRootUiState.FirstChat,
        filePath: String,
        clientMessageId: String,
    ): ChatAudioSendPreparation<RealsRootUiState.FirstChat> {
        if (
            current.loading ||
            current.refreshing ||
            current.sending ||
            current.audioUpload.uploading ||
            current.actionLoading ||
            current.guidanceActionLoading ||
            current.manualBlock.loading ||
            current.hasPendingExitRequest()
        ) {
            return ChatAudioSendPreparation.Ignored
        }
        val chat = current.chat ?: return ChatAudioSendPreparation.Ignored
        return prepareAudioSend(
            filePath = filePath,
            clientMessageId = clientMessageId,
            chatId = chat.id,
            senderId = current.session.user.id,
            maxFileSizeBytes = chat.audioPolicy?.maxFileSizeBytes,
            maxDurationMillis = chat.audioPolicy?.maxDurationMillis,
            draftDurationMillis = current.audioDraft?.takeIf {
                it.filePath == filePath && it.clientMessageId == clientMessageId
            }?.durationMillis,
            policyEnabled = chat.audioPolicy?.enabled == true,
            unavailableReason = chat.audioPolicy?.unavailableReason,
            invalidState = { error, nonRetryable ->
                current.copy(
                    audioUpload = ChatAudioUploadUiState(error = error, nonRetryable = nonRetryable),
                    error = null,
                    message = null,
                )
            },
            pendingState = { optimisticMessage ->
                current.copy(
                    optimisticMessages = current.optimisticMessages + optimisticMessage,
                    audioUpload = ChatAudioUploadUiState(uploading = true),
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
        if (current.loading || current.refreshing || current.sending || current.audioUpload.uploading || current.actionLoading) {
            return ChatMessageSendPreparation.Ignored
        }
        if (current.lifecycle.status != null && !current.lifecycle.timingPresentation().genuinelyActive) {
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
                    audioUpload = current.audioUpload.copy(error = null, completedClientMessageId = null),
                    error = null,
                    message = null,
                )
            },
        )
    }

    fun prepareSecondChatAudioSend(
        current: RealsRootUiState.SecondChat,
        filePath: String,
        clientMessageId: String,
    ): ChatAudioSendPreparation<RealsRootUiState.SecondChat> {
        if (
            current.loading ||
            current.refreshing ||
            current.sending ||
            current.audioUpload.uploading ||
            current.actionLoading ||
            current.manualBlock.loading ||
            current.lifecycle.status != null && !current.lifecycle.timingPresentation().genuinelyActive
        ) {
            return ChatAudioSendPreparation.Ignored
        }
        val chat = current.chat ?: return ChatAudioSendPreparation.Ignored
        return prepareAudioSend(
            filePath = filePath,
            clientMessageId = clientMessageId,
            chatId = chat.id,
            senderId = current.session.user.id,
            maxFileSizeBytes = chat.audioPolicy?.maxFileSizeBytes,
            maxDurationMillis = chat.audioPolicy?.maxDurationMillis,
            draftDurationMillis = current.audioDraft?.takeIf {
                it.filePath == filePath && it.clientMessageId == clientMessageId
            }?.durationMillis,
            policyEnabled = chat.audioPolicy?.enabled == true,
            unavailableReason = chat.audioPolicy?.unavailableReason,
            invalidState = { error, nonRetryable ->
                current.copy(
                    audioUpload = ChatAudioUploadUiState(error = error, nonRetryable = nonRetryable),
                    error = null,
                    message = null,
                )
            },
            pendingState = { optimisticMessage ->
                current.copy(
                    optimisticMessages = current.optimisticMessages + optimisticMessage,
                    audioUpload = ChatAudioUploadUiState(uploading = true),
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

    private fun <T> prepareAudioSend(
        filePath: String,
        clientMessageId: String,
        chatId: String,
        senderId: String,
        maxFileSizeBytes: Long?,
        maxDurationMillis: Long?,
        draftDurationMillis: Long?,
        policyEnabled: Boolean,
        unavailableReason: ChatAudioUnavailableReason?,
        invalidState: (ApiError, Boolean) -> T,
        pendingState: (OptimisticOutgoingMessage) -> T,
    ): ChatAudioSendPreparation<T> {
        if (!policyEnabled) {
            return ChatAudioSendPreparation.Rejected(
                invalidState(audioPolicyUnavailableError(unavailableReason), true)
            )
        }
        val file = File(filePath)
        if (clientMessageId.isBlank() || !file.isFile || file.length() <= 0L) {
            return ChatAudioSendPreparation.Rejected(
                invalidState(ApiError.Unexpected("La grabación no es válida."), true)
            )
        }
        if (maxFileSizeBytes != null && file.length() > maxFileSizeBytes) {
            return ChatAudioSendPreparation.Rejected(
                invalidState(ApiError.Unexpected("La grabación supera el tamaño permitido."), true)
            )
        }
        if (draftDurationMillis == null || draftDurationMillis < 1_000L) {
            return ChatAudioSendPreparation.Rejected(
                invalidState(ApiError.Unexpected("La grabación quedó demasiado corta. Intentá nuevamente."), true)
            )
        }
        if (maxDurationMillis != null && draftDurationMillis > maxDurationMillis) {
            return ChatAudioSendPreparation.Rejected(
                invalidState(ApiError.Unexpected("La grabación supera la duración permitida."), true)
            )
        }
        val optimisticMessage = newOptimisticOutgoingAudioMessage(
            chatId = chatId,
            senderId = senderId,
            clientMessageId = clientMessageId,
            durationMillis = draftDurationMillis,
        )
        return ChatAudioSendPreparation.Accepted(
            pendingState = pendingState(optimisticMessage),
            chatId = chatId,
            file = file,
            clientMessageId = clientMessageId,
        )
    }

    private fun invalidMessageError(): ApiError =
        ApiError.Unexpected("El mensaje no es válido.")

    private fun audioPolicyUnavailableError(reason: ChatAudioUnavailableReason?): ApiError =
        ApiError.Unexpected(
            when (reason) {
                ChatAudioUnavailableReason.GuidanceRequired ->
                    "Respondan la pregunta actual para habilitar audios."
                ChatAudioUnavailableReason.GuidanceNotAvailable ->
                    "Los audios se habilitarán al avanzar en las preguntas."
                ChatAudioUnavailableReason.LimitReached ->
                    "Ya enviaste el audio disponible en este chat."
                ChatAudioUnavailableReason.WaitingForBoth ->
                    "El audio se habilita cuando ambas personas hayan ingresado."
                ChatAudioUnavailableReason.WaitingDelay ->
                    "El audio todavía no está disponible."
                ChatAudioUnavailableReason.ChatNotWritable ->
                    "Este chat no admite nuevos mensajes."
                ChatAudioUnavailableReason.FeatureDisabled ->
                    "Los audios no están disponibles."
                is ChatAudioUnavailableReason.Unknown,
                null -> "El audio no está disponible en este momento."
            }
        )

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

internal sealed interface ChatAudioSendPreparation<out T> {
    data class Accepted<T>(
        val pendingState: T,
        val chatId: String,
        val file: File,
        val clientMessageId: String,
    ) : ChatAudioSendPreparation<T>

    data class Rejected<T>(
        val state: T,
    ) : ChatAudioSendPreparation<T>

    data object Ignored : ChatAudioSendPreparation<Nothing>
}
