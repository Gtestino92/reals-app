package com.reals.app.notifications

import com.reals.app.foreground.ForegroundDestination
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
    val expiry = parseNotificationExpiresAt(expiresAt)
        ?: return MatchFoundExpiryDecision.Present(timeoutAfterMillis = null)

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
    data object SuppressInvalidated : MatchFoundDispatchAction
    data class Present(
        val matchId: String?,
        val timeoutAfterMillis: Long?,
    ) : MatchFoundDispatchAction
}

internal fun matchFoundDispatchAction(
    notification: IncomingNotificationContext,
    shouldPresent: Boolean,
    now: Instant,
    isInvalidated: (String, Instant) -> Boolean = { _, _ -> false },
): MatchFoundDispatchAction {
    if (!shouldPresent) return MatchFoundDispatchAction.RefreshHomeOnly

    val matchId = notification.matchId.trimToNonBlank()
    if (matchId != null && isInvalidated(matchId, now)) {
        return MatchFoundDispatchAction.SuppressInvalidated
    }

    return when (val expiry = matchFoundExpiryDecision(notification.expiresAt, now)) {
        is MatchFoundExpiryDecision.Present -> MatchFoundDispatchAction.Present(
            matchId = notification.matchId,
            timeoutAfterMillis = expiry.timeoutAfterMillis,
        )

        MatchFoundExpiryDecision.Stale -> MatchFoundDispatchAction.IgnoreStale
    }
}

internal sealed interface MatchFoundInvalidationDecision {
    data object Ignore : MatchFoundInvalidationDecision
    data class CancelOnly(
        val matchId: String,
    ) : MatchFoundInvalidationDecision

    data class PersistAndCancel(
        val matchId: String,
        val expiresAt: Instant,
    ) : MatchFoundInvalidationDecision
}

internal fun matchFoundInvalidationDecision(
    notification: IncomingNotificationContext,
    now: Instant,
): MatchFoundInvalidationDecision {
    val matchId = notification.matchId.trimToNonBlank()
        ?: return MatchFoundInvalidationDecision.Ignore
    val expiresAt = parseNotificationExpiresAt(notification.expiresAt)

    return if (expiresAt != null && expiresAt.isAfter(now)) {
        MatchFoundInvalidationDecision.PersistAndCancel(
            matchId = matchId,
            expiresAt = expiresAt,
        )
    } else {
        MatchFoundInvalidationDecision.CancelOnly(matchId = matchId)
    }
}

internal data class MatchFoundInvalidationDispatchAction(
    val decision: MatchFoundInvalidationDecision,
    val requestHomeRefresh: Boolean,
)

internal fun matchFoundInvalidationDispatchAction(
    notification: IncomingNotificationContext,
    foregroundDestination: ForegroundDestination?,
    now: Instant,
): MatchFoundInvalidationDispatchAction =
    MatchFoundInvalidationDispatchAction(
        decision = matchFoundInvalidationDecision(
            notification = notification,
            now = now,
        ),
        requestHomeRefresh = foregroundDestination === ForegroundDestination.Home,
    )

internal fun parseNotificationExpiresAt(expiresAt: String?): Instant? {
    val normalizedExpiresAt = expiresAt.trimToNonBlank() ?: return null

    return try {
        OffsetDateTime.parse(normalizedExpiresAt).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}
