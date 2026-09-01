package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.AuthFailureReason
import com.reals.app.core.network.isTerminalAuthFailure
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.network.isAccountDeleted
import com.reals.app.core.network.toUserMessage
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.domain.model.ChatReplyTarget
import com.reals.app.domain.model.ChatStatus
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.failureError
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.Response
import java.io.File

class ChatRepositoryTest {
    private val api = FakeRealsApi()
    private val tokenProvider = FakeAuthTokenProvider()
    private val repository = ChatRepository(api, testJson, tokenProvider, testApiExecutor())

    @Test
    fun `getChat maps api success and sends authorization header`() = runBlocking {
        val chat = repository.getChat("chat-1").successValue()

        assertEquals("getChat", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals("chat-1", api.lastPathId)
        assertEquals(ChatStatus.Active, chat.status)
    }

    @Test
    fun `getMessages handles legacy array response`() = runBlocking {
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesArrayPayload(
                listOf(TestDtos.chatMessage("message-1"), TestDtos.chatMessage("message-2")),
            )
        )

        val messages = repository.getMessages("chat-1").successValue()

        assertEquals(listOf("message-1", "message-2"), messages.map { it.id })
        assertEquals(500, api.lastChatMessagesLimit)
    }

    @Test
    fun `getMessages handles paged response`() = runBlocking {
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesPagedPayload(listOf(TestDtos.chatMessage("message-3")))
        )

        val messages = repository.getMessages("chat-1", afterMessageId = "message-2").successValue()

        assertEquals(listOf("message-3"), messages.map { it.id })
        assertEquals(500, api.lastChatMessagesLimit)
    }

    @Test
    fun `getMessages handles mixed text and audio array response without content null crash`() = runBlocking {
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesArrayPayload(
                listOf(TestDtos.chatMessage("message-1"), TestDtos.audioChatMessage("message-2")),
            )
        )

        val messages = repository.getMessages("chat-1").successValue()

        assertEquals(listOf("message-1", "message-2"), messages.map { it.id })
        assertEquals(null, messages[1].content)
        assertEquals("https://example.test/audio", messages[1].audio?.url)
    }

    @Test
    fun `getMessages handles mixed text and audio paged response`() = runBlocking {
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesPagedPayload(
                listOf(TestDtos.chatMessage("message-1"), TestDtos.audioChatMessage("message-2")),
            )
        )

        val messages = repository.getMessages("chat-1").successValue()

        assertEquals(listOf("message-1", "message-2"), messages.map { it.id })
        assertEquals("audio/mp4", messages[1].audio?.contentType)
    }

    @Test
    fun `structurally invalid messages array returns failure`() = runBlocking {
        api.chatMessagesResponse = Response.success(testJson.parseToJsonElement("""[{"id":4}]"""))

        val error = repository.getMessages("chat-1").failureError()

        assertTrue(error is ApiError.Unexpected)
    }

    @Test
    fun `structurally invalid paged response returns failure`() = runBlocking {
        api.chatMessagesResponse = Response.success(testJson.parseToJsonElement("""{"messages":4}"""))

        val error = repository.getMessages("chat-1").failureError()

        assertTrue(error is ApiError.Unexpected)
    }

    @Test
    fun `sendMessage sends content as expected`() = runBlocking {
        val clientMessageId = "00000000-0000-0000-0000-000000000201"
        val message = repository.sendMessage("chat-1", "  hola  ", clientMessageId).successValue()

        assertEquals("sendChatMessage", api.calls.single())
        assertEquals("chat-1", api.lastPathId)
        assertEquals(clientMessageId, api.chatMessageBody?.clientMessageId)
        assertEquals("  hola  ", api.chatMessageBody?.content)
        assertEquals(null, api.chatMessageBody?.replyTo)
        assertEquals("message-1", message.id)
    }

    @Test
    fun `sendMessage sends reply target without preview metadata`() = runBlocking {
        val clientMessageId = "00000000-0000-0000-0000-000000000202"

        repository.sendMessage(
            chatId = "chat-1",
            content = "respuesta",
            clientMessageId = clientMessageId,
            replyTo = ChatReplyTarget.GuidanceQuestion("00000000-0000-0000-0000-000000000027"),
        ).successValue()

        assertEquals(clientMessageId, api.chatMessageBody?.clientMessageId)
        assertEquals("respuesta", api.chatMessageBody?.content)
        assertEquals("GUIDANCE_QUESTION", api.chatMessageBody?.replyTo?.type)
        assertEquals("00000000-0000-0000-0000-000000000027", api.chatMessageBody?.replyTo?.targetId)
    }

    @Test
    fun `sendAudioMessage sends multipart file and client id`() = runBlocking {
        val file = File.createTempFile("chat-audio-test", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val clientMessageId = "00000000-0000-0000-0000-000000000101"
        try {
            val message = repository.sendAudioMessage("chat-1", file, clientMessageId).successValue()

            assertEquals("sendChatAudioMessage", api.calls.single())
            assertEquals("chat-1", api.lastPathId)
            assertEquals("audio-message-1", message.id)
            val filePart = api.chatAudioFilePart
            assertEquals("audio/mp4", filePart?.body?.contentType().toString())
            assertTrue(filePart?.headers.toString().contains("name=\"file\""))
            assertTrue(filePart?.headers.toString().contains(".m4a"))
            val buffer = Buffer()
            api.chatAudioClientMessageIdPart?.writeTo(buffer)
            assertEquals(clientMessageId, buffer.readUtf8())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `putMessageReaction sends PUT path body and maps canonical response`() = runBlocking {
        api.chatMessageReactionResponse = Response.success(TestDtos.chatMessage("message-2", reactionType = "HEART"))

        val message = repository.putMessageReaction(
            chatId = "chat-1",
            messageId = "message-2",
            reactionType = ChatMessageReactionType.Heart,
        ).successValue()
        val apiMethod = com.reals.app.data.api.RealsApi::class.java.methods
            .single { it.name == "putChatMessageReaction" }

        assertEquals("putChatMessageReaction", api.calls.single())
        assertEquals("chat-1/message-2", api.lastPathId)
        assertEquals("HEART", api.chatMessageReactionBody?.type)
        assertEquals(ChatMessageReactionType.Heart, message.reactionType)
        assertEquals("api/chats/{chatId}/messages/{messageId}/reaction", apiMethod.getAnnotation(PUT::class.java)?.value)
        assertEquals(
            listOf("chatId", "messageId"),
            apiMethod.parameterAnnotations
                .flatMap { annotations -> annotations.filterIsInstance<Path>().map { it.value } },
        )
        assertTrue(apiMethod.parameterAnnotations.any { annotations -> annotations.any { it is Body } })
    }

    @Test
    fun `requestNextFirstChatGuidanceQuestion posts without body and maps response`() = runBlocking {
        api.firstChatGuidanceResponse = Response.success(
            TestDtos.firstChatGuidance(questionId = "Q028", questionText = "Pregunta siguiente")
        )

        val guidance = repository.requestNextFirstChatGuidanceQuestion("chat-1").successValue()

        assertEquals("requestNextFirstChatGuidanceQuestion", api.calls.single())
        assertEquals("chat-1", api.lastPathId)
        assertEquals("Q028", guidance.question.id)
        assertEquals("Pregunta siguiente", guidance.question.text)
    }

    @Test
    fun `sendMessage failure surfaces chat backend code`() = runBlocking {
        api.chatMessageResponse = backendErrorResponse(
            statusCode = 400,
            code = "CHAT_MESSAGE_INVALID",
            message = "raw backend message",
        )

        val error = repository.sendMessage(
            "chat-1",
            "",
            "00000000-0000-0000-0000-000000000203",
        ).failureError() as ApiError.Backend

        assertEquals("CHAT_MESSAGE_INVALID", error.code)
        assertEquals(BackendErrorCode.ChatMessageInvalid, error.backendErrorCode)
        assertEquals(
            "Revisá el mensaje. No puede estar vacío ni superar el límite permitido.",
            error.toUserMessage(ErrorContext.Chat),
        )
    }

    @Test
    fun `requestMutualExit sends reason and trimmed details`() = runBlocking {
        val request = repository.requestMutualExit(
            chatId = "chat-1",
            reason = ChatExitReason.NoLongerInterested,
            details = "  listo  ",
        ).successValue()

        assertEquals("requestChatExit", api.calls.single())
        assertEquals("NO_LONGER_INTERESTED", api.exitBody?.reason)
        assertEquals("listo", api.exitBody?.details)
        assertEquals(ChatExitRequestStatus.Pending, request.status)
    }

    @Test
    fun `requestMutualExit converts blank details to null`() = runBlocking {
        repository.requestMutualExit("chat-1", reason = null, details = "   ").successValue()

        assertEquals(null, api.exitBody?.reason)
        assertEquals(null, api.exitBody?.details)
    }

    @Test
    fun `exit request outcome actions map outcome`() = runBlocking {
        assertEquals(ChatStatus.Cancelled, repository.acceptExitRequest("chat-1", "exit-1").successValue().chat.status)
        assertEquals(ChatStatus.Cancelled, repository.rejectExitRequest("chat-1", "exit-1").successValue().chat.status)
        assertEquals(ChatStatus.Cancelled, repository.timeoutExitRequest("chat-1", "exit-1").successValue().chat.status)

        assertEquals(
            listOf("acceptChatExitRequest", "rejectChatExitRequest", "timeoutChatExitRequest"),
            api.calls,
        )
    }

    @Test
    fun `cancelChat sends ordinary exit body without safety block field`() = runBlocking {
        repository.cancelChat("chat-1", ChatExitReason.Other, "  cancel  ").successValue()

        assertEquals("cancelChat", api.calls.last())
        assertEquals("OTHER", api.exitBody?.reason)
        assertEquals("cancel", api.exitBody?.details)
        assertEquals(null, api.safetyCancellationBody)
    }

    @Test
    fun `safetyCancelChat sends block false and maps outcome`() = runBlocking {
        val outcome = repository.safetyCancelChat(
            chatId = "chat-1",
            reason = ChatExitReason.Harassment,
            details = "  safety  ",
            blockUser = false,
        ).successValue()

        assertEquals("safetyCancelChat", api.calls.last())
        assertEquals("HARASSMENT", api.safetyCancellationBody?.reason)
        assertEquals("safety", api.safetyCancellationBody?.details)
        assertEquals(false, api.safetyCancellationBody?.blockUser)
        assertEquals(false, outcome.penaltyApplied)
        assertEquals(null, outcome.penalizedUserId)
    }

    @Test
    fun `safetyCancelChat sends block true`() = runBlocking {
        repository.safetyCancelChat(
            chatId = "chat-1",
            reason = ChatExitReason.ChildSafetyConcern,
            details = "  bloquear  ",
            blockUser = true,
        ).successValue()

        assertEquals("safetyCancelChat", api.calls.single())
        assertEquals("CHILD_SAFETY_CONCERN", api.safetyCancellationBody?.reason)
        assertEquals("bloquear", api.safetyCancellationBody?.details)
        assertEquals(true, api.safetyCancellationBody?.blockUser)
        assertEquals(null, api.exitBody)
    }

    @Test
    fun `token missing maps to auth error before api call`() = runBlocking {
        tokenProvider.failMissingToken()

        val error = repository.getChat("chat-1").failureError()

        assertTrue(error is ApiError.Auth)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `invalid auth token refreshes token and retries once`() = runBlocking {
        api.chatResponseQueue = mutableListOf(
            backendErrorResponse(401, "INVALID_TOKEN"),
            Response.success(TestDtos.chat(status = "ACTIVE")),
        )

        val chat = repository.getChat("chat-1").successValue()

        assertEquals(ChatStatus.Active, chat.status)
        assertEquals(listOf(false, true), tokenProvider.calls)
        assertEquals(listOf("getChat", "getChat"), api.calls)
    }

    @Test
    fun `invalid app check token does not refresh Firebase auth token`() = runBlocking {
        api.chatResponseQueue = mutableListOf(backendErrorResponse(401, "INVALID_APP_CHECK_TOKEN"))

        val error = repository.getChat("chat-1").failureError()

        assertEquals(listOf(false), tokenProvider.calls)
        assertEquals(listOf("getChat"), api.calls)
        assertEquals(BackendErrorCode.InvalidAppCheckToken, (error as ApiError.Backend).backendErrorCode)
    }

    @Test
    fun `missing app check token does not refresh Firebase auth token`() = runBlocking {
        api.chatResponseQueue = mutableListOf(backendErrorResponse(401, "MISSING_APP_CHECK_TOKEN"))

        val error = repository.getChat("chat-1").failureError()

        assertEquals(listOf(false), tokenProvider.calls)
        assertEquals(listOf("getChat"), api.calls)
        assertEquals(BackendErrorCode.MissingAppCheckToken, (error as ApiError.Backend).backendErrorCode)
    }

    @Test
    fun `app check verification unavailable does not refresh Firebase auth token`() = runBlocking {
        api.chatResponseQueue = mutableListOf(backendErrorResponse(503, "APP_CHECK_VERIFICATION_UNAVAILABLE"))

        val error = repository.getChat("chat-1").failureError()

        assertEquals(listOf(false), tokenProvider.calls)
        assertEquals(listOf("getChat"), api.calls)
        assertEquals(BackendErrorCode.AppCheckVerificationUnavailable, (error as ApiError.Backend).backendErrorCode)
    }

    @Test
    fun `account deletion does not refresh Firebase auth token`() = runBlocking {
        api.chatResponseQueue = mutableListOf(backendErrorResponse(410, "ACCOUNT_DELETED"))

        val error = repository.getChat("chat-1").failureError()

        assertEquals(listOf(false), tokenProvider.calls)
        assertEquals(listOf("getChat"), api.calls)
        assertTrue(error.isAccountDeleted())
    }

    @Test
    fun `invalid Firebase user before request maps to terminal signed out auth failure`() = runBlocking {
        tokenProvider.failure = invalidFirebaseUser()

        val error = repository.getChat("chat-1").failureError()

        assertEquals(AuthFailureReason.NOT_SIGNED_IN, (error as ApiError.Auth).reason)
        assertTrue(error.isTerminalAuthFailure())
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `invalid Firebase user during forced refresh does not retry api request`() = runBlocking {
        api.chatResponseQueue = mutableListOf(backendErrorResponse(401, "INVALID_TOKEN"))
        tokenProvider.failWhen(forceRefresh = true, throwable = invalidFirebaseUser())

        val error = repository.getChat("chat-1").failureError()

        assertEquals(listOf(false, true), tokenProvider.calls)
        assertEquals(listOf("getChat"), api.calls)
        assertEquals(AuthFailureReason.NOT_SIGNED_IN, (error as ApiError.Auth).reason)
    }

    @Test
    fun `generic token failure remains recoverable and non terminal`() = runBlocking {
        tokenProvider.failure = IllegalStateException("temporary token failure")

        val error = repository.getChat("chat-1").failureError()

        assertEquals(AuthFailureReason.TOKEN_UNAVAILABLE, (error as ApiError.Auth).reason)
        assertEquals(false, error.isTerminalAuthFailure())
        assertTrue(api.calls.isEmpty())
    }

    private fun invalidFirebaseUser() = FirebaseAuthInvalidUserException(
        "ERROR_USER_DISABLED",
        "Firebase user is disabled",
    )
}
