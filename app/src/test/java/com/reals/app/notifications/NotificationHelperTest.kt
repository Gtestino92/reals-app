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
}
