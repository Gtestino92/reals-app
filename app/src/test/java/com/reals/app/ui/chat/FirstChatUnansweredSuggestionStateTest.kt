package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.testutil.TestDtos
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstChatUnansweredSuggestionStateTest {
    @Test
    fun `counterpart never wrote uses chat start as reference`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertTrue(state.visible)
        assertEquals("started:2026-06-18T21:00:00Z", state.periodReference)
    }

    @Test
    fun `no own confirmed message after reference hides suggestion`() {
        val state = suggestionState(confirmedMessages = emptyList())

        assertFalse(state.visible)
    }

    @Test
    fun `first own confirmed message after partner keeps threshold at partner reference`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-1", sentAt = "2026-06-18T21:02:30Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("partner:partner-1", state.periodReference)
    }

    @Test
    fun `several own messages do not reset threshold`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-1", sentAt = "2026-06-18T21:01:00Z"),
                message(id = "own-2", sentAt = "2026-06-18T21:02:59Z"),
            ),
        )

        assertTrue(state.visible)
    }

    @Test
    fun `new own message does not hide eligible suggestion`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:01:00Z"),
                message(id = "own-2", sentAt = "2026-06-18T21:03:01Z"),
            ),
        )

        assertTrue(state.visible)
    }

    @Test
    fun `latest counterpart message creates new period`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:04:00Z"),
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-1", sentAt = "2026-06-18T21:01:00Z"),
                message(id = "partner-2", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:01:00Z"),
                message(id = "own-2", sentAt = "2026-06-18T21:02:00Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("partner:partner-2", state.periodReference)
    }

    @Test
    fun `new counterpart message immediately hides old suggestion`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:01:00Z"),
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:03:00Z"),
            ),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `own message before latest counterpart does not satisfy participation`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:06:00Z"),
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:01:00Z"),
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:02:00Z"),
            ),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `own message after latest counterpart satisfies participation`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:04:00Z"),
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:01:00Z"),
                message(id = "own-1", sentAt = "2026-06-18T21:01:01Z"),
            ),
        )

        assertTrue(state.visible)
    }

    @Test
    fun `exact three minute boundary is visible`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:03:00Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertTrue(state.visible)
        assertTrue(state.actionEnabled)
    }

    @Test
    fun `one millisecond below threshold is hidden`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:02:59.999Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `dismissed current period is hidden`() {
        val state = suggestionState(
            dismissedPeriodReference = "started:2026-06-18T21:00:00Z",
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `own messages do not invalidate dismissal`() {
        val state = suggestionState(
            dismissedPeriodReference = "started:2026-06-18T21:00:00Z",
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:01:00Z"),
                message(id = "own-2", sentAt = "2026-06-18T21:04:00Z"),
            ),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `new counterpart period does not match old dismissal`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:04:00Z"),
            dismissedPeriodReference = "started:2026-06-18T21:00:00Z",
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:01:00Z"),
                message(id = "own-1", sentAt = "2026-06-18T21:02:00Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("partner:partner-1", state.periodReference)
    }

    @Test
    fun `optimistic and failed messages are ignored by derivation`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertTrue(state.visible)
    }

    @Test
    fun `pending exit request hides suggestion`() {
        val state = suggestionState(
            pendingExitRequest = TestDtos.exitRequest().toDomain(),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `non-active first chat hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(status = ChatStatus.Cancelled),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `expired first chat hides suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:06:00Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `non-pending own decision hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(myDecision = ChatDecisionState.Approved),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `decision-only hides suggestion even when unanswered timing is eligible`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(
                myDecision = ChatDecisionState.Pending,
                partnerDecision = ChatDecisionState.Approved,
            ),
            nowMillis = millis("2026-06-18T21:04:00Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
            mutualExitActionAvailable = true,
        )

        assertFalse(state.visible)
        assertFalse(state.actionEnabled)
    }

    @Test
    fun `second chat hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(chatType = ChatType.SecondChat),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `messages from another chat are ignored`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "own-other", chatId = "other-chat", sentAt = "2026-06-18T21:01:00Z"),
            ),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `malformed timestamps fail closed`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = "not-a-date")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `equal timestamps use message id tie breaker`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "z-partner", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:00Z"),
                message(id = "a-own", sentAt = "2026-06-18T21:00:00Z"),
            ),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `missing server estimate hides suggestion`() {
        val state = suggestionState(
            nowMillis = null,
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:01:00Z")),
        )

        assertFalse(state.visible)
    }

    private fun suggestionState(
        chat: Chat = activeFirstChat(),
        confirmedMessages: List<ChatMessage>,
        pendingExitRequest: ChatExitRequest? = null,
        nowMillis: Long? = millis("2026-06-18T21:03:00Z"),
        dismissedPeriodReference: String? = null,
        mutualExitActionAvailable: Boolean = true,
    ): FirstChatUnansweredSuggestionState =
        firstChatUnansweredSuggestionState(
            chat = chat,
            currentUserId = CURRENT_USER_ID,
            confirmedMessages = confirmedMessages,
            pendingExitRequest = pendingExitRequest,
            estimatedServerNowMillis = nowMillis,
            dismissedPeriodReference = dismissedPeriodReference,
            mutualExitActionAvailable = mutualExitActionAvailable,
        )

    private fun activeFirstChat(): Chat = TestDtos.chat().toDomain()

    private fun message(
        id: String,
        senderId: String = CURRENT_USER_ID,
        chatId: String = "chat-1",
        sentAt: String,
    ): ChatMessage =
        TestDtos.chatMessage(id = id)
            .copy(chatSessionId = chatId, senderId = senderId, sentAt = sentAt)
            .toDomain()

    private fun millis(value: String): Long = Instant.parse(value).toEpochMilli()

    private companion object {
        const val CURRENT_USER_ID = "user-1"
        const val PARTNER_USER_ID = "user-2"
    }
}
