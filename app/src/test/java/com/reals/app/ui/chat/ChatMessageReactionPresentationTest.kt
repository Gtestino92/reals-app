package com.reals.app.ui.chat

import com.reals.app.domain.model.ChatAudio
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.domain.model.ChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageReactionPresentationTest {
    @Test
    fun `incoming eligible null message shows add heart`() {
        val message = message("incoming", senderId = "other")

        assertEquals(
            ChatMessageReactionPresentation.AddHeart,
            chatMessageReactionPresentation(
                message = message,
                mine = false,
                pendingReactionMessageIds = emptySet(),
                reactableMessageIds = setOf("incoming"),
            ),
        )
    }

    @Test
    fun `incoming pending message shows given heart`() {
        val message = message("incoming", senderId = "other")

        assertEquals(
            ChatMessageReactionPresentation.GivenHeart,
            chatMessageReactionPresentation(
                message = message,
                mine = false,
                pendingReactionMessageIds = setOf("incoming"),
                reactableMessageIds = setOf("incoming"),
            ),
        )
    }

    @Test
    fun `incoming confirmed heart shows given heart even when old`() {
        val message = message(
            id = "incoming",
            senderId = "other",
            reactionType = ChatMessageReactionType.Heart,
        )

        assertEquals(
            ChatMessageReactionPresentation.GivenHeart,
            chatMessageReactionPresentation(
                message = message,
                mine = false,
                pendingReactionMessageIds = emptySet(),
                reactableMessageIds = emptySet(),
            ),
        )
    }

    @Test
    fun `incoming old null message shows no heart`() {
        val message = message("incoming", senderId = "other")

        assertEquals(
            ChatMessageReactionPresentation.None,
            chatMessageReactionPresentation(
                message = message,
                mine = false,
                pendingReactionMessageIds = emptySet(),
                reactableMessageIds = emptySet(),
            ),
        )
    }

    @Test
    fun `own confirmed heart shows received heart`() {
        val message = message(
            id = "mine",
            senderId = "me",
            reactionType = ChatMessageReactionType.Heart,
        )

        assertEquals(
            ChatMessageReactionPresentation.ReceivedHeart,
            chatMessageReactionPresentation(
                message = message,
                mine = true,
                pendingReactionMessageIds = emptySet(),
                reactableMessageIds = emptySet(),
            ),
        )
    }

    @Test
    fun `own null and unknown reactions show no heart affordance`() {
        val own = message("mine", senderId = "me")
        val unknown = message(
            id = "incoming",
            senderId = "other",
            reactionType = ChatMessageReactionType.Unknown("FIRE"),
        )

        assertEquals(
            ChatMessageReactionPresentation.None,
            chatMessageReactionPresentation(own, mine = true, emptySet(), setOf("mine")),
        )
        assertEquals(
            ChatMessageReactionPresentation.None,
            chatMessageReactionPresentation(unknown, mine = false, emptySet(), setOf("incoming")),
        )
    }

    @Test
    fun `audio messages use the same reaction presentation`() {
        val audio = message("audio", senderId = "other").copy(
            messageType = ChatMessageType.Audio,
            content = null,
            audio = ChatAudio(
                url = "https://example.test/audio",
                durationMillis = 1_000,
                contentType = "audio/mp4",
                sizeBytes = 12,
            ),
        )

        assertEquals(
            ChatMessageReactionPresentation.AddHeart,
            chatMessageReactionPresentation(audio, mine = false, emptySet(), setOf("audio")),
        )
    }

    private fun message(
        id: String,
        senderId: String,
        reactionType: ChatMessageReactionType? = null,
    ): ChatMessage = ChatMessage(
        id = id,
        chatSessionId = "chat-1",
        senderId = senderId,
        content = "hola",
        reactionType = reactionType,
        sentAt = "2026-06-18T21:00:00Z",
    )
}
