package com.reals.app.notifications

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHelperTest {
    @Test
    fun `visual review channel uses high importance`() {
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            NotificationHelper.VISUAL_REVIEW_CHANNEL_IMPORTANCE,
        )
    }

    @Test
    fun `visual review notification uses high priority`() {
        assertEquals(
            NotificationCompat.PRIORITY_HIGH,
            NotificationHelper.VISUAL_REVIEW_NOTIFICATION_PRIORITY,
        )
    }

    @Test
    fun `visual review reminder contract keeps historical type compatibility`() {
        assertEquals("VISUAL_REVIEW_REMINDER", PushNotificationContract.TYPE_VISUAL_REVIEW_REMINDER)
        assertEquals("VISUAL_REVIEW_AVAILABLE", PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE)
        assertEquals("match_id", PushNotificationContract.EXTRA_MATCH_ID)
        assertEquals(10_000, PushNotificationContract.VISUAL_REVIEW_NOTIFICATION_ID_BASE)
    }

    @Test
    fun `second chat reminder contract refreshes home instead of deep linking`() {
        assertEquals("SECOND_CHAT_REMINDER", PushNotificationContract.TYPE_SECOND_CHAT_REMINDER)
        assertEquals("connection_id", PushNotificationContract.EXTRA_CONNECTION_ID)
        assertEquals("available_at", PushNotificationContract.EXTRA_AVAILABLE_AT)
        assertEquals(20_000, PushNotificationContract.SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE)
    }

    @Test
    fun `scheduling available contract refreshes home instead of deep linking`() {
        assertEquals("SCHEDULING_AVAILABLE", PushNotificationContract.TYPE_SCHEDULING_AVAILABLE)
        assertEquals(
            "SCHEDULING_PROPOSALS_RECEIVED",
            PushNotificationContract.TYPE_SCHEDULING_PROPOSALS_RECEIVED,
        )
        assertEquals("SCHEDULING_CONFIRMED", PushNotificationContract.TYPE_SCHEDULING_CONFIRMED)
        assertEquals("connection_id", PushNotificationContract.EXTRA_CONNECTION_ID)
        assertEquals("match_id", PushNotificationContract.EXTRA_MATCH_ID)
        assertEquals(15_000, PushNotificationContract.SCHEDULING_AVAILABLE_NOTIFICATION_ID_BASE)
    }

    @Test
    fun `scheduling notification copy supports new scheduling push types`() {
        assertEquals(
            "Nuevos horarios propuestos" to "Entr\u00e1 a Reals para revisar las opciones.",
            NotificationHelper.schedulingNotificationCopy(
                PushNotificationContract.TYPE_SCHEDULING_PROPOSALS_RECEIVED
            ),
        )
        assertEquals(
            "Horario confirmado" to "Entr\u00e1 a Reals para ver la segunda charla.",
            NotificationHelper.schedulingNotificationCopy(
                PushNotificationContract.TYPE_SCHEDULING_CONFIRMED
            ),
        )
        assertEquals(
            "Coordinaci\u00f3n disponible" to "Ya pod\u00e9s coordinar horarios en Reals.",
            NotificationHelper.schedulingNotificationCopy(
                PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
            ),
        )
    }
}
