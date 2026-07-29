package com.reals.app.ui.chat

import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType

private const val FIRST_CHAT_UNANSWERED_EXIT_SUGGESTION_MILLIS = 3 * 60 * 1000L

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
    if (!hasConfirmedOwnMessageAfterPeriod(chat, currentUserId, confirmedMessages, period)) {
        return hiddenFirstChatUnansweredSuggestionState
    }
    val visible = nowMillis >= period.referenceTimeEpochMillis + FIRST_CHAT_UNANSWERED_EXIT_SUGGESTION_MILLIS
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
    return if (latestPartnerMessage != null) {
        FirstChatUnansweredPeriodReference(
            reference = "partner:${latestPartnerMessage.message.id}",
            referenceTimeEpochMillis = latestPartnerMessage.sentAtMillis,
            referenceMessageId = latestPartnerMessage.message.id,
        )
    } else {
        val startedAtMillis = backendInstantOrNull(chat.startedAt)?.toEpochMilli() ?: return null
        FirstChatUnansweredPeriodReference(
            reference = "started:${chat.startedAt}",
            referenceTimeEpochMillis = startedAtMillis,
            referenceMessageId = null,
        )
    }
}

internal data class FirstChatUnansweredPeriodReference(
    val reference: String,
    val referenceTimeEpochMillis: Long,
    val referenceMessageId: String?,
)

private data class ParsedChatMessage(
    val message: ChatMessage,
    val sentAtMillis: Long,
)

private fun hasConfirmedOwnMessageAfterPeriod(
    chat: Chat,
    currentUserId: String,
    confirmedMessages: List<ChatMessage>,
    period: FirstChatUnansweredPeriodReference,
): Boolean =
    confirmedMessages
        .filter { it.chatSessionId == chat.id && it.senderId == currentUserId }
        .any { message ->
            val sentAtMillis = backendInstantOrNull(message.sentAt)?.toEpochMilli()
            when {
                sentAtMillis == null -> false
                period.referenceMessageId == null -> sentAtMillis >= period.referenceTimeEpochMillis
                else -> sentAtMillis > period.referenceTimeEpochMillis ||
                    (sentAtMillis == period.referenceTimeEpochMillis && message.id > period.referenceMessageId)
            }
        }
