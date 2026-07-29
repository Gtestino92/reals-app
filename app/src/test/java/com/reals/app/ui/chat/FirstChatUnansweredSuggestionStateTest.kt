package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ChatType
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.root.OptimisticOutgoingMessage
import com.reals.app.ui.root.OutgoingMessageDeliveryState
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstChatUnansweredSuggestionStateTest {
    @Test
    fun `no messages hides suggestion`() {
        val state = suggestionState(confirmedMessages = emptyList())

        assertFalse(state.visible)
        assertFalse(state.actionEnabled)
    }

    @Test
    fun `latest partner message hides suggestion`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = THRESHOLD_START)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `latest own message below threshold hides suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:02:59Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `latest own message at threshold shows suggestion`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertTrue(state.visible)
        assertTrue(state.actionEnabled)
    }

    @Test
    fun `latest own message above threshold shows suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:03:01Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertTrue(state.visible)
        assertTrue(state.actionEnabled)
    }

    @Test
    fun `newer partner reply hides suggestion`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "partner-1", senderId = PARTNER_USER_ID, sentAt = "2026-06-18T21:02:00Z"),
            ),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `newer own message restarts threshold`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "own-1", sentAt = "2026-06-18T21:00:00Z"),
                message(id = "own-2", sentAt = "2026-06-18T21:02:00Z"),
            ),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `pending exit request hides suggestion`() {
        val state = suggestionState(
            pendingExitRequest = TestDtos.exitRequest().toDomain(),
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `non-active first chat hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(status = ChatStatus.Cancelled),
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `expired first chat hides suggestion`() {
        val state = suggestionState(
            nowMillis = millis("2026-06-18T21:05:00Z"),
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `second chat hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(chatType = ChatType.SecondChat),
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `non-pending user decision hides suggestion`() {
        val state = suggestionState(
            chat = activeFirstChat().copy(myDecision = ChatDecisionState.Approved),
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `optimistic sending message suppresses suggestion`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
            optimisticMessages = listOf(optimisticMessage(OutgoingMessageDeliveryState.Sending)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `failed optimistic message suppresses suggestion while retryable`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
            optimisticMessages = listOf(optimisticMessage(OutgoingMessageDeliveryState.Failed)),
        )

        assertFalse(state.visible)
    }

    @Test
    fun `action loading hides CTA`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
            mutualExitActionAvailable = false,
        )

        assertFalse(state.visible)
        assertFalse(state.actionEnabled)
    }

    @Test
    fun `message send in progress hides suggestion`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
            messageSendInFlight = true,
        )

        assertFalse(state.visible)
        assertFalse(state.actionEnabled)
    }

    @Test
    fun `audio interaction in progress hides CTA`() {
        val state = suggestionState(
            confirmedMessages = listOf(message(id = "own-1", sentAt = THRESHOLD_START)),
            mutualExitActionAvailable = false,
        )

        assertFalse(state.visible)
        assertFalse(state.actionEnabled)
    }

    @Test
    fun `latest audio message uses same confirmed timestamp threshold`() {
        val state = suggestionState(
            confirmedMessages = listOf(audioMessage(id = "audio-1", sentAt = THRESHOLD_START)),
        )

        assertTrue(state.visible)
    }

    @Test
    fun `sentAt then id selects latest confirmed message deterministically`() {
        val state = suggestionState(
            confirmedMessages = listOf(
                message(id = "b", sentAt = THRESHOLD_START),
                message(id = "z", senderId = PARTNER_USER_ID, sentAt = THRESHOLD_START),
            ),
        )

        assertFalse(state.visible)
    }

    private fun suggestionState(
        chat: Chat = activeFirstChat(),
        confirmedMessages: List<ChatMessage>,
        optimisticMessages: List<OptimisticOutgoingMessage> = emptyList(),
        pendingExitRequest: ChatExitRequest? = null,
        nowMillis: Long = THRESHOLD_MILLIS,
        mutualExitActionAvailable: Boolean = true,
        messageSendInFlight: Boolean = false,
    ): FirstChatUnansweredSuggestionState =
        firstChatUnansweredSuggestionState(
            chat = chat,
            currentUserId = CURRENT_USER_ID,
            confirmedMessages = confirmedMessages,
            optimisticMessages = optimisticMessages,
            pendingExitRequest = pendingExitRequest,
            nowMillis = nowMillis,
            mutualExitActionAvailable = mutualExitActionAvailable,
            messageSendInFlight = messageSendInFlight,
        )

    private fun activeFirstChat(): Chat = TestDtos.chat().toDomain()

    private fun message(
        id: String,
        senderId: String = CURRENT_USER_ID,
        sentAt: String,
    ): ChatMessage =
        TestDtos.chatMessage(id = id)
            .copy(senderId = senderId, sentAt = sentAt)
            .toDomain()

    private fun audioMessage(
        id: String,
        senderId: String = CURRENT_USER_ID,
        sentAt: String,
    ): ChatMessage =
        TestDtos.audioChatMessage(id = id)
            .copy(senderId = senderId, sentAt = sentAt)
            .toDomain()

    private fun optimisticMessage(
        deliveryState: OutgoingMessageDeliveryState,
    ): OptimisticOutgoingMessage =
        OptimisticOutgoingMessage(
            localId = "local-1",
            chatId = "chat-1",
            senderId = CURRENT_USER_ID,
            content = "mensaje local",
            createdAtMillis = millis("2026-06-18T21:02:30Z"),
            deliveryState = deliveryState,
        )

    private fun millis(value: String): Long = Instant.parse(value).toEpochMilli()

    private companion object {
        const val CURRENT_USER_ID = "user-1"
        const val PARTNER_USER_ID = "user-2"
        const val THRESHOLD_START = "2026-06-18T21:00:00Z"
        val THRESHOLD_MILLIS: Long = Instant.parse("2026-06-18T21:03:00Z").toEpochMilli()
    }
}
