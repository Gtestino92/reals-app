package com.reals.app.core.time

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

fun backendInstantOrNull(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

fun remainingMillisUntil(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    val instant = backendInstantOrNull(value) ?: return null
    return instant.toEpochMilli() - nowMillis
}

fun remainingSecondsUntil(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? =
    remainingMillisUntil(value, nowMillis)?.let { millis ->
        (millis.coerceAtLeast(0L) + 999L) / 1000L
    }

fun isExpired(value: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val remaining = remainingMillisUntil(value, nowMillis) ?: return false
    return remaining <= 0L
}

fun isWithinWarningWindow(
    value: String?,
    nowMillis: Long = System.currentTimeMillis(),
    warningMillis: Long,
): Boolean {
    val remaining = remainingMillisUntil(value, nowMillis) ?: return false
    return remaining in 1..warningMillis
}
