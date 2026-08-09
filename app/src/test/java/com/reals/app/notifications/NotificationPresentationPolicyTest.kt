package com.reals.app.notifications

import com.reals.app.foreground.ForegroundDestination
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPresentationPolicyTest {
    private val policy = NotificationPresentationPolicy()

    @Test
    fun `match found is suppressed only while Home is foreground`() {
        val notification = notification(type = TYPE_MATCH_FOUND)

        assertFalse(policy.shouldPresent(notification, ForegroundDestination.Home))
        assertTrue(policy.shouldPresent(notification, ForegroundDestination.FirstChat("match-1", "chat-1")))
        assertTrue(policy.shouldPresent(notification, ForegroundDestination.SecondChat("connection-1")))
        assertTrue(policy.shouldPresent(notification, ForegroundDestination.ProfileManagement))
        assertTrue(policy.shouldPresent(notification, null))
    }

    @Test
    fun `same second chat connection suppresses started notification`() {
        assertFalse(
            policy.shouldPresent(
                notification = notification(connectionId = " connection-1 "),
                foregroundDestination = ForegroundDestination.SecondChat("connection-1"),
            ),
        )
    }

    @Test
    fun `different connection presents started notification`() {
        assertTrue(
            policy.shouldPresent(
                notification = notification(connectionId = "connection-2"),
                foregroundDestination = ForegroundDestination.SecondChat("connection-1"),
            ),
        )
    }

    @Test
    fun `home first chat background and missing target present started notification`() {
        val notification = notification(connectionId = "connection-1")

        assertTrue(policy.shouldPresent(notification, ForegroundDestination.Home))
        assertTrue(policy.shouldPresent(notification, ForegroundDestination.FirstChat("match-1", "chat-1")))
        assertTrue(policy.shouldPresent(notification, null))
        assertTrue(policy.shouldPresent(notification(connectionId = " "), ForegroundDestination.SecondChat("connection-1")))
    }

    @Test
    fun `existing known and unknown types present by default at policy level`() {
        assertTrue(
            policy.shouldPresent(
                notification = notification(type = TYPE_SCHEDULING_AVAILABLE),
                foregroundDestination = ForegroundDestination.SecondChat("connection-1"),
            ),
        )
        assertTrue(
            policy.shouldPresent(
                notification = notification(type = "UNKNOWN"),
                foregroundDestination = ForegroundDestination.SecondChat("connection-1"),
            ),
        )
    }

    private fun notification(
        type: String = TYPE_SECOND_CHAT_STARTED,
        connectionId: String? = "connection-1",
    ): IncomingNotificationContext = IncomingNotificationContext(
        type = type,
        connectionId = connectionId,
        matchId = "match-1",
        availableAt = "2026-07-31T21:00:00Z",
        expiresAt = "2026-08-01T21:00:00Z",
    )
}
