package com.reals.app.ui.chat

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessagePresentation
import com.reals.app.domain.model.ChatMessageReply
import com.reals.app.domain.model.ChatMessageReplyTargetType
import com.reals.app.domain.model.ChatMessageType
import com.reals.app.domain.model.ChatReplyDraft
import com.reals.app.domain.model.FirstChatGuidance

internal const val AudioReplyPreviewText = "Mensaje de audio"
private const val PartnerFallbackReplyLabel = "Tu match"

internal data class ChatReplyPreview(
    val label: String,
    val text: String,
)

internal fun ChatMessage.isCitableReplyTarget(currentUserId: String): Boolean =
    senderId != currentUserId &&
        when (presentation) {
            is ChatMessagePresentation.Text,
            is ChatMessagePresentation.Audio -> true
            ChatMessagePresentation.Unsupported -> false
        }

internal fun ChatMessage.toReplyDraftOrNull(currentUserId: String): ChatReplyDraft.Message? {
    if (!isCitableReplyTarget(currentUserId)) return null
    return ChatReplyDraft.Message(
        targetId = id,
        senderId = senderId,
        messageType = messageType,
        previewText = when (presentation) {
            is ChatMessagePresentation.Text -> content
            is ChatMessagePresentation.Audio -> AudioReplyPreviewText
            ChatMessagePresentation.Unsupported -> null
        },
    )
}

internal fun FirstChatGuidance.toGuidanceReplyDraftOrNull(
    canSendMessages: Boolean,
): ChatReplyDraft.GuidanceQuestion? {
    val instanceId = question.instanceId?.takeIf { it.isNotBlank() } ?: return null
    if (!canSendMessages) return null
    return ChatReplyDraft.GuidanceQuestion(
        targetId = instanceId,
        previewText = question.text,
    )
}

internal fun ChatReplyDraft.toPreview(
    currentUserId: String,
    partnerDisplayName: String?,
): ChatReplyPreview = when (this) {
    is ChatReplyDraft.Message -> ChatReplyPreview(
        label = replyMessageLabel(senderId, currentUserId, partnerDisplayName),
        text = replyTextFor(messageType, previewText),
    )
    is ChatReplyDraft.GuidanceQuestion -> ChatReplyPreview(
        label = "Pregunta",
        text = TextSafety.safeDisplay(previewText),
    )
}

internal fun ChatMessageReply.toPreview(
    currentUserId: String,
    partnerDisplayName: String?,
): ChatReplyPreview? {
    val text = when (type) {
        ChatMessageReplyTargetType.Message -> replyTextFor(messageType, previewText)
        ChatMessageReplyTargetType.GuidanceQuestion -> TextSafety.safeDisplay(previewText.orEmpty())
        is ChatMessageReplyTargetType.Unknown -> TextSafety.safeDisplay(previewText.orEmpty())
    }.takeIf { it.isNotBlank() } ?: return null
    val label = when (type) {
        ChatMessageReplyTargetType.Message -> replyMessageLabel(senderId, currentUserId, partnerDisplayName)
        ChatMessageReplyTargetType.GuidanceQuestion -> "Pregunta"
        is ChatMessageReplyTargetType.Unknown -> "Respuesta"
    }
    return ChatReplyPreview(label = label, text = text)
}

private fun replyMessageLabel(
    senderId: String?,
    currentUserId: String,
    partnerDisplayName: String?,
): String = if (senderId == currentUserId) {
    "Vos"
} else {
    partnerDisplayName
        ?.takeIf { it.isNotBlank() }
        ?.let(TextSafety::safeDisplay)
        ?: PartnerFallbackReplyLabel
}

private fun replyTextFor(
    messageType: ChatMessageType?,
    previewText: String?,
): String = when (messageType) {
    ChatMessageType.Audio -> AudioReplyPreviewText
    ChatMessageType.Text,
    null -> TextSafety.safeDisplay(previewText.orEmpty())
    is ChatMessageType.Unknown -> TextSafety.safeDisplay(previewText.orEmpty())
}.ifBlank { AudioReplyPreviewText.takeIf { messageType == ChatMessageType.Audio }.orEmpty() }

internal val ChatReplyDraftSaver: Saver<ChatReplyDraft?, Any> = Saver(
    save = { draft -> draft.toSaveableList() },
    restore = { value -> (value as? List<*>)?.toChatReplyDraft() },
)

internal val ChatReplyDraftMutableStateSaver: Saver<MutableState<ChatReplyDraft?>, Any> = Saver(
    save = { state -> state.value.toSaveableList() },
    restore = { value -> mutableStateOf((value as? List<*>)?.toChatReplyDraft()) },
)

private fun ChatReplyDraft?.toSaveableList(): List<String> = when (this) {
    null -> listOf("NONE")
    is ChatReplyDraft.Message -> listOf(
        "MESSAGE",
        targetId,
        senderId,
        messageType.rawValue,
        previewText.orEmpty(),
    )
    is ChatReplyDraft.GuidanceQuestion -> listOf(
        "GUIDANCE_QUESTION",
        targetId,
        previewText,
    )
}

private fun List<*>.toChatReplyDraft(): ChatReplyDraft? {
    return when (getOrNull(0) as? String) {
        "MESSAGE" -> ChatReplyDraft.Message(
            targetId = getOrNull(1) as? String ?: return null,
            senderId = getOrNull(2) as? String ?: return null,
            messageType = ChatMessageType.fromBackend(getOrNull(3) as? String),
            previewText = (getOrNull(4) as? String)?.takeIf { it.isNotBlank() },
        )
        "GUIDANCE_QUESTION" -> ChatReplyDraft.GuidanceQuestion(
            targetId = getOrNull(1) as? String ?: return null,
            previewText = getOrNull(2) as? String ?: return null,
        )
        else -> null
    }
}

internal fun shouldSelectReplyForSwipe(
    horizontalDistancePx: Float,
    thresholdPx: Float,
): Boolean = horizontalDistancePx >= thresholdPx

internal fun shouldPreserveBottomForComposerHeightChange(
    wasNearBottomBeforeComposerHeightChange: Boolean,
): Boolean = wasNearBottomBeforeComposerHeightChange
