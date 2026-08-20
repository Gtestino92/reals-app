package com.reals.app.ui.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal fun backendInstantOrNull(value: String?): Instant? =
    value?.takeIf { it.isNotBlank() }?.let { timestamp ->
        try {
            OffsetDateTime.parse(timestamp).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

fun deadlineRemainingFraction(
    startedAt: String?,
    expiresAt: String?,
    nowMillis: Long,
): Double? {
    val start = backendInstantOrNull(startedAt) ?: return null
    val end = backendInstantOrNull(expiresAt) ?: return null
    val totalMillis = end.toEpochMilli() - start.toEpochMilli()
    if (totalMillis <= 0L) return null

    val remainingMillis = end.toEpochMilli() - nowMillis
    return (remainingMillis.toDouble() / totalMillis.toDouble()).coerceIn(0.0, 1.0)
}

fun deadlineElapsedFraction(
    startedAt: String?,
    expiresAt: String?,
    nowMillis: Long,
): Double? {
    val start = backendInstantOrNull(startedAt) ?: return null
    val end = backendInstantOrNull(expiresAt) ?: return null
    val totalMillis = end.toEpochMilli() - start.toEpochMilli()
    if (totalMillis <= 0L) return null

    val elapsedMillis = nowMillis - start.toEpochMilli()
    return (elapsedMillis.toDouble() / totalMillis.toDouble()).coerceIn(0.0, 1.0)
}
