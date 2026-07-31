package com.reals.app.ui.root

import com.reals.app.domain.model.ChatAudio
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageCollectionTest {
    @Test
    fun `lastMessageCursor returns latest by sentAt then id`() {
        val messages = listOf(
            message("b", "2026-06-18T21:00:00Z"),
            message("a", "2026-06-18T21:00:00Z"),
            message("c", "2026-06-18T20:00:00Z"),
        )

        assertEquals("b", messages.lastMessageCursor())
    }

    @Test
    fun `appendUnique appends new messages and keeps sorted order`() {
        val current = listOf(message("1", "2026-06-18T21:00:00Z"))
        val appended = current.appendUnique(
            listOf(
                message("1", "2026-06-18T21:00:00Z"),
                message("2", "2026-06-18T20:00:00Z"),
            )
        )

        assertEquals(listOf("2", "1"), appended.map { it.id })
    }

    @Test
    fun `appendUnique deduplicates repeated incoming id when not already present`() {
        val appended = emptyList<ChatMessage>().appendUnique(
            listOf(
                message("1", "2026-06-18T21:00:00Z").copy(content = "stale"),
                message("1", "2026-06-18T21:01:00Z").copy(content = "fresh"),
            )
        )

        assertEquals(1, appended.size)
        assertEquals("fresh", appended.single().content)
    }

    @Test
    fun `appendUnique keeps incoming representation for existing id`() {
        val current = listOf(message("1", "2026-06-18T21:00:00Z").copy(content = "old"))
        val appended = current.appendUnique(
            listOf(message("1", "2026-06-18T21:01:00Z").copy(content = "new"))
        )

        assertEquals(1, appended.size)
        assertEquals("new", appended.single().content)
    }

    @Test
    fun `appendUnique deduplicates repeated ids across existing and incoming messages`() {
        val current = listOf(
            message("1", "2026-06-18T21:00:00Z").copy(content = "old-1"),
            message("2", "2026-06-18T21:02:00Z").copy(content = "old-2"),
        )
        val appended = current.appendUnique(
            listOf(
                message("1", "2026-06-18T21:01:00Z").copy(content = "new-1"),
                message("1", "2026-06-18T21:03:00Z").copy(content = "newer-1"),
                message("2", "2026-06-18T21:04:00Z").copy(content = "new-2"),
                message("3", "2026-06-18T20:59:00Z").copy(content = "new-3"),
                message("3", "2026-06-18T21:05:00Z").copy(content = "newer-3"),
            )
        )

        assertEquals(listOf("1", "2", "3"), appended.map { it.id }.sorted())
        assertEquals("newer-1", appended.single { it.id == "1" }.content)
        assertEquals("new-2", appended.single { it.id == "2" }.content)
        assertEquals("newer-3", appended.single { it.id == "3" }.content)
    }

    @Test
    fun `appendUnique sorts deterministically by sentAt then id`() {
        val current = listOf(message("c", "2026-06-18T21:00:00Z"))
        val appended = current.appendUnique(
            listOf(
                message("b", "2026-06-18T21:00:00Z"),
                message("a", "2026-06-18T20:59:00Z"),
            )
        )

        assertEquals(listOf("a", "b", "c"), appended.map { it.id })
    }

    @Test
    fun `appendUnique replaces same id message metadata`() {
        val current = listOf(audioMessage("1", "https://old.test/audio"))
        val appended = current.appendUnique(listOf(audioMessage("1", "https://new.test/audio")))

        assertEquals(1, appended.size)
        assertEquals("https://new.test/audio", appended.single().audio?.url)
    }

    @Test
    fun `appendUnique keeps refreshed audio metadata for duplicate incoming id`() {
        val current = listOf(audioMessage("1", "https://old.test/audio"))
        val appended = current.appendUnique(
            listOf(
                audioMessage("1", "https://stale.test/audio"),
                audioMessage("1", "https://fresh.test/audio"),
            )
        )

        assertEquals(1, appended.size)
        assertEquals("https://fresh.test/audio", appended.single().audio?.url)
    }

    @Test
    fun `lastMessageCursor includes audio messages`() {
        val messages = listOf(
            message("text", "2026-06-18T21:00:00Z"),
            audioMessage("audio", "https://example.test/audio", "2026-06-18T21:01:00Z"),
        )

        assertEquals("audio", messages.lastMessageCursor())
    }

    private fun message(id: String, sentAt: String) = ChatMessage(
        id = id,
        chatSessionId = "chat-1",
        senderId = "user-1",
        content = "hola",
        sentAt = sentAt,
    )

    private fun audioMessage(
        id: String,
        url: String,
        sentAt: String = "2026-06-18T21:00:00Z",
    ) = ChatMessage(
        id = id,
        chatSessionId = "chat-1",
        senderId = "user-1",
        clientMessageId = "00000000-0000-0000-0000-000000000101",
        messageType = ChatMessageType.Audio,
        content = null,
        audio = ChatAudio(
            url = url,
            durationMillis = 3_158,
            contentType = "audio/mp4",
            sizeBytes = 77_832,
        ),
        sentAt = sentAt,
    )
}
