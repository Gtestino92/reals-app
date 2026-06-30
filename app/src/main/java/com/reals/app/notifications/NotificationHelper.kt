package com.reals.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reals.app.MainActivity
import com.reals.app.R
import com.reals.app.notifications.PushNotificationContract.EXTRA_PUSH_TYPE
import com.reals.app.notifications.PushNotificationContract.EXTRA_REFRESH_HOME
import com.reals.app.notifications.PushNotificationContract.SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
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
    fun showVisualReviewAvailable(context: Context, matchId: String?) {
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, VISUAL_REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tenés una revisión disponible")
            .setContentText("Ya podés revisar el perfil visual de una conversación reciente.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Ya podés revisar el perfil visual de una conversación reciente."),
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
            Log.w(TAG, "Could not show visual review notification because permission was denied.", exception)
        }
    }

    @SuppressLint("MissingPermission")
    fun showSecondChatReminder(context: Context, connectionId: String?) {
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
            .setContentIntent(secondChatReminderPendingIntent(context, connectionId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE + notificationSuffix(connectionId),
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(TAG, "Could not show second chat reminder because permission was denied.", exception)
        }
    }

    private fun visualReviewPendingIntent(context: Context, matchId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            setPackage(context.packageName)
            putExtra(EXTRA_PUSH_TYPE, TYPE_VISUAL_REVIEW_AVAILABLE)
            putExtra(EXTRA_REFRESH_HOME, true)
        }

        return PendingIntent.getActivity(
            context,
            VISUAL_REVIEW_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or
                    PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun secondChatReminderPendingIntent(
        context: Context,
        connectionId: String?,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            setPackage(context.packageName)
            putExtra(EXTRA_PUSH_TYPE, TYPE_SECOND_CHAT_REMINDER)
            putExtra(EXTRA_REFRESH_HOME, true)
        }

        return PendingIntent.getActivity(
            context,
            SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE + notificationSuffix(connectionId),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or
                    PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

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
