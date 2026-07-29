package com.reals.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstChatUnansweredSuggestionDismissalStoreTest {
    @Test
    fun `dismissed period can be read by a new store over same backing`() {
        val backing = mutableMapOf<String, String>()
        InMemoryFirstChatUnansweredSuggestionDismissalStore(backing)
            .dismissPeriod("user-1", "chat-1", "started:t0")

        assertEquals(
            "started:t0",
            InMemoryFirstChatUnansweredSuggestionDismissalStore(backing)
                .dismissedPeriod("user-1", "chat-1"),
        )
    }

    @Test
    fun `users do not share dismissal`() {
        val store = InMemoryFirstChatUnansweredSuggestionDismissalStore()

        store.dismissPeriod("user-1", "chat-1", "started:t0")

        assertNull(store.dismissedPeriod("user-2", "chat-1"))
    }

    @Test
    fun `chats do not share dismissal`() {
        val store = InMemoryFirstChatUnansweredSuggestionDismissalStore()

        store.dismissPeriod("user-1", "chat-1", "started:t0")

        assertNull(store.dismissedPeriod("user-1", "chat-2"))
    }

    @Test
    fun `new period replaces previous period for same user and chat`() {
        val store = InMemoryFirstChatUnansweredSuggestionDismissalStore()

        store.dismissPeriod("user-1", "chat-1", "started:t0")
        store.dismissPeriod("user-1", "chat-1", "partner:m2")

        assertEquals("partner:m2", store.dismissedPeriod("user-1", "chat-1"))
    }
}
