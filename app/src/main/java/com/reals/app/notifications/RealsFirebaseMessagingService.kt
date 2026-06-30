package com.reals.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.reals.app.RealsApplication
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
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

        when (remoteMessage.data["type"]) {
            TYPE_VISUAL_REVIEW_AVAILABLE -> NotificationHelper.showVisualReviewAvailable(
                context = this,
                matchId = remoteMessage.data["matchId"],
            )

            TYPE_SCHEDULING_AVAILABLE -> NotificationHelper.showSchedulingAvailable(
                context = this,
                connectionId = remoteMessage.data["connectionId"],
                matchId = remoteMessage.data["matchId"],
            )

            TYPE_SECOND_CHAT_REMINDER -> NotificationHelper.showSecondChatReminder(
                context = this,
                connectionId = remoteMessage.data["connectionId"],
                availableAt = remoteMessage.data["availableAt"],
            )

            else -> {
                // Unknown push types are intentionally ignored.
            }
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
