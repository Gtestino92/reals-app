package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.domain.model.ChatMessageType
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import java.io.File
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
    fun `first chat send is ignored during decision-only`() {
        val result = ChatMessageActionHandler.prepareFirstChatSend(
            current = firstChatState().copy(
                chat = TestDtos.chat(myDecision = "PENDING", partnerDecision = "APPROVED").toDomain(),
            ),
            content = "hola",
        )

        assertEquals(ChatMessageSendPreparation.Ignored, result)
    }

    @Test
    fun `first chat send is ignored while send operation is in progress`() {
        val result = ChatMessageActionHandler.prepareFirstChatSend(
            current = firstChatState(sending = true),
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
    fun `first chat retry keeps failed optimistic message during decision-only`() {
        val failed = failedOptimisticMessage(localId = "local-failed")
        val current = firstChatState(optimisticMessages = listOf(failed)).copy(
            chat = TestDtos.chat(myDecision = "PENDING", partnerDecision = "APPROVED").toDomain(),
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

    @Test
    fun `first chat reaction accepted adds pending id without sending or action loading`() {
        val result = ChatMessageActionHandler.prepareFirstChatReaction(
            current = firstChatState(
                messages = listOf(chatMessage("incoming", senderId = "other")),
                error = ApiError.Unexpected("previous error"),
                message = "previous message",
            ),
            messageId = "incoming",
        )

        assertTrue(result is ChatReactionPreparation.Accepted<*>)
        result as ChatReactionPreparation.Accepted<RealsRootUiState.FirstChat>
        assertEquals(setOf("incoming"), result.pendingState.reaction.pendingMessageIds)
        assertEquals(false, result.pendingState.sending)
        assertEquals(false, result.pendingState.actionLoading)
        assertEquals("previous error", (result.pendingState.error as ApiError.Unexpected).message)
        assertEquals("previous message", result.pendingState.message)
        assertEquals("chat-1", result.chatId)
    }

    @Test
    fun `first chat reaction accepts while guidance action is loading`() {
        val result = ChatMessageActionHandler.prepareFirstChatReaction(
            current = firstChatState(
                messages = listOf(chatMessage("incoming", senderId = "other")),
                guidanceActionLoading = true,
            ),
            messageId = "incoming",
        )

        assertTrue(result is ChatReactionPreparation.Accepted<*>)
    }

    @Test
    fun `first chat reaction ignores non active first chat`() {
        val current = firstChatState(
            messages = listOf(chatMessage("incoming", senderId = "other")),
        ).copy(chat = activeFirstChatDto(status = "EXPIRED").toDomain())

        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "incoming"))
    }

    @Test
    fun `first chat reaction ignores non first chat`() {
        val current = firstChatState(
            messages = listOf(chatMessage("incoming", senderId = "other")),
        ).copy(chat = activeFirstChatDto(status = "ACTIVE").copy(chatType = "SECOND_CHAT").toDomain())

        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "incoming"))
    }

    @Test
    fun `second chat reaction accepted adds pending id without sending or action loading`() {
        val result = ChatMessageActionHandler.prepareSecondChatReaction(
            current = secondChatState(messages = listOf(chatMessage("incoming", senderId = "other"))),
            messageId = "incoming",
        )

        assertTrue(result is ChatReactionPreparation.Accepted<*>)
        result as ChatReactionPreparation.Accepted<RealsRootUiState.SecondChat>
        assertEquals(setOf("incoming"), result.pendingState.reaction.pendingMessageIds)
        assertEquals(false, result.pendingState.sending)
        assertEquals(false, result.pendingState.actionLoading)
    }

    @Test
    fun `reaction ignores duplicate pending id but allows different ids`() {
        val current = firstChatState(
            messages = listOf(
                chatMessage("a", senderId = "other"),
                chatMessage("b", "2026-06-18T21:01:00Z", senderId = "other"),
            ),
        ).copy(reaction = ChatReactionUiState(pendingMessageIds = setOf("a")))

        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "a"))
        val second = ChatMessageActionHandler.prepareFirstChatReaction(current, "b")

        assertTrue(second is ChatReactionPreparation.Accepted<*>)
        second as ChatReactionPreparation.Accepted<RealsRootUiState.FirstChat>
        assertEquals(setOf("a", "b"), second.pendingState.reaction.pendingMessageIds)
    }

    @Test
    fun `reaction ignores own old reacted unknown and unsupported messages`() {
        val current = firstChatState(
            messages = listOf(
                chatMessage("old", senderId = "other"),
                chatMessage("own", "2026-06-18T21:01:00Z", senderId = "user-1"),
                chatMessage("reacted", "2026-06-18T21:02:00Z", senderId = "other")
                    .copy(reactionType = ChatMessageReactionType.Heart),
                chatMessage("unknown", "2026-06-18T21:03:00Z", senderId = "other")
                    .copy(reactionType = ChatMessageReactionType.Unknown("FIRE")),
                chatMessage("unsupported", "2026-06-18T21:04:00Z", senderId = "other")
                    .copy(messageType = ChatMessageType.Unknown("STICKER")),
            ),
        )

        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "old"))
        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "own"))
        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "reacted"))
        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "unknown"))
        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "unsupported"))
    }

    @Test
    fun `first chat reaction ignores decision only and pending mutual exit`() {
        val current = firstChatState(messages = listOf(chatMessage("incoming", senderId = "other")))
        val decisionOnly = current.copy(
            chat = TestDtos.chat(myDecision = "PENDING", partnerDecision = "APPROVED").toDomain(),
        )
        val exitPending = current.copy(exitRequests = listOf(TestDtos.exitRequest(status = "PENDING").toDomain()))

        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(decisionOnly, "incoming"))
        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(exitPending, "incoming"))
    }

    @Test
    fun `first chat reaction ignores local expiry`() {
        val current = firstChatState(
            messages = listOf(chatMessage("incoming", senderId = "other")),
        ).copy(
            chat = TestDtos.chat()
                .copy(expiresAt = "2020-06-18T21:00:00Z", inactivityExpiresAt = null)
                .toDomain(),
        )

        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareFirstChatReaction(current, "incoming"))
    }

    @Test
    fun `second chat reaction ignores inactive lifecycle`() {
        val current = secondChatState(
            messages = listOf(chatMessage("incoming", senderId = "other")),
            lifecycle = SecondChatLifecycleUiState(
                status = TestDtos.secondChatStatus(chatStatus = "FINISHED").toDomain(),
                statusReceivedAtMillis = System.currentTimeMillis(),
            ),
        )

        assertEquals(ChatReactionPreparation.Ignored, ChatMessageActionHandler.prepareSecondChatReaction(current, "incoming"))
    }

    @Test
    fun `audio send rejects subsecond draft before upload`() {
        val file = createTempFile()
        val state = firstChatState().copy(
            chat = activeFirstChatDto(audioPolicy = TestDtos.audioPolicy(enabled = true)).toDomain(),
            audioDraft = ChatAudioDraftUiState(
                filePath = file.absolutePath,
                clientMessageId = "client-1",
                durationMillis = 838,
                sizeBytes = file.length(),
            ),
        )

        val result = ChatMessageActionHandler.prepareFirstChatAudioSend(
            current = state,
            filePath = file.absolutePath,
            clientMessageId = "client-1",
        )

        assertTrue(result is ChatAudioSendPreparation.Rejected<*>)
        val error = (result as ChatAudioSendPreparation.Rejected<RealsRootUiState.FirstChat>)
            .state.audioUpload.error as ApiError.Unexpected
        assertEquals("La grabación quedó demasiado corta. Intentá nuevamente.", error.message)
    }

    @Test
    fun `audio send keeps same file and UUID when accepted`() {
        val file = createTempFile()
        val state = firstChatState().copy(
            chat = activeFirstChatDto(audioPolicy = TestDtos.audioPolicy(enabled = true)).toDomain(),
            audioDraft = ChatAudioDraftUiState(
                filePath = file.absolutePath,
                clientMessageId = "client-1",
                durationMillis = 1_000,
                sizeBytes = file.length(),
            ),
        )

        val result = ChatMessageActionHandler.prepareFirstChatAudioSend(
            current = state,
            filePath = file.absolutePath,
            clientMessageId = "client-1",
        )

        assertTrue(result is ChatAudioSendPreparation.Accepted<*>)
        result as ChatAudioSendPreparation.Accepted<RealsRootUiState.FirstChat>
        assertEquals(file.absolutePath, result.file.absolutePath)
        assertEquals("client-1", result.clientMessageId)
        assertTrue(result.pendingState.audioUpload.uploading)
        val optimistic = result.pendingState.optimisticMessages.single()
        assertEquals("client-1", optimistic.localId)
        assertEquals("chat-1", optimistic.chatId)
        assertEquals("user-1", optimistic.senderId)
        assertEquals(OptimisticOutgoingMessageType.Audio, optimistic.messageType)
        assertEquals(1_000L, optimistic.audioDurationMillis)
        assertEquals(OutgoingMessageDeliveryState.Sending, optimistic.deliveryState)
    }

    @Test
    fun `first chat audio send is ignored during decision-only`() {
        val file = createTempFile()
        val state = firstChatState().copy(
            chat = TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "APPROVED",
                audioPolicy = TestDtos.audioPolicy(enabled = true),
            ).toDomain(),
            audioDraft = ChatAudioDraftUiState(
                filePath = file.absolutePath,
                clientMessageId = "client-1",
                durationMillis = 1_000,
                sizeBytes = file.length(),
            ),
        )

        val result = ChatMessageActionHandler.prepareFirstChatAudioSend(
            current = state,
            filePath = file.absolutePath,
            clientMessageId = "client-1",
        )

        assertEquals(ChatAudioSendPreparation.Ignored, result)
    }

    @Test
    fun `second chat audio send uses status policy when chat policy is absent`() {
        val file = createTempFile()
        val state = secondChatState(
            lifecycle = SecondChatLifecycleUiState(
                status = TestDtos.secondChatStatus(
                    audioPolicy = TestDtos.audioPolicy(enabled = true),
                ).toDomain(),
                statusReceivedAtMillis = System.currentTimeMillis(),
            ),
        ).copy(
            audioDraft = ChatAudioDraftUiState(
                filePath = file.absolutePath,
                clientMessageId = "client-1",
                durationMillis = 1_000,
                sizeBytes = file.length(),
            ),
        )

        val result = ChatMessageActionHandler.prepareSecondChatAudioSend(
            current = state,
            filePath = file.absolutePath,
            clientMessageId = "client-1",
        )

        assertTrue(result is ChatAudioSendPreparation.Accepted<*>)
    }

    private fun firstChatState(
        optimisticMessages: List<OptimisticOutgoingMessage> = emptyList(),
        exitRequests: List<ChatExitRequest> = emptyList(),
        messages: List<ChatMessage> = emptyList(),
        sending: Boolean = false,
        guidanceActionLoading: Boolean = false,
        error: ApiError? = null,
        message: String? = null,
    ): RealsRootUiState.FirstChat = RealsRootUiState.FirstChat(
        session = TestDomain.session(),
        matchId = "match-1",
        chatId = "chat-1",
        chat = activeFirstChatDto().toDomain(),
        messages = messages,
        optimisticMessages = optimisticMessages,
        exitRequests = exitRequests,
        sending = sending,
        guidanceActionLoading = guidanceActionLoading,
        error = error,
        message = message,
    )

    private fun activeFirstChatDto(
        status: String = "ACTIVE",
        audioPolicy: com.reals.app.data.dto.ChatAudioPolicyResponseDto? = null,
    ): com.reals.app.data.dto.ChatResponseDto = TestDtos.chat(
        status = status,
        audioPolicy = audioPolicy,
    ).copy(
        expiresAt = "2099-06-20T21:00:00Z",
        inactivityExpiresAt = "2099-06-18T21:05:00Z",
    )

    private fun secondChatState(
        optimisticMessages: List<OptimisticOutgoingMessage> = emptyList(),
        messages: List<ChatMessage> = emptyList(),
        lifecycle: SecondChatLifecycleUiState = SecondChatLifecycleUiState(),
        error: ApiError? = null,
        message: String? = null,
    ): RealsRootUiState.SecondChat = RealsRootUiState.SecondChat(
        session = TestDomain.session(),
        connectionId = "connection-1",
        matchId = "match-1",
        chatId = "chat-1",
        chat = TestDtos.chat().copy(chatType = "SECOND_CHAT").toDomain(),
        messages = messages,
        optimisticMessages = optimisticMessages,
        lifecycle = lifecycle.takeUnless { it.status == null } ?: SecondChatLifecycleUiState(
            status = activeSecondChatStatus().toDomain(),
            statusReceivedAtMillis = System.currentTimeMillis(),
        ),
        error = error,
        message = message,
    )

    private fun activeSecondChatStatus(): com.reals.app.data.dto.SecondChatAttendanceResponseDto =
        TestDtos.secondChatStatus(
            scheduledAt = "2099-06-18T21:00:00Z",
            entryClosesAt = "2099-06-18T21:20:00Z",
            absoluteExpiresAt = "2099-06-18T23:00:00Z",
            serverTime = "2099-06-18T21:00:00Z",
            mutualCompletionEligibleAt = "2099-06-18T21:10:00Z",
            inactivityClaimableAt = "2099-06-18T21:05:00Z",
            inactivityClosesAt = "2099-06-18T21:10:00Z",
        )

    private fun chatMessage(
        id: String,
        sentAt: String = "2026-06-18T21:00:00Z",
        senderId: String,
    ): ChatMessage = ChatMessage(
        id = id,
        chatSessionId = "chat-1",
        senderId = senderId,
        content = "hola",
        sentAt = sentAt,
    )

    private fun failedOptimisticMessage(localId: String): OptimisticOutgoingMessage =
        newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = localId,
            createdAtMillis = 123L,
        ).copy(deliveryState = OutgoingMessageDeliveryState.Failed)

    private fun createTempFile(): File =
        kotlin.io.path.createTempFile(suffix = ".m4a").toFile().apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
}
