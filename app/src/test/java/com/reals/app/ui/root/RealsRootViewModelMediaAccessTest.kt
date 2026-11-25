package com.reals.app.ui.root

import com.reals.app.domain.model.ChatMessageType
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.toOptimisticReply
import com.reals.app.domain.model.ChatReplyDraft
import com.reals.app.domain.model.CounterpartAudioAccess
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelMediaAccessTest : ChatHistoryViewModelFixture() {
    @Test
    fun `both chat renewals retire media before failed decoding and refuse stale or omitted sources`() = runTest(dispatcher) {
        for (first in listOf(true, false)) {
            val api = FakeRealsApi()
            val vm = viewModel(api)
            val partner = TestDtos.audioChatMessage("partner", senderId = "other")
            val own = TestDtos.audioChatMessage("own")
            val text = TestDtos.chatMessage("text", replyTo = TestDtos.messageReplyTo(targetId = "partner", messageType = "AUDIO"))
            vm.setState(if (first) firstChatState(emptyList()) else secondChatState(emptyList()))
            suspend fun renew() = if (first) vm.refreshFirstChatAudioUrl("partner") else vm.refreshSecondChatAudioUrl("partner")
            fun rows() = when (val state = vm.uiState.value) {
                is RealsRootUiState.FirstChat -> state.messages
                is RealsRootUiState.SecondChat -> state.messages
                else -> error("History owner lost")
            }
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner, own, text))
            assertEquals(partner.audio?.url, renew())
            api.chatMessagesResponse = chatMessagesPayload(emptyList())
            assertEquals(null, renew())
            assertTrue(rows().single { it.id == "partner" }.audio != null)

            api.chatHistoryHeaders = Headers.Builder().build()
            renew()
            assertTrue(rows().single { it.id == "partner" }.withdrawn)
            api.chatHistoryHeaders = accessHeaders("ALLOWED")
            renew()
            assertEquals(null, rows().single { it.id == "partner" }.audio)
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
            assertEquals(partner.audio?.url, renew())

            api.chatHistoryHeaders = accessHeaders("RESTRICTED")
            api.chatMessagesResponse = Response.success(testJson.parseToJsonElement("false"))
            assertEquals(null, renew())
            val retired = rows().single { it.id == "partner" }
            assertTrue(retired.withdrawn)
            assertEquals(null, retired.audio)
            assertEquals(null, retired.clientMessageId)
            assertEquals(own.audio?.url, rows().single { it.id == "own" }.audio?.url)
            assertEquals(text.content, rows().single { it.id == "text" }.content)
            assertEquals("partner", rows().single { it.id == "text" }.replyTo?.targetId)
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
            api.chatHistoryHeaders = accessHeaders("ALLOWED")
            assertEquals(null, renew())
            assertTrue(rows().single { it.id == "partner" }.withdrawn)
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner.copy(senderId = "user-1")))
            assertEquals(null, renew())
            assertTrue(rows().single { it.id == "partner" }.withdrawn)
        }
    }

    @Test
    fun `same identifiers on reentry reject a pending renewal and its restriction`() = runTest(dispatcher) {
        for (first in listOf(true, false)) {
            val api = FakeRealsApi()
            val gate = CompletableDeferred<Unit>()
            api.beforeGetChatMessagesResponse = { gate.await() }
            api.chatHistoryHeaders = accessHeaders("RESTRICTED")
            val vm = viewModel(api)
            fun entry() = if (first) firstChatState(emptyList()) else secondChatState(emptyList())
            vm.setState(entry())
            val job = launch {
                assertEquals(null, if (first) vm.refreshFirstChatAudioUrl("a") else vm.refreshSecondChatAudioUrl("a"))
            }
            runCurrent()
            val replacement = entry()
            vm.setState(replacement)
            gate.complete(Unit)
            job.join()
            assertEquals(replacement, vm.uiState.value)
            assertEquals(CounterpartAudioAccess.UNKNOWN, replacement.chatHistoryOwner?.access)
        }
    }

    @Test
    fun `both chats reject stale HEART responses and new replies after a history conflict`() = runTest(dispatcher) {
        for (first in listOf(true, false)) {
            val gate = CompletableDeferred<Unit>()
            val api = FakeRealsApi()
            val partner = TestDtos.audioChatMessage("a", senderId = "other")
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
            api.beforePutChatMessageReactionResponse = { gate.await() }
            api.chatMessageReactionResponse = Response.success(partner.copy(reactionType = "HEART"))
            val vm = viewModel(api)
            vm.setState(if (first) firstChatState(emptyList()) else secondChatState(emptyList()))
            suspend fun renew() = if (first) vm.refreshFirstChatAudioUrl("a") else vm.refreshSecondChatAudioUrl("a")
            assertEquals(partner.audio?.url, renew())
            assertTrue(if (first) vm.reactToFirstChatMessage("a") else vm.reactToSecondChatMessage("a"))
            runCurrent()
            api.chatMessagesResponse = backendErrorResponse(409, "USER_PAIR_BLOCKED")
            assertEquals(null, renew())
            val reply = ChatReplyDraft.Message(
                "a", "other", ChatMessageType.Audio, null,
            )
            assertFalse(if (first) vm.sendFirstChatMessage("draft", reply) else vm.sendSecondChatMessage("draft", reply))
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
            gate.complete(Unit)
            advanceUntilIdle()
            val rows = when (val state = vm.uiState.value) {
                is RealsRootUiState.FirstChat -> state.messages
                is RealsRootUiState.SecondChat -> state.messages
                else -> error("Media-only conflict incorrectly closed chat")
            }
            assertTrue(rows.single { it.id == "a" }.withdrawn)
            assertEquals(null, rows.single { it.id == "a" }.reactionType)
            assertEquals(null, vm.remoteAudio(checkNotNull(vm.uiState.value.chatHistoryOwner), "a"))
        }
    }

    @Test
    fun `restricted incremental history retires audio before pending exits and survives their failure`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val api = FakeRealsApi()
        val vm = viewModel(api)
        vm.setState(firstChatState(emptyList()))
        api.chatMessagesResponse = chatMessagesPayload(listOf(TestDtos.audioChatMessage("a", senderId = "other")))
        assertTrue(vm.refreshFirstChatAudioUrl("a") != null)
        api.beforeGetChatExitRequestsResponse = { gate.await() }
        api.exitRequestsResponse = backendErrorResponse(500, "SERVER_ERROR")
        api.chatHistoryHeaders = accessHeaders("RESTRICTED")
        api.chatMessagesResponse = Response.success(TestDtos.chatMessagesPagedPayload(emptyList()))
        vm.refreshFirstChat()
        runCurrent()
        assertEquals("a", api.lastChatMessagesAfter)
        val pending = vm.uiState.value as RealsRootUiState.FirstChat
        assertTrue(pending.refreshing)
        assertTrue(pending.messages.single().withdrawn)
        gate.complete(Unit)
        advanceUntilIdle()
        val final = vm.uiState.value as RealsRootUiState.FirstChat
        assertTrue(final.messages.single().withdrawn)
        assertFalse(final.refreshing)
    }

    @Test
    fun `HEART conflict and reconciliation read conflict retire both chats without waiting for status refresh`() = runTest(dispatcher) {
        for (first in listOf(true, false)) for (duringRead in listOf(true, false)) {
            val api = FakeRealsApi()
            val partner = TestDtos.audioChatMessage("a", senderId = "other")
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
            val vm = viewModel(api)
            vm.setState(if (first) firstChatState(emptyList()) else secondChatState(emptyList()))
            val url = if (first) vm.refreshFirstChatAudioUrl("a") else vm.refreshSecondChatAudioUrl("a")
            assertEquals(partner.audio?.url, url)

            val gate = CompletableDeferred<Unit>()
            api.beforeGetFirstChatForMatchResponse = { gate.await() }
            api.beforeGetSecondChatStatusResponse = { gate.await() }
            api.chatMessageReactionResponse = backendErrorResponse(
                409, if (duringRead) "CHAT_MESSAGE_REACTION_NOT_AVAILABLE" else "USER_PAIR_BLOCKED",
            )
            api.chatMessagesResponse = backendErrorResponse(409, "USER_PAIR_BLOCKED")
            assertTrue(if (first) vm.reactToFirstChatMessage("a") else vm.reactToSecondChatMessage("a"))
            runCurrent()
            val owner = checkNotNull(vm.uiState.value.chatHistoryOwner)
            assertEquals(CounterpartAudioAccess.RESTRICTED, owner.access)
            assertEquals(null, vm.remoteAudio(owner, "a"))
            gate.complete(Unit)
            advanceUntilIdle()
        }
    }

    @Test
    fun `late successful send retains its acknowledgement but cannot reintroduce prior audio`() = runTest(dispatcher) {
        for (first in listOf(true, false)) {
            val api = FakeRealsApi()
            val partner = TestDtos.audioChatMessage("a", senderId = "other")
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
            val vm = viewModel(api)
            vm.setState(if (first) firstChatState(emptyList()) else secondChatState(emptyList()))
            suspend fun renew() = if (first) vm.refreshFirstChatAudioUrl("a") else vm.refreshSecondChatAudioUrl("a")
            assertEquals(partner.audio?.url, renew())
            val gate = CompletableDeferred<Unit>()
            api.beforeSendChatMessageResponse = { gate.await() }
            api.chatMessageResponse = Response.success(TestDtos.chatMessage("sent"))
            assertTrue(if (first) vm.sendFirstChatMessage("draft") else vm.sendSecondChatMessage("draft"))
            runCurrent()
            fun pending() = when (val state = vm.uiState.value) {
                is RealsRootUiState.FirstChat -> state.optimisticMessages
                is RealsRootUiState.SecondChat -> state.optimisticMessages
                else -> error("Missing chat")
            }
            val transaction = pending().single()
            api.chatHistoryHeaders = accessHeaders("RESTRICTED")
            api.chatMessagesResponse = chatMessagesPayload(emptyList())
            assertEquals(null, renew())
            assertEquals(transaction, pending().single())
            api.chatHistoryHeaders = accessHeaders("ALLOWED")
            api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
            gate.complete(Unit)
            advanceUntilIdle()
            val rows = when (val state = vm.uiState.value) {
                is RealsRootUiState.FirstChat -> state.messages
                is RealsRootUiState.SecondChat -> state.messages
                else -> error("Missing chat")
            }
            assertTrue(pending().isEmpty())
            assertTrue(rows.single { it.id == "a" }.withdrawn)
            assertTrue(rows.any { it.id == "sent" })
        }
    }

    @Test
    fun `cancelled renewal and logout discard late access decisions`() = runTest(dispatcher) {
        for (signOut in listOf(true, false)) {
            val gate = CompletableDeferred<Unit>()
            val api = FakeRealsApi()
            val vm = viewModel(api)
            val state = firstChatState(emptyList())
            vm.setState(state)
            api.chatHistoryHeaders = accessHeaders("RESTRICTED")
            api.beforeGetChatMessagesResponse = { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() } }
            val request = launch { assertEquals(null, vm.refreshFirstChatAudioUrl("a")) }
            runCurrent()
            if (signOut) vm.signOut() else request.cancel()
            gate.complete(Unit)
            advanceUntilIdle()
            if (signOut) assertTrue(vm.uiState.value is RealsRootUiState.Login)
            else assertEquals(CounterpartAudioAccess.UNKNOWN, state.historyOwner.access)
        }
    }

    @Test
    fun `historical second chat renewal obeys the anchored read only deadline`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val partner = TestDtos.audioChatMessage("a", senderId = "other")
        api.chatMessagesResponse = chatMessagesPayload(listOf(partner))
        val vm = viewModel(api)
        val active = secondChatState(emptyList())
        val until = "2099-06-18T21:01:00Z"
        val historical = active.copy(
            chat = active.chat?.copy(status = com.reals.app.domain.model.ChatStatus.Finished, readOnlyUntil = until),
            lifecycle = active.lifecycle.copy(status = active.lifecycle.status?.copy(
                chatStatus = com.reals.app.domain.model.ChatStatus.Finished, readOnlyUntil = until,
            )),
        )
        vm.setState(historical)
        assertEquals(partner.audio?.url, vm.refreshSecondChatAudioUrl("a"))
        vm.setState((vm.uiState.value as RealsRootUiState.SecondChat).copy(
            lifecycle = historical.lifecycle.copy(statusReceivedAtMillis = System.currentTimeMillis() - 61_000L),
        ))
        assertEquals(null, vm.refreshSecondChatAudioUrl("a"))
        assertEquals(null, vm.remoteAudio(historical.historyOwner, "a"))
    }

    @Test
    fun `both initial loads authorize audio and an empty restricted poll keeps text and own audio`() = runTest(dispatcher) {
        for (first in listOf(true, false)) {
            val api = FakeRealsApi()
            api.matchResponse = Response.success(TestDtos.match("CHAT_ACTIVE"))
            api.chatResponse = Response.success(TestDtos.chat().copy(
                chatType = if (first) "FIRST_CHAT" else "SECOND_CHAT",
                expiresAt = "2099-01-01T00:00:00Z", inactivityExpiresAt = null,
            ))
            api.secondChatStatusResponse = Response.success(activeSecondChatStatus())
            api.exitRequestsResponse = Response.success(emptyList())
            api.chatMessagesResponse = chatMessagesPayload(listOf(
                TestDtos.audioChatMessage("a", senderId = "other"), TestDtos.audioChatMessage("mine"),
                TestDtos.chatMessage("text"),
            ))
            val vm = viewModel(api)
            vm.setState(RealsRootUiState.Ready(TestDomain.session()))
            if (first) vm.openFirstChat("match-1") else vm.openSecondChat("connection-1", "match-1")
            advanceUntilIdle()
            val owner = checkNotNull(vm.uiState.value.chatHistoryOwner)
            assertEquals(CounterpartAudioAccess.ALLOWED, owner.access)
            assertTrue(vm.remoteAudio(owner, "a") != null)
            api.chatHistoryHeaders = accessHeaders("RESTRICTED")
            api.chatMessagesResponse = Response.success(TestDtos.chatMessagesPagedPayload(emptyList()))
            if (first) vm.refreshFirstChat(silent = true) else vm.refreshSecondChat(silent = true)
            advanceUntilIdle()
            assertEquals("text", api.lastChatMessagesAfter)
            assertEquals(CounterpartAudioAccess.RESTRICTED, owner.access)
            assertEquals(null, vm.remoteAudio(owner, "a"))
            assertTrue(vm.remoteAudio(owner, "mine") != null)
        }
    }

    @Test
    fun `withdrawal preserves locked recording payload and rejects a new recording reply to the same audio`() = runTest(dispatcher) {
        val file = java.io.File.createTempFile("synthetic-chat", ".m4a")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val uuid = java.util.UUID.randomUUID().toString()
            val target = ChatReplyDraft.Message("a", "other", ChatMessageType.Audio, null)
            val draft = ChatAudioDraftUiState(file.absolutePath, uuid, 2000L, file.length(),
                sendReplyTo = target.toOptimisticReply(), sendPayloadLocked = true)
            val policy = TestDtos.chat(audioPolicy = TestDtos.audioPolicy()).toDomain().audioPolicy
            for (first in listOf(true, false)) {
                val api = FakeRealsApi()
                val vm = viewModel(api)
                val initial = if (first) firstChatState(emptyList()).let {
                    it.copy(chat = it.chat?.copy(audioPolicy = policy), audioDraft = draft)
                } else secondChatState(emptyList()).let {
                    it.copy(chat = it.chat?.copy(audioPolicy = policy), audioDraft = draft)
                }
                vm.setState(initial)
                api.chatHistoryHeaders = accessHeaders("RESTRICTED")
                api.chatMessagesResponse = chatMessagesPayload(listOf(TestDtos.audioChatMessage("a", senderId = "other")))
                if (first) vm.refreshFirstChatAudioUrl("a") else vm.refreshSecondChatAudioUrl("a")
                val state = vm.uiState.value
                fun prepare(locked: Boolean) = when (state) {
                    is RealsRootUiState.FirstChat -> ChatMessageActionHandler.prepareFirstChatAudioSend(
                        state.copy(audioDraft = if (locked) state.audioDraft else draft.copy(sendPayloadLocked = false)),
                        file.absolutePath, uuid, target,
                    )
                    is RealsRootUiState.SecondChat -> ChatMessageActionHandler.prepareSecondChatAudioSend(
                        state.copy(audioDraft = if (locked) state.audioDraft else draft.copy(sendPayloadLocked = false)),
                        file.absolutePath, uuid, target,
                    )
                    else -> error("Missing chat")
                }
                val retry = prepare(true) as ChatAudioSendPreparation.Accepted
                assertEquals(uuid, retry.clientMessageId)
                assertEquals(file, retry.file)
                assertEquals("a", retry.replyTo?.targetId)
                assertEquals(ChatAudioSendPreparation.Ignored, prepare(false))
                assertTrue(file.isFile)
                assertFalse("sendChatAudioMessage" in api.calls)
            }
        } finally {
            file.delete()
        }
    }

    private fun accessHeaders(value: String) =
        Headers.Builder().add("X-Reals-Counterpart-Audio-Access", value).build()
}
