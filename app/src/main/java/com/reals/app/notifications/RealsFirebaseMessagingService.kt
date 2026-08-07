package com.reals.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.reals.app.RealsApplication
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_CONFIRMED
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_PROPOSALS_RECEIVED
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_REMINDER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RealsFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val registrationService = (application as? RealsApplication)
            ?.appContainer
            ?.pushTokenRegistrationService
        if (registrationService == null) {
            Log.w(TAG, "Push token registration service unavailable.")
            return
        }

        scope.launch {
            registrationService.registerToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        NotificationHelper.ensureChannels(this)

        val notification = remoteMessage.data.incomingNotificationContext()
        val appContainer = (application as? RealsApplication)?.appContainer
        val foregroundDestination =
            appContainer?.foregroundDestinationTracker?.current()
        val presentationPolicy =
            appContainer?.notificationPresentationPolicy
                ?: NotificationPresentationPolicy()

        if (!isKnownForegroundNotificationType(notification.type)) {
            // Unknown push types are intentionally ignored.
            return
        }

        val shouldPresent = presentationPolicy.shouldPresent(
            notification = notification,
            foregroundDestination = foregroundDestination,
        )

        if (
            notification.type == TYPE_MATCH_FOUND &&
            !shouldPresent
        ) {
            appContainer
                ?.homeRefreshSignal
                ?.request()

            return
        }

        if (!shouldPresent) return

        when (notification.type) {
            TYPE_MATCH_FOUND -> NotificationHelper.showMatchFound(
                context = this,
                matchId = notification.matchId,
            )

            TYPE_VISUAL_REVIEW_REMINDER,
            TYPE_VISUAL_REVIEW_AVAILABLE -> NotificationHelper.showVisualReviewReminder(
                context = this,
                matchId = notification.matchId,
            )

            TYPE_SCHEDULING_AVAILABLE,
            TYPE_SCHEDULING_PROPOSALS_RECEIVED,
            TYPE_SCHEDULING_CONFIRMED -> NotificationHelper.showSchedulingAvailable(
                context = this,
                type = notification.type ?: TYPE_SCHEDULING_AVAILABLE,
                connectionId = notification.connectionId,
                matchId = notification.matchId,
            )

            TYPE_SECOND_CHAT_REMINDER -> NotificationHelper.showSecondChatReminder(
                context = this,
                connectionId = notification.connectionId,
                availableAt = notification.availableAt,
            )

            TYPE_SECOND_CHAT_STARTED -> NotificationHelper.showSecondChatStarted(
                context = this,
                connectionId = notification.connectionId,
                matchId = notification.matchId,
                availableAt = notification.availableAt,
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RealsMessagingService"
    }
}

internal fun Map<String, String>.incomingNotificationContext(): IncomingNotificationContext =
    IncomingNotificationContext(
        type = this["type"].trimToNonBlank(),
        connectionId = this["connectionId"].trimToNonBlank(),
        matchId = this["matchId"].trimToNonBlank(),
        availableAt = this["availableAt"].trimToNonBlank(),
    )

internal fun isKnownForegroundNotificationType(type: String?): Boolean = when (type?.trim()) {
    TYPE_MATCH_FOUND,
    TYPE_VISUAL_REVIEW_REMINDER,
    TYPE_VISUAL_REVIEW_AVAILABLE,
    TYPE_SCHEDULING_AVAILABLE,
    TYPE_SCHEDULING_PROPOSALS_RECEIVED,
    TYPE_SCHEDULING_CONFIRMED,
    TYPE_SECOND_CHAT_REMINDER,
    TYPE_SECOND_CHAT_STARTED -> true
    else -> false
}
