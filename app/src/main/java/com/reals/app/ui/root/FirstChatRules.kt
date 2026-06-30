package com.reals.app.ui.root

import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.MatchState

internal fun ChatStatus.isOpenFirstChatStatus(): Boolean = this == ChatStatus.Active

internal fun ChatStatus.firstChatClosedMessage(): String? = when (this) {
    ChatStatus.Expired -> "El chat venci\u00f3."
    ChatStatus.Abandoned -> "La conversaci\u00f3n se cerr\u00f3 por inactividad."
    else -> null
}

internal fun List<ChatMessage>.lastMessageCursor(): String? =
    sortedWith(
        compareBy<ChatMessage> { it.sentAt }
            .thenBy { it.id }
    ).lastOrNull()?.id

internal fun List<ChatMessage>.appendUnique(newMessages: List<ChatMessage>): List<ChatMessage> {
    val seen = map { it.id }.toMutableSet()
    return (this + newMessages.filter { seen.add(it.id) }).sortedBy { it.sentAt }
}

internal fun List<ChatExitRequest>.latestExitRequest(): ChatExitRequest? =
    maxByOrNull { it.createdAt }

internal fun ChatExitRequestStatus?.isResolvedExitStatus(): Boolean =
    this == ChatExitRequestStatus.Accepted ||
        this == ChatExitRequestStatus.Rejected ||
        this == ChatExitRequestStatus.TimedOut

internal fun firstChatDecisionMessage(state: MatchState): String = when (state) {
    MatchState.ChatActive -> "Guardamos tu decision. Esperamos la respuesta de la otra persona."
    MatchState.VisualPhase -> "Ambas personas aprobaron. La revision visual ya esta pendiente."
    MatchState.ChatRejected -> "El chat fue rechazado. Actualizamos tu Home."
    MatchState.Expired -> "El chat venci\u00f3."
    MatchState.VisualApproved -> "La revision ya fue aprobada. Actualizamos tu Home."
    MatchState.VisualRejected -> "La revision visual quedo cerrada. Actualizamos tu Home."
    is MatchState.Unknown -> "Guardamos tu decision. Actualizamos tu Home."
}

internal fun firstChatExitMessage(state: MatchState?): String = when (state) {
    MatchState.VisualPhase -> "El chat paso a revision visual. Actualizamos tu lista."
    MatchState.ChatRejected -> "El chat fue rechazado. Actualizamos tu Home."
    MatchState.Expired -> "El chat venci\u00f3."
    MatchState.VisualApproved -> "La revision ya fue aprobada. Actualizamos tu Home."
    MatchState.VisualRejected -> "La revision visual quedo cerrada. Actualizamos tu Home."
    MatchState.ChatActive,
    null,
    is MatchState.Unknown -> "El chat cambio de estado. Actualizamos tu Home."
}
