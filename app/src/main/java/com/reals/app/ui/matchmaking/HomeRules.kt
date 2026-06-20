package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private const val SECOND_CHAT_NEAR_WINDOW_MILLIS = 15 * 60 * 1000L
private const val SECOND_CHAT_POLL_INTERVAL_MILLIS = 60 * 1000L

internal fun ApiError?.isActiveMatchLimitReached(): Boolean =
    this is ApiError.Backend &&
        (code == "ACTIVE_MATCH_LIMIT_REACHED" || code == "ACTIVE_CONNECTION_LIMIT_REACHED")

internal fun ApiError?.matchmakingBlockedMessage(): String? {
    if (this == null) return null

    if (isActiveMatchLimitReached()) {
        return "Ya tenes conversaciones o experiencias activas. Termina una antes de buscar otra."
    }

    return when (this) {
        is ApiError.Backend -> message.takeIf { it.isNotBlank() }
        else -> null
    }
}

internal fun emptyHomeScreenModel(): HomeScreenModel = HomeScreenModel(
    pendingActions = emptyList(),
    nextSteps = emptyList(),
    activeInteractionsSummary = null,
    passiveNotices = emptyList(),
    matchmaking = HomeMatchmakingUiState(
        inQueue = false,
        canSearch = true,
        blockedReason = null,
    ),
)

internal fun HomeScreenModel.shouldPollHome(): Boolean =
    matchmaking.inQueue ||
        pendingActions.isNotEmpty() ||
        nextSteps.isNotEmpty() ||
        passiveNotices.isNotEmpty() ||
        activeInteractionsSummary.hasActiveInteractions()

internal fun HomeScreenModel.shouldPollSecondChatAvailability(
    nowMillis: Long = System.currentTimeMillis(),
): Boolean =
    nextSteps.any { it.needsSecondChatAvailabilityPolling(nowMillis) }

internal fun HomeScreenModel.nextSecondChatPollDelayMillis(
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    val nextDueDelay = nextSteps
        .mapNotNull { it.secondChatAvailableAtInstant() }
        .map { it.toEpochMilli() - nowMillis }
        .filter { it in 1..SECOND_CHAT_POLL_INTERVAL_MILLIS }
        .minOrNull()

    return nextDueDelay ?: SECOND_CHAT_POLL_INTERVAL_MILLIS
}

private fun com.reals.app.domain.model.HomeActiveInteractionsSummary?.hasActiveInteractions(): Boolean {
    if (this == null) return false
    return activeInitialCount > 0 ||
        activeConnectionCount > 0 ||
        pendingSchedulingConnectionCount > 0 ||
        actionableConnectionCount > 0
}

private fun HomeNextStepItem.needsSecondChatAvailabilityPolling(nowMillis: Long): Boolean {
    val availableAt = secondChatAvailableAtInstant() ?: return false
    if (isStaleExpiredSecondChat(nowMillis)) return false
    val millisUntilAvailable = availableAt.toEpochMilli() - nowMillis
    val isNearOrDue = millisUntilAvailable <= SECOND_CHAT_NEAR_WINDOW_MILLIS
    if (!isNearOrDue) return false

    return when (this) {
        is HomeNextStepItem.SecondChatScheduled -> true
        is HomeNextStepItem.SecondChatAvailable -> !hasAvailableSecondChatReference()
        else -> false
    }
}

private fun HomeNextStepItem.secondChatAvailableAtInstant(): Instant? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> availableAt.toInstantOrNull()
        is HomeNextStepItem.SecondChatAvailable -> availableAt.toInstantOrNull()
        is HomeNextStepItem.SecondChatReadOnly -> availableAt.toInstantOrNull()
        else -> null
    }

private fun HomeNextStepItem.SecondChatAvailable.hasAvailableSecondChatReference(): Boolean =
    chatId?.isNotBlank() == true && (chatStatus == "AVAILABLE" || chatStatus == "ACTIVE")

private fun HomeNextStepItem.isStaleExpiredSecondChat(nowMillis: Long): Boolean {
    val expiresAt = when (this) {
        is HomeNextStepItem.SecondChatScheduled -> expiresAt
        is HomeNextStepItem.SecondChatAvailable -> expiresAt
        else -> null
    }.toInstantOrNull() ?: return false

    return !hasAnySecondChatReference() && !Instant.ofEpochMilli(nowMillis).isBefore(expiresAt)
}

private fun HomeNextStepItem.hasAnySecondChatReference(): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatAvailable -> hasAvailableSecondChatReference()
        is HomeNextStepItem.SecondChatReadOnly -> chatId?.isNotBlank() == true && chatStatus == "EXPIRED"
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
