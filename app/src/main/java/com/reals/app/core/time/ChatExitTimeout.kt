package com.reals.app.core.time

import java.time.OffsetDateTime

fun remainingExitSeconds(
    createdAt: String,
    nowMillis: Long,
    timeoutSeconds: Long,
): Long {
    val createdAtMillis = runCatching {
        OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
    }.getOrElse {
        nowMillis
    }
    val elapsedSeconds = ((nowMillis - createdAtMillis).coerceAtLeast(0L)) / 1_000L
    return (timeoutSeconds - elapsedSeconds).coerceAtLeast(0L)
}
