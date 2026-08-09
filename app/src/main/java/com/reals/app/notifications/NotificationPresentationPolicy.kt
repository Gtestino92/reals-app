package com.reals.app.notifications

import com.reals.app.foreground.ForegroundDestination
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED


data class IncomingNotificationContext(
    val type: String?,
    val connectionId: String?,
    val matchId: String?,
    val availableAt: String?,
    val expiresAt: String?,
)

class NotificationPresentationPolicy {
    fun shouldPresent(
        notification: IncomingNotificationContext,
        foregroundDestination: ForegroundDestination?,
    ): Boolean {
        return when (notification.type?.trim()) {
            TYPE_MATCH_FOUND -> foregroundDestination !== ForegroundDestination.Home
            TYPE_SECOND_CHAT_STARTED -> shouldPresentSecondChatStarted(notification, foregroundDestination)
            else -> true
        }
    }

    private fun shouldPresentSecondChatStarted(
        notification: IncomingNotificationContext,
        foregroundDestination: ForegroundDestination?,
    ): Boolean {
        val payloadConnectionId = notification.connectionId.trimToNonBlank() ?: return true
        val visibleSecondChat =
            foregroundDestination as? ForegroundDestination.SecondChat ?: return true
        val visibleConnectionId = visibleSecondChat.connectionId.trimToNonBlank() ?: return true

        return visibleConnectionId != payloadConnectionId
    }
}

internal fun String?.trimToNonBlank(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
