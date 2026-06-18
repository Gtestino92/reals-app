package com.reals.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class ChatExitTimeoutTest {
    private val createdAt = "2026-06-18T21:00:00Z"
    private val createdAtMillis = OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()

    @Test
    fun `remainingExitSeconds returns full timeout at creation`() {
        assertEquals(20L, remainingExitSeconds(createdAt, createdAtMillis, timeoutSeconds = 20L))
    }

    @Test
    fun `remainingExitSeconds decreases with elapsed time`() {
        assertEquals(13L, remainingExitSeconds(createdAt, createdAtMillis + 7_000L, timeoutSeconds = 20L))
    }

    @Test
    fun `remainingExitSeconds clamps at zero`() {
        assertEquals(0L, remainingExitSeconds(createdAt, createdAtMillis + 21_000L, timeoutSeconds = 20L))
    }

    @Test
    fun `remainingExitSeconds handles invalid createdAt safely`() {
        assertEquals(20L, remainingExitSeconds("not-a-date", nowMillis = createdAtMillis, timeoutSeconds = 20L))
    }
}
