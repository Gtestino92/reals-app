package com.reals.app.ui.common

import java.time.Instant
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

fun formatBackendContextualDateTime(
    value: String?,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        val dateTime = OffsetDateTime.parse(value).atZoneSameInstant(zoneId)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        when (dateTime.toLocalDate()) {
            now.toLocalDate() -> "Hoy, $time"
            now.toLocalDate().plusDays(1) -> "Mañana, $time"
            else -> {
                val pattern = if (dateTime.year == now.year) {
                    "EEEE d 'de' MMMM, HH:mm"
                } else {
                    "EEEE d 'de' MMMM 'de' yyyy, HH:mm"
                }
                dateTime.format(DateTimeFormatter.ofPattern(pattern, locale))
                    .replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(locale) else char.toString()
                    }
            }
        }
    }.getOrElse {
        formatBackendDateTime(value, zoneId, locale)
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
