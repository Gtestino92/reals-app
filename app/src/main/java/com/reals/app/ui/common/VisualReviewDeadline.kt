package com.reals.app.ui.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt

data class VisualReviewHomeDeadlineStrings(
    val expired: String,
    val underOneHour: String,
    val today: String,
    val tomorrow: String,
    val laterSameYear: String,
    val laterDifferentYear: String,
)

data class VisualReviewDetailDeadlineStrings(
    val futureSameYear: String,
    val futureDifferentYear: String,
    val pastSameYear: String,
    val pastDifferentYear: String,
)

enum class VisualReviewProgressUrgency {
    Normal,
    Warning,
    Critical,
}

internal val defaultVisualReviewHomeDeadlineStrings = VisualReviewHomeDeadlineStrings(
    expired = "vencida",
    underOneHour = "vence en %d min",
    today = "vence hoy %s",
    tomorrow = "vence mañana %s",
    laterSameYear = "vence el %s %d de %s, %s",
    laterDifferentYear = "vence el %s %d de %s de %d, %s",
)

internal val defaultVisualReviewDetailDeadlineStrings = VisualReviewDetailDeadlineStrings(
    futureSameYear = "Vence el %s %d de %s a las %s",
    futureDifferentYear = "Vence el %s %d de %s de %d a las %s",
    pastSameYear = "Venció el %s %d de %s a las %s",
    pastDifferentYear = "Venció el %s %d de %s de %d a las %s",
)

fun formatVisualReviewHomeDeadline(
    visualExpiresAt: String?,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
    strings: VisualReviewHomeDeadlineStrings = defaultVisualReviewHomeDeadlineStrings,
): String? {
    val expiresAt = visualExpiresAt.toBackendInstantOrNull() ?: return null
    val now = Instant.ofEpochMilli(nowMillis)
    val expiresDateTime = expiresAt.atZone(zoneId)
    val nowDateTime = now.atZone(zoneId)
    val time = expiresDateTime.format(DateTimeFormatter.ofPattern("HH:mm", locale))

    if (!now.isBefore(expiresAt)) return strings.expired

    val remainingMillis = expiresAt.toEpochMilli() - nowMillis
    if (remainingMillis < 60 * 60_000L) {
        val remainingMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
        return strings.underOneHour.format(locale, remainingMinutes)
    }

    return when (expiresDateTime.toLocalDate()) {
        nowDateTime.toLocalDate() -> strings.today.format(locale, time)
        nowDateTime.toLocalDate().plusDays(1) -> strings.tomorrow.format(locale, time)
        else -> {
            val weekday = expiresDateTime.format(DateTimeFormatter.ofPattern("EEEE", locale))
            val month = expiresDateTime.format(DateTimeFormatter.ofPattern("MMMM", locale))
            if (expiresDateTime.year == nowDateTime.year) {
                strings.laterSameYear.format(locale, weekday, expiresDateTime.dayOfMonth, month, time)
            } else {
                strings.laterDifferentYear.format(
                    locale,
                    weekday,
                    expiresDateTime.dayOfMonth,
                    month,
                    expiresDateTime.year,
                    time,
                )
            }
        }
    }
}

fun formatVisualReviewDetailDeadline(
    visualExpiresAt: String?,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
    strings: VisualReviewDetailDeadlineStrings = defaultVisualReviewDetailDeadlineStrings,
): String? {
    val expiresAt = visualExpiresAt.toBackendInstantOrNull() ?: return null
    val now = Instant.ofEpochMilli(nowMillis)
    val expiresDateTime = expiresAt.atZone(zoneId)
    val nowDateTime = now.atZone(zoneId)
    val weekday = expiresDateTime.format(DateTimeFormatter.ofPattern("EEEE", locale))
    val month = expiresDateTime.format(DateTimeFormatter.ofPattern("MMMM", locale))
    val time = expiresDateTime.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    val future = now.isBefore(expiresAt)
    val sameYear = expiresDateTime.year == nowDateTime.year

    return when {
        future && sameYear ->
            strings.futureSameYear.format(locale, weekday, expiresDateTime.dayOfMonth, month, time)
        future ->
            strings.futureDifferentYear.format(
                locale,
                weekday,
                expiresDateTime.dayOfMonth,
                month,
                expiresDateTime.year,
                time,
            )
        sameYear ->
            strings.pastSameYear.format(locale, weekday, expiresDateTime.dayOfMonth, month, time)
        else ->
            strings.pastDifferentYear.format(
                locale,
                weekday,
                expiresDateTime.dayOfMonth,
                month,
                expiresDateTime.year,
                time,
            )
    }
}

fun visualReviewRemainingFraction(
    visualStartedAt: String?,
    visualExpiresAt: String?,
    nowMillis: Long,
): Double? {
    val startedAt = visualStartedAt.toBackendInstantOrNull() ?: return null
    val expiresAt = visualExpiresAt.toBackendInstantOrNull() ?: return null
    val totalMillis = expiresAt.toEpochMilli() - startedAt.toEpochMilli()
    if (totalMillis <= 0L) return null

    val remainingMillis = expiresAt.toEpochMilli() - nowMillis
    val fraction = remainingMillis.toDouble() / totalMillis.toDouble()
    return fraction.coerceIn(0.0, 1.0)
}

fun visualReviewProgressUrgency(remainingFraction: Double): VisualReviewProgressUrgency =
    when {
        remainingFraction <= 0.10 -> VisualReviewProgressUrgency.Critical
        remainingFraction <= 0.40 -> VisualReviewProgressUrgency.Warning
        else -> VisualReviewProgressUrgency.Normal
    }

fun visualReviewRemainingPercentLabel(remainingFraction: Double): String =
    "${(remainingFraction.coerceIn(0.0, 1.0) * 100).roundToInt()} % del tiempo restante"

private fun String?.toBackendInstantOrNull(): Instant? =
    this?.takeIf { it.isNotBlank() }?.let { value ->
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
