package com.reals.app.ui.root

import com.reals.app.domain.model.ChatMessage

private val chatMessageOrder = compareBy<ChatMessage> { it.sentAt }
    .thenBy { it.id }

internal fun List<ChatMessage>.lastMessageCursor(): String? =
    sortedWith(chatMessageOrder).lastOrNull()?.id

internal fun List<ChatMessage>.reactionReconciliationCursor(): String? {
    val ordered = sortedWith(chatMessageOrder)
    if (ordered.isEmpty()) return null

    var runsSeen = 1
    var oldestIncludedIndex = ordered.lastIndex
    var currentSenderId = ordered.last().senderId
    for (index in ordered.lastIndex - 1 downTo 0) {
        val message = ordered[index]
        if (message.senderId != currentSenderId) {
            runsSeen += 1
            currentSenderId = message.senderId
            if (runsSeen > 2) {
                return ordered[index].id
            }
        }
        oldestIncludedIndex = index
    }
    return ordered.getOrNull(oldestIncludedIndex - 1)?.id
}

internal fun List<ChatMessage>.appendUnique(newMessages: List<ChatMessage>): List<ChatMessage> {
    val mergedById = LinkedHashMap<String, ChatMessage>()
    forEach { message ->
        mergedById[message.id] = message
    }
    newMessages.forEach { message ->
        mergedById[message.id] = mergedById[message.id]?.mergeWithIncoming(message) ?: message
    }
    return mergedById.values.sortedWith(chatMessageOrder)
}

private fun ChatMessage.mergeWithIncoming(incoming: ChatMessage): ChatMessage =
    incoming.copy(reactionType = incoming.reactionType ?: reactionType)

internal class SilentChatMessagePollingCursor {
    private var chatId: String? = null
    private var nextReconcilesReactions: Boolean = false

    fun reset(chatId: String? = null) {
        this.chatId = chatId
        nextReconcilesReactions = false
    }

    fun nextCursor(chatId: String, messages: List<ChatMessage>): String? {
        if (this.chatId != chatId) {
            this.chatId = chatId
            nextReconcilesReactions = false
        }

        val reconcileReactions = nextReconcilesReactions
        nextReconcilesReactions = !nextReconcilesReactions
        return if (reconcileReactions) {
            messages.reactionReconciliationCursor()
        } else {
            messages.lastMessageCursor()
        }
    }
}
