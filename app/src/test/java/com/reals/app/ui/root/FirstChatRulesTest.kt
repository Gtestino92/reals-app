package com.reals.app.ui.root

import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.MatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstChatRulesTest {
    @Test
    fun `lastMessageCursor returns latest by sentAt then id`() {
        val messages = listOf(
            message("b", "2026-06-18T21:00:00Z"),
            message("a", "2026-06-18T21:00:00Z"),
            message("c", "2026-06-18T20:00:00Z"),
        )

        assertEquals("b", messages.lastMessageCursor())
    }

    @Test
    fun `appendUnique appends new messages and keeps sorted order`() {
        val current = listOf(message("1", "2026-06-18T21:00:00Z"))
        val appended = current.appendUnique(
            listOf(
                message("1", "2026-06-18T21:00:00Z"),
                message("2", "2026-06-18T20:00:00Z"),
            )
        )

        assertEquals(listOf("2", "1"), appended.map { it.id })
    }

    @Test
    fun `latestExitRequest returns newest request`() {
        val requests = listOf(
            exitRequest("old", "2026-06-18T20:00:00Z"),
            exitRequest("new", "2026-06-18T21:00:00Z"),
        )

        assertEquals("new", requests.latestExitRequest()?.id)
    }

    @Test
    fun `resolved exit status includes accepted rejected and timed out`() {
        assertTrue(ChatExitRequestStatus.Accepted.isResolvedExitStatus())
        assertTrue(ChatExitRequestStatus.Rejected.isResolvedExitStatus())
        assertTrue(ChatExitRequestStatus.TimedOut.isResolvedExitStatus())
        assertFalse(ChatExitRequestStatus.Pending.isResolvedExitStatus())
        assertFalse(null.isResolvedExitStatus())
    }

    @Test
    fun `first chat messages cover decision and exit states`() {
        assertEquals(
            "Guardamos tu decisión. Esperamos la respuesta de la otra persona.",
            firstChatDecisionMessage(MatchState.ChatActive),
        )
        assertEquals(
            "Ambas personas aprobaron. La revisión visual ya está pendiente.",
            firstChatDecisionMessage(MatchState.VisualPhase),
        )
        assertEquals(
            "El chat fue rechazado. Actualizamos tu Home.",
            firstChatExitMessage(MatchState.ChatRejected),
        )
        assertEquals(
            "El chat cambió de estado. Actualizamos tu Home.",
            firstChatExitMessage(null),
        )
        assertEquals("El chat venci\u00f3.", ChatStatus.Expired.firstChatClosedMessage())
        assertEquals(
            "La conversaci\u00f3n se cerr\u00f3 por inactividad.",
            ChatStatus.Abandoned.firstChatClosedMessage(),
        )
    }

    @Test
    fun `resolved mutual exit messages distinguish requester and responder`() {
        val accepted = exitRequest(status = ChatExitRequestStatus.Accepted)
        val rejected = exitRequest(status = ChatExitRequestStatus.Rejected)

        assertEquals("La otra persona aceptó la salida consensuada.", accepted.resolvedHomeMessage("user-1"))
        assertEquals("Aceptaste la salida consensuada.", accepted.resolvedHomeMessage("user-2"))
        assertEquals("La otra persona rechazó la salida consensuada.", rejected.resolvedHomeMessage("user-1"))
        assertEquals("Rechazaste la salida consensuada.", rejected.resolvedHomeMessage("user-2"))
        assertEquals(
            "La solicitud de salida venció.",
            exitRequest(status = ChatExitRequestStatus.TimedOut).resolvedHomeMessage("user-1"),
        )
    }

    @Test
    fun `open first chat status only accepts active`() {
        assertTrue(ChatStatus.Active.isOpenFirstChatStatus())
        assertFalse(ChatStatus.Closed.isOpenFirstChatStatus())
        assertFalse(ChatStatus.Cancelled.isOpenFirstChatStatus())
    }

    private fun message(id: String, sentAt: String) = ChatMessage(
        id = id,
        chatSessionId = "chat-1",
        senderId = "user-1",
        content = "hola",
        sentAt = sentAt,
    )

    private fun exitRequest(
        id: String = "exit-1",
        createdAt: String = "2026-06-18T21:00:00Z",
        status: ChatExitRequestStatus = ChatExitRequestStatus.Pending,
    ) = ChatExitRequest(
        id = id,
        chatId = "chat-1",
        requesterUserId = "user-1",
        responderUserId = "user-2",
        type = ChatExitRequestType.MutualCancel,
        status = status,
        reason = ChatExitReason.Other,
        details = null,
        createdAt = createdAt,
        resolvedAt = null,
    )
}
