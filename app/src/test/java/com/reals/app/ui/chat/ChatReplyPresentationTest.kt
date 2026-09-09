package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatMessageType
import com.reals.app.domain.model.ChatReplyDraft
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplyPresentationTest {
    @Test
    fun `confirmed partner TEXT is citable`() {
        val message = TestDtos.chatMessage(senderId = "partner", content = "respuesta").toDomain()

        assertTrue(message.isCitableReplyTarget(currentUserId = "me"))
    }

    @Test
    fun `confirmed partner AUDIO is citable`() {
        val message = TestDtos.audioChatMessage(senderId = "partner").toDomain()

        assertTrue(message.isCitableReplyTarget(currentUserId = "me"))
        assertEquals(AudioReplyPreviewText, message.toReplyDraftOrNull("me")?.previewText)
    }

    @Test
    fun `own message is not citable`() {
        val message = TestDtos.chatMessage(senderId = "me").toDomain()

        assertFalse(message.isCitableReplyTarget(currentUserId = "me"))
        assertNull(message.toReplyDraftOrNull("me"))
    }

    @Test
    fun `guidance with instance id is citable when composer writable`() {
        val guidance = TestDtos.firstChatGuidance(
            questionInstanceId = "00000000-0000-0000-0000-000000000027",
            questionText = "Pregunta activa",
        ).toDomain()

        val draft = guidance.toGuidanceReplyDraftOrNull(canSendMessages = true)

        assertEquals("00000000-0000-0000-0000-000000000027", draft?.targetId)
        assertEquals("Pregunta activa", draft?.previewText)
    }

    @Test
    fun `guidance without instance id is not citable`() {
        val guidance = TestDtos.firstChatGuidance(questionInstanceId = null).toDomain()

        assertNull(guidance.toGuidanceReplyDraftOrNull(canSendMessages = true))
    }

    @Test
    fun `selecting quoted message uses direct content only`() {
        val message = TestDtos.chatMessage(
            id = "message-b",
            senderId = "partner",
            content = "respuesta de B",
            replyTo = TestDtos.messageReplyTo(
                targetId = "message-a",
                previewText = "texto de A",
            ),
        ).toDomain()

        val draft = message.toReplyDraftOrNull(currentUserId = "me")

        assertEquals(
            ChatReplyDraft.Message(
                targetId = "message-b",
                senderId = "partner",
                messageType = ChatMessageType.Text,
                previewText = "respuesta de B",
            ),
            draft,
        )
    }

    @Test
    fun `reply preview labels own sender as Vos`() {
        val draft = ChatReplyDraft.Message(
            targetId = "message-1",
            senderId = "me",
            messageType = ChatMessageType.Text,
            previewText = "hola",
        )

        assertEquals("Vos", draft.toPreview(currentUserId = "me", partnerDisplayName = "Ana").label)
    }

    @Test
    fun `swipe threshold accepts only right distance at threshold`() {
        assertFalse(shouldSelectReplyForSwipe(horizontalDistancePx = 71f, thresholdPx = 72f))
        assertTrue(shouldSelectReplyForSwipe(horizontalDistancePx = 72f, thresholdPx = 72f))
        assertFalse(shouldSelectReplyForSwipe(horizontalDistancePx = -90f, thresholdPx = 72f))
    }

    @Test
    fun `composer height preserve decision follows prior bottom state`() {
        assertTrue(shouldPreserveBottomForComposerHeightChange(true))
        assertFalse(shouldPreserveBottomForComposerHeightChange(false))
    }
}
