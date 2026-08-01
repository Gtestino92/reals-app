package com.reals.app.notifications

import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealsFirebaseMessagingServiceContractTest {
    @Test
    fun `incoming notification context preserves backend field names`() {
        val context = mapOf(
            "type" to " $TYPE_SECOND_CHAT_STARTED ",
            "connectionId" to " connection-1 ",
            "matchId" to " match-1 ",
            "availableAt" to " 2026-07-31T21:00:00Z ",
        ).incomingNotificationContext()

        assertEquals(TYPE_SECOND_CHAT_STARTED, context.type)
        assertEquals("connection-1", context.connectionId)
        assertEquals("match-1", context.matchId)
        assertEquals("2026-07-31T21:00:00Z", context.availableAt)
    }

    @Test
    fun `known foreground notification types include started and unknown remains ignored`() {
        assertTrue(isKnownForegroundNotificationType(TYPE_SECOND_CHAT_STARTED))
        assertFalse(isKnownForegroundNotificationType("UNKNOWN"))
    }
}
