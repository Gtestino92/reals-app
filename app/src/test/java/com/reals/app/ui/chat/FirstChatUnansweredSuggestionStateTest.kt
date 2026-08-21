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
    fun `partner has never replied at one millisecond before initial threshold hides suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:00:29.999Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
        assertFalse(state.actionEnabled)
        assertEquals("own:own-1", state.periodReference)
    }

    @Test
    fun `partner has never replied at initial threshold shows suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:00:30Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertTrue(state.visible)
        assertTrue(state.actionEnabled)
        assertEquals("own:own-1", state.periodReference)
    }

    @Test
    fun `initial own follow-up does not reset threshold`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:00:30Z"),
            confirmedMessages = listOf(
                message(id = "own-a", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-b", sentAt = "2026-06-18T21:00:10Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("own:own-a", state.periodReference)
    }

    @Test
    fun `no own confirmed message hides suggestion even after initial threshold`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:04:00Z"),
            confirmedMessages = emptyList(),
        )

        assertFalse(state.visible)
        assertEquals(null, state.periodReference)
    }

    @Test
    fun `partner reply before initial threshold resolves initial period`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:00:30Z"),
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:20Z"),
            ),
        )

        assertFalse(state.visible)
        assertEquals(null, state.periodReference)
    }

    @Test
    fun `partner has participated before and new own message under ongoing threshold hides suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:02:59.999Z"),
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-1", sentAt = "2026-06-18T21:00:00.001Z"),
            ),
        )

        assertFalse(state.visible)
        assertEquals("own:own-1", state.periodReference)
    }

    @Test
    fun `same ongoing period at exactly three minutes shows suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:03:00.001Z"),
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-1", sentAt = "2026-06-18T21:00:00.001Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("own:own-1", state.periodReference)
    }

    @Test
    fun `ongoing own follow-ups do not reset threshold`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:03:00.001Z"),
            confirmedMessages = listOf(
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-a", sentAt = "2026-06-18T21:00:00.001Z"),
                message(id = "own-b", sentAt = "2026-06-18T21:00:40Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("own:own-a", state.periodReference)
    }

    @Test
    fun `partner reply hides old unanswered period`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:04:00Z"),
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:20Z"),
            ),
        )

        assertFalse(state.visible)
        assertEquals(null, state.periodReference)
    }

    @Test
    fun `later own message after partner response creates new ongoing period`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:03:20.001Z"),
            confirmedMessages = listOf(
                message(id = "own-initial", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:20Z"),
                message(id = "own-later", sentAt = "2026-06-18T21:00:20.001Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("own:own-later", state.periodReference)
    }

    @Test
    fun `dismissed period does not reshow during same unanswered period`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:04:00Z"),
            dismissedPeriodReference = "own:own-a",
            confirmedMessages = listOf(
                message(id = "own-a", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-b", sentAt = "2026-06-18T21:03:30Z"),
            ),
        )

        assertFalse(state.visible)
        assertEquals(null, state.periodReference)
    }

    @Test
    fun `partner response and new unanswered period is not suppressed by previous dismissal`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:04:00Z"),
            dismissedPeriodReference = "own:own-initial",
            confirmedMessages = listOf(
                message(id = "own-initial", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:00:20Z"),
                message(id = "own-later", sentAt = "2026-06-18T21:00:30Z"),
            ),
        )

        assertTrue(state.visible)
        assertEquals("own:own-later", state.periodReference)
    }

    @Test
    fun `pending exit request hides suggestion`() {
        val state = suggestionState(
            pendingExitRequest = TestDtos.exitRequest().toDomain(),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `decision-only hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(
                myDecision = ChatDecisionState.Pending,
                partnerDecision = ChatDecisionState.Approved,
            ),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
        assertFalse(state.actionEnabled)
    }

    @Test
    fun `expired first chat hides suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:06:00Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `non-active first chat hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(status = ChatStatus.Cancelled),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `second chat hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(chatType = ChatType.SecondChat),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `messages from another chat are ignored`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "own-other", chatId = "other-chat", sentAt = "2026-06-18T21:00:00Z"),
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
    fun `equal timestamps use message id tie breaker for ongoing periods`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:03:00Z"),
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
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `non-pending own decision hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(myDecision = ChatDecisionState.Approved),
            confirmedMessages = listOf(message(id = "own-1", sentAt = "2026-06-18T21:00:00Z")),
        )

        assertFalse(state.visible)
    }

    private fun suggestionState(
        chat: Chat = activeFirstChat(),
        confirmedMessages: List<ChatMessage>,
        pendingExitRequest: ChatExitRequest? = null,
        nowMillis: Long? = millis("2026-06-18T21:04:00Z"),
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
