package com.reals.app.ui.root

import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
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
    val incomingById = newMessages.associateBy { it.id }
    return (
        map { message -> incomingById[message.id] ?: message } +
            newMessages.filterNot { incoming -> any { it.id == incoming.id } }
        )
        .sortedWith(
            compareBy<ChatMessage> { it.sentAt }
                .thenBy { it.id }
        )
}

internal fun List<ChatExitRequest>.latestExitRequest(): ChatExitRequest? =
    maxByOrNull { it.createdAt }

internal fun ChatExitRequestStatus?.isResolvedExitStatus(): Boolean =
    this == ChatExitRequestStatus.Accepted ||
        this == ChatExitRequestStatus.Rejected ||
        this == ChatExitRequestStatus.TimedOut

internal fun ChatExitRequest.resolvedHomeMessage(currentUserId: String): String = when (status) {
    ChatExitRequestStatus.Accepted -> when (type) {
        ChatExitRequestType.MutualCancel ->
            if (responderUserId == currentUserId) {
                "Aceptaste la salida consensuada."
            } else {
                "La otra persona aceptó la salida consensuada."
            }

        else -> "El chat fue cerrado."
    }

    ChatExitRequestStatus.Rejected -> when (type) {
        ChatExitRequestType.MutualCancel ->
            if (responderUserId == currentUserId) {
                "Rechazaste la salida consensuada."
            } else {
                "La otra persona rechazó la salida consensuada."
            }

        else -> "El chat fue cerrado."
    }

    ChatExitRequestStatus.TimedOut -> "La solicitud de salida venció."
    else -> "El chat fue cerrado."
}

internal fun firstChatDecisionMessage(state: MatchState): String = when (state) {
    MatchState.ChatActive -> "Guardamos tu decisión. Esperamos la respuesta de la otra persona."
    MatchState.VisualPhase -> "Ambas personas aprobaron. La revisión visual ya está pendiente."
    MatchState.ChatRejected -> "El chat fue rechazado. Actualizamos tu Home."
    MatchState.Expired -> "El chat venci\u00f3."
    MatchState.VisualApproved -> "La revisión ya fue aprobada. Actualizamos tu Home."
    MatchState.VisualRejected -> "La revisión visual quedó cerrada. Actualizamos tu Home."
    is MatchState.Unknown -> "Guardamos tu decisión. Actualizamos tu Home."
}

internal fun firstChatExitMessage(state: MatchState?): String = when (state) {
    MatchState.VisualPhase -> "El chat pasó a revisión visual. Actualizamos tu lista."
    MatchState.ChatRejected -> "El chat fue rechazado. Actualizamos tu Home."
    MatchState.Expired -> "El chat venci\u00f3."
    MatchState.VisualApproved -> "La revisión ya fue aprobada. Actualizamos tu Home."
    MatchState.VisualRejected -> "La revisión visual quedó cerrada. Actualizamos tu Home."
    MatchState.ChatActive,
    null,
    is MatchState.Unknown -> "El chat cambió de estado. Actualizamos tu Home."
}
