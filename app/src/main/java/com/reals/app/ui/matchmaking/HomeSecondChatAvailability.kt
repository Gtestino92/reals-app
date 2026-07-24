package com.reals.app.ui.matchmaking

import com.reals.app.ui.common.formatBackendTime
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private const val DEFAULT_SECOND_CHAT_DURATION_MINUTES = 120L

internal enum class SecondChatHomeState {
    Waiting,
    Preparing,
    Open,
    ReadOnly,
    ReadOnlyEnded,
    Expired,
}

internal data class HomeSecondChatPresentation(
    val state: SecondChatHomeState,
    val canOpenChat: Boolean,
    val canOpenPartnerProfile: Boolean,
    val canDismiss: Boolean,
    val primaryCtaLabel: String?,
)

internal fun HomeNextStepItem.canOpenSecondChatNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
    secondChatHomePresentation(nowMillis)?.canOpenChat == true

internal fun HomeNextStepItem.secondChatHomePresentation(
    nowMillis: Long,
): HomeSecondChatPresentation? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled,
        is HomeNextStepItem.SecondChatAvailable -> activeSecondChatPresentation(nowMillis)
        is HomeNextStepItem.SecondChatReadOnly -> readOnlySecondChatPresentation(nowMillis)
        else -> null
    }

private fun HomeNextStepItem.activeSecondChatPresentation(
    nowMillis: Long,
): HomeSecondChatPresentation {
    val now = Instant.ofEpochMilli(nowMillis)
    val availableAt = secondChatAvailableAtInstant()
    val expiresAt = secondChatExpiresAtInstant()
    val state = when {
        expiresAt != null && !now.isBefore(expiresAt) -> SecondChatHomeState.Expired
        availableAt == null || now.isBefore(availableAt) -> SecondChatHomeState.Waiting
        else -> SecondChatHomeState.Open
    }
    return HomeSecondChatPresentation(
        state = state,
        canOpenChat = state == SecondChatHomeState.Open,
        canOpenPartnerProfile = state != SecondChatHomeState.Expired,
        canDismiss = connectionIdForSecondChat().isNotBlank() &&
            (hasExpiredSecondChatStatus() || state == SecondChatHomeState.Expired),
        primaryCtaLabel = when (state) {
            SecondChatHomeState.Waiting -> secondChatAvailableAt()
                ?.let { "Disponible a las ${formatBackendTime(it)}" }
                ?: "Segundo chat pendiente"
            SecondChatHomeState.Preparing -> "Entrar al segundo chat"
            SecondChatHomeState.Open -> "Entrar al segundo chat"
            SecondChatHomeState.Expired -> "Segundo chat vencido"
            SecondChatHomeState.ReadOnly,
            SecondChatHomeState.ReadOnlyEnded -> null
        },
    )
}

private fun HomeNextStepItem.SecondChatReadOnly.readOnlySecondChatPresentation(
    nowMillis: Long,
): HomeSecondChatPresentation {
    val state = if (hasReadOnlySecondChatReference() && isReadOnlyWindowOpen(nowMillis)) {
        SecondChatHomeState.ReadOnly
    } else {
        SecondChatHomeState.ReadOnlyEnded
    }
    return HomeSecondChatPresentation(
        state = state,
        canOpenChat = state == SecondChatHomeState.ReadOnly,
        canOpenPartnerProfile = false,
        canDismiss = connectionId.isNotBlank(),
        primaryCtaLabel = if (state == SecondChatHomeState.ReadOnly) "Ver segundo chat" else null,
    )
}

internal fun HomeNextStepItem.secondChatAvailableAt(): String? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> availableAt
        is HomeNextStepItem.SecondChatAvailable -> availableAt
        is HomeNextStepItem.SecondChatReadOnly -> availableAt
        else -> null
    }

internal fun HomeNextStepItem.secondChatAvailableAtInstant(): Instant? =
    secondChatAvailableAt().toInstantOrNull()

internal fun HomeNextStepItem.secondChatExpiresAtInstant(): Instant? {
    val explicitExpiresAt = when (this) {
        is HomeNextStepItem.SecondChatScheduled -> expiresAt
        is HomeNextStepItem.SecondChatAvailable -> expiresAt
        is HomeNextStepItem.SecondChatReadOnly -> expiresAt
        else -> null
    }.toInstantOrNull()
    if (explicitExpiresAt != null) return explicitExpiresAt

    val availableAt = secondChatAvailableAtInstant() ?: return null
    return availableAt.plusSeconds(secondChatDurationMinutes() * 60L)
}

private fun HomeNextStepItem.secondChatRawExpiresAtInstant(): Instant? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> expiresAt
        is HomeNextStepItem.SecondChatAvailable -> expiresAt
        is HomeNextStepItem.SecondChatReadOnly -> expiresAt
        else -> null
    }.toInstantOrNull()

internal fun HomeNextStepItem.secondChatDurationMinutes(): Long =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> durationMinutes
        is HomeNextStepItem.SecondChatAvailable -> durationMinutes
        is HomeNextStepItem.SecondChatReadOnly -> durationMinutes
        else -> null
    } ?: DEFAULT_SECOND_CHAT_DURATION_MINUTES

internal fun HomeNextStepItem.hasOpenSecondChatReference(): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled ->
            chatId?.isNotBlank() == true && (chatStatus == "AVAILABLE" || chatStatus == "ACTIVE")
        is HomeNextStepItem.SecondChatAvailable ->
            chatId?.isNotBlank() == true && (chatStatus == "AVAILABLE" || chatStatus == "ACTIVE")
        else -> false
    }

internal fun HomeNextStepItem.hasExpiredSecondChatStatus(): Boolean =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled -> chatStatus == "EXPIRED"
        is HomeNextStepItem.SecondChatAvailable -> chatStatus == "EXPIRED"
        is HomeNextStepItem.SecondChatReadOnly -> chatStatus == "EXPIRED"
        else -> false
    }

internal fun HomeNextStepItem.connectionIdForSecondChat(): String = when (this) {
    is HomeNextStepItem.SecondChatScheduled -> connectionId
    is HomeNextStepItem.SecondChatAvailable -> connectionId
    is HomeNextStepItem.SecondChatReadOnly -> connectionId
    else -> ""
}

private fun HomeNextStepItem.SecondChatReadOnly.hasReadOnlySecondChatReference(): Boolean =
    chatId?.isNotBlank() == true && chatStatus in listOf("EXPIRED", "FINISHED", "ABANDONED")

private fun HomeNextStepItem.SecondChatReadOnly.isReadOnlyWindowOpen(nowMillis: Long): Boolean {
    val readOnlyUntilInstant = readOnlyUntil.toInstantOrNull() ?: return false
    return Instant.ofEpochMilli(nowMillis).isBefore(readOnlyUntilInstant)
}

internal fun HomeNextStepItem.isSecondChatExpiredForHome(nowMillis: Long): Boolean {
    val expiresAt = secondChatRawExpiresAtInstant() ?: secondChatExpiresAtInstant() ?: return false
    return !Instant.ofEpochMilli(nowMillis).isBefore(expiresAt)
}

internal fun String?.toInstantOrNull(): Instant? =
    this?.let { value ->
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
