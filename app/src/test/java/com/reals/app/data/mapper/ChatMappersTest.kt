package com.reals.app.data.mapper

import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
import com.reals.app.domain.model.ChatStatus
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMappersTest {
    @Test
    fun `ChatResponseDto maps chat core fields`() {
        val chat = TestDtos.chat(
            status = "ACTIVE",
            myDecision = "APPROVED",
            partnerDecision = "PENDING",
        ).toDomain()

        assertEquals("chat-1", chat.id)
        assertEquals("match-1", chat.matchId)
        assertEquals(ChatStatus.Active, chat.status)
        assertEquals("2026-06-19T21:00:00Z", chat.timeoutAt)
        assertEquals("2026-06-20T21:00:00Z", chat.expiresAt)
        assertEquals("2026-06-18T21:05:00Z", chat.inactivityExpiresAt)
        assertEquals("Taylor", chat.partner?.displayName)
        assertEquals(ChatDecisionState.Approved, chat.myDecision)
        assertEquals(ChatDecisionState.Pending, chat.partnerDecision)
    }

    @Test
    fun `ChatResponseDto uses timeoutAt when expiresAt missing`() {
        val chat = TestDtos.chat().copy(expiresAt = null).toDomain()

        assertEquals(chat.timeoutAt, chat.expiresAt)
    }

    @Test
    fun `ChatMessageResponseDto maps message`() {
        val message = TestDtos.chatMessage("message-2").toDomain()

        assertEquals("message-2", message.id)
        assertEquals("chat-1", message.chatSessionId)
        assertEquals("user-1", message.senderId)
        assertEquals("hola", message.content)
        assertEquals(TestDtos.now, message.sentAt)
    }

    @Test
    fun `ChatExitRequestResponseDto maps mutual cancel pending`() {
        val request = TestDtos.exitRequest(
            status = "PENDING",
            type = "MUTUAL_CANCEL",
            reason = "NO_LONGER_INTERESTED",
        ).toDomain()

        assertEquals("exit-1", request.id)
        assertEquals(ChatExitRequestType.MutualCancel, request.type)
        assertEquals(ChatExitRequestStatus.Pending, request.status)
        assertEquals(ChatExitReason.NoLongerInterested, request.reason)
        assertEquals("detalle", request.details)
        assertEquals("user-1", request.requesterUserId)
        assertEquals("user-2", request.responderUserId)
    }

    @Test
    fun `ChatExitRequestResponseDto maps TIMED_OUT`() {
        val request = TestDtos.exitRequest(status = "TIMED_OUT").toDomain()

        assertEquals(ChatExitRequestStatus.TimedOut, request.status)
    }

    @Test
    fun `ChatExitOutcomeResponseDto maps cancelled safety report without immediate penalty`() {
        val outcome = TestDtos.exitOutcome().toDomain()

        assertEquals(ChatStatus.Cancelled, outcome.chat.status)
        assertEquals(ChatExitRequestStatus.Accepted, outcome.exitRequest.status)
        assertEquals(false, outcome.penaltyApplied)
        assertEquals(null, outcome.penalizedUserId)
    }

    @Test
    fun `unknown statuses map safely`() {
        val chat = TestDtos.chat(status = "PAUSED").toDomain()
        val request = TestDtos.exitRequest(status = "WAITING", type = "CUSTOM").toDomain()

        assertTrue(chat.status is ChatStatus.Unknown)
        assertEquals("PAUSED", chat.status.rawValue)
        assertTrue(request.status is ChatExitRequestStatus.Unknown)
        assertEquals("WAITING", request.status.rawValue)
        assertTrue(request.type is ChatExitRequestType.Unknown)
        assertEquals("CUSTOM", request.type.rawValue)
    }
}
