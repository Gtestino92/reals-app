package com.reals.app.ui.root

import com.reals.app.domain.model.ChatMessage

internal fun List<ChatMessage>.lastMessageCursor(): String? =
    sortedWith(
        compareBy<ChatMessage> { it.sentAt }
            .thenBy { it.id }
    ).lastOrNull()?.id

internal fun List<ChatMessage>.appendUnique(newMessages: List<ChatMessage>): List<ChatMessage> {
    val mergedById = LinkedHashMap<String, ChatMessage>()
    forEach { message ->
        mergedById[message.id] = message
    }
    newMessages.forEach { message ->
        mergedById[message.id] = message
    }
    return mergedById.values.sortedWith(
        compareBy<ChatMessage> { it.sentAt }
            .thenBy { it.id }
    )
}
