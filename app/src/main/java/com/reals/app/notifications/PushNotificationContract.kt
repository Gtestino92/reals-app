package com.reals.app.notifications

object PushNotificationContract {
    const val TYPE_VISUAL_REVIEW_AVAILABLE = "VISUAL_REVIEW_AVAILABLE"

    const val EXTRA_PUSH_TYPE = "push_type"
    const val EXTRA_MATCH_ID = "match_id"
    const val EXTRA_REFRESH_HOME = "refresh_home"

    const val VISUAL_REVIEW_CHANNEL_ID = "visual_review"
    const val VISUAL_REVIEW_NOTIFICATION_ID_BASE = 10_000
}
