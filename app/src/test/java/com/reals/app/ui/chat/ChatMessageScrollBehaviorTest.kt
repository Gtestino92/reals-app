package com.reals.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageScrollBehaviorTest {

    @Test
    fun `own newest message requests auto scroll even away from bottom`() {
        assertTrue(
            shouldAutoScrollForLatestMessage(
                latestMessageIsMine = true,
                wasNearBottomBeforeLatestChange = false,
            ),
        )
    }

    @Test
    fun `incoming newest message near bottom requests auto scroll and no unseen indicator`() {
        assertTrue(
            shouldAutoScrollForLatestMessage(
                latestMessageIsMine = false,
                wasNearBottomBeforeLatestChange = true,
            ),
        )
        assertFalse(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = true,
                previousBackendMessageIds = setOf("a"),
                currentBackendMessages = listOf(
                    BackendMessageIdentity(id = "a", senderId = "user-1"),
                    BackendMessageIdentity(id = "b", senderId = "user-2"),
                ),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = true,
            ),
        )
    }

    @Test
    fun `incoming newest message away from bottom shows unseen indicator without auto scroll`() {
        assertFalse(
            shouldAutoScrollForLatestMessage(
                latestMessageIsMine = false,
                wasNearBottomBeforeLatestChange = false,
            ),
        )
        assertTrue(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = true,
                previousBackendMessageIds = setOf("a"),
                currentBackendMessages = listOf(
                    BackendMessageIdentity(id = "a", senderId = "user-1"),
                    BackendMessageIdentity(id = "b", senderId = "user-2"),
                ),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = false,
            ),
        )
    }

    @Test
    fun `existing message reaction update does not show unseen indicator`() {
        assertFalse(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = true,
                previousBackendMessageIds = setOf("a"),
                currentBackendMessages = listOf(BackendMessageIdentity(id = "a", senderId = "user-2")),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = false,
            ),
        )
    }

    @Test
    fun `existing message reaction appearance preserves bottom when already at bottom`() {
        assertTrue(
            shouldPreserveBottomForReactionLayoutChange(
                previous = listOf(MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false)),
                current = listOf(MessageReactionLayoutIdentity(id = "a", hasReactionExtent = true)),
                wasNearBottomBeforeReactionChange = true,
            ),
        )
    }

    @Test
    fun `given heart keeps the same side lane geometry`() {
        assertFalse(
            shouldPreserveBottomForReactionLayoutChange(
                previous = listOf(
                    MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false),
                    MessageReactionLayoutIdentity(id = "b", hasReactionExtent = false),
                ),
                current = listOf(
                    MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false),
                    MessageReactionLayoutIdentity(id = "b", hasReactionExtent = false),
                ),
                wasNearBottomBeforeReactionChange = true,
            ),
        )
    }

    @Test
    fun `received reaction appearance can preserve bottom for an existing message`() {
        assertTrue(
            shouldPreserveBottomForReactionLayoutChange(
                previous = listOf(
                    MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false),
                    MessageReactionLayoutIdentity(id = "b", hasReactionExtent = false),
                ),
                current = listOf(
                    MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false),
                    MessageReactionLayoutIdentity(id = "b", hasReactionExtent = true),
                ),
                wasNearBottomBeforeReactionChange = true,
            ),
        )
    }

    @Test
    fun `new message insertion is not treated as reaction layout preservation`() {
        assertFalse(
            shouldPreserveBottomForReactionLayoutChange(
                previous = listOf(MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false)),
                current = listOf(
                    MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false),
                    MessageReactionLayoutIdentity(id = "b", hasReactionExtent = true),
                ),
                wasNearBottomBeforeReactionChange = true,
            ),
        )
    }

    @Test
    fun `reaction layout change does not force bottom when user is away`() {
        assertFalse(
            shouldPreserveBottomForReactionLayoutChange(
                previous = listOf(MessageReactionLayoutIdentity(id = "a", hasReactionExtent = false)),
                current = listOf(MessageReactionLayoutIdentity(id = "a", hasReactionExtent = true)),
                wasNearBottomBeforeReactionChange = false,
            ),
        )
    }

    @Test
    fun `pending to confirmed reaction does not request second bottom preservation`() {
        assertFalse(
            shouldPreserveBottomForReactionLayoutChange(
                previous = listOf(MessageReactionLayoutIdentity(id = "a", hasReactionExtent = true)),
                current = listOf(MessageReactionLayoutIdentity(id = "a", hasReactionExtent = true)),
                wasNearBottomBeforeReactionChange = true,
            ),
        )
    }

    @Test
    fun `own optimistic message does not show unseen indicator`() {
        assertFalse(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = true,
                previousBackendMessageIds = setOf("a"),
                currentBackendMessages = listOf(BackendMessageIdentity(id = "a", senderId = "user-2")),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = false,
            ),
        )
    }

    @Test
    fun `own backend ack does not show unseen indicator`() {
        assertFalse(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = true,
                previousBackendMessageIds = setOf("a"),
                currentBackendMessages = listOf(
                    BackendMessageIdentity(id = "a", senderId = "user-2"),
                    BackendMessageIdentity(id = "b", senderId = "user-1"),
                ),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = false,
            ),
        )
    }

    @Test
    fun `new partner message id after baseline shows unseen indicator`() {
        assertTrue(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = true,
                previousBackendMessageIds = setOf("a"),
                currentBackendMessages = listOf(
                    BackendMessageIdentity(id = "a", senderId = "user-1"),
                    BackendMessageIdentity(id = "b", senderId = "user-2"),
                ),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = false,
            ),
        )
    }

    @Test
    fun `initial baseline does not show unseen indicator`() {
        assertFalse(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = false,
                previousBackendMessageIds = null,
                currentBackendMessages = listOf(BackendMessageIdentity(id = "a", senderId = "user-2")),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = false,
            ),
        )
    }

    @Test
    fun `multiple new partner messages still produce one unseen boolean`() {
        assertTrue(
            shouldMarkIncomingMessagesUnseen(
                baselineEstablished = true,
                previousBackendMessageIds = setOf("a"),
                currentBackendMessages = listOf(
                    BackendMessageIdentity(id = "a", senderId = "user-1"),
                    BackendMessageIdentity(id = "b", senderId = "user-2"),
                    BackendMessageIdentity(id = "c", senderId = "user-2"),
                ),
                currentUserId = "user-1",
                wasNearBottomBeforeMessageChange = false,
            ),
        )
    }

    @Test
    fun `usable viewport end subtracts after content padding`() {
        assertEquals(
            760,
            usableLazyListViewportEnd(viewportEndOffset = 1_000, afterContentPadding = 240),
        )
    }

    @Test
    fun `latest item behind composer reserved padding produces positive overflow`() {
        assertEquals(
            40,
            latestItemOverflow(itemEndOffset = 800, usableViewportEnd = 760),
        )
    }

    @Test
    fun `fully visible latest item above composer reserved area has zero overflow`() {
        assertEquals(
            0,
            latestItemOverflow(itemEndOffset = 740, usableViewportEnd = 760),
        )
    }
}
