package com.reals.app.ui.chat

import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.isFirstChatDecisionOnly

internal const val INITIAL_FIRST_CHAT_UNANSWERED_MILLIS = 30_000L
internal const val ONGOING_FIRST_CHAT_UNANSWERED_MILLIS = 3 * 60_000L

internal data class FirstChatUnansweredSuggestionState(
    val visible: Boolean,
    val actionEnabled: Boolean,
    val periodReference: String?,
)

private val hiddenFirstChatUnansweredSuggestionState =
    FirstChatUnansweredSuggestionState(visible = false, actionEnabled = false, periodReference = null)

internal fun firstChatUnansweredSuggestionState(
    chat: Chat?,
    currentUserId: String,
    confirmedMessages: List<ChatMessage>,
    pendingExitRequest: ChatExitRequest?,
    estimatedServerNowMillis: Long?,
    dismissedPeriodReference: String?,
    mutualExitActionAvailable: Boolean,
): FirstChatUnansweredSuggestionState {
    if (chat?.chatType != ChatType.FirstChat) return hiddenFirstChatUnansweredSuggestionState
    if (chat.status != ChatStatus.Active) return hiddenFirstChatUnansweredSuggestionState
    if (chat.isFirstChatDecisionOnly()) return hiddenFirstChatUnansweredSuggestionState
    if (chat.myDecision != ChatDecisionState.Pending) return hiddenFirstChatUnansweredSuggestionState
    if (pendingExitRequest != null) return hiddenFirstChatUnansweredSuggestionState
    val nowMillis = estimatedServerNowMillis ?: return hiddenFirstChatUnansweredSuggestionState
    if (firstChatLifecycleUiState(chat, nowMillis)?.expired == true) {
        return hiddenFirstChatUnansweredSuggestionState
    }
    if (!mutualExitActionAvailable) {
        return hiddenFirstChatUnansweredSuggestionState
    }
    val period = firstChatUnansweredPeriodReference(chat, currentUserId, confirmedMessages)
        ?: return hiddenFirstChatUnansweredSuggestionState
    if (period.reference == dismissedPeriodReference) return hiddenFirstChatUnansweredSuggestionState
    val visible = nowMillis >= period.referenceTimeEpochMillis + period.thresholdMillis
    return FirstChatUnansweredSuggestionState(
        visible = visible,
        actionEnabled = visible,
        periodReference = period.reference,
    )
}

internal fun firstChatUnansweredPeriodReference(
    chat: Chat?,
    currentUserId: String,
    confirmedMessages: List<ChatMessage>,
): FirstChatUnansweredPeriodReference? {
    if (chat?.chatType != ChatType.FirstChat) return null
    val parsedMessages = confirmedMessages
        .filter { it.chatSessionId == chat.id }
        .map { message ->
            val sentAtMillis = backendInstantOrNull(message.sentAt)?.toEpochMilli() ?: return null
            ParsedChatMessage(message = message, sentAtMillis = sentAtMillis)
        }
    val latestPartnerMessage = parsedMessages
        .filter { it.message.senderId != currentUserId }
        .maxWithOrNull(compareBy<ParsedChatMessage> { it.sentAtMillis }.thenBy { it.message.id })
    val firstUnansweredOwnMessage = parsedMessages
        .filter { it.message.senderId == currentUserId }
        .filter { ownMessage ->
            latestPartnerMessage == null || ownMessage.isAfter(latestPartnerMessage)
        }
        .minWithOrNull(compareBy<ParsedChatMessage> { it.sentAtMillis }.thenBy { it.message.id })
        ?: return null
    return FirstChatUnansweredPeriodReference(
        reference = "own:${firstUnansweredOwnMessage.message.id}",
        referenceTimeEpochMillis = firstUnansweredOwnMessage.sentAtMillis,
        referenceMessageId = firstUnansweredOwnMessage.message.id,
        thresholdMillis = if (latestPartnerMessage == null) {
            INITIAL_FIRST_CHAT_UNANSWERED_MILLIS
        } else {
            ONGOING_FIRST_CHAT_UNANSWERED_MILLIS
        },
    )
}

internal data class FirstChatUnansweredPeriodReference(
    val reference: String,
    val referenceTimeEpochMillis: Long,
    val referenceMessageId: String?,
    val thresholdMillis: Long,
)

private data class ParsedChatMessage(
    val message: ChatMessage,
    val sentAtMillis: Long,
)

private fun ParsedChatMessage.isAfter(other: ParsedChatMessage): Boolean =
    sentAtMillis > other.sentAtMillis ||
        (sentAtMillis == other.sentAtMillis && message.id > other.message.id)
