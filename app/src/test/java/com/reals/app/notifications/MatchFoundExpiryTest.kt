package com.reals.app.notifications

import com.reals.app.foreground.ForegroundDestination
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
    fun `match found dispatch refreshes Home before expiry filtering`() {
        assertEquals(
            MatchFoundDispatchAction.RefreshHomeOnly,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T11:00:00Z"),
                foregroundDestination = ForegroundDestination.Home,
                now = now,
            ),
        )
    }

    @Test
    fun `match found dispatch refreshes Home before invalidation filtering`() {
        assertEquals(
            MatchFoundDispatchAction.RefreshHomeOnly,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:01:00Z"),
                foregroundDestination = ForegroundDestination.Home,
                now = now,
                isInvalidated = { _, _ -> true },
            ),
        )
    }

    @Test
    fun `match found dispatch suppresses foreground FirstChat without Home refresh`() {
        assertEquals(
            MatchFoundDispatchAction.SuppressForeground,
            matchFoundDispatchAction(
                notification = notification(
                    matchId = "match-1",
                    expiresAt = "2026-08-01T12:01:00Z",
                ),
                foregroundDestination = ForegroundDestination.FirstChat("match-1", "chat-1"),
                now = now,
            ),
        )
        assertEquals(
            MatchFoundDispatchAction.SuppressForeground,
            matchFoundDispatchAction(
                notification = notification(
                    matchId = "match-2",
                    expiresAt = "2026-08-01T12:01:00Z",
                ),
                foregroundDestination = ForegroundDestination.FirstChat("match-1", "chat-1"),
                now = now,
            ),
        )
    }

    @Test
    fun `match found dispatch suppresses non Home foreground destination`() {
        assertEquals(
            MatchFoundDispatchAction.SuppressForeground,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:01:00Z"),
                foregroundDestination = ForegroundDestination.ProfileManagement,
                now = now,
            ),
        )
    }

    @Test
    fun `delayed match found uses latest FirstChat foreground destination`() {
        val foregroundDestination = ForegroundDestination.FirstChat("match-1", "chat-1")

        assertEquals(
            MatchFoundDispatchAction.SuppressForeground,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:01:00Z"),
                foregroundDestination = foregroundDestination,
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
                foregroundDestination = null,
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
                foregroundDestination = null,
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
                foregroundDestination = null,
                now = now,
            ),
        )
    }

    @Test
    fun `match found dispatch suppresses a live invalidation tombstone`() {
        assertEquals(
            MatchFoundDispatchAction.SuppressInvalidated,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:01:00Z"),
                foregroundDestination = null,
                now = now,
                isInvalidated = { matchId, currentNow ->
                    matchId == "match-1" && currentNow == now
                },
            ),
        )
    }

    @Test
    fun `match found invalidation decision persists only future authoritative expiry`() {
        assertEquals(
            MatchFoundInvalidationDecision.PersistAndCancel(
                matchId = "match-1",
                expiresAt = Instant.parse("2026-08-01T12:02:00Z"),
            ),
            matchFoundInvalidationDecision(
                notification = notification(
                    matchId = " match-1 ",
                    expiresAt = "2026-08-01T12:02:00Z",
                ),
                now = now,
            ),
        )
        assertEquals(
            MatchFoundInvalidationDecision.CancelOnly(matchId = "match-1"),
            matchFoundInvalidationDecision(
                notification = notification(expiresAt = "2026-08-01T12:00:00Z"),
                now = now,
            ),
        )
        assertEquals(
            MatchFoundInvalidationDecision.CancelOnly(matchId = "match-1"),
            matchFoundInvalidationDecision(
                notification = notification(expiresAt = "2026-08-01T11:59:59Z"),
                now = now,
            ),
        )
    }

    @Test
    fun `match found invalidation decision cancels only when expiry is missing blank or malformed`() {
        val expected = MatchFoundInvalidationDecision.CancelOnly(matchId = "match-1")

        assertEquals(expected, matchFoundInvalidationDecision(notification(expiresAt = null), now))
        assertEquals(expected, matchFoundInvalidationDecision(notification(expiresAt = " "), now))
        assertEquals(expected, matchFoundInvalidationDecision(notification(expiresAt = "not-a-date"), now))
    }

    @Test
    fun `match found invalidation ignores missing match id`() {
        assertEquals(
            MatchFoundInvalidationDecision.Ignore,
            matchFoundInvalidationDecision(
                notification = notification(matchId = " ", expiresAt = "2026-08-01T12:02:00Z"),
                now = now,
            ),
        )
    }

    @Test
    fun `match found invalidation timezone offset compares by instant`() {
        assertEquals(
            MatchFoundInvalidationDecision.PersistAndCancel(
                matchId = "match-1",
                expiresAt = Instant.parse("2026-08-01T12:15:00Z"),
            ),
            matchFoundInvalidationDecision(
                notification = notification(expiresAt = "2026-08-01T09:15:00-03:00"),
                now = now,
            ),
        )
    }

    @Test
    fun `match found invalidation dispatch requests Home refresh without visible presentation`() {
        assertEquals(
            MatchFoundInvalidationDispatchAction(
                decision = MatchFoundInvalidationDecision.PersistAndCancel(
                    matchId = "match-1",
                    expiresAt = Instant.parse("2026-08-01T12:02:00Z"),
                ),
                requestHomeRefresh = true,
            ),
            matchFoundInvalidationDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:02:00Z"),
                foregroundDestination = ForegroundDestination.Home,
                now = now,
            ),
        )
        assertEquals(
            MatchFoundInvalidationDispatchAction(
                decision = MatchFoundInvalidationDecision.PersistAndCancel(
                    matchId = "match-1",
                    expiresAt = Instant.parse("2026-08-01T12:02:00Z"),
                ),
                requestHomeRefresh = false,
            ),
            matchFoundInvalidationDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:02:00Z"),
                foregroundDestination = ForegroundDestination.FirstChat("match-1", "chat-1"),
                now = now,
            ),
        )
    }

    @Test
    fun `out of order invalidation suppresses delayed match found before expiry`() {
        val store = InMemoryMatchFoundInvalidationStore()
        val invalidation = matchFoundInvalidationDecision(
            notification = notification(expiresAt = "2026-08-01T12:05:00Z"),
            now = now,
        ) as MatchFoundInvalidationDecision.PersistAndCancel

        store.recordInvalidation(
            matchId = invalidation.matchId,
            expiresAt = invalidation.expiresAt,
            now = now,
        )

        assertEquals(
            MatchFoundDispatchAction.SuppressInvalidated,
            matchFoundDispatchAction(
                notification = notification(expiresAt = "2026-08-01T12:05:00Z"),
                foregroundDestination = null,
                now = now.plusSeconds(60),
                isInvalidated = store::isInvalidated,
            ),
        )
    }

    @Test
    fun `match invalidation does not suppress a different match`() {
        val store = InMemoryMatchFoundInvalidationStore()
        store.recordInvalidation(
            matchId = "match-1",
            expiresAt = Instant.parse("2026-08-01T12:05:00Z"),
            now = now,
        )

        assertEquals(
            MatchFoundDispatchAction.Present(
                matchId = "match-2",
                timeoutAfterMillis = 240_000L,
            ),
            matchFoundDispatchAction(
                notification = notification(
                    matchId = "match-2",
                    expiresAt = "2026-08-01T12:05:00Z",
                ),
                foregroundDestination = null,
                now = now.plusSeconds(60),
                isInvalidated = store::isInvalidated,
            ),
        )
    }

    private fun notification(
        expiresAt: String?,
        matchId: String? = "match-1",
    ): IncomingNotificationContext = IncomingNotificationContext(
        type = PushNotificationContract.TYPE_MATCH_FOUND,
        connectionId = null,
        matchId = matchId,
        availableAt = null,
        expiresAt = expiresAt,
    )
}
