package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageActionHandlerTest {
    @Test
    fun `first chat send rejects blank content`() {
        val result = ChatMessageActionHandler.prepareFirstChatSend(
            current = firstChatState(),
            content = "   ",
        )

        assertTrue(result is ChatMessageSendPreparation.Rejected<*>)
        val state = (result as ChatMessageSendPreparation.Rejected<RealsRootUiState.FirstChat>).state
        val error = state.error as ApiError.Unexpected
        assertEquals("El mensaje no es válido.", error.message)
        assertEquals(null, state.message)
    }

    @Test
    fun `first chat send rejects html-like content`() {
        val result = ChatMessageActionHandler.prepareFirstChatSend(
            current = firstChatState(),
            content = "<b>hola</b>",
        )

        assertTrue(result is ChatMessageSendPreparation.Rejected<*>)
        assertTrue(
            (result as ChatMessageSendPreparation.Rejected<RealsRootUiState.FirstChat>)
                .state.error is ApiError.Unexpected
        )
    }

    @Test
    fun `first chat send accepted content returns pending optimistic state`() {
        val result = ChatMessageActionHandler.prepareFirstChatSend(
            current = firstChatState(
                error = ApiError.Unexpected("previous"),
                message = "previous",
            ),
            content = "  hola\t mundo  ",
        )

        assertTrue(result is ChatMessageSendPreparation.Accepted<*>)
        result as ChatMessageSendPreparation.Accepted<RealsRootUiState.FirstChat>
        assertEquals("hola mundo", result.cleanContent)
        assertTrue(result.localId.startsWith("local-"))
        assertTrue(result.pendingState.sending)
        assertEquals(null, result.pendingState.error)
        assertEquals(null, result.pendingState.message)
        assertEquals(1, result.pendingState.optimisticMessages.size)
        val optimistic = result.pendingState.optimisticMessages.single()
        assertEquals("chat-1", optimistic.chatId)
        assertEquals("user-1", optimistic.senderId)
        assertEquals("hola mundo", optimistic.content)
        assertEquals(OutgoingMessageDeliveryState.Sending, optimistic.deliveryState)
    }

    @Test
    fun `first chat send is ignored while mutual cancellation is pending`() {
        val result = ChatMessageActionHandler.prepareFirstChatSend(
            current = firstChatState(
                exitRequests = listOf(TestDtos.exitRequest(status = "PENDING").toDomain()),
            ),
            content = "hola",
        )

        assertEquals(ChatMessageSendPreparation.Ignored, result)
    }

    @Test
    fun `first chat retry keeps failed optimistic message while mutual cancellation is pending`() {
        val failed = failedOptimisticMessage(localId = "local-failed")
        val current = firstChatState(
            optimisticMessages = listOf(failed),
            exitRequests = listOf(TestDtos.exitRequest(status = "PENDING").toDomain()),
        )

        val state = ChatMessageActionHandler.retryFirstChat(current, "local-failed")

        assertEquals(listOf("local-failed"), state.optimisticMessages.map { it.localId })
    }

    @Test
    fun `first chat retry removes failed optimistic message`() {
        val failed = failedOptimisticMessage(localId = "local-failed")
        val kept = failedOptimisticMessage(localId = "local-kept")
        val current = firstChatState(optimisticMessages = listOf(failed, kept))

        val state = ChatMessageActionHandler.retryFirstChat(current, "local-failed")

        assertEquals(listOf("local-kept"), state.optimisticMessages.map { it.localId })
    }

    @Test
    fun `second chat send rejects blank content`() {
        val result = ChatMessageActionHandler.prepareSecondChatSend(
            current = secondChatState(),
            content = "\n\n",
        )

        assertTrue(result is ChatMessageSendPreparation.Rejected<*>)
        val state = (result as ChatMessageSendPreparation.Rejected<RealsRootUiState.SecondChat>).state
        val error = state.error as ApiError.Unexpected
        assertEquals("El mensaje no es válido.", error.message)
        assertEquals(null, state.message)
    }

    @Test
    fun `second chat send rejects html-like content`() {
        val result = ChatMessageActionHandler.prepareSecondChatSend(
            current = secondChatState(),
            content = "<script>alert(1)</script>",
        )

        assertTrue(result is ChatMessageSendPreparation.Rejected<*>)
        assertTrue(
            (result as ChatMessageSendPreparation.Rejected<RealsRootUiState.SecondChat>)
                .state.error is ApiError.Unexpected
        )
    }

    @Test
    fun `second chat send accepted content returns pending optimistic state`() {
        val result = ChatMessageActionHandler.prepareSecondChatSend(
            current = secondChatState(
                error = ApiError.Unexpected("previous"),
                message = "previous",
            ),
            content = "  segundo   chat  ",
        )

        assertTrue(result is ChatMessageSendPreparation.Accepted<*>)
        result as ChatMessageSendPreparation.Accepted<RealsRootUiState.SecondChat>
        assertEquals("segundo chat", result.cleanContent)
        assertTrue(result.localId.startsWith("local-"))
        assertTrue(result.pendingState.sending)
        assertEquals(null, result.pendingState.error)
        assertEquals(null, result.pendingState.message)
        assertEquals(1, result.pendingState.optimisticMessages.size)
        val optimistic = result.pendingState.optimisticMessages.single()
        assertEquals("chat-1", optimistic.chatId)
        assertEquals("user-1", optimistic.senderId)
        assertEquals("segundo chat", optimistic.content)
        assertEquals(OutgoingMessageDeliveryState.Sending, optimistic.deliveryState)
    }

    @Test
    fun `second chat send remains enabled while resolution request is pending`() {
        val result = ChatMessageActionHandler.prepareSecondChatSend(
            current = secondChatState(
                lifecycle = SecondChatLifecycleUiState(
                    status = TestDtos.secondChatStatus(
                        activeResolutionRequest = TestDtos.secondChatResolutionRequest(
                            type = "PARTNER_INACTIVITY",
                        ),
                    ).toDomain(),
                    statusReceivedAtMillis = System.currentTimeMillis(),
                )
            ),
            content = "respondo",
        )

        assertTrue(result is ChatMessageSendPreparation.Accepted<*>)
    }

    @Test
    fun `second chat retry removes failed optimistic message`() {
        val failed = failedOptimisticMessage(localId = "local-failed")
        val kept = failedOptimisticMessage(localId = "local-kept")
        val current = secondChatState(optimisticMessages = listOf(failed, kept))

        val state = ChatMessageActionHandler.retrySecondChat(current, "local-failed")

        assertEquals(listOf("local-kept"), state.optimisticMessages.map { it.localId })
    }

    private fun firstChatState(
        optimisticMessages: List<OptimisticOutgoingMessage> = emptyList(),
        exitRequests: List<ChatExitRequest> = emptyList(),
        error: ApiError? = null,
        message: String? = null,
    ): RealsRootUiState.FirstChat = RealsRootUiState.FirstChat(
        session = TestDomain.session(),
        matchId = "match-1",
        chatId = "chat-1",
        chat = TestDtos.chat().toDomain(),
        optimisticMessages = optimisticMessages,
        exitRequests = exitRequests,
        error = error,
        message = message,
    )

    private fun secondChatState(
        optimisticMessages: List<OptimisticOutgoingMessage> = emptyList(),
        lifecycle: SecondChatLifecycleUiState = SecondChatLifecycleUiState(),
        error: ApiError? = null,
        message: String? = null,
    ): RealsRootUiState.SecondChat = RealsRootUiState.SecondChat(
        session = TestDomain.session(),
        connectionId = "connection-1",
        matchId = "match-1",
        chatId = "chat-1",
        chat = TestDtos.chat().copy(chatType = "SECOND_CHAT").toDomain(),
        optimisticMessages = optimisticMessages,
        lifecycle = lifecycle,
        error = error,
        message = message,
    )

    private fun failedOptimisticMessage(localId: String): OptimisticOutgoingMessage =
        newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = localId,
            createdAtMillis = 123L,
        ).copy(deliveryState = OutgoingMessageDeliveryState.Failed)
}
