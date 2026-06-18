package com.reals.app.ui.common

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatBackendDate(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(zoneId)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))
    }.getOrElse {
        value.substringBefore("T").ifBlank { value }
    }
}

fun formatBackendDateTime(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(zoneId)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale))
    }.getOrElse {
        value.replace("T", " ").substringBeforeLast(":")
    }
}

fun formatBackendTime(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(zoneId)
            .format(DateTimeFormatter.ofPattern("HH:mm", locale))
    }.getOrElse {
        value.substringAfter("T", value).take(5).ifBlank { "-" }
    }
}
