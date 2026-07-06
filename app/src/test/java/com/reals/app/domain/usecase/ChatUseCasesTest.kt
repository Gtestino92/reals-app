package com.reals.app.domain.usecase

import com.reals.app.data.repository.ChatRepository
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUseCasesTest {
    private val api = FakeRealsApi()
    private val repository = ChatRepository(api, testJson, FakeAuthTokenProvider(), testApiExecutor())

    @Test
    fun `RequestMutualChatExitUseCase delegates reason and details`() = runBlocking {
        RequestMutualChatExitUseCase(repository)(
            chatId = "chat-1",
            reason = ChatExitReason.Other,
            details = "  detalle  ",
        ).successValue()

        assertEquals("requestChatExit", api.calls.single())
        assertEquals("OTHER", api.exitBody?.reason)
        assertEquals("detalle", api.exitBody?.details)
    }

    @Test
    fun `RequestNextFirstChatGuidanceQuestionUseCase delegates chat id`() = runBlocking {
        val guidance = RequestNextFirstChatGuidanceQuestionUseCase(repository)("chat-1").successValue()

        assertEquals("requestNextFirstChatGuidanceQuestion", api.calls.single())
        assertEquals("chat-1", api.lastPathId)
        assertEquals("Q027", guidance.question.id)
    }

    @Test
    fun `exit resolution use cases delegate ids`() = runBlocking {
        AcceptChatExitRequestUseCase(repository)("chat-1", "exit-1").successValue()
        RejectChatExitRequestUseCase(repository)("chat-1", "exit-2").successValue()
        TimeoutChatExitRequestUseCase(repository)("chat-1", "exit-3").successValue()

        assertEquals(
            listOf("acceptChatExitRequest", "rejectChatExitRequest", "timeoutChatExitRequest"),
            api.calls,
        )
        assertEquals("chat-1/exit-3", api.lastPathId)
    }

    @Test
    fun `CancelChatUseCase delegates reason and details`() = runBlocking {
        CancelChatUseCase(repository)("chat-1", ChatExitReason.NoLongerInterested, " no ").successValue()

        assertEquals("cancelChat", api.calls.single())
        assertEquals("NO_LONGER_INTERESTED", api.exitBody?.reason)
        assertEquals("no", api.exitBody?.details)
    }

    @Test
    fun `SafetyCancelChatUseCase delegates with reason and details`() = runBlocking {
        SafetyCancelChatUseCase(repository)("chat-1", ChatExitReason.Harassment, " acoso ").successValue()

        assertEquals("safetyCancelChat", api.calls.single())
        assertEquals("HARASSMENT", api.exitBody?.reason)
        assertEquals("acoso", api.exitBody?.details)
    }
}
