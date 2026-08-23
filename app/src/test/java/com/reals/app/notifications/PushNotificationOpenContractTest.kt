package com.reals.app.notifications

import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND_INVALIDATED
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCHMAKING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationOpenContractTest {
    @Test
    fun `internal push type is recognized before raw FCM type`() {
        assertEquals(
            TYPE_SECOND_CHAT_STARTED,
            PushNotificationOpenContract.resolveType(
                internalPushType = " $TYPE_SECOND_CHAT_STARTED ",
                rawFcmType = "OTHER",
            ),
        )
    }

    @Test
    fun `raw FCM type is recognized when internal type is missing`() {
        assertEquals(
            TYPE_SECOND_CHAT_STARTED,
            PushNotificationOpenContract.resolveType(
                internalPushType = null,
                rawFcmType = " $TYPE_SECOND_CHAT_STARTED ",
            ),
        )
    }

    @Test
    fun `raw matchmaking available FCM type is recognized when internal type is missing`() {
        assertEquals(
            TYPE_MATCHMAKING_AVAILABLE,
            PushNotificationOpenContract.resolveType(
                internalPushType = null,
                rawFcmType = " $TYPE_MATCHMAKING_AVAILABLE ",
            ),
        )
    }

    @Test
    fun `handled notification opens include matchmaking available and unknown is ignored`() {
        assertTrue(PushNotificationOpenContract.shouldHandleExternalOpen(TYPE_MATCH_FOUND))
        assertTrue(PushNotificationOpenContract.shouldHandleExternalOpen(TYPE_SECOND_CHAT_STARTED))
        assertTrue(PushNotificationOpenContract.shouldHandleExternalOpen(TYPE_MATCHMAKING_AVAILABLE))
        assertFalse(PushNotificationOpenContract.shouldHandleExternalOpen(TYPE_MATCH_FOUND_INVALIDATED))
        assertFalse(PushNotificationOpenContract.shouldHandleExternalOpen("UNKNOWN"))
    }
}
