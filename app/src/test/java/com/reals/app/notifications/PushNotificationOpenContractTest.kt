package com.reals.app.notifications

import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
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
    fun `match found and second chat started are handled and unknown is ignored`() {
        assertTrue(PushNotificationOpenContract.shouldHandleExternalOpen(TYPE_MATCH_FOUND))
        assertTrue(PushNotificationOpenContract.shouldHandleExternalOpen(TYPE_SECOND_CHAT_STARTED))
        assertFalse(PushNotificationOpenContract.shouldHandleExternalOpen("UNKNOWN"))
    }
}
