package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

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
    }

    @Test
    fun `getMessages handles paged response`() = runBlocking {
        api.chatMessagesResponse = Response.success(
            TestDtos.chatMessagesPagedPayload(listOf(TestDtos.chatMessage("message-3")))
        )

        val messages = repository.getMessages("chat-1", afterMessageId = "message-2").successValue()

        assertEquals(listOf("message-3"), messages.map { it.id })
    }

    @Test
    fun `sendMessage sends content as expected`() = runBlocking {
        val message = repository.sendMessage("chat-1", "  hola  ").successValue()

        assertEquals("sendChatMessage", api.calls.single())
        assertEquals("chat-1", api.lastPathId)
        assertEquals("  hola  ", api.chatMessageBody?.content)
        assertEquals("message-1", message.id)
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
    fun `cancelChat and safetyCancelChat send bodies and map outcome`() = runBlocking {
        repository.cancelChat("chat-1", ChatExitReason.Other, "  cancel  ").successValue()
        assertEquals("cancelChat", api.calls.last())
        assertEquals("OTHER", api.exitBody?.reason)
        assertEquals("cancel", api.exitBody?.details)

        val outcome = repository.safetyCancelChat(
            chatId = "chat-1",
            reason = ChatExitReason.Harassment,
            details = "  safety  ",
        ).successValue()

        assertEquals("safetyCancelChat", api.calls.last())
        assertEquals("HARASSMENT", api.exitBody?.reason)
        assertEquals("safety", api.exitBody?.details)
        assertEquals(true, outcome.penaltyApplied)
    }

    @Test
    fun `token missing maps to auth error before api call`() = runBlocking {
        tokenProvider.failMissingToken()

        val error = repository.getChat("chat-1").failureError()

        assertTrue(error is ApiError.Auth)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `401 refreshes token and retries once`() = runBlocking {
        api.chatResponseQueue = mutableListOf(
            backendErrorResponse(401, "UNAUTHORIZED"),
            Response.success(TestDtos.chat(status = "ACTIVE")),
        )

        val chat = repository.getChat("chat-1").successValue()

        assertEquals(ChatStatus.Active, chat.status)
        assertEquals(listOf(false, true), tokenProvider.calls)
        assertEquals(listOf("getChat", "getChat"), api.calls)
    }
}
