package com.reals.app.ui.common

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val backendDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

private val backendDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())

private val backendTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun formatBackendDate(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(backendDateFormatter)
    }.getOrElse {
        value.substringBefore("T").ifBlank { value }
    }
}

fun formatBackendDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(backendDateTimeFormatter)
    }.getOrElse {
        value.replace("T", " ").substringBeforeLast(":")
    }
}

fun formatBackendTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(backendTimeFormatter)
    }.getOrElse {
        value.substringAfter("T", value).take(5).ifBlank { "-" }
    }
}
