package com.reals.app.ui.matchmaking

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private const val DEFAULT_SECOND_CHAT_DURATION_MINUTES = 120L

internal fun HomeNextStepItem.canOpenSecondChatNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled,
        is HomeNextStepItem.SecondChatAvailable -> isInsideSecondChatEntryWindow(nowMillis)
        is HomeNextStepItem.SecondChatReadOnly -> hasSecondChatReference()
        else -> false
    }

private fun HomeNextStepItem.isInsideSecondChatEntryWindow(nowMillis: Long): Boolean {
    val availableAt = secondChatAvailableAt().toInstantOrNull() ?: return false
    val now = Instant.ofEpochMilli(nowMillis)
    if (now.isBefore(availableAt)) return false

    val expiresAt = secondChatExpiresAtInstant() ?: availableAt.plusSeconds(secondChatDurationMinutes() * 60L)
    return now.isBefore(expiresAt)
}

private fun HomeNextStepItem.secondChatAvailableAt(): String? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> availableAt
        is HomeNextStepItem.SecondChatAvailable -> availableAt
        is HomeNextStepItem.SecondChatReadOnly -> availableAt
        else -> null
    }

private fun HomeNextStepItem.secondChatExpiresAtInstant(): Instant? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> expiresAt
        is HomeNextStepItem.SecondChatAvailable -> expiresAt
        is HomeNextStepItem.SecondChatReadOnly -> expiresAt
        else -> null
    }.toInstantOrNull()

private fun HomeNextStepItem.secondChatDurationMinutes(): Long =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> durationMinutes
        is HomeNextStepItem.SecondChatAvailable -> durationMinutes
        is HomeNextStepItem.SecondChatReadOnly -> durationMinutes
        else -> null
    } ?: DEFAULT_SECOND_CHAT_DURATION_MINUTES

private fun HomeNextStepItem.hasSecondChatReference(): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatAvailable ->
            chatId?.isNotBlank() == true && (chatStatus == "AVAILABLE" || chatStatus == "ACTIVE")
        is HomeNextStepItem.SecondChatReadOnly ->
            chatId?.isNotBlank() == true && chatStatus == "EXPIRED"
        else -> false
    }

private fun String?.toInstantOrNull(): Instant? =
    this?.let { value ->
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
