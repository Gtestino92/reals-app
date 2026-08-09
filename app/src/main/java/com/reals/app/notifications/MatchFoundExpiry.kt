package com.reals.app.notifications

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal sealed interface MatchFoundExpiryDecision {
    data class Present(
        val timeoutAfterMillis: Long?,
    ) : MatchFoundExpiryDecision

    data object Stale : MatchFoundExpiryDecision
}

internal fun matchFoundExpiryDecision(
    expiresAt: String?,
    now: Instant,
): MatchFoundExpiryDecision {
    val normalizedExpiresAt = expiresAt.trimToNonBlank()
        ?: return MatchFoundExpiryDecision.Present(timeoutAfterMillis = null)

    val expiry = try {
        OffsetDateTime.parse(normalizedExpiresAt).toInstant()
    } catch (_: DateTimeParseException) {
        return MatchFoundExpiryDecision.Present(timeoutAfterMillis = null)
    }

    if (!expiry.isAfter(now)) return MatchFoundExpiryDecision.Stale

    val remainingMillis = Duration.between(now, expiry).toMillis()
    return if (remainingMillis > 0) {
        MatchFoundExpiryDecision.Present(timeoutAfterMillis = remainingMillis)
    } else {
        MatchFoundExpiryDecision.Stale
    }
}

internal sealed interface MatchFoundDispatchAction {
    data object RefreshHomeOnly : MatchFoundDispatchAction
    data object IgnoreStale : MatchFoundDispatchAction
    data class Present(
        val matchId: String?,
        val timeoutAfterMillis: Long?,
    ) : MatchFoundDispatchAction
}

internal fun matchFoundDispatchAction(
    notification: IncomingNotificationContext,
    shouldPresent: Boolean,
    now: Instant,
): MatchFoundDispatchAction {
    if (!shouldPresent) return MatchFoundDispatchAction.RefreshHomeOnly

    return when (val expiry = matchFoundExpiryDecision(notification.expiresAt, now)) {
        is MatchFoundExpiryDecision.Present -> MatchFoundDispatchAction.Present(
            matchId = notification.matchId,
            timeoutAfterMillis = expiry.timeoutAfterMillis,
        )

        MatchFoundExpiryDecision.Stale -> MatchFoundDispatchAction.IgnoreStale
    }
}
