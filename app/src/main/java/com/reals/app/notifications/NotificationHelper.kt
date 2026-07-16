package com.reals.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reals.app.R
import com.reals.app.notifications.PushNotificationContract.SCHEDULING_AVAILABLE_NOTIFICATION_ID_BASE
import com.reals.app.notifications.PushNotificationContract.SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_REMINDER
import com.reals.app.notifications.PushNotificationContract.VISUAL_REVIEW_CHANNEL_ID
import com.reals.app.notifications.PushNotificationContract.VISUAL_REVIEW_NOTIFICATION_ID_BASE

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    internal const val VISUAL_REVIEW_CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH
    internal const val VISUAL_REVIEW_NOTIFICATION_PRIORITY = NotificationCompat.PRIORITY_HIGH

    fun ensureChannels(context: Context) {

        val channel = NotificationChannel(
            VISUAL_REVIEW_CHANNEL_ID,
            "Revisiones",
            VISUAL_REVIEW_CHANNEL_IMPORTANCE,
        ).apply {
            description = "Avisos cuando hay una revisión visual disponible"
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun showVisualReviewReminder(context: Context, matchId: String?) {
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, VISUAL_REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Revisión visual pendiente")
            .setContentText("Entrá a Reals para completarla antes de que venza.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Entrá a Reals para completarla antes de que venza."),
            )
            .setPriority(VISUAL_REVIEW_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(visualReviewPendingIntent(context, matchId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                VISUAL_REVIEW_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show visual review reminder because permission was denied.",
                exception
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun showSecondChatReminder(context: Context, connectionId: String?, availableAt: String? = null) {
        if (!canPostNotifications(context)) return

        val body = "Entr\u00e1 a Reals para ver el horario y prepararte."
        val notification = NotificationCompat.Builder(context, VISUAL_REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tu segunda charla empieza pronto")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(VISUAL_REVIEW_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(secondChatReminderPendingIntent(context, connectionId, availableAt))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE + notificationSuffix(connectionId),
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show second chat reminder because permission was denied.",
                exception
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun showSchedulingAvailable(context: Context, connectionId: String?, matchId: String?) {
        if (!canPostNotifications(context)) return

        val body = "Ya pod\u00e9s coordinar horarios en Reals."
        val notification = NotificationCompat.Builder(context, VISUAL_REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Coordinaci\u00f3n disponible")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(VISUAL_REVIEW_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(schedulingAvailablePendingIntent(context, connectionId, matchId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                SCHEDULING_AVAILABLE_NOTIFICATION_ID_BASE + notificationSuffix(connectionId ?: matchId),
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show scheduling notification because permission was denied.",
                exception
            )
        }
    }

    private fun visualReviewPendingIntent(context: Context, matchId: String?) =
        NotificationPendingIntents.mainActivity(
            context,
            VISUAL_REVIEW_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
            TYPE_VISUAL_REVIEW_REMINDER,
        )

    private fun secondChatReminderPendingIntent(
        context: Context,
        connectionId: String?,
        availableAt: String?,
    ) = NotificationPendingIntents.mainActivity(
        context,
        SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE + notificationSuffix(connectionId),
        TYPE_SECOND_CHAT_REMINDER,
        connectionId,
        null,
        availableAt,
    )

    private fun schedulingAvailablePendingIntent(
        context: Context,
        connectionId: String?,
        matchId: String?,
    ) = NotificationPendingIntents.mainActivity(
        context,
        SCHEDULING_AVAILABLE_NOTIFICATION_ID_BASE + notificationSuffix(connectionId ?: matchId),
        TYPE_SCHEDULING_AVAILABLE,
        connectionId,
        matchId,
        null,
    )

    private fun notificationSuffix(matchId: String?): Int =
        matchId?.hashCode()?.floorMod(9000) ?: 0

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
