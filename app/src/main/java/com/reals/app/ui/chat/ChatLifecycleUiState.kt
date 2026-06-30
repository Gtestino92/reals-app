package com.reals.app.ui.chat

import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatType

private const val FIRST_CHAT_COUNTDOWN_WARNING_MILLIS = 60_000L

internal enum class FirstChatExpiryReason {
    Absolute,
    Inactivity,
}

internal data class FirstChatLifecycleUiState(
    val deadline: String?,
    val reason: FirstChatExpiryReason,
    val remainingMillis: Long,
    val showCountdown: Boolean,
    val expired: Boolean,
) {
    val remainingSeconds: Long = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
}

internal fun firstChatLifecycleUiState(
    chat: Chat?,
    nowMillis: Long = System.currentTimeMillis(),
): FirstChatLifecycleUiState? {
    if (chat?.chatType != ChatType.FirstChat) return null

    val deadlines = listOfNotNull(
        chat.expiresAt.takeIf { it.isNotBlank() }?.let {
            FirstChatExpiryReason.Absolute to it
        },
        chat.inactivityExpiresAt?.takeIf { it.isNotBlank() }?.let {
            FirstChatExpiryReason.Inactivity to it
        },
    )

    val effective = deadlines.minByOrNull { (_, value) ->
        backendInstantOrNull(value)?.toEpochMilli() ?: Long.MAX_VALUE
    } ?: return null

    val deadlineMillis = backendInstantOrNull(effective.second)?.toEpochMilli() ?: return null
    val remainingMillis = deadlineMillis - nowMillis
    return FirstChatLifecycleUiState(
        deadline = effective.second,
        reason = effective.first,
        remainingMillis = remainingMillis,
        showCountdown = remainingMillis in 1..FIRST_CHAT_COUNTDOWN_WARNING_MILLIS,
        expired = remainingMillis <= 0L,
    )
}

internal fun FirstChatLifecycleUiState.warningCopy(): String =
    when (reason) {
        FirstChatExpiryReason.Absolute -> "El chat vence en ${remainingSeconds}s."
        FirstChatExpiryReason.Inactivity -> "El chat se cierra por inactividad en ${remainingSeconds}s."
    }

internal fun FirstChatLifecycleUiState.expiredCopy(): String =
    when (reason) {
        FirstChatExpiryReason.Absolute -> "El chat venci\u00f3. Actualizando estado..."
        FirstChatExpiryReason.Inactivity -> "El chat se cerr\u00f3 por inactividad. Actualizando estado..."
    }
