package com.reals.app.data.mapper

import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
import com.reals.app.domain.model.ChatAudioUnavailableReason
import com.reals.app.domain.model.ChatMessagePresentation
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.domain.model.ChatMessageType
import com.reals.app.domain.model.ChatStatus
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.testJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMappersTest {
    @Test
    fun `ChatResponseDto maps chat core fields`() {
        val chat = TestDtos.chat(
            status = "ACTIVE",
            myDecision = "APPROVED",
            partnerDecision = "PENDING",
        ).toDomain()

        assertEquals("chat-1", chat.id)
        assertEquals("match-1", chat.matchId)
        assertEquals(ChatStatus.Active, chat.status)
        assertEquals("2026-06-19T21:00:00Z", chat.timeoutAt)
        assertEquals("2026-06-20T21:00:00Z", chat.expiresAt)
        assertEquals("2026-06-18T21:05:00Z", chat.inactivityExpiresAt)
        assertEquals("Taylor", chat.partner?.displayName)
        assertEquals(ChatDecisionState.Approved, chat.myDecision)
        assertEquals(ChatDecisionState.Pending, chat.partnerDecision)
    }

    @Test
    fun `ChatResponseDto uses timeoutAt when expiresAt missing`() {
        val chat = TestDtos.chat().copy(expiresAt = null).toDomain()

        assertEquals(chat.timeoutAt, chat.expiresAt)
    }

    @Test
    fun `FirstChatResponse with guidance deserializes successfully`() {
        val dto = testJson.decodeFromString<ChatResponseDto>(
            """
            {
              "id": "chat-1",
              "matchId": "match-1",
              "connectionId": "connection-1",
              "chatType": "FIRST_CHAT",
              "status": "ACTIVE",
              "startedAt": "2026-06-18T21:00:00Z",
              "timeoutAt": "2026-06-19T21:00:00Z",
              "expiresAt": "2026-06-20T21:00:00Z",
              "partner": { "userId": "user-2", "profileId": "profile-2", "displayName": "Taylor" },
              "myDecision": "PENDING",
              "partnerDecision": "PENDING",
              "guidance": {
                "question": { "id": "Q027", "text": "Pregunta inicial" },
                "questionOrdinal": 1,
                "maxQuestions": 3,
                "requiredCharacters": 40,
                "canRequestNext": true,
                "myNextRequested": false,
                "completed": false
              }
            }
            """.trimIndent(),
        )

        assertEquals("Q027", dto.guidance?.question?.id)
        assertEquals("Pregunta inicial", dto.guidance?.question?.text)
    }

    @Test
    fun `absent guidance remains compatible`() {
        val dto = testJson.decodeFromString<ChatResponseDto>(
            """
            {
              "id": "chat-1",
              "matchId": "match-1",
              "chatType": "FIRST_CHAT",
              "status": "ACTIVE",
              "startedAt": "2026-06-18T21:00:00Z",
              "timeoutAt": "2026-06-19T21:00:00Z"
            }
            """.trimIndent(),
        )

        assertNull(dto.guidance)
        assertNull(dto.toDomain().guidance)
    }

    @Test
    fun `nullable guidance remains compatible`() {
        val dto = TestDtos.chat(guidance = null)

        assertNull(dto.guidance)
        assertNull(dto.toDomain().guidance)
    }

    @Test
    fun `guidance fields map exactly to domain`() {
        val guidance = TestDtos.chat(
            guidance = TestDtos.firstChatGuidance(
                questionId = "Q002",
                questionText = "Nueva pregunta",
                questionOrdinal = 2,
                maxQuestions = 3,
                requiredCharacters = 40,
                canRequestNext = false,
                myNextRequested = true,
                completed = false,
            )
        ).toDomain().guidance

        assertEquals("Q002", guidance?.question?.id)
        assertEquals("Nueva pregunta", guidance?.question?.text)
        assertEquals(2, guidance?.questionOrdinal)
        assertEquals(3, guidance?.maxQuestions)
        assertEquals(40, guidance?.requiredCharacters)
        assertEquals(false, guidance?.canRequestNext)
        assertEquals(true, guidance?.myNextRequested)
        assertEquals(false, guidance?.completed)
    }

    @Test
    fun `completed guidance retains final question`() {
        val guidance = TestDtos.chat(
            guidance = TestDtos.firstChatGuidance(
                questionId = "Q003",
                questionText = "Pregunta final",
                questionOrdinal = 3,
                canRequestNext = false,
                myNextRequested = false,
                completed = true,
            )
        ).toDomain().guidance

        assertEquals("Q003", guidance?.question?.id)
        assertEquals("Pregunta final", guidance?.question?.text)
        assertEquals(3, guidance?.questionOrdinal)
        assertEquals(true, guidance?.completed)
        assertEquals(false, guidance?.myNextRequested)
    }

    @Test
    fun `ChatMessageResponseDto maps message`() {
        val message = TestDtos.chatMessage("message-2").toDomain()

        assertEquals("message-2", message.id)
        assertEquals("chat-1", message.chatSessionId)
        assertEquals("user-1", message.senderId)
        assertEquals("hola", message.content)
        assertEquals(ChatMessageType.Text, message.messageType)
        assertTrue(message.presentation is ChatMessagePresentation.Text)
        assertEquals(TestDtos.now, message.sentAt)
    }

    @Test
    fun `ChatMessageResponseDto maps absent and null reaction to none`() {
        val absentDto = testJson.decodeFromString<com.reals.app.data.dto.ChatMessageResponseDto>(
            """
            {
              "id": "message-2",
              "chatSessionId": "chat-1",
              "senderId": "user-1",
              "messageType": "TEXT",
              "content": "hola",
              "audio": null,
              "sentAt": "2026-06-18T21:00:00Z"
            }
            """.trimIndent(),
        )
        val nullDto = TestDtos.chatMessage("message-3", reactionType = null)

        assertNull(absentDto.toDomain().reactionType)
        assertNull(nullDto.toDomain().reactionType)
    }

    @Test
    fun `ChatMessageResponseDto maps HEART reaction for text and audio messages`() {
        val text = TestDtos.chatMessage("message-2", reactionType = "HEART").toDomain()
        val audio = TestDtos.audioChatMessage("audio-message-2", reactionType = "HEART").toDomain()

        assertEquals(ChatMessageReactionType.Heart, text.reactionType)
        assertEquals(ChatMessageReactionType.Heart, audio.reactionType)
        assertEquals(ChatMessageType.Text, text.messageType)
        assertEquals(ChatMessageType.Audio, audio.messageType)
    }

    @Test
    fun `ChatMessageResponseDto maps unknown reaction without crashing`() {
        val message = TestDtos.chatMessage("message-2", reactionType = "SMILE").toDomain()

        assertTrue(message.reactionType is ChatMessageReactionType.Unknown)
        assertEquals("SMILE", message.reactionType?.rawValue)
    }

    @Test
    fun `AUDIO message with null content maps nested metadata`() {
        val message = TestDtos.audioChatMessage().toDomain()

        assertEquals(ChatMessageType.Audio, message.messageType)
        assertEquals(null, message.content)
        assertEquals("https://example.test/audio", message.audio?.url)
        assertEquals(3_158L, message.audio?.durationMillis)
        assertTrue(message.presentation is ChatMessagePresentation.Audio)
    }

    @Test
    fun `unknown and malformed messages map to unsupported presentation`() {
        val unknown = TestDtos.chatMessage().copy(messageType = "VIDEO").toDomain()
        val audioWithoutMetadata = TestDtos.audioChatMessage().copy(audio = null).toDomain()
        val textWithoutContent = TestDtos.chatMessage().copy(content = null).toDomain()

        assertTrue(unknown.messageType is ChatMessageType.Unknown)
        assertEquals(ChatMessagePresentation.Unsupported, unknown.presentation)
        assertEquals(ChatMessagePresentation.Unsupported, audioWithoutMetadata.presentation)
        assertEquals(ChatMessagePresentation.Unsupported, textWithoutContent.presentation)
    }

    @Test
    fun `audio policy maps unknown reason safely`() {
        val chat = TestDtos.chat(
            audioPolicy = TestDtos.audioPolicy(enabled = false, unavailableReason = "FUTURE_REASON"),
        ).toDomain()

        assertTrue(chat.audioPolicy?.unavailableReason is ChatAudioUnavailableReason.Unknown)
        assertEquals("FUTURE_REASON", chat.audioPolicy?.unavailableReason?.rawValue)
    }

    @Test
    fun `ChatExitRequestResponseDto maps mutual cancel pending`() {
        val request = TestDtos.exitRequest(
            status = "PENDING",
            type = "MUTUAL_CANCEL",
            reason = "NO_LONGER_INTERESTED",
        ).toDomain()

        assertEquals("exit-1", request.id)
        assertEquals(ChatExitRequestType.MutualCancel, request.type)
        assertEquals(ChatExitRequestStatus.Pending, request.status)
        assertEquals(ChatExitReason.NoLongerInterested, request.reason)
        assertEquals("detalle", request.details)
        assertEquals("user-1", request.requesterUserId)
        assertEquals("user-2", request.responderUserId)
    }

    @Test
    fun `ChatExitRequestResponseDto maps TIMED_OUT`() {
        val request = TestDtos.exitRequest(status = "TIMED_OUT").toDomain()

        assertEquals(ChatExitRequestStatus.TimedOut, request.status)
    }

    @Test
    fun `ChatExitOutcomeResponseDto maps cancelled safety report without immediate penalty`() {
        val outcome = TestDtos.exitOutcome().toDomain()

        assertEquals(ChatStatus.Cancelled, outcome.chat.status)
        assertEquals(ChatExitRequestStatus.Accepted, outcome.exitRequest.status)
        assertEquals(false, outcome.penaltyApplied)
        assertEquals(null, outcome.penalizedUserId)
    }

    @Test
    fun `unknown statuses map safely`() {
        val chat = TestDtos.chat(status = "PAUSED").toDomain()
        val request = TestDtos.exitRequest(status = "WAITING", type = "CUSTOM").toDomain()

        assertTrue(chat.status is ChatStatus.Unknown)
        assertEquals("PAUSED", chat.status.rawValue)
        assertTrue(request.status is ChatExitRequestStatus.Unknown)
        assertEquals("WAITING", request.status.rawValue)
        assertTrue(request.type is ChatExitRequestType.Unknown)
        assertEquals("CUSTOM", request.type.rawValue)
    }
}
