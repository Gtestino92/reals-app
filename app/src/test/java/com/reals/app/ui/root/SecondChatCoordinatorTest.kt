package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.ChatRepository
import com.reals.app.di.SecondChatFeatureDependencies
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.CreateSecondChatCompletionRequestUseCase
import com.reals.app.domain.usecase.CreateSecondChatInactivityClaimUseCase
import com.reals.app.domain.usecase.CreateSecondChatNoShowClaimUseCase
import com.reals.app.domain.usecase.DecideSecondChatCompletionRequestUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetChatUseCase
import com.reals.app.domain.usecase.GetSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.GetSecondChatStatusUseCase
import com.reals.app.domain.usecase.JoinSecondChatUseCase
import com.reals.app.domain.usecase.PutChatMessageReactionUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SecondChatCoordinatorTest {
    private val api = FakeRealsApi()

    @Test
    fun `second chat safety cancel rejects invalid details without backend call`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val current = secondChatState()

        val result = secondCoordinator.safetyCancel(
            current = current,
            reason = ChatExitReason.InappropriateBehavior,
            details = "<script>alert(1)</script>",
            blockUser = false,
            onPending = {},
        )

        assertTrue(result is SecondChatActionResult.Show)
        val state = (result as SecondChatActionResult.Show).state
        assertTrue(state.error is ApiError.Unexpected)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `second chat safety cancel report-only passes false and avoids permanent block copy`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val current = secondChatState()

        val result = secondCoordinator.safetyCancel(
            current = current,
            reason = ChatExitReason.ChildSafetyConcern,
            details = "detalle válido",
            blockUser = false,
            onPending = {},
        )

        assertTrue(result is SecondChatActionResult.ReturnHome)
        result as SecondChatActionResult.ReturnHome
        assertEquals(
            "Reporte enviado. Cerramos esta conversación por seguridad y será revisado.",
            result.message,
        )
        assertEquals(listOf("safetyCancelChat"), api.calls)
        assertEquals("CHILD_SAFETY_CONCERN", api.safetyCancellationBody?.reason)
        assertEquals(false, api.safetyCancellationBody?.blockUser)
        assertFalse(result.message.orEmpty().contains("No volverán a ser emparejados"))
    }

    @Test
    fun `second chat safety cancel report and block passes true and explains permanent block`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val current = secondChatState()

        val result = secondCoordinator.safetyCancel(
            current = current,
            reason = ChatExitReason.ChildSafetyConcern,
            details = "detalle válido",
            blockUser = true,
            onPending = {},
        )

        assertTrue(result is SecondChatActionResult.ReturnHome)
        result as SecondChatActionResult.ReturnHome
        assertEquals(
            "Reporte enviado. Cerramos esta conversación y bloqueamos a esta persona. No volverán a ser emparejados.",
            result.message,
        )
        assertEquals(listOf("safetyCancelChat"), api.calls)
        assertEquals("CHILD_SAFETY_CONCERN", api.safetyCancellationBody?.reason)
        assertEquals(true, api.safetyCancellationBody?.blockUser)
    }

    @Test
    fun `second chat audio lifecycle failures refresh authoritative status`() = runBlocking {
        val codes = listOf(
            "CHAT_NOT_AVAILABLE",
            "SECOND_CHAT_EXPIRED",
            "SECOND_CHAT_ALREADY_RESOLVED",
            "SECOND_CHAT_CONVERSATION_ALREADY_RESOLVED",
            "SECOND_CHAT_JOIN_REQUIRED",
            "CHAT_AUDIO_WAITING_FOR_BOTH",
            "CHAT_AUDIO_NOT_AVAILABLE_YET",
            "CHAT_AUDIO_FEATURE_DISABLED",
        )

        codes.forEach { code ->
            val api = FakeRealsApi().apply {
                chatAudioMessageResponse = backendErrorResponse(statusCode = 409, code = code)
                secondChatStatusResponse = Response.success(
                    TestDtos.secondChatStatus(chatStatus = "EXPIRED", readOnlyUntil = "2026-06-18T21:10:00Z")
                )
                chatResponse = Response.success(
                    TestDtos.chat(status = "EXPIRED").copy(chatType = "SECOND_CHAT")
                )
            }
            val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
            val file = tempAudioFile()

            secondCoordinator.sendAudioMessage(
                current = secondChatState().copy(
                    audioDraft = audioDraft(file),
                    audioUpload = ChatAudioUploadUiState(uploading = true),
                ),
                file = file,
                clientMessageId = "client-1",
            )

            assertTrue("Expected status refresh for $code", api.calls.contains("getSecondChatStatus"))
        }
    }

    @Test
    fun `second chat terminal audio failure clears unsendable draft`() = runBlocking {
        val api = FakeRealsApi().apply {
            chatAudioMessageResponse = backendErrorResponse(statusCode = 409, code = "SECOND_CHAT_EXPIRED")
            secondChatStatusResponse = Response.success(
                TestDtos.secondChatStatus(chatStatus = "EXPIRED", readOnlyUntil = "2026-06-18T21:10:00Z")
            )
            chatResponse = Response.success(
                TestDtos.chat(status = "EXPIRED").copy(chatType = "SECOND_CHAT")
            )
        }
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val file = tempAudioFile()

        val result = secondCoordinator.sendAudioMessage(
            current = secondChatState().copy(
                audioDraft = audioDraft(file),
                audioUpload = ChatAudioUploadUiState(uploading = true),
            ),
            file = file,
            clientMessageId = "client-1",
        )

        assertEquals(null, result.audioDraft)
        assertEquals(ChatAudioUploadUiState(), result.audioUpload)
        assertFalse(file.exists())
    }

    @Test
    fun `second chat text send deduplicates post result also returned by incremental refresh`() = runBlocking {
        val api = FakeRealsApi().apply {
            chatMessageResponse = Response.success(TestDtos.chatMessage(id = "message-1"))
            chatMessagesResponse = Response.success(
                TestDtos.chatMessagesArrayPayload(listOf(TestDtos.chatMessage(id = "message-1")))
            )
            chatResponse = Response.success(
                TestDtos.chat(status = "ACTIVE").copy(chatType = "SECOND_CHAT")
            )
        }
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-1",
            createdAtMillis = 123L,
        )

        val result = secondCoordinator.sendMessage(
            current = secondChatState().copy(
                optimisticMessages = listOf(optimistic),
                sending = true,
            ),
            cleanContent = "hola",
            localId = "local-1",
        )

        assertEquals(1, result.messages.count { it.id == "message-1" })
        assertTrue(result.optimisticMessages.isEmpty())
    }

    @Test
    fun `second chat audio send deduplicates post result also returned by incremental refresh`() = runBlocking {
        val api = FakeRealsApi().apply {
            chatAudioMessageResponse = Response.success(
                TestDtos.audioChatMessage(id = "audio-message-1", url = "https://sent.test/audio")
            )
            chatMessagesResponse = Response.success(
                TestDtos.chatMessagesArrayPayload(
                    listOf(TestDtos.audioChatMessage(id = "audio-message-1", url = "https://stale.test/audio"))
                )
            )
            chatResponse = Response.success(
                TestDtos.chat(status = "ACTIVE").copy(chatType = "SECOND_CHAT")
            )
        }
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val file = tempAudioFile()
        val optimistic = newOptimisticOutgoingAudioMessage(
            chatId = "chat-1",
            senderId = "user-1",
            clientMessageId = "client-1",
            durationMillis = 2_000L,
        )

        val result = secondCoordinator.sendAudioMessage(
            current = secondChatState().copy(
                audioDraft = audioDraft(file),
                audioUpload = ChatAudioUploadUiState(uploading = true),
                optimisticMessages = listOf(optimistic),
            ),
            file = file,
            clientMessageId = "client-1",
        )

        assertEquals(1, result.messages.count { it.id == "audio-message-1" })
        assertEquals("https://sent.test/audio", result.messages.single { it.id == "audio-message-1" }.audio?.url)
        assertTrue(result.optimisticMessages.isEmpty())
        assertEquals("client-1", result.audioUpload.completedClientMessageId)
    }

    @Test
    fun `silent refresh alternates incremental and reaction reconciliation cursors`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val current = secondChatState().copy(messages = pollingMessages())
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesPagedPayload(emptyList()))

        secondCoordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        assertEquals("d", api.lastChatMessagesAfter)

        secondCoordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        assertEquals("b", api.lastChatMessagesAfter)

        secondCoordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)
        assertEquals("d", api.lastChatMessagesAfter)
    }

    @Test
    fun `silent refresh alternation resets when second chat id changes`() = runBlocking {
        val secondCoordinator = SecondChatCoordinator(secondChatDependencies(api))
        val oldChat = secondChatState().chat?.copy(id = "chat-old")
        val current = secondChatState().copy(chatId = "chat-old", chat = oldChat, messages = pollingMessages())
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesPagedPayload(emptyList()))

        secondCoordinator.refresh(current, silent = true, useReactionReconciliationAlternation = true)

        assertEquals(null, api.lastChatMessagesAfter)
    }

    private fun secondChatState(): RealsRootUiState.SecondChat =
        RealsRootUiState.SecondChat(
            session = TestDomain.session(),
            connectionId = "connection-1",
            matchId = "match-1",
            chatId = "chat-1",
            chat = TestDtos.chat(status = "ACTIVE").copy(chatType = "SECOND_CHAT").toDomain(),
        )

    private fun pollingMessages(): List<ChatMessage> =
        listOf(
            TestDtos.chatMessage("a").copy(senderId = "other", sentAt = "2026-06-18T21:00:00Z").toDomain(),
            TestDtos.chatMessage("b").copy(senderId = "me", sentAt = "2026-06-18T21:01:00Z").toDomain(),
            TestDtos.chatMessage("c").copy(senderId = "other", sentAt = "2026-06-18T21:02:00Z").toDomain(),
            TestDtos.chatMessage("d").copy(senderId = "me", sentAt = "2026-06-18T21:03:00Z").toDomain(),
        )

    private fun audioDraft(file: File): ChatAudioDraftUiState =
        ChatAudioDraftUiState(
            filePath = file.absolutePath,
            clientMessageId = "client-1",
            durationMillis = 2_000L,
            sizeBytes = file.length(),
        )

    private fun tempAudioFile(): File =
        kotlin.io.path.createTempFile(suffix = ".m4a").toFile().apply {
            writeBytes(byteArrayOf(1))
        }

    private fun secondChatDependencies(api: FakeRealsApi): SecondChatFeatureDependencies {
        val tokenProvider = FakeAuthTokenProvider()
        val chatRepository = ChatRepository(api, testJson, tokenProvider, testApiExecutor())
        return SecondChatFeatureDependencies(
            getStatus = GetSecondChatStatusUseCase(chatRepository),
            join = JoinSecondChatUseCase(chatRepository),
            createNoShowClaim = CreateSecondChatNoShowClaimUseCase(chatRepository),
            getChat = GetChatUseCase(chatRepository),
            getSecondChatForConnection = GetSecondChatForConnectionUseCase(chatRepository),
            getChatMessages = GetChatMessagesUseCase(chatRepository),
            sendChatMessage = SendChatMessageUseCase(chatRepository),
            sendChatAudioMessage = com.reals.app.domain.usecase.SendChatAudioMessageUseCase(chatRepository),
            putMessageReaction = PutChatMessageReactionUseCase(chatRepository),
            safetyCancelChat = SafetyCancelChatUseCase(chatRepository),
            createCompletionRequest = CreateSecondChatCompletionRequestUseCase(chatRepository),
            decideCompletionRequest = DecideSecondChatCompletionRequestUseCase(chatRepository),
            createInactivityClaim = CreateSecondChatInactivityClaimUseCase(chatRepository),
        )
    }
}
