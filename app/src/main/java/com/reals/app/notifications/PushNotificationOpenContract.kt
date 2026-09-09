package com.reals.app.notifications

import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_CONFIRMED
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_PROPOSALS_RECEIVED
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_REMINDER

object PushNotificationOpenContract {
    fun resolveType(
        internalPushType: String?,
        rawFcmType: String?,
    ): String? = internalPushType.trimToNonBlank() ?: rawFcmType.trimToNonBlank()

    fun shouldHandleExternalOpen(type: String?): Boolean = when (type?.trim()) {
        TYPE_MATCH_FOUND,
        TYPE_VISUAL_REVIEW_AVAILABLE,
        TYPE_VISUAL_REVIEW_REMINDER,
        TYPE_SCHEDULING_AVAILABLE,
        TYPE_SCHEDULING_PROPOSALS_RECEIVED,
        TYPE_SCHEDULING_CONFIRMED,
        TYPE_SECOND_CHAT_REMINDER,
        TYPE_SECOND_CHAT_STARTED -> true
        else -> false
    }
}
