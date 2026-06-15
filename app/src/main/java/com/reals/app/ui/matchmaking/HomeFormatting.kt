package com.reals.app.ui.matchmaking

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun formatBackendDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault()))
    }.getOrElse {
        value.replace("T", " ").substringBeforeLast(":")
    }
}
