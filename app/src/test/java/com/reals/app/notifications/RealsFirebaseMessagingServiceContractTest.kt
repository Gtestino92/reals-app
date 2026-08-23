package com.reals.app.notifications

import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND_INVALIDATED
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCHMAKING_AVAILABLE
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
            "expiresAt" to " 2026-08-01T21:00:00-03:00 ",
        ).incomingNotificationContext()

        assertEquals(TYPE_SECOND_CHAT_STARTED, context.type)
        assertEquals("connection-1", context.connectionId)
        assertEquals("match-1", context.matchId)
        assertEquals("2026-07-31T21:00:00Z", context.availableAt)
        assertEquals("2026-08-01T21:00:00-03:00", context.expiresAt)
    }

    @Test
    fun `incoming match found context normalizes optional expiry fields`() {
        val missingExpiry = mapOf(
            "type" to " $TYPE_MATCH_FOUND ",
            "matchId" to " match-1 ",
            "availableAt" to " ",
        ).incomingNotificationContext()
        val blankExpiry = mapOf(
            "type" to TYPE_MATCH_FOUND,
            "matchId" to "match-1",
            "expiresAt" to " ",
        ).incomingNotificationContext()

        assertEquals(TYPE_MATCH_FOUND, missingExpiry.type)
        assertEquals("match-1", missingExpiry.matchId)
        assertEquals(null, missingExpiry.connectionId)
        assertEquals(null, missingExpiry.availableAt)
        assertEquals(null, missingExpiry.expiresAt)
        assertEquals(null, blankExpiry.expiresAt)
    }

    @Test
    fun `known foreground notification types include match found and unknown remains ignored`() {
        assertTrue(isKnownForegroundNotificationType(TYPE_MATCH_FOUND))
        assertTrue(isKnownForegroundNotificationType(TYPE_MATCH_FOUND_INVALIDATED))
        assertTrue(isKnownForegroundNotificationType(TYPE_SECOND_CHAT_STARTED))
        assertTrue(isKnownForegroundNotificationType(TYPE_MATCHMAKING_AVAILABLE))
        assertFalse(isKnownForegroundNotificationType("UNKNOWN"))
    }

    @Test
    fun `matchmaking available contract uses exact backend type and no resource ids`() {
        val context = mapOf(
            "type" to " $TYPE_MATCHMAKING_AVAILABLE ",
        ).incomingNotificationContext()

        assertEquals("MATCHMAKING_AVAILABLE", TYPE_MATCHMAKING_AVAILABLE)
        assertEquals(TYPE_MATCHMAKING_AVAILABLE, context.type)
        assertEquals(null, context.matchId)
        assertEquals(null, context.connectionId)
        assertEquals(null, context.availableAt)
        assertEquals(null, context.expiresAt)
    }

    @Test
    fun `matchmaking available dispatch always refreshes Home and follows presentation policy`() {
        assertEquals(
            MatchmakingAvailableDispatchAction(
                requestHomeRefresh = true,
                presentNotification = true,
            ),
            matchmakingAvailableDispatchAction(shouldPresent = true),
        )
        assertEquals(
            MatchmakingAvailableDispatchAction(
                requestHomeRefresh = true,
                presentNotification = false,
            ),
            matchmakingAvailableDispatchAction(shouldPresent = false),
        )
    }

    @Test
    fun `incoming match found invalidated context trims control payload fields`() {
        val context = mapOf(
            "type" to " $TYPE_MATCH_FOUND_INVALIDATED ",
            "matchId" to " match-1 ",
            "expiresAt" to " 2026-08-01T21:00:00-03:00 ",
        ).incomingNotificationContext()

        assertEquals(TYPE_MATCH_FOUND_INVALIDATED, context.type)
        assertEquals("match-1", context.matchId)
        assertEquals("2026-08-01T21:00:00-03:00", context.expiresAt)
        assertEquals(null, context.connectionId)
        assertEquals(null, context.availableAt)
    }

    @Test
    fun `incoming match found invalidated context tolerates missing fields`() {
        val context = mapOf(
            "type" to TYPE_MATCH_FOUND_INVALIDATED,
        ).incomingNotificationContext()

        assertEquals(TYPE_MATCH_FOUND_INVALIDATED, context.type)
        assertEquals(null, context.matchId)
        assertEquals(null, context.expiresAt)
    }
}
