package com.reals.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MatchFoundExpiryTest {
    private val now = Instant.parse("2026-08-01T12:00:00Z")

    @Test
    fun `future expiresAt presents with positive timeout`() {
        assertEquals(
            MatchFoundExpiryDecision.Present(timeoutAfterMillis = 120_000L),
            matchFoundExpiryDecision("2026-08-01T12:02:00Z", now),
        )
    }

    @Test
    fun `exact and past expiresAt are stale`() {
        assertEquals(
            MatchFoundExpiryDecision.Stale,
            matchFoundExpiryDecision("2026-08-01T12:00:00Z", now),
        )
        assertEquals(
            MatchFoundExpiryDecision.Stale,
            matchFoundExpiryDecision("2026-08-01T11:59:59Z", now),
        )
    }

    @Test
    fun `timezone offset timestamp compares by instant`() {
        assertEquals(
            MatchFoundExpiryDecision.Present(timeoutAfterMillis = 900_000L),
            matchFoundExpiryDecision("2026-08-01T09:15:00-03:00", now),
        )
    }

    @Test
    fun `missing blank and malformed expiresAt preserve no-timeout presentation`() {
        val expected = MatchFoundExpiryDecision.Present(timeoutAfterMillis = null)

        assertEquals(expected, matchFoundExpiryDecision(null, now))
        assertEquals(expected, matchFoundExpiryDecision(" ", now))
        assertEquals(expected, matchFoundExpiryDecision("not-a-date", now))
    }

    @Test
    fun `match found dispatch refreshes Home before expiry filtering when suppressed`() {
        assertEquals(
            MatchFoundDispatchAction.RefreshHomeOnly,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T11:00:00Z"),
                shouldPresent = false,
                now = now,
            ),
        )
    }

    @Test
    fun `match found dispatch presents eligible data message with timeout`() {
        assertEquals(
            MatchFoundDispatchAction.Present(
                matchId = "match-1",
                timeoutAfterMillis = 60_000L,
            ),
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:01:00Z"),
                shouldPresent = true,
                now = now,
            ),
        )
    }

    @Test
    fun `match found dispatch ignores stale and preserves legacy missing expiry`() {
        assertEquals(
            MatchFoundDispatchAction.IgnoreStale,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T11:59:00Z"),
                shouldPresent = true,
                now = now,
            ),
        )
        assertEquals(
            MatchFoundDispatchAction.Present(
                matchId = "match-1",
                timeoutAfterMillis = null,
            ),
            matchFoundDispatchAction(
                notification = notification(expiresAt = null),
                shouldPresent = true,
                now = now,
            ),
        )
    }

    private fun notification(
        expiresAt: String?,
    ): IncomingNotificationContext = IncomingNotificationContext(
        type = PushNotificationContract.TYPE_MATCH_FOUND,
        connectionId = null,
        matchId = "match-1",
        availableAt = null,
        expiresAt = expiresAt,
    )
}
