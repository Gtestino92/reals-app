package com.reals.app.ui.chat

import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.ui.root.OptimisticOutgoingMessage

private const val FIRST_CHAT_UNANSWERED_EXIT_SUGGESTION_MILLIS = 3 * 60 * 1000L

internal data class FirstChatUnansweredSuggestionState(
    val visible: Boolean,
    val actionEnabled: Boolean,
)

private val hiddenFirstChatUnansweredSuggestionState =
    FirstChatUnansweredSuggestionState(visible = false, actionEnabled = false)

internal fun firstChatUnansweredSuggestionState(
    chat: Chat?,
    currentUserId: String,
    confirmedMessages: List<ChatMessage>,
    optimisticMessages: List<OptimisticOutgoingMessage>,
    pendingExitRequest: ChatExitRequest?,
    nowMillis: Long,
    mutualExitActionAvailable: Boolean,
    messageSendInFlight: Boolean,
): FirstChatUnansweredSuggestionState {
    if (chat?.chatType != ChatType.FirstChat) return hiddenFirstChatUnansweredSuggestionState
    if (chat.status != ChatStatus.Active) return hiddenFirstChatUnansweredSuggestionState
    if (chat.myDecision != ChatDecisionState.Pending) return hiddenFirstChatUnansweredSuggestionState
    if (pendingExitRequest != null) return hiddenFirstChatUnansweredSuggestionState
    if (firstChatLifecycleUiState(chat, nowMillis)?.expired == true) {
        return hiddenFirstChatUnansweredSuggestionState
    }
    if (!mutualExitActionAvailable || messageSendInFlight) {
        return hiddenFirstChatUnansweredSuggestionState
    }
    if (optimisticMessages.any { it.chatId == chat.id }) {
        return hiddenFirstChatUnansweredSuggestionState
    }

    val latestConfirmedMessage = confirmedMessages
        .asSequence()
        .filter { it.chatSessionId == chat.id }
        .sortedWith(
            compareBy<ChatMessage> { it.sentAt }
                .thenBy { it.id }
        )
        .lastOrNull()
        ?: return hiddenFirstChatUnansweredSuggestionState

    if (latestConfirmedMessage.senderId != currentUserId) {
        return hiddenFirstChatUnansweredSuggestionState
    }

    val latestOwnSentAtMillis = backendInstantOrNull(latestConfirmedMessage.sentAt)
        ?.toEpochMilli()
        ?: return hiddenFirstChatUnansweredSuggestionState
    val visible = nowMillis >= latestOwnSentAtMillis + FIRST_CHAT_UNANSWERED_EXIT_SUGGESTION_MILLIS
    return FirstChatUnansweredSuggestionState(
        visible = visible,
        actionEnabled = visible,
    )
}
